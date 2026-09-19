/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.ai;

import android.content.Context;

import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the summarizer chain for this device and process.
 *
 * Returns {@link NullSessionSummarizer} for the overwhelmingly common case -- feature flag off, or no
 * on-device model SDK on the classpath -- which makes the whole feature a no-op costing a few
 * {@code Class.forName} calls at startup.
 *
 * <h3>Order, and why</h3>
 * Each candidate is added only if its SDK is present; the chain then decides at inference time which
 * one actually works, because "SDK present" and "model usable" turned out to be very different things:
 *
 * <ol>
 *   <li><b>{@code genai-prompt}</b> -- free-form, needs no allowlist, supported Java futures API.
 *       The only candidate that is both free-form and generally distributed.</li>
 *   <li><b>AICore</b> -- free-form, but per-app experimental-access registration. Observed
 *       {@code 601-BINDING_FAILURE} on Pixel (registration refused) and {@code 8-NOT_AVAILABLE} on
 *       Galaxy S25 (feature absent). Frozen at {@code 0.0.1-exp02}.</li>
 *   <li><b>{@code genai-summarization}</b> -- provisioned wherever AICore exists and demonstrably
 *       runs, but it is a fixed-purpose article summarizer that refuses session records. Last because
 *       it is the least likely to be useful, not the least likely to run.</li>
 * </ol>
 */
public class SessionSummarizerFactory {

    private static final AgentLog log = AgentLogManager.getAgentLog();

    private SessionSummarizerFactory() {
    }

    public static SessionSummarizer create(Context context) {
        if (!FeatureFlag.featureEnabled(FeatureFlag.SessionSummarization) || context == null) {
            return new NullSessionSummarizer();
        }

        final List<SessionSummarizer> candidates = new ArrayList<>();

        add(candidates, "ML Kit GenAI Prompt", new Supplier() {
            @Override
            public SessionSummarizer get() {
                return new MLKitPromptSessionSummarizer();
            }
        });

        add(candidates, "AICore", new Supplier() {
            @Override
            public SessionSummarizer get() {
                return new AICoreSessionSummarizer(context);
            }
        });

        add(candidates, "ML Kit GenAI Summarization", new Supplier() {
            @Override
            public SessionSummarizer get() {
                return new MLKitSessionSummarizer(context);
            }
        });

        if (candidates.isEmpty()) {
            log.debug("SessionSummary: no on-device summarizer SDK found on this device");
            return new NullSessionSummarizer();
        }

        final StringBuilder names = new StringBuilder();
        for (SessionSummarizer candidate : candidates) {
            if (names.length() > 0) {
                names.append(" -> ");
            }
            names.append(candidate.getModelName());
        }
        log.debug("SessionSummary: summarizer chain: " + names);

        return new ChainedSessionSummarizer(candidates);
    }

    /**
     * Instantiating an adapter loads classes that touch its SDK, so {@code NoClassDefFoundError} is an
     * expected outcome here, not an exceptional one.
     */
    private static void add(List<SessionSummarizer> candidates, String label, Supplier supplier) {
        try {
            final SessionSummarizer summarizer = supplier.get();
            if (summarizer.isAvailable()) {
                candidates.add(summarizer);
            }
        } catch (Throwable t) {
            log.debug("SessionSummary: " + label + " unavailable: " + t);
        }
    }

    private interface Supplier {
        SessionSummarizer get();
    }
}
