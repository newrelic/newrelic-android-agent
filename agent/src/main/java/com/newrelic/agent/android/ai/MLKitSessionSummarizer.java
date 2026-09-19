/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.ai;

import android.content.Context;

import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;

import java.lang.reflect.Method;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Adapter over ML Kit's GenAI summarization task, which runs Gemini Nano through AICore on devices
 * that have it.
 *
 * <h3>Why reflection and not a Gradle dependency</h3>
 * The agent AAR is embedded in every customer app, so anything in its dependency graph is inherited
 * by thousands of apps that did not ask for it. Even {@code compileOnly} is not free: the artifact
 * must resolve at build time for the agent itself to build. Reflection costs some ugliness here and
 * keeps the dependency graph untouched. Apps that want this feature add
 * {@code com.google.mlkit:genai-summarization} themselves; apps that do not are unaffected and pay
 * one {@code Class.forName} at startup. This follows the existing pattern in
 * {@code ComposeChecker} and {@code KmpChecker}.
 *
 * <h3>Never downloads a model</h3>
 * ML Kit reports the summarization feature as available, downloadable, or downloading. Only
 * <em>available</em> is treated as usable. An SDK sitting inside someone else's app has no business
 * starting a multi-hundred-megabyte download on an end user's cellular connection, so a
 * downloadable-but-absent model is simply an unavailable model.
 *
 * <h3>Measured result: this model refuses session telemetry</h3>
 * Verified on a Galaxy S25 (Android 16, AICore 0.release.qc.prod_aicore_20260723, Samsung AICore
 * 3.0.02.7) against genai-summarization 1.0.0-beta1, with the ARTICLE feature downloaded and
 * {@code FeatureStatus.AVAILABLE}:
 * <ul>
 *   <li>Ordinary news-article prose summarizes correctly and repeatably, in ~3.4 seconds.</li>
 *   <li>Every rendering of a session record is refused with
 *       {@code GenAiException: Couldn't generate a response. Try a different input.}</li>
 * </ul>
 * The refusal is not about how the record is written. It survived one-clause-per-line, flowing
 * narrative, an explicit framing sentence, 518 through 1458 characters, ONE_BULLET and
 * THREE_BULLETS, and a fully humanized rewrite carrying the same facts with natural page names, no
 * camelCase identifiers, no endpoint paths, and numbers spelled out. Runs were interleaved with the
 * article control, which passed every time in the same process and on the same reused client, ruling
 * out ordering and client-state effects.
 *
 * A later run on the same device, against a richer record, returned a more specific message:
 * {@code [ErrorCode 15] Couldn't generate a response due to policy check failure}. A line-by-line
 * bisect of that record then rejected <em>every</em> subset -- including one carrying no endpoints, no
 * error codes, no device identifiers and no failure lines at all -- while neutral prose of the same
 * length passed immediately before and after on the same client. So no single clause is responsible.
 *
 * The conclusion is about the API, not the device and not the wording: ML Kit's summarization task is a
 * purpose-built article and conversation summarizer whose content classifier refuses session records
 * as a category, however they are phrased, and there is no instruction channel to talk it into
 * cooperating. Enriching or rewording the record does not move this; that has now been tested twice
 * against two generations of the renderer.
 *
 * This class is kept because it is correct against the API and may work on other devices or later
 * model versions -- and because {@link #isAvailable()} plus the feature-status gate make it a safe
 * no-op when it does not. But summarizing sessions needs a free-form model where we supply the
 * prompt ({@code com.google.ai.edge.aicore:aicore}, which is publicly resolvable), using the text in
 * {@link SessionSummaryPrompt#INSTRUCTIONS}. Do not spend time re-tuning the prose for this adapter;
 * that experiment is done.
 *
 * <h3>On the symbol names below</h3>
 * Verified against {@code com.google.mlkit:genai-summarization:1.0.0-beta1} and
 * {@code genai-common:1.0.0-beta1} by inspecting the published AARs. Since none of it is
 * compiler-checked and none of it can be covered in CI (emulators do not ship AICore), every
 * reflective lookup logs the exact symbol it could not find.
 *
 * Note the async type: ML Kit GenAI returns Guava {@code ListenableFuture}, not a Play Services
 * {@code Task}. {@code ListenableFuture} extends {@link java.util.concurrent.Future}, so the result
 * is awaited through a plain cast rather than reflectively.
 */
public class MLKitSessionSummarizer implements SessionSummarizer {

    private static final AgentLog log = AgentLogManager.getAgentLog();

    static final String CLASS_SUMMARIZATION = "com.google.mlkit.genai.summarization.Summarization";
    static final String CLASS_SUMMARIZER_OPTIONS = "com.google.mlkit.genai.summarization.SummarizerOptions";
    static final String CLASS_SUMMARIZATION_REQUEST = "com.google.mlkit.genai.summarization.SummarizationRequest";
    static final String CLASS_FEATURE_STATUS = "com.google.mlkit.genai.common.FeatureStatus";

    static final String MODEL_NAME = "mlkit-genai-summarization";

    /**
     * {@code FeatureStatus.AVAILABLE} as published in genai-common 1.0.0-beta1. Read reflectively at
     * runtime with this as the fallback. Getting this constant wrong is not a benign failure: 1 is
     * DOWNLOADABLE and 2 is DOWNLOADING, so a wrong value here would mean running inference against
     * a model that is not there yet, or worse, treating a mid-download state as ready.
     */
    static final int FEATURE_STATUS_AVAILABLE_FALLBACK = 3;

    /** ML Kit's undocumented floor for the ARTICLE input type. See {@link #getMinimumInputChars()}. */
    static final int MINIMUM_ARTICLE_INPUT_CHARS = 400;

    private static final long FEATURE_CHECK_TIMEOUT_SECONDS = 5L;
    private static final long INFERENCE_TIMEOUT_SECONDS = 15L;
    private static final long PREPARE_TIMEOUT_SECONDS = 10L;

    private final Context context;
    private final boolean symbolsResolved;

    private volatile Object mlkitSummarizer;

    public MLKitSessionSummarizer(Context context) {
        this.context = context;
        this.symbolsResolved = resolveSymbols();
    }

    private static boolean resolveSymbols() {
        final String[] required = {
                CLASS_SUMMARIZATION,
                CLASS_SUMMARIZER_OPTIONS,
                CLASS_SUMMARIZATION_REQUEST,
                CLASS_FEATURE_STATUS
        };

        for (String className : required) {
            try {
                Class.forName(className);
            } catch (Throwable t) {
                log.debug("SessionSummary: ML Kit GenAI not present (missing " + className + ")");
                return false;
            }
        }

        return true;
    }

    /**
     * Reports only whether the ML Kit SDK is present, which is a cheap synchronous check. Whether
     * the <em>model</em> is downloaded is decided in {@link #summarize} instead, because that check
     * blocks on a Task and this method is called from the harvest thread.
     */
    @Override
    public boolean isAvailable() {
        return symbolsResolved && context != null;
    }

    @Override
    public String getModelName() {
        return MODEL_NAME;
    }

    /**
     * ML Kit's summarization task takes input text and options -- there is no instruction channel,
     * no system prompt, and no output schema. So it receives the bare prose from
     * {@link SessionDigest#renderPromptText()}, and that prose is the only control we have over the
     * result.
     */
    @Override
    public boolean acceptsInstructions() {
        return false;
    }

    /**
     * Measured on a Galaxy S25 running genai-summarization 1.0.0-beta1: anything shorter fails with
     * {@code GenAiException: Input text length is smaller than the minimum character limit of 400 for
     * the ARTICLE InputType}. Not documented in the API surface -- only the device tells you.
     */
    @Override
    public int getMinimumInputChars() {
        return MINIMUM_ARTICLE_INPUT_CHARS;
    }

    @Override
    public void summarize(String promptText, Callback callback) {
        if (!isAvailable()) {
            callback.onFailure("ml kit genai not present");
            return;
        }

        try {
            final Object summarizer = obtainSummarizer();
            if (summarizer == null) {
                callback.onFailure("could not create summarizer client");
                return;
            }

            final int featureStatus = checkFeatureStatus(summarizer);
            final int availableStatus = readFeatureStatusAvailable();
            if (featureStatus != availableStatus) {
                // DOWNLOADABLE and DOWNLOADING are both "not available". downloadFeature() exists on
                // the client and is deliberately never called.
                callback.onFailure("model not available (feature status " + featureStatus
                        + ", need " + availableStatus + ")");
                return;
            }

            prepareInferenceEngine(summarizer);

            // No instruction channel on this task, so compose() returns the bare record.
            final String composed = SessionSummaryPrompt.compose(promptText, acceptsInstructions());

            final String summary = runInference(summarizer, composed);
            if (summary == null || summary.trim().isEmpty()) {
                callback.onFailure("model returned no summary");
                return;
            }

            callback.onSummary(summary);
        } catch (Throwable t) {
            callback.onFailure(t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private Object obtainSummarizer() throws Exception {
        if (mlkitSummarizer != null) {
            return mlkitSummarizer;
        }

        synchronized (this) {
            if (mlkitSummarizer != null) {
                return mlkitSummarizer;
            }

            final Object options = buildOptions();
            if (options == null) {
                return null;
            }

            final Class<?> summarizationClass = Class.forName(CLASS_SUMMARIZATION);
            final Class<?> optionsClass = Class.forName(CLASS_SUMMARIZER_OPTIONS);
            final Method getClient = summarizationClass.getMethod("getClient", optionsClass);

            mlkitSummarizer = getClient.invoke(null, options);
            return mlkitSummarizer;
        }
    }

    /**
     * Builds SummarizerOptions, degrading rather than failing when an option cannot be set. If the
     * nested InputType/OutputType/Language constants move or are renamed, we still get a working
     * summarizer on ML Kit's defaults, which is far better than no summary at all.
     */
    private Object buildOptions() throws Exception {
        final Class<?> optionsClass = Class.forName(CLASS_SUMMARIZER_OPTIONS);
        final Method builderMethod = optionsClass.getMethod("builder", Context.class);
        final Object builder = builderMethod.invoke(null, context);

        // A session record reads as an article rather than a conversation; three bullets is the
        // most detail the task offers, and the controller flattens them into one line anyway.
        trySetIntOption(builder, "setInputType", CLASS_SUMMARIZER_OPTIONS + "$InputType", "ARTICLE");
        trySetIntOption(builder, "setOutputType", CLASS_SUMMARIZER_OPTIONS + "$OutputType", "THREE_BULLETS");
        trySetIntOption(builder, "setLanguage", CLASS_SUMMARIZER_OPTIONS + "$Language", "ENGLISH");

        // The digest is capped at 2000 characters, comfortably inside the model's input window, so
        // this should never engage. It is set anyway: if a future model ships a smaller window, a
        // truncated summary is a better outcome than a hard inference failure.
        trySetBooleanOption(builder, "setLongInputAutoTruncationEnabled", true);

        return builder.getClass().getMethod("build").invoke(builder);
    }

    private void trySetIntOption(Object builder, String setterName, String constantsClassName,
                                 String constantName) {
        try {
            final Class<?> constantsClass = Class.forName(constantsClassName);
            final int value = constantsClass.getField(constantName).getInt(null);
            builder.getClass().getMethod(setterName, int.class).invoke(builder, value);
        } catch (Throwable t) {
            log.debug("SessionSummary: could not set " + setterName
                    + " (" + constantsClassName + "." + constantName + "); using ML Kit default");
        }
    }

    private void trySetBooleanOption(Object builder, String setterName, boolean value) {
        try {
            builder.getClass().getMethod(setterName, boolean.class).invoke(builder, value);
        } catch (Throwable t) {
            log.debug("SessionSummary: could not set " + setterName + "; using ML Kit default");
        }
    }

    private int checkFeatureStatus(Object summarizer) throws Exception {
        final Object future = summarizer.getClass().getMethod("checkFeatureStatus").invoke(summarizer);
        final Object status = awaitFuture(future, FEATURE_CHECK_TIMEOUT_SECONDS);

        return (status instanceof Number) ? ((Number) status).intValue() : -1;
    }

    /**
     * Warms up the inference engine. Best-effort: a failure here is not a reason to skip the
     * inference, it just means the first run pays the cold cost.
     */
    private void prepareInferenceEngine(Object summarizer) {
        try {
            final Object future = summarizer.getClass()
                    .getMethod("prepareInferenceEngine").invoke(summarizer);
            awaitFuture(future, PREPARE_TIMEOUT_SECONDS);
        } catch (Throwable t) {
            log.debug("SessionSummary: prepareInferenceEngine skipped: " + t);
        }
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

    private String runInference(Object summarizer, String promptText) throws Exception {
        final Class<?> requestClass = Class.forName(CLASS_SUMMARIZATION_REQUEST);
        final Object requestBuilder = requestClass.getMethod("builder", String.class)
                .invoke(null, promptText);
        final Object request = requestBuilder.getClass().getMethod("build").invoke(requestBuilder);

        // Two overloads exist; this selects runInference(SummarizationRequest), not the streaming one.
        final Method runInference = summarizer.getClass().getMethod("runInference", requestClass);
        final Object future = runInference.invoke(summarizer, request);
        final Object result = awaitFuture(future, INFERENCE_TIMEOUT_SECONDS);

        if (result == null) {
            return null;
        }

        try {
            final Object summary = result.getClass().getMethod("getSummary").invoke(result);
            return (summary == null) ? null : summary.toString();
        } catch (NoSuchMethodException e) {
            // Some result types expose the text only via toString(); better than discarding it.
            log.debug("SessionSummary: result has no getSummary(), falling back to toString()");
            return result.toString();
        }
    }

    /**
     * Awaits a Guava {@code ListenableFuture}. Because it extends {@link Future}, a plain cast is
     * enough -- no reflection, and the timeout is enforced by the JDK rather than by us.
     *
     * Blocking is safe and intended here: {@code summarize} always runs on the controller's dedicated
     * worker thread, which exists to be blocked, and the controller layers its own outer timeout on
     * top in case an adapter never returns at all.
     */
    private Object awaitFuture(Object future, long timeoutSeconds) throws Exception {
        if (!(future instanceof Future)) {
            throw new IllegalStateException("ML Kit returned " + (future == null
                    ? "null" : future.getClass().getName()) + ", expected a Future");
        }

        return ((Future<?>) future).get(timeoutSeconds, TimeUnit.SECONDS);
    }

    @Override
    public void shutdown() {
        final Object summarizer = mlkitSummarizer;
        if (summarizer == null) {
            return;
        }

        mlkitSummarizer = null;

        try {
            summarizer.getClass().getMethod("close").invoke(summarizer);
        } catch (Throwable t) {
            log.debug("SessionSummary: failed to close summarizer: " + t);
        }
    }
}
