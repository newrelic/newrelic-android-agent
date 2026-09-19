/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.ai;

/**
 * Stands in whenever no on-device model is present, which is the overwhelmingly common case.
 * Always unavailable, so {@link SessionSummaryController} short-circuits before it ever
 * accumulates a digest.
 */
public class NullSessionSummarizer implements SessionSummarizer {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public String getModelName() {
        return "none";
    }

    @Override
    public boolean acceptsInstructions() {
        return false;
    }

    @Override
    public void summarize(String promptText, Callback callback) {
        if (callback != null) {
            callback.onFailure("no summarizer available");
        }
    }

    @Override
    public void shutdown() {
        // nothing to release
    }
}
