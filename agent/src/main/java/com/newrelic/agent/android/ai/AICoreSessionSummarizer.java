/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.ai;

import android.content.Context;

import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.EmptyCoroutineContext;

/**
 * Adapter over AICore's free-form {@code GenerativeModel}, which runs Gemini Nano with a prompt we
 * control.
 *
 * <h3>Why this exists alongside {@link MLKitSessionSummarizer}</h3>
 * ML Kit's summarization task refuses session telemetry outright -- measured on a Galaxy S25, it
 * summarizes news-article prose in ~3.4s and rejects every rendering of a session record with
 * {@code Couldn't generate a response}, regardless of shape, length, or identifier style. See that
 * class's javadoc for the full experiment. It is a purpose-built article summarizer with a quality
 * gate and no instruction channel. AICore has no such gate and takes a real prompt, which is what
 * {@link SessionSummaryPrompt#INSTRUCTIONS} is for.
 *
 * <h3>Why Java and reflection rather than a Kotlin adapter</h3>
 * {@code GenerativeModel.generateContent} is a Kotlin suspend function, so the obvious approach is a
 * Kotlin adapter with {@code compileOnly} on the AICore artifact. That does not compile here: AICore
 * requires kotlin-stdlib 2.1.0 and this module builds with Kotlin 1.7.0, and a 1.7 compiler cannot
 * read 2.1 metadata. Upgrading the agent's Kotlin to satisfy one optional adapter is not a trade
 * worth making.
 *
 * So the suspend call is bridged by hand. {@code kotlin.coroutines.Continuation} is a two-method
 * interface and kotlin-stdlib is already a dependency of this module, so a Java implementation of it
 * needs no reflection at all -- only the AICore types do. {@link BlockingContinuation} below is that
 * bridge: it either receives the result through {@code resumeWith} or, if the call completed without
 * suspending, takes the directly-returned value.
 *
 * <h3>Model download: what this cannot guarantee</h3>
 * The ML Kit adapter can refuse to run when its model is merely downloadable, because ML Kit exposes
 * a feature-status check. <b>AICore exposes no equivalent and no way to decline a download</b> --
 * {@code DownloadConfig} accepts only an observer callback. Preparing the model may therefore start a
 * download that the agent cannot prevent.
 *
 * What is enforced instead: a download callback that, on the first sign of a download starting,
 * abandons the inference and disables this summarizer for the rest of the process, so the agent never
 * waits on a download and never triggers a second one. Consent comes from the host app, which must
 * both enable {@code FeatureFlag.SessionSummarization} and put AICore on its own classpath. That is a
 * weaker guarantee than the ML Kit path offers and the difference is deliberate, not overlooked.
 *
 * <h3>Measured result: the free-form feature is access-gated</h3>
 * On a Galaxy S25 (Android 16, Google AICore 0.release.qc.prod_aicore_20260723, Samsung AICore
 * 3.0.02.7), both {@code prepareInferenceEngine()} and {@code generateContent()} fail in under 20ms
 * with {@code InferenceException: error type 2-INFERENCE_ERROR, error code 8-NOT_AVAILABLE: Required
 * LLM feature not found}, and no download callback fires at all -- AICore does not attempt to fetch
 * the feature, it reports that there is nothing to fetch.
 *
 * This is not a defect in the bridge below. The identical failure was reproduced by calling AICore
 * natively from Kotlin with {@code runBlocking} in the host app, bypassing all the reflection here.
 * The service binding itself succeeds -- logcat shows {@code bindService is allowed by freecess,
 * caller is: <app package>} -- so the IPC works and the service answers; the feature is simply not
 * provisioned for this application.
 *
 * That matches how the two SDKs are gated. ML Kit GenAI ships its own provisioned task features and
 * works on any AICore device (its 13MB summarization adapter downloaded and ran here). Free-form
 * Gemini Nano through {@code com.google.ai.edge.aicore} is an experimental SDK whose access is
 * granted per application package by Google. Until this app's package is allowlisted, this adapter
 * will correctly report unavailable and the feature will no-op.
 *
 * So the state of play: the agent-side pipeline is complete and verified, ML Kit is a measured dead
 * end for this content, and this path is blocked on an access request rather than on code.
 *
 * <h3>Symbol names</h3>
 * Verified against {@code com.google.ai.edge.aicore:aicore:0.0.1-exp02} by inspecting the published
 * AAR. It is an experimental artifact; every lookup logs the exact symbol it could not find.
 */
public class AICoreSessionSummarizer implements SessionSummarizer {

    private static final AgentLog log = AgentLogManager.getAgentLog();

    static final String CLASS_GENERATIVE_MODEL = "com.google.ai.edge.aicore.GenerativeModel";
    static final String CLASS_GENERATION_CONFIG = "com.google.ai.edge.aicore.GenerationConfig";
    static final String CLASS_GENERATION_CONFIG_BUILDER = "com.google.ai.edge.aicore.GenerationConfig$Builder";
    static final String CLASS_DOWNLOAD_CONFIG = "com.google.ai.edge.aicore.DownloadConfig";
    static final String CLASS_DOWNLOAD_CALLBACK = "com.google.ai.edge.aicore.DownloadCallback";

    static final String CLASS_COROUTINE_SINGLETONS = "kotlin.coroutines.intrinsics.CoroutineSingletons";
    static final String CLASS_RESULT = "kotlin.Result";
    static final String CLASS_RESULT_FAILURE = "kotlin.Result$Failure";
    static final String COROUTINE_SUSPENDED = "COROUTINE_SUSPENDED";

    static final String MODEL_NAME = "aicore-gemini-nano";

    /**
     * Low temperature and a tight token budget: this is an extraction-and-compression task, not a
     * creative one, and the attribute is capped at 1000 characters downstream anyway.
     */
    private static final float TEMPERATURE = 0.2f;
    private static final int TOP_K = 16;
    private static final int MAX_OUTPUT_TOKENS = 256;
    private static final int CANDIDATE_COUNT = 1;

    private static final long GENERATE_TIMEOUT_SECONDS = 20L;

    private final Context context;
    private final boolean symbolsResolved;

    /** Set if AICore ever signals a download. Sticky: disables this summarizer for the process. */
    private final AtomicBoolean downloadObserved = new AtomicBoolean(false);

    private volatile Object generativeModel;

    public AICoreSessionSummarizer(Context context) {
        this.context = context;
        this.symbolsResolved = resolveSymbols();
    }

    private static boolean resolveSymbols() {
        final String[] required = {
                CLASS_GENERATIVE_MODEL,
                CLASS_GENERATION_CONFIG_BUILDER,
                CLASS_DOWNLOAD_CONFIG,
                CLASS_DOWNLOAD_CALLBACK
        };

        for (String className : required) {
            try {
                Class.forName(className);
            } catch (Throwable t) {
                log.debug("SessionSummary: AICore not present (missing " + className + ")");
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean isAvailable() {
        return symbolsResolved && context != null && !downloadObserved.get();
    }

    @Override
    public String getModelName() {
        return MODEL_NAME;
    }

    /**
     * The whole reason this adapter exists: AICore takes a free-form prompt, so
     * {@link SessionSummaryPrompt#INSTRUCTIONS} is prepended to the session record.
     */
    @Override
    public boolean acceptsInstructions() {
        return true;
    }

    /**
     * No input floor. ML Kit's 400-character ARTICLE minimum is a property of that task, not of
     * Gemini Nano, so short sessions are summarizable here.
     */
    @Override
    public int getMinimumInputChars() {
        return 0;
    }

    @Override
    public void summarize(String promptText, Callback callback) {
        if (!isAvailable()) {
            callback.onFailure(downloadObserved.get()
                    ? "disabled after AICore signalled a model download"
                    : "aicore not present");
            return;
        }

        try {
            final Object model = obtainModel();
            if (model == null) {
                callback.onFailure("could not create GenerativeModel");
                return;
            }

            final String composed = SessionSummaryPrompt.compose(promptText, acceptsInstructions());

            final String text = generateContent(model, composed);

            if (downloadObserved.get()) {
                callback.onFailure("abandoned: AICore started a model download");
                return;
            }

            if (text == null || text.trim().isEmpty()) {
                callback.onFailure("model returned no text");
                return;
            }

            callback.onSummary(text);
        } catch (Throwable t) {
            callback.onFailure(describe(t));
        }
    }

    /**
     * Walks to the root cause. AICore's real diagnostics live several wrappings down -- reflection's
     * InvocationTargetException around our own wrapper around the GenerativeAIException -- and a
     * failure reason of "RuntimeException: AICore inference failed" is useless in a log.
     */
    static String describe(Throwable t) {
        final StringBuilder chain = new StringBuilder();

        Throwable current = t;
        int depth = 0;
        while (current != null && depth++ < 5) {
            if (chain.length() > 0) {
                chain.append(" <- ");
            }
            chain.append(current.getClass().getSimpleName());
            if (current.getMessage() != null) {
                chain.append('(').append(current.getMessage()).append(')');
            }

            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }

        return chain.toString();
    }

    private Object obtainModel() throws Exception {
        if (generativeModel != null) {
            return generativeModel;
        }

        synchronized (this) {
            if (generativeModel != null) {
                return generativeModel;
            }

            final Object generationConfig = buildGenerationConfig();
            final Object downloadConfig = buildDownloadConfig();

            final Class<?> modelClass = Class.forName(CLASS_GENERATIVE_MODEL);
            final Class<?> configClass = Class.forName(CLASS_GENERATION_CONFIG);
            final Class<?> downloadConfigClass = Class.forName(CLASS_DOWNLOAD_CONFIG);

            final Constructor<?> constructor =
                    modelClass.getConstructor(configClass, downloadConfigClass);

            generativeModel = constructor.newInstance(generationConfig, downloadConfig);
            return generativeModel;
        }
    }

    /**
     * GenerationConfig.Builder is a plain JavaBean-style builder -- no-arg constructor plus setters
     * that return void -- so it is straightforward to drive reflectively.
     */
    private Object buildGenerationConfig() throws Exception {
        final Class<?> builderClass = Class.forName(CLASS_GENERATION_CONFIG_BUILDER);
        final Object builder = builderClass.getConstructor().newInstance();

        builderClass.getMethod("setContext", Context.class).invoke(builder, context);

        trySet(builderClass, builder, "setTemperature", Float.class, TEMPERATURE);
        trySet(builderClass, builder, "setTopK", Integer.class, TOP_K);
        trySet(builderClass, builder, "setMaxOutputTokens", Integer.class, MAX_OUTPUT_TOKENS);
        trySet(builderClass, builder, "setCandidateCount", Integer.class, CANDIDATE_COUNT);

        return builderClass.getMethod("build").invoke(builder);
    }

    private void trySet(Class<?> builderClass, Object builder, String setter,
                        Class<?> parameterType, Object value) {
        try {
            builderClass.getMethod(setter, parameterType).invoke(builder, value);
        } catch (Throwable t) {
            log.debug("SessionSummary: could not set " + setter + "; using AICore default");
        }
    }

    /**
     * Builds a DownloadConfig whose callback exists purely to detect that a download is happening.
     * AICore offers no way to forbid one, so the agent's response is to stop using this summarizer
     * rather than to wait on bytes it never asked for.
     */
    private Object buildDownloadConfig() throws Exception {
        final Class<?> callbackClass = Class.forName(CLASS_DOWNLOAD_CALLBACK);

        final Object callback = Proxy.newProxyInstance(
                callbackClass.getClassLoader(),
                new Class<?>[]{callbackClass},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        final String name = method.getName();

                        if ("onDownloadStarted".equals(name)
                                || "onDownloadPending".equals(name)
                                || "onDownloadProgress".equals(name)) {
                            if (downloadObserved.compareAndSet(false, true)) {
                                log.debug("SessionSummary: AICore signalled a model download ("
                                        + name + "); disabling on-device summarization for this process");
                            }
                        } else if ("onDownloadFailed".equals(name)
                                || "onDownloadDidNotStart".equals(name)) {
                            downloadObserved.set(true);
                            log.debug("SessionSummary: AICore download unavailable (" + name + ")");
                        }

                        // Every method on DownloadCallback returns void.
                        return null;
                    }
                });

        final Class<?> downloadConfigClass = Class.forName(CLASS_DOWNLOAD_CONFIG);
        return downloadConfigClass.getConstructor(callbackClass).newInstance(callback);
    }

    /**
     * Invokes the suspend function {@code generateContent(String, Continuation)} and blocks for the
     * result.
     *
     * A suspend function compiled to JVM bytecode either returns its value directly, when it happened
     * not to suspend, or returns the {@code COROUTINE_SUSPENDED} marker and delivers the value later
     * through the continuation. Both paths are handled; treating only one of them as real is the
     * classic way to get a bridge like this subtly wrong.
     */
    private String generateContent(Object model, String promptText) throws Exception {
        final BlockingContinuation continuation = new BlockingContinuation();

        final Method generateContent = model.getClass()
                .getMethod("generateContent", String.class, Continuation.class);

        final Object returned = generateContent.invoke(model, promptText, continuation);

        final Object response;
        if (isCoroutineSuspended(returned)) {
            response = continuation.await(GENERATE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } else {
            response = returned;
        }

        if (response == null) {
            return null;
        }

        final Object text = response.getClass().getMethod("getText").invoke(response);
        return (text == null) ? null : text.toString();
    }

    private static boolean isCoroutineSuspended(Object returned) {
        return returned instanceof Enum
                && CLASS_COROUTINE_SINGLETONS.equals(returned.getClass().getName())
                && COROUTINE_SUSPENDED.equals(((Enum<?>) returned).name());
    }

    @Override
    public void shutdown() {
        final Object model = generativeModel;
        if (model == null) {
            return;
        }

        generativeModel = null;

        try {
            // GenerativeModel implements AutoCloseable.
            model.getClass().getMethod("close").invoke(model);
        } catch (Throwable t) {
            log.debug("SessionSummary: failed to close GenerativeModel: " + t);
        }
    }

    /**
     * Java implementation of {@code kotlin.coroutines.Continuation} that parks the calling thread
     * until the coroutine completes.
     *
     * Blocking is correct here: this only ever runs on the controller's dedicated single worker
     * thread, which exists to be blocked, and the controller applies its own outer timeout.
     */
    static final class BlockingContinuation implements Continuation<Object> {

        private final CountDownLatch latch = new CountDownLatch(1);

        private volatile Object value;
        private volatile Throwable failure;

        @Override
        public CoroutineContext getContext() {
            return EmptyCoroutineContext.INSTANCE;
        }

        @Override
        public void resumeWith(Object result) {
            final Object unwrapped = unbox(result);

            if (unwrapped != null && CLASS_RESULT_FAILURE.equals(unwrapped.getClass().getName())) {
                failure = readFailureException(unwrapped);
            } else {
                value = unwrapped;
            }

            latch.countDown();
        }

        Object await(long timeout, TimeUnit unit) throws Exception {
            if (!latch.await(timeout, unit)) {
                throw new IllegalStateException("AICore did not respond within " + timeout + " " + unit);
            }

            if (failure != null) {
                throw new RuntimeException("AICore inference failed", failure);
            }

            return value;
        }

        /**
         * {@code resumeWith} takes a {@code kotlin.Result}, which is a value class. In the interface's
         * erased signature it normally arrives already unboxed -- the success value itself, or a
         * {@code Result.Failure} -- but a boxed {@code Result} is handled too rather than assumed
         * away. {@code unbox-impl} cannot be named from Java source because of the hyphen, hence
         * reflection.
         */
        private static Object unbox(Object result) {
            if (result == null || !CLASS_RESULT.equals(result.getClass().getName())) {
                return result;
            }

            try {
                return result.getClass().getMethod("unbox-impl").invoke(result);
            } catch (Throwable t) {
                log.debug("SessionSummary: could not unbox kotlin.Result: " + t);
                return result;
            }
        }

        private static Throwable readFailureException(Object failureObject) {
            try {
                final Object exception = failureObject.getClass().getField("exception").get(failureObject);
                return (exception instanceof Throwable)
                        ? (Throwable) exception
                        : new RuntimeException(String.valueOf(exception));
            } catch (Throwable t) {
                return new RuntimeException("AICore failed, and the cause could not be read", t);
            }
        }
    }
}
