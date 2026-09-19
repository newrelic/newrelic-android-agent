/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.ai;

import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tries several summarizers in order and sticks with the first one that actually produces a summary.
 *
 * <h3>Why this exists</h3>
 * The factory used to pick a single summarizer up front, based on whether its SDK classes were on the
 * classpath. That conflates <em>present</em> with <em>usable</em>, and the difference is not academic:
 * on a Galaxy S25 the AICore SDK was present and every inference failed with
 * {@code NOT_AVAILABLE: Required LLM feature not found}, while on a Pixel it failed with
 * {@code 601-BINDING_FAILURE}. In both cases the chosen adapter was permanently broken, the other
 * adapters were never tried, and the feature produced nothing at all rather than falling back.
 *
 * Whether a model can run is only knowable by asking it, on a background thread, at inference time.
 * So selection happens there instead of at construction.
 *
 * <h3>Retiring an adapter</h3>
 * A summarizer that reports its model feature missing or its service unbindable is not going to start
 * working later in the process, so it is retired after {@link #MAX_FAILURES_BEFORE_RETIRING}
 * consecutive failures and the chain moves on. Retirement is per-process: a new session re-evaluates
 * from the top, which matters because AICore is Play-updatable and a feature can appear between runs.
 *
 * Once a summarizer succeeds it is pinned, so the common case costs no extra probing.
 */
public class ChainedSessionSummarizer implements SessionSummarizer {

    private static final AgentLog log = AgentLogManager.getAgentLog();

    /**
     * Deliberately small. The failures this guards against are structural -- an absent model feature,
     * a refused binding -- not transient, so there is nothing to be gained by retrying at length.
     */
    static final int MAX_FAILURES_BEFORE_RETIRING = 2;

    private final List<Link> links;
    private final AtomicReference<Link> pinned = new AtomicReference<>(null);

    public ChainedSessionSummarizer(List<SessionSummarizer> summarizers) {
        final List<Link> built = new ArrayList<>();

        if (summarizers != null) {
            for (SessionSummarizer summarizer : summarizers) {
                if (summarizer != null && summarizer.isAvailable()) {
                    built.add(new Link(summarizer));
                }
            }
        }

        this.links = Collections.unmodifiableList(built);
    }

    @Override
    public boolean isAvailable() {
        final Link current = pinned.get();
        if (current != null) {
            return true;
        }

        for (Link link : links) {
            if (!link.retired) {
                return true;
            }
        }

        return false;
    }

    /**
     * @return the pinned summarizer's model name once one has succeeded, otherwise the first
     * candidate's -- so the {@code sessionSummaryModel} attribute always names whatever actually
     * produced the summary being reported.
     */
    @Override
    public String getModelName() {
        final Link current = pinned.get();
        if (current != null) {
            return current.summarizer.getModelName();
        }

        return links.isEmpty() ? "none" : links.get(0).summarizer.getModelName();
    }

    /**
     * Reported from the pinned summarizer once known. Before that, the first live candidate's answer is
     * used -- the controller composes the prompt before it knows which adapter will take it, and the
     * candidates are ordered so the preferred one leads.
     */
    @Override
    public boolean acceptsInstructions() {
        final Link current = firstLiveLink();
        return current != null && current.summarizer.acceptsInstructions();
    }

    /**
     * The <em>largest</em> floor among live candidates, so a prompt composed once satisfies whichever
     * adapter ends up handling it. Taking the smallest would compose input that a later link in the
     * chain then rejects as too short.
     */
    @Override
    public int getMinimumInputChars() {
        final Link current = pinned.get();
        if (current != null) {
            return current.summarizer.getMinimumInputChars();
        }

        int floor = 0;
        for (Link link : links) {
            if (!link.retired) {
                floor = Math.max(floor, link.summarizer.getMinimumInputChars());
            }
        }

        return floor;
    }

    @Override
    public void summarize(String promptText, final Callback callback) {
        // The pinned summarizer gets first refusal, but its result is still observed rather than
        // forwarded blind -- a model that stops working (feature removed by an AICore update, service
        // no longer binding) must release the pin so the chain can move on.
        final Link current = pinned.get();
        if (current != null) {
            final Outcome outcome = attempt(current, promptText);

            if (outcome.summary != null) {
                callback.onSummary(outcome.summary);
                return;
            }

            log.debug("SessionSummary: un-pinning " + current.summarizer.getModelName()
                    + " after failure: " + outcome.reason);
            pinned.compareAndSet(current, null);
            // Fall through and give the rest of the chain a turn on this same request.
        }

        for (Link link : links) {
            if (link.retired || link == current) {
                continue;
            }

            final Outcome outcome = attempt(link, promptText);

            if (outcome.summary != null) {
                pinned.set(link);
                link.failures = 0;
                log.debug("SessionSummary: pinned " + link.summarizer.getModelName());
                callback.onSummary(outcome.summary);
                return;
            }

            link.failures++;
            if (link.failures >= MAX_FAILURES_BEFORE_RETIRING) {
                link.retired = true;
                log.debug("SessionSummary: retiring " + link.summarizer.getModelName()
                        + " after " + link.failures + " failures: " + outcome.reason);
            } else {
                log.debug("SessionSummary: " + link.summarizer.getModelName()
                        + " failed: " + outcome.reason);
            }
        }

        callback.onFailure("all summarizers failed or retired");
    }

    private Outcome attempt(Link link, String promptText) {
        final Outcome outcome = new Outcome();

        try {
            link.summarizer.summarize(promptText, outcome);
        } catch (Throwable t) {
            // Adapters are contractually forbidden from throwing, but this is the seam where a
            // misbehaving one would otherwise reach the harvest, so it is contained here too.
            outcome.onFailure(link.summarizer.getModelName() + " threw "
                    + t.getClass().getSimpleName());
        }

        if (outcome.summary == null && outcome.reason == null) {
            // An adapter that invoked neither callback. Treat as failure rather than hanging.
            outcome.onFailure("no callback invoked");
        }

        return outcome;
    }

    private Link firstLiveLink() {
        final Link current = pinned.get();
        if (current != null) {
            return current;
        }

        for (Link link : links) {
            if (!link.retired) {
                return link;
            }
        }

        return null;
    }

    @Override
    public void shutdown() {
        for (Link link : links) {
            try {
                link.summarizer.shutdown();
            } catch (Throwable t) {
                log.debug("SessionSummary: " + link.summarizer.getModelName()
                        + " failed to shut down: " + t);
            }
        }
    }

    /** Collects one attempt's result so the chain can decide whether to continue. */
    private static final class Outcome implements Callback {
        String summary;
        String reason;

        @Override
        public void onSummary(String summary) {
            this.summary = summary;
        }

        @Override
        public void onFailure(String reason) {
            this.reason = reason;
        }
    }

    private static final class Link {
        final SessionSummarizer summarizer;
        int failures;
        volatile boolean retired;

        Link(SessionSummarizer summarizer) {
            this.summarizer = summarizer;
        }
    }

    List<SessionSummarizer> getCandidates() {
        final List<SessionSummarizer> candidates = new ArrayList<>();
        for (Link link : links) {
            candidates.add(link.summarizer);
        }
        return candidates;
    }
}
