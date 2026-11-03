/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.mobileview;

import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.analytics.AnalyticsControllerImpl;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MobileViewTiming events, per the Mobile Views IDD (NR-356639) §5.3.6 and §6.2-6.3: admission
 * policy (what is allowed to be recorded, and how often), the public mark/record entry points, and
 * the agent-owned timeToInitialDisplay baseline. View state lives in {@link MobileViewContext};
 * this class only reads an immutable {@link ViewTimingSnapshot} of it.
 */
public class MobileViewTiming {
    private static final AgentLog log = AgentLogManager.getAgentLog();
    private static final MobileViewTiming instance = new MobileViewTiming();

    /** The agent-owned baseline. Same string on every agent; reserved from customer use. */
    public static final String TIME_TO_INITIAL_DISPLAY = "timeToInitialDisplay";
    /** Standard customer timing names. Not reserved. */
    public static final String TIME_TO_FULL_DISPLAY = "timeToFullDisplay";
    public static final String TIME_TO_INTERACTIVE = "timeToInteractive";

    public static final int MAX_TIMINGS_PER_VIEW_INSTANCE = 16;
    public static final int MAX_NAME_LENGTH = 128;
    public static final double MAX_MILLISECONDS = 10 * 60 * 1000;

    // Upper bound on tracked view-instance buckets: every visited view instance would otherwise be
    // remembered for the life of the process. Oldest-inserted buckets are evicted first, since they
    // belong to views the user has long since left.
    static final int MAX_TRACKED_BUCKETS = 64;

    private final Object lock = new Object();
    // Insertion-ordered (the default), so the first entry is always the oldest-inserted bucket.
    private final LinkedHashMap<String, Integer> viewBuckets = new LinkedHashMap<>();
    // Bucket for timings recorded with no view current. A view instance's bucket stops mattering
    // when the view goes away; this one never does, so it is exempt from eviction and emptied
    // explicitly at each session start.
    private int unattributedCount;
    private boolean warnedAboutCap;

    private MobileViewTiming() {
    }

    public static MobileViewTiming getInstance() {
        return instance;
    }

    /**
     * Timing rides the MobileViews flags rather than having one of its own: either flag enables it.
     */
    public boolean isEnabled() {
        return FeatureFlag.featureEnabled(FeatureFlag.AutomaticMobileViewTracing)
                || FeatureFlag.featureEnabled(FeatureFlag.ManualMobileViewTracing);
    }

    /**
     * Monotonic milliseconds, for load-time measurement. Wall-clock deltas are not acceptable: a
     * clock adjustment mid-load would produce negative or absurd durations (IDD §5.4).
     */
    public static long monotonicNowMs() {
        return System.nanoTime() / 1_000_000L;
    }

    /**
     * Mark a timing against the current view: the duration is measured from the view's
     * construction start (so the mark shares the baseline's origin and encloses it), or from its
     * appearance when no construction start was observed, until now.
     *
     * @return false if timing is disabled, no view is current, or the timing was rejected
     */
    public boolean mark(String name) {
        if (!isEnabled()) {
            log.debug("MobileViewTiming.mark: both Mobile Views feature flags are disabled, ignoring.");
            return false;
        }

        final ViewTimingSnapshot snapshot = MobileViewContext.getInstance().snapshotForTiming();
        final long nowNs = System.nanoTime();

        // No current view means no zero point. A duration measured from an unknown start looks like
        // real data in an aggregate, which is worse than recording nothing.
        if (!snapshot.hasCurrentView()) {
            log.warn("View timing [" + name + "] ignored: no view is currently being tracked. "
                    + "Use recordViewTiming(name, milliseconds) to supply the duration yourself.");
            return false;
        }

        final long originNs = snapshot.hasLoadStart() ? snapshot.getLoadStartNs() : snapshot.getAppearNs();
        return emit(name, millisBetween(originNs, nowNs), snapshot, false);
    }

    /**
     * Record a timing whose duration the caller measured. Works with no view current, in which case
     * the event carries no view identity.
     *
     * @return false if timing is disabled or the timing was rejected
     */
    public boolean record(String name, double milliseconds) {
        if (!isEnabled()) {
            log.debug("MobileViewTiming.record: both Mobile Views feature flags are disabled, ignoring.");
            return false;
        }

        return emit(name, milliseconds, MobileViewContext.getInstance().snapshotForTiming(), false);
    }

    /**
     * Agent-owned baseline: load start to appear, derived from the origins the context already
     * holds rather than from a number passed in, so the baseline and customer marks cannot be
     * measured from different instants. Emits nothing when no view is current or no construction
     * start was observed: there is nothing to time, and a placeholder would be a real value in
     * every percentile.
     */
    void recordInitialDisplay(ViewTimingSnapshot snapshot) {
        if (!isEnabled() || !snapshot.hasCurrentView() || !snapshot.hasLoadStart()) {
            return;
        }

        emit(TIME_TO_INITIAL_DISPLAY, millisBetween(snapshot.getLoadStartNs(), snapshot.getAppearNs()), snapshot, true);
    }

    private boolean emit(String name, double milliseconds, ViewTimingSnapshot snapshot, boolean agentOwned) {
        try {
            // Checked before admission, which counts the timing against its cap: a timing that is
            // admitted but then refused by an uninitialized controller would use up a slot for
            // nothing, and calls made before the agent starts could exhaust the shared no-view
            // bucket until the next session.
            if (!AnalyticsControllerImpl.getInstance().isInitializedAndEnabled()) {
                log.debug("MobileViewTiming: analytics controller is not initialized or enabled, ignoring [" + name + "].");
                return false;
            }
            if (!admit(name, milliseconds, snapshot.getViewInstanceId(), agentOwned)) {
                return false;
            }
            return AnalyticsControllerImpl.getInstance().recordMobileViewTimingEvent(timingAttributes(name, milliseconds, snapshot));
        } catch (Exception e) {
            log.error("MobileViewTiming.emit: ", e);
            return false;
        }
    }

    static Map<String, Object> timingAttributes(String name, double milliseconds, ViewTimingSnapshot snapshot) {
        final Map<String, Object> attributes = new HashMap<>();
        attributes.put(AnalyticsAttribute.MOBILE_VIEW_TIMING_NAME_ATTRIBUTE, name);
        // + 0.0 turns -0.0 into 0.0, which passes the non-negative check but would serialize as "-0.0"
        attributes.put(AnalyticsAttribute.MOBILE_VIEW_TIMING_VALUE_ATTRIBUTE, milliseconds + 0.0);
        if (snapshot.hasCurrentView()) {
            attributes.put(AnalyticsAttribute.MOBILE_VIEW_NAME_ATTRIBUTE, snapshot.getViewName());
            if (snapshot.getViewInstanceId() != null && !snapshot.getViewInstanceId().isEmpty()) {
                attributes.put(AnalyticsAttribute.MOBILE_VIEW_INSTANCE_ID_ATTRIBUTE, snapshot.getViewInstanceId());
            }
            if (snapshot.getPreviousView() != null && !snapshot.getPreviousView().isEmpty()) {
                attributes.put(AnalyticsAttribute.MOBILE_VIEW_PREVIOUS_VIEW_ATTRIBUTE, snapshot.getPreviousView());
            }
        }
        return attributes;
    }

    private static double millisBetween(long startNs, long endNs) {
        return (endNs - startNs) / 1_000_000.0;
    }

    /**
     * Decide whether a timing may be recorded, counting it against its view instance's cap if so.
     * Rejections are logged at warning level with their reason.
     *
     * @param name           timing name
     * @param milliseconds   duration in milliseconds
     * @param viewInstanceId the view instance the timing belongs to, or null/empty when no view is current
     * @param agentOwned     true for the agent's own baseline, which skips the reserved-name check and the cap
     * @return true if the timing is admitted
     */
    public boolean admit(String name, double milliseconds, String viewInstanceId, boolean agentOwned) {
        if (!isValidName(name, agentOwned) || !isValidDuration(name, milliseconds)) {
            return false;
        }

        // Agent-owned rows bypass the cap: a customer marking in a loop must not be able to
        // suppress the out-of-the-box baseline.
        return agentOwned || admitWithinCap(viewInstanceId);
    }

    /**
     * Empty the bucket shared by timings that have no view identity. Called at each session start.
     */
    public void resetUnattributedCapForNewSession() {
        synchronized (lock) {
            unattributedCount = 0;
        }
    }

    /**
     * Clears all state. Intended for test use only.
     */
    public void clear() {
        synchronized (lock) {
            viewBuckets.clear();
            unattributedCount = 0;
            warnedAboutCap = false;
        }
    }

    private boolean isValidName(String name, boolean agentOwned) {
        if (name == null || name.isEmpty()) {
            log.warn("View timing ignored: name must not be empty.");
            return false;
        }
        if (name.length() > MAX_NAME_LENGTH) {
            log.warn("View timing [" + name + "] ignored: name exceeds " + MAX_NAME_LENGTH + " characters.");
            return false;
        }
        if (!agentOwned && TIME_TO_INITIAL_DISPLAY.equals(name)) {
            log.warn("View timing [" + name + "] ignored: that name is recorded by the agent and is reserved. Choose another name.");
            return false;
        }
        return true;
    }

    private boolean isValidDuration(String name, double milliseconds) {
        // NaN fails every comparison, so a range check alone would let it through, and a single NaN
        // silently poisons every average and percentile computed over the event type.
        if (Double.isNaN(milliseconds) || Double.isInfinite(milliseconds)) {
            log.warn("View timing [" + name + "] ignored: duration is not a finite number.");
            return false;
        }
        if (milliseconds < 0) {
            log.warn("View timing [" + name + "] ignored: duration " + milliseconds + " is negative.");
            return false;
        }
        if (milliseconds > MAX_MILLISECONDS) {
            log.warn("View timing [" + name + "] ignored: duration " + milliseconds + "ms exceeds the "
                    + (long) MAX_MILLISECONDS + "ms limit. Durations are milliseconds, not seconds.");
            return false;
        }
        return true;
    }

    private boolean admitWithinCap(String viewInstanceId) {
        final boolean unattributed = (viewInstanceId == null || viewInstanceId.isEmpty());
        boolean admitted = false;
        boolean shouldWarn = false;

        synchronized (lock) {
            if (unattributed) {
                if (unattributedCount < MAX_TIMINGS_PER_VIEW_INSTANCE) {
                    unattributedCount++;
                    admitted = true;
                }
            } else {
                Integer count = viewBuckets.get(viewInstanceId);
                if (count == null) {
                    count = 0;
                    // Evict oldest-inserted buckets so a long session cannot accumulate one per visited view.
                    // A new bucket is added first and the oldest removed after, so it can never evict itself.
                    viewBuckets.put(viewInstanceId, count);
                    Iterator<Map.Entry<String, Integer>> oldest = viewBuckets.entrySet().iterator();
                    while (viewBuckets.size() > MAX_TRACKED_BUCKETS && oldest.hasNext()) {
                        oldest.next();
                        oldest.remove();
                    }
                }
                if (count < MAX_TIMINGS_PER_VIEW_INSTANCE) {
                    viewBuckets.put(viewInstanceId, count + 1);
                    admitted = true;
                }
            }

            if (!admitted && !warnedAboutCap) {
                warnedAboutCap = true;
                shouldWarn = true;
            }
        }

        // Logged outside the lock: the logger can take locks of its own, so holding ours across it risks deadlock.
        if (shouldWarn) {
            log.warn("View timing dropped: more than " + MAX_TIMINGS_PER_VIEW_INSTANCE
                    + " timings recorded for a single view. Later timings for this view are ignored.");
        }
        return admitted;
    }
}
