/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.ai;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.analytics.AnalyticsControllerImpl;
import com.newrelic.agent.android.harvest.Harvest;
import com.newrelic.agent.android.harvest.HarvestLifecycleAware;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.stats.StatsEngine;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Owns all policy for on-device session summarization: when to run inference, how often, what to do
 * when it fails, and how the result reaches a harvest payload.
 *
 * <h3>Why inference runs mid-session</h3>
 * The obvious design -- summarize when the session ends -- does not work on Android. Backgrounding
 * calls {@code AndroidAgentImpl.stop()}, which calls {@code Harvest.harvestNow(true, true)}
 * synchronously; the final harvest is already in flight before any inference could finish, and
 * blocking it is not an option (the agent already tracks when that path lands on the main thread).
 * So inference runs during the live session, debounced, and the newest cached summary is what the
 * session event picks up. That also means the attribute genuinely describes the session it is
 * attached to, with no race at all.
 *
 * <h3>Two paths, by design</h3>
 * <ul>
 *   <li><b>In-session:</b> a cached summary is attached to the session event via
 *       {@link SessionSummaryProvider}.</li>
 *   <li><b>Deferred:</b> if the tail of the session never made it into a summary, or the session
 *       was too short to clear the gates, the prompt text is persisted and summarized on the next
 *       launch, then emitted as a {@code MobileSessionSummary} event carrying
 *       {@code previousSessionId}. This mirrors how {@code ApplicationExitMonitor} reports
 *       last-session data at startup.</li>
 * </ul>
 * Consumers must read both event types and union them; that is the cost of not losing short
 * sessions.
 *
 * <h3>Failure policy</h3>
 * Nothing here can break a harvest. Every entry point swallows, counts, and logs. A failed or slow
 * model simply means no attribute this session.
 */
public class SessionSummaryController
        implements HarvestLifecycleAware, SessionSummaryProvider {

    private static final AgentLog log = AgentLogManager.getAgentLog();

    public static final String ATTRIBUTE_SUMMARY = "sessionSummary";
    public static final String ATTRIBUTE_SOURCE = "sessionSummarySource";
    public static final String ATTRIBUTE_MODEL = "sessionSummaryModel";
    public static final String ATTRIBUTE_PREVIOUS_SESSION_ID = "previousSessionId";
    public static final String ATTRIBUTE_DEFERRED = "sessionSummaryDeferred";

    public static final String EVENT_TYPE_SESSION_SUMMARY = "MobileSessionSummary";
    public static final String SOURCE_ON_DEVICE = "onDevice";

    static final String METRIC_ROOT = "Supportability/Mobile/Android/AI/SessionSummary/";
    static final String METRIC_AVAILABLE = METRIC_ROOT + "Available";
    static final String METRIC_UNAVAILABLE = METRIC_ROOT + "Unavailable";
    static final String METRIC_SUCCESS = METRIC_ROOT + "Success";
    static final String METRIC_FAILED = METRIC_ROOT + "Failed";
    static final String METRIC_TIMEOUT = METRIC_ROOT + "Timeout";
    static final String METRIC_INPUT_TOO_SHORT = METRIC_ROOT + "InputTooShort";
    static final String METRIC_ATTACHED_IN_SESSION = METRIC_ROOT + "AttachedInSession";
    static final String METRIC_DEFERRED_STORED = METRIC_ROOT + "DeferredStored";
    static final String METRIC_DEFERRED_EMITTED = METRIC_ROOT + "DeferredEmitted";
    static final String METRIC_LATENCY_MS = METRIC_ROOT + "InferenceMs";

    /** Minimum gap between inferences, so a long session costs a handful of runs, not hundreds. */
    static final long DEBOUNCE_MS = 90_000L;

    /** Abandon an inference that has not called back. Protects the worker thread, not the model. */
    static final long INFERENCE_TIMEOUT_MS = 10_000L;

    /** Total inference attempts allowed per session, successes included. */
    static final int MAX_ATTEMPTS_PER_SESSION = 3;

    enum State {
        COLLECTING,
        SUMMARIZING,
        READY
    }

    private final SessionSummarizer summarizer;
    private final SessionSummaryStore store;
    private final AgentConfiguration agentConfiguration;

    private final SessionDigest digest = new SessionDigest();
    private final SessionDigestCollector collector = new SessionDigestCollector(digest);

    private final AtomicReference<State> state = new AtomicReference<>(State.COLLECTING);
    private final AtomicReference<String> cachedSummary = new AtomicReference<>(null);
    private final AtomicInteger attempts = new AtomicInteger(0);
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    /** Digest revision the cached summary was computed from; -1 means nothing summarized yet. */
    private volatile int revisionSummarized = -1;
    private volatile long lastInferenceAtMs = 0L;
    private volatile boolean attachedInSession = false;

    private final ExecutorService executor;

    public SessionSummaryController(SessionSummarizer summarizer,
                                    SessionSummaryStore store,
                                    AgentConfiguration agentConfiguration) {
        this.summarizer = (summarizer == null) ? new NullSessionSummarizer() : summarizer;
        this.store = store;
        this.agentConfiguration = agentConfiguration;

        this.executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                final Thread thread = new Thread(runnable, "NR-session-summary");
                // Must never hold up process exit; abandoned inference is expected, not exceptional.
                thread.setDaemon(true);
                thread.setPriority(Thread.MIN_PRIORITY);
                return thread;
            }
        });
    }

    /**
     * @return true if a model is present and ready. Recorded as a supportability metric because the
     * real device-eligibility rate in the field is the single most important unknown about this
     * feature, and it is not something we can usefully guess.
     */
    public boolean isEnabled() {
        if (!FeatureFlag.featureEnabled(FeatureFlag.SessionSummarization)) {
            return false;
        }

        final boolean available = summarizer.isAvailable();
        StatsEngine.get().inc(available ? METRIC_AVAILABLE : METRIC_UNAVAILABLE);

        return available;
    }

    // ---------------------------------------------------------------- deferred path

    /**
     * Summarizes a digest left behind by a previous session, if there is one. Called once at agent
     * init, where the process is warm and nothing is time-critical -- the most forgiving place to
     * run inference that exists in the agent's lifecycle.
     */
    public void processPendingDigest() {
        if (store == null || shutdown.get()) {
            return;
        }

        try {
            final SessionSummaryStore.PendingDigest pending = store.load();
            if (pending == null) {
                return;
            }

            // Always clear first. A digest that cannot be summarized must not be retried forever,
            // and a crash mid-inference must not leave it to be picked up again next launch.
            store.clear();

            if (!pending.isUsable()) {
                log.debug("SessionSummary: discarding unusable pending digest");
                return;
            }

            runInference(pending.promptText, new SessionSummarizer.Callback() {
                @Override
                public void onSummary(String summary) {
                    emitDeferredEvent(pending.sessionId, summary);
                }

                @Override
                public void onFailure(String reason) {
                    log.debug("SessionSummary: deferred inference failed: " + reason);
                }
            });
        } catch (Throwable t) {
            log.debug("SessionSummary: processPendingDigest failed: " + t);
        }
    }

    private void emitDeferredEvent(String previousSessionId, String rawSummary) {
        final String summary = SessionSummaryPrompt.normalize(rawSummary);
        if (summary == null) {
            StatsEngine.get().inc(METRIC_FAILED);
            return;
        }

        try {
            final Map<String, Object> attributes = new HashMap<>();
            attributes.put(ATTRIBUTE_SUMMARY, summary);
            attributes.put(ATTRIBUTE_SOURCE, SOURCE_ON_DEVICE);
            attributes.put(ATTRIBUTE_MODEL, summarizer.getModelName());
            attributes.put(ATTRIBUTE_PREVIOUS_SESSION_ID, previousSessionId);
            attributes.put(ATTRIBUTE_DEFERRED, Boolean.TRUE);

            if (AnalyticsControllerImpl.getInstance()
                    .recordCustomEvent(EVENT_TYPE_SESSION_SUMMARY, attributes)) {
                StatsEngine.get().inc(METRIC_DEFERRED_EMITTED);
                log.debug("SessionSummary: emitted deferred summary for session " + previousSessionId);
            }
        } catch (Throwable t) {
            log.debug("SessionSummary: failed to emit deferred summary: " + t);
        }
    }

    // ---------------------------------------------------------------- harvest lifecycle

    /**
     * Folds the cycle's data into the digest and decides whether to spend an inference.
     *
     * {@code onHarvestFinalize} is the correct hook: {@code Harvest.execute()} fires
     * {@code onHarvestBefore} then {@code onHarvest} (where the analytics controller moves queued
     * events into HarvestData) then {@code onHarvestFinalize}, and {@code harvestData.reset()} only
     * runs after {@code onHarvestComplete}. So the data is fully populated and not yet cleared.
     */
    @Override
    public void onHarvestFinalize() {
        if (!isEnabled() || shutdown.get()) {
            return;
        }

        try {
            final Harvest harvest = Harvest.getInstance();
            if (harvest == null) {
                return;
            }

            collector.fold(harvest.getHarvestData());

            final long sessionDurationMs = Harvest.getMillisSinceStart();
            if (sessionDurationMs != Harvest.INVALID_SESSION_DURATION) {
                digest.setSessionDurationMs(sessionDurationMs);
            }

            maybeSummarize();
        } catch (Throwable t) {
            log.debug("SessionSummary: onHarvestFinalize failed: " + t);
        }
    }

    /**
     * Gates: enough substance to be worth summarizing, enough time since the last run, and an
     * attempt budget that a pathological session cannot exhaust the battery against.
     */
    private void maybeSummarize() {
        if (state.get() == State.SUMMARIZING) {
            return;
        }

        if (!digest.isMaterial()) {
            return;
        }

        final int revision = digest.getRevision();
        if (revision == revisionSummarized) {
            return;
        }

        if (attempts.get() >= MAX_ATTEMPTS_PER_SESSION) {
            return;
        }

        final long nowMs = now();
        if (lastInferenceAtMs != 0L && (nowMs - lastInferenceAtMs) < DEBOUNCE_MS) {
            return;
        }

        if (!state.compareAndSet(State.COLLECTING, State.SUMMARIZING)
                && !state.compareAndSet(State.READY, State.SUMMARIZING)) {
            return;
        }

        lastInferenceAtMs = nowMs;

        final String promptText = digest.renderPromptText();

        runInference(promptText, new SessionSummarizer.Callback() {
            @Override
            public void onSummary(String summary) {
                final String normalized = SessionSummaryPrompt.normalize(summary);
                if (normalized == null) {
                    // A blank answer is a failed inference, not a valid empty summary.
                    StatsEngine.get().inc(METRIC_FAILED);
                    state.set(State.COLLECTING);
                    return;
                }

                cachedSummary.set(normalized);
                revisionSummarized = revision;
                state.set(State.READY);
                log.debug("SessionSummary: summary ready at revision " + revision
                        + ": " + normalized);
            }

            @Override
            public void onFailure(String reason) {
                log.debug("SessionSummary: inference failed: " + reason);
                state.set(State.COLLECTING);
            }
        });
    }

    /**
     * Runs one inference on the worker thread, bounded by {@link #INFERENCE_TIMEOUT_MS}.
     *
     * The latch is what makes the timeout real: {@code summarize} is asynchronous and an adapter
     * whose model hangs would otherwise never call back at all. Exactly one of the caller's
     * callback methods is invoked.
     */
    private void runInference(final String promptText, final SessionSummarizer.Callback callback) {
        if (promptText == null || promptText.isEmpty()) {
            callback.onFailure("empty prompt");
            return;
        }

        // The raw record is handed down as-is. Each adapter composes its own final prompt via
        // SessionSummaryPrompt, because whether instructions are wanted is a property of the model,
        // not of the session -- and with a chain of summarizers we do not know which one will take it.
        // Composing once up front would send an instruction preamble to a task API that has no
        // instruction channel.
        //
        // Gating on the raw record is conservative: instructions only ever add length.
        final int minimumChars = summarizer.getMinimumInputChars();
        if (promptText.length() < minimumChars) {
            StatsEngine.get().inc(METRIC_INPUT_TOO_SHORT);
            callback.onFailure("input too short (" + promptText.length() + " < " + minimumChars + ")");
            return;
        }

        attempts.incrementAndGet();

        log.debug("SessionSummary: record (" + promptText.length() + " chars):\n" + promptText);

        try {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    final long startedAtMs = now();
                    final CountDownLatch latch = new CountDownLatch(1);
                    final AtomicReference<String> result = new AtomicReference<>(null);
                    final AtomicReference<String> failure = new AtomicReference<>(null);

                    try {
                        summarizer.summarize(promptText, new SessionSummarizer.Callback() {
                            @Override
                            public void onSummary(String summary) {
                                result.set(summary);
                                latch.countDown();
                            }

                            @Override
                            public void onFailure(String reason) {
                                failure.set(reason);
                                latch.countDown();
                            }
                        });

                        if (!latch.await(INFERENCE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                            StatsEngine.get().inc(METRIC_TIMEOUT);
                            callback.onFailure("timed out after " + INFERENCE_TIMEOUT_MS + "ms");
                            return;
                        }

                        StatsEngine.get().sampleTimeMs(METRIC_LATENCY_MS, now() - startedAtMs);

                        final String summary = result.get();
                        if (summary != null) {
                            StatsEngine.get().inc(METRIC_SUCCESS);
                            callback.onSummary(summary);
                        } else {
                            StatsEngine.get().inc(METRIC_FAILED);
                            callback.onFailure(String.valueOf(failure.get()));
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        callback.onFailure("interrupted");
                    } catch (Throwable t) {
                        StatsEngine.get().inc(METRIC_FAILED);
                        callback.onFailure("threw " + t.getClass().getSimpleName());
                    }
                }
            });
        } catch (Throwable t) {
            // Rejected execution (shutting down) is not worth a metric; it means we are on the way out.
            callback.onFailure("not scheduled: " + t.getClass().getSimpleName());
        }
    }

    // ---------------------------------------------------------------- attach

    /**
     * Called on the harvest thread during session finalization. Returns whatever summary is cached,
     * or nothing -- never blocks, never waits for an in-flight inference.
     */
    @Override
    public Set<AnalyticsAttribute> getSessionEventAttributes() {
        if (!FeatureFlag.featureEnabled(FeatureFlag.SessionSummarization)) {
            return Collections.emptySet();
        }

        final String summary = cachedSummary.get();
        if (summary == null) {
            return Collections.emptySet();
        }

        try {
            final Set<AnalyticsAttribute> attributes = new HashSet<>();
            attributes.add(new AnalyticsAttribute(ATTRIBUTE_SUMMARY, summary, false));
            attributes.add(new AnalyticsAttribute(ATTRIBUTE_SOURCE, SOURCE_ON_DEVICE, false));
            attributes.add(new AnalyticsAttribute(ATTRIBUTE_MODEL, summarizer.getModelName(), false));

            attachedInSession = true;
            StatsEngine.get().inc(METRIC_ATTACHED_IN_SESSION);

            return attributes;
        } catch (Throwable t) {
            log.debug("SessionSummary: failed to build session event attributes: " + t);
            return Collections.emptySet();
        }
    }

    // ---------------------------------------------------------------- application state

    /**
     * Persists the digest when the session is ending and the summary we have (if any) does not
     * cover it.
     *
     * Deliberately <em>not</em> driven by {@code ApplicationStateListener}. Ordering matters twice
     * over and listener registration order is the wrong thing to depend on for either: this must run
     * <em>after</em> the final harvest (so {@link #attachedInSession} is known, and we do not persist
     * a digest that was already summarized and attached) and <em>before</em> {@link #shutdown()}.
     * {@code AndroidAgentImpl.stop(boolean)} calls it at exactly that point.
     *
     * Returns immediately; the calling thread has just finished a synchronous harvest and may be the
     * main thread.
     */
    public void persistPendingDigest() {
        if (!isEnabled() || store == null || shutdown.get()) {
            return;
        }

        try {
            final int revision = digest.getRevision();

            final boolean alreadyCovered = attachedInSession && revision == revisionSummarized;
            if (alreadyCovered) {
                return;
            }

            if (revision == 0) {
                // Nothing was ever folded in; there is no session to describe.
                return;
            }

            // The same materiality bar the in-session path uses. Without this, every trivial
            // session -- a splash screen opened for twenty seconds -- would cost a deferred
            // inference on the next launch and emit a MobileSessionSummary event describing
            // nothing, which is precisely the ingest cost this feature is supposed to reduce.
            if (!digest.isMaterial()) {
                return;
            }

            final String sessionId = (agentConfiguration == null)
                    ? null : agentConfiguration.getSessionID();
            if (sessionId == null || sessionId.isEmpty()) {
                return;
            }

            final SessionSummaryStore.PendingDigest pending = new SessionSummaryStore.PendingDigest(
                    SessionDigest.SCHEMA_VERSION, sessionId, digest.renderPromptText());

            if (store.save(pending)) {
                StatsEngine.get().inc(METRIC_DEFERRED_STORED);
                log.debug("SessionSummary: stored digest for deferred summarization, session " + sessionId);
            }
        } catch (Throwable t) {
            log.debug("SessionSummary: persistPendingDigest failed: " + t);
        }
    }

    public void shutdown() {
        if (!shutdown.compareAndSet(false, true)) {
            return;
        }

        try {
            executor.shutdownNow();
        } catch (Throwable t) {
            log.debug("SessionSummary: executor shutdown failed: " + t);
        }

        try {
            summarizer.shutdown();
        } catch (Throwable t) {
            log.debug("SessionSummary: summarizer shutdown failed: " + t);
        }
    }

    // ---------------------------------------------------------------- test seams

    /**
     * Overridable so debounce and timeout behavior can be tested without sleeping.
     */
    protected long now() {
        return System.currentTimeMillis();
    }

    SessionDigest getDigest() {
        return digest;
    }

    State getState() {
        return state.get();
    }

    String getCachedSummary() {
        return cachedSummary.get();
    }

    int getAttempts() {
        return attempts.get();
    }
}
