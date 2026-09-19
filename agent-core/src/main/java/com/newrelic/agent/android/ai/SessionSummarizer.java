/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.ai;

/**
 * Produces a natural-language summary of a session from the prompt text rendered by
 * {@link SessionDigest}.
 *
 * Implementations are adapters and nothing more: probe availability, hand the text to a model,
 * hand the result back. All policy -- when to summarize, how often, what to do on failure, how to
 * normalize the output -- lives in {@link SessionSummaryController}, which is plain JVM code and
 * therefore unit testable. Nothing that runs an on-device model can be covered in CI (emulators
 * do not ship AICore), so the untestable surface is kept deliberately thin.
 *
 * Implementations must never throw from any method and must never block the calling thread.
 */
public interface SessionSummarizer {

    interface Callback {
        /**
         * @param summary raw model output. May contain bullet markers and newlines; the controller
         *                normalizes it before the value reaches a harvest payload.
         */
        void onSummary(String summary);

        /**
         * @param reason short, non-PII description for logging and supportability metrics
         */
        void onFailure(String reason);
    }

    /**
     * @return true only if a model is present AND ready to run right now. An implementation whose
     * model is merely downloadable must return false: the agent never initiates a model download.
     */
    boolean isAvailable();

    /**
     * @return short stable identifier recorded as the {@code sessionSummaryModel} attribute, so a
     * summary can always be attributed to the thing that produced it
     */
    String getModelName();

    /**
     * @return true if this implementation takes a free-form prompt, in which case the controller
     * prepends {@link SessionSummaryPrompt#INSTRUCTIONS}. ML Kit's summarization task is
     * options-driven and has no instruction channel, so its adapter returns false and receives the
     * bare prose. Keeping this decision here rather than inside the adapters means the prompt text
     * itself stays in unit-testable code.
     */
    boolean acceptsInstructions();

    /**
     * Minimum input length this implementation will accept, in characters.
     *
     * ML Kit's ARTICLE input type rejects anything under 400 characters outright
     * ({@code GenAiException: Input text length is smaller than the minimum character limit of 400}),
     * so the controller checks this before spending an inference attempt. Padding the record to clear
     * the floor would be gaming it -- a session that renders under the floor genuinely has too little
     * in it to summarize, and is better left to the next cycle once more has accumulated.
     *
     * @return 0 if the implementation has no floor
     */
    default int getMinimumInputChars() {
        return 0;
    }

    /**
     * Runs inference asynchronously. Exactly one callback method is invoked, exactly once.
     *
     * @param promptText rendered by {@link SessionDigest#renderPromptText()}
     */
    void summarize(String promptText, Callback callback);

    /**
     * Releases model resources. Safe to call more than once, and safe to call when never used.
     */
    void shutdown();
}
