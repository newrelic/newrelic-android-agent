/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.ai;

import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Adapter over ML Kit's GenAI <b>Prompt</b> API ({@code com.google.mlkit:genai-prompt}) -- free-form
 * Gemini Nano with a prompt we control.
 *
 * <h3>Why this is the preferred adapter</h3>
 * Of the three on-device paths tried, this is the only one that is both free-form and generally
 * distributed:
 * <ul>
 *   <li>{@code genai-summarization} is provisioned everywhere AICore exists, but it is a fixed-purpose
 *       article summarizer that <em>refuses session records outright</em> (measured; see
 *       {@link MLKitSessionSummarizer}). No instruction channel to argue with.</li>
 *   <li>{@code com.google.ai.edge.aicore} is free-form but requires per-application registration in
 *       Google's experimental-access program. Denied apps get
 *       {@code 601-BINDING_FAILURE: AiCore service failed to bind} (observed on Pixel), or
 *       {@code 8-NOT_AVAILABLE} where the feature is absent entirely (observed on Galaxy S25). It is
 *       also frozen at {@code 0.0.1-exp02}.</li>
 *   <li>This API needs no allowlist and ships a supported Java surface.</li>
 * </ul>
 *
 * <h3>No Continuation bridge needed</h3>
 * Unlike the AICore SDK, this one ships {@code prompt.java.GenerativeModelFutures}, whose methods
 * return Guava {@code ListenableFuture}. Since that extends {@link Future}, results are awaited with
 * a plain cast -- none of the hand-rolled Kotlin coroutine plumbing
 * {@link AICoreSessionSummarizer} needs.
 *
 * <h3>{@code download()} is never called, and that is partly a safety measure</h3>
 * The agent does not download models, as always. Here there is a second reason: on a device where the
 * feature was unavailable, {@code genai-prompt:1.0.0-beta4} threw
 * {@code NoSuchMethodError: kotlinx.coroutines.Job.cancel$default} from inside ML Kit's own thread
 * pool while tearing down the {@code Flow} behind {@code download()} -- an uncatchable crash of the
 * host process from a background thread. Only {@code checkStatus}, {@code warmup} and
 * {@code generateContent} are touched here, and an unavailable feature is simply reported unavailable.
 *
 * <h3>Symbol names</h3>
 * Verified against {@code com.google.mlkit:genai-prompt:1.0.0-beta4} by inspecting the published AAR.
 * Every lookup logs the exact symbol it could not find.
 */
public class MLKitPromptSessionSummarizer implements SessionSummarizer {

    private static final AgentLog log = AgentLogManager.getAgentLog();

    static final String CLASS_GENERATION = "com.google.mlkit.genai.prompt.Generation";
    static final String CLASS_GENERATIVE_MODEL = "com.google.mlkit.genai.prompt.GenerativeModel";
    static final String CLASS_MODEL_FUTURES = "com.google.mlkit.genai.prompt.java.GenerativeModelFutures";
    static final String CLASS_FEATURE_STATUS = "com.google.mlkit.genai.common.FeatureStatus";

    static final String MODEL_NAME = "mlkit-genai-prompt";

    /** {@code FeatureStatus.AVAILABLE} in genai-common. 0=UNAVAILABLE, 1=DOWNLOADABLE, 2=DOWNLOADING. */
    static final int FEATURE_STATUS_AVAILABLE_FALLBACK = 3;

    private static final long STATUS_TIMEOUT_SECONDS = 10L;
    private static final long WARMUP_TIMEOUT_SECONDS = 30L;
    private static final long GENERATE_TIMEOUT_SECONDS = 30L;

    private final boolean symbolsResolved;

    private volatile Object modelFutures;
    private volatile boolean warmedUp;

    public MLKitPromptSessionSummarizer() {
        this.symbolsResolved = resolveSymbols();
    }

    private static boolean resolveSymbols() {
        final String[] required = {
                CLASS_GENERATION,
                CLASS_GENERATIVE_MODEL,
                CLASS_MODEL_FUTURES,
                CLASS_FEATURE_STATUS
        };

        for (String className : required) {
            try {
                Class.forName(className);
            } catch (Throwable t) {
                log.debug("SessionSummary: ML Kit GenAI Prompt not present (missing " + className + ")");
                return false;
            }
        }

        return true;
    }

    /**
     * Only reports whether the SDK is on the classpath. Whether the model feature is provisioned is
     * decided in {@link #summarize} instead, because {@code checkStatus()} blocks on a future and this
     * is called from the harvest thread.
     */
    @Override
    public boolean isAvailable() {
        return symbolsResolved;
    }

    @Override
    public String getModelName() {
        return MODEL_NAME;
    }

    /** The point of this adapter: a free-form prompt, so the instruction block is used. */
    @Override
    public boolean acceptsInstructions() {
        return true;
    }

    /** No input floor. The 400-character minimum belongs to the summarization task, not to the model. */
    @Override
    public int getMinimumInputChars() {
        return 0;
    }

    @Override
    public void summarize(String promptText, Callback callback) {
        if (!isAvailable()) {
            callback.onFailure("ml kit genai prompt not present");
            return;
        }

        try {
            final Object futures = obtainModelFutures();
            if (futures == null) {
                callback.onFailure("could not create GenerativeModelFutures");
                return;
            }

            final int status = checkStatus(futures);
            final int available = readFeatureStatusAvailable();
            if (status != available) {
                // 0=UNAVAILABLE means AICore has no such feature on this device; 1=DOWNLOADABLE means
                // it could be fetched, which we decline to do. Neither is an error worth retrying.
                callback.onFailure("model feature not available (status " + status
                        + ", need " + available + ")");
                return;
            }

            warmupOnce(futures);

            final String composed = SessionSummaryPrompt.compose(promptText, acceptsInstructions());

            final String text = generateContent(futures, composed);
            if (text == null || text.trim().isEmpty()) {
                callback.onFailure("model returned no text");
                return;
            }

            callback.onSummary(text);
        } catch (Throwable t) {
            callback.onFailure(describe(t));
        }
    }

    private Object obtainModelFutures() throws Exception {
        if (modelFutures != null) {
            return modelFutures;
        }

        synchronized (this) {
            if (modelFutures != null) {
                return modelFutures;
            }

            // Generation is a Kotlin object: Generation.INSTANCE.getClient()
            final Class<?> generationClass = Class.forName(CLASS_GENERATION);
            final Object generation = generationClass.getField("INSTANCE").get(null);
            final Object generativeModel = generationClass.getMethod("getClient").invoke(generation);

            if (generativeModel == null) {
                return null;
            }

            // GenerativeModelFutures.Companion.from(GenerativeModel)
            final Class<?> futuresClass = Class.forName(CLASS_MODEL_FUTURES);
            final Field companionField = futuresClass.getField("Companion");
            final Object companion = companionField.get(null);

            final Class<?> modelInterface = Class.forName(CLASS_GENERATIVE_MODEL);
            final Method from = companion.getClass().getMethod("from", modelInterface);

            modelFutures = from.invoke(companion, generativeModel);
            return modelFutures;
        }
    }

    private int checkStatus(Object futures) throws Exception {
        final Object future = futures.getClass().getMethod("checkStatus").invoke(futures);
        final Object status = awaitFuture(future, STATUS_TIMEOUT_SECONDS);

        return (status instanceof Number) ? ((Number) status).intValue() : -1;
    }

    private int readFeatureStatusAvailable() {
        try {
            return Class.forName(CLASS_FEATURE_STATUS).getField("AVAILABLE").getInt(null);
        } catch (Throwable t) {
            log.debug("SessionSummary: could not read FeatureStatus.AVAILABLE, assuming "
                    + FEATURE_STATUS_AVAILABLE_FALLBACK);
            return FEATURE_STATUS_AVAILABLE_FALLBACK;
        }
    }

    /**
     * Warms the inference engine once per process. Best-effort: a failure here only means the first
     * real inference pays the cold cost.
     */
    private void warmupOnce(Object futures) {
        if (warmedUp) {
            return;
        }
        warmedUp = true;

        try {
            awaitFuture(futures.getClass().getMethod("warmup").invoke(futures), WARMUP_TIMEOUT_SECONDS);
        } catch (Throwable t) {
            log.debug("SessionSummary: warmup skipped: " + describe(t));
        }
    }

    private String generateContent(Object futures, String promptText) throws Exception {
        // generateContent(String) -- not the streaming overload, and not the Request overload.
        final Method generateContent = futures.getClass()
                .getMethod("generateContent", String.class);

        final Object response = awaitFuture(
                generateContent.invoke(futures, promptText), GENERATE_TIMEOUT_SECONDS);

        if (response == null) {
            return null;
        }

        final Object candidates = response.getClass().getMethod("getCandidates").invoke(response);
        if (!(candidates instanceof List) || ((List<?>) candidates).isEmpty()) {
            return null;
        }

        final Object candidate = ((List<?>) candidates).get(0);
        final Object text = candidate.getClass().getMethod("getText").invoke(candidate);

        return (text == null) ? null : text.toString();
    }

    /**
     * Awaits a Guava {@code ListenableFuture} through the {@link Future} interface it extends -- no
     * reflection, and the JDK enforces the timeout.
     *
     * Blocking is intended: {@code summarize} only runs on the controller's dedicated worker thread.
     */
    private Object awaitFuture(Object future, long timeoutSeconds) throws Exception {
        if (!(future instanceof Future)) {
            throw new IllegalStateException("ML Kit returned " + (future == null
                    ? "null" : future.getClass().getName()) + ", expected a Future");
        }

        return ((Future<?>) future).get(timeoutSeconds, TimeUnit.SECONDS);
    }

    /** Walks the cause chain; ML Kit's real diagnostics sit several wrappings down. */
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

    @Override
    public void shutdown() {
        // GenerativeModelFutures exposes no close(); dropping the reference is all there is to do.
        modelFutures = null;
    }
}
