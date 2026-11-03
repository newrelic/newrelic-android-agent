/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.mobileview;

import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.analytics.AnalyticsControllerImpl;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Shared state for MobileView tracking, written to by all MobileView producers
 * (Activity/Fragment lifecycle, Compose navigation, and the manual API). Stamps
 * referrer ("previousView"/"previousViewInstanceId") information onto MobileView events
 * and assigns each appearance's "viewInstanceId", per the Mobile Views IDD (NR-356639) §5.1:
 * viewInstanceId is assigned by the context, not by producers, so it stays unique per visible
 * lifetime even when producers overlap.
 */
public class MobileViewContext {
    private static final AgentLog log = AgentLogManager.getAgentLog();
    private static final MobileViewContext instance = new MobileViewContext();

    // viewName, previousView and currentView are truncated at this many characters (IDD §5.3.5).
    static final int MAX_VIEW_NAME_LENGTH = 128;

    private final Object lock = new Object();
    private String currentView;
    private String currentViewInstanceId;
    private String previousView;
    private String previousViewInstanceId;
    private long currentViewAppearedAtMs;
    // Timing origins for the current view (see ViewTimingSnapshot). Monotonic System.nanoTime()
    // values, kept next to the identity above under the same lock so the baseline and customer
    // marks always resolve against the same instants.
    private boolean currentViewVisible;
    private long currentViewAppearedAtNs;
    private long currentViewLoadStartNs;
    private boolean currentViewHasLoadStart;
    private final Set<String> everAppearedViewNames = new HashSet<>();

    private MobileViewContext() {
    }

    public static MobileViewContext getInstance() {
        return instance;
    }

    /**
     * Record that a view has appeared, emitting a MobileView event and updating
     * the current/previous view referrer chain.
     *
     * @param appearance Details of the view that appeared.
     * @return The viewInstanceId assigned to this appearance, or null if the event was not recorded.
     */
    public String onViewAppeared(MobileViewAppearance appearance) {
        String viewName = truncateViewName(appearance.getViewName());

        if (viewName == null || viewName.isEmpty()) {
            log.warn("MobileViewContext.onViewAppeared(): view name is null or empty, ignoring.");
            return null;
        }

        if (appearance.getUiPlatform() == null) {
            log.warn("MobileViewContext.onViewAppeared(): uiFramework is null, ignoring.");
            return null;
        }

        if (!AnalyticsControllerImpl.getInstance().isInitializedAndEnabled()) {
            log.debug("MobileViewContext.onViewAppeared(): analytics controller is not initialized or enabled, ignoring.");
            return null;
        }

        final String referrer;
        final String referrerInstanceId;
        final String viewInstanceId = UUID.randomUUID().toString();
        final boolean restarted;
        final long now = System.currentTimeMillis();
        final long appearNs = System.nanoTime();

        // A load time longer than the longest accepted timing is not vouched for: the view was not
        // plausibly being built that whole time (IDD §6.3). It is treated as unobserved, so the
        // MobileView carries no loadTime and no baseline timing is sent.
        final Long loadTimeMs = appearance.getLoadTimeMs();
        final boolean hasLoadTime = loadTimeMs != null && loadTimeMs >= 0 && loadTimeMs <= MobileViewTiming.MAX_MILLISECONDS;
        if (loadTimeMs != null && !hasLoadTime) {
            log.debug("MobileViewContext.onViewAppeared(): [" + viewName + "] load time " + loadTimeMs + "ms is out of range, omitting.");
        }
        final ViewTimingSnapshot snapshot;

        synchronized (lock) {
            referrer = currentView;
            referrerInstanceId = currentViewInstanceId;
            restarted = !everAppearedViewNames.add(viewName);
            previousView = currentView;
            previousViewInstanceId = currentViewInstanceId;
            currentView = viewName;
            currentViewInstanceId = viewInstanceId;
            currentViewAppearedAtMs = now;
            currentViewVisible = true;
            currentViewAppearedAtNs = appearNs;
            currentViewHasLoadStart = hasLoadTime;
            currentViewLoadStartNs = hasLoadTime ? appearNs - loadTimeMs * 1_000_000L : 0;
            snapshot = new ViewTimingSnapshot(viewName, viewInstanceId, referrer, appearNs,
                    currentViewLoadStartNs, hasLoadTime, true);
        }

        Map<String, Object> eventAttributes = new HashMap<>();
        if (appearance.getAttributes() != null) {
            eventAttributes.putAll(appearance.getAttributes());
        }
        eventAttributes.put(AnalyticsAttribute.MOBILE_VIEW_NAME_ATTRIBUTE, viewName);
        eventAttributes.put(AnalyticsAttribute.MOBILE_VIEW_INSTANCE_ID_ATTRIBUTE, viewInstanceId);
        eventAttributes.put(AnalyticsAttribute.MOBILE_VIEW_APPEARED_ATTRIBUTE, true);
        eventAttributes.put(AnalyticsAttribute.MOBILE_VIEW_RESTARTED_ATTRIBUTE, restarted);
        eventAttributes.put(AnalyticsAttribute.MOBILE_VIEW_UI_FRAMEWORK_ATTRIBUTE, appearance.getUiPlatform().getWireValue());
        String viewClass = appearance.getViewClass();
        if (viewClass != null && !viewClass.isEmpty()) {
            eventAttributes.put(AnalyticsAttribute.MOBILE_VIEW_CLASS_ATTRIBUTE, viewClass);
        }
        if (referrer != null && !referrer.isEmpty()) {
            eventAttributes.put(AnalyticsAttribute.MOBILE_VIEW_PREVIOUS_VIEW_ATTRIBUTE, referrer);
            if (referrerInstanceId != null) {
                eventAttributes.put(AnalyticsAttribute.MOBILE_VIEW_PREVIOUS_VIEW_INSTANCE_ID_ATTRIBUTE, referrerInstanceId);
            }
        }
        if (hasLoadTime) {
            // loadTime is only meaningful for a genuine first construction (IDD §5.4). Producers
            // already encode that: they only supply a loadTimeMs when they observed this specific
            // instance's construction-to-first-resume window (e.g. the Activity/Fragment producers
            // key their created-at timestamp per instance and clear it once consumed), so a resumed
            // screen that was not rebuilt arrives here with loadTimeMs == null regardless of whether
            // its view name has appeared before. Gating on `restarted` (an "ever seen this view name
            // in the process" signal) instead would permanently suppress loadTime for every
            // subsequent genuine reconstruction of a view name after its first appearance.
            eventAttributes.put(AnalyticsAttribute.MOBILE_VIEW_LOAD_TIME_ATTRIBUTE, loadTimeMs.doubleValue());
        }

        boolean recorded = AnalyticsControllerImpl.getInstance().recordMobileViewEvent(viewName, eventAttributes);
        if (!recorded) {
            // The referrer chain (currentView/previousView) was already advanced above so a
            // disappear racing with this call sees consistent state - but if the event itself
            // was rejected (e.g. attribute validation failure), this appearance never actually
            // happened as far as any caller is concerned, so it must not be reported as if it had
            // (IDD §5.1 - callers like NewRelic.setCurrentView() return this success/failure to
            // their own caller).
            log.warn("MobileViewContext.onViewAppeared(): [" + viewName + "] event was not recorded, returning null.");
            return null;
        }

        // Emitted outside the lock, from the snapshot taken inside it, so a concurrent transition
        // cannot attach the baseline to a different view.
        MobileViewTiming.getInstance().recordInitialDisplay(snapshot);

        return viewInstanceId;
    }

    /**
     * Record that a view has disappeared, emitting a MobileView event with the
     * time the view was visible.
     *
     * @param viewName Name previously passed to {@link #onViewAppeared}
     */
    public void onViewDisappeared(String viewName) {
        if (viewName == null || viewName.isEmpty()) {
            log.warn("MobileViewContext.onViewDisappeared(): view name is null or empty, ignoring.");
            return;
        }

        // Producers report the untruncated name; it has to match what onViewAppeared stored.
        viewName = truncateViewName(viewName);

        if (!AnalyticsControllerImpl.getInstance().isInitializedAndEnabled()) {
            log.debug("MobileViewContext.onViewDisappeared(): analytics controller is not initialized or enabled, ignoring.");
            return;
        }

        final double timeVisibleMs;
        final String viewInstanceId;

        synchronized (lock) {
            if (!viewName.equals(currentView)) {
                // Another view has since become current (e.g. this callback fired after the
                // next view's appearance during a lifecycle transition) — the appearance
                // timestamp we're holding no longer belongs to viewName, so it can't be used
                // to compute a meaningful dwell time.
                log.debug("MobileViewContext.onViewDisappeared(): [" + viewName + "] is not the current view, ignoring.");
                return;
            }
            timeVisibleMs = currentViewAppearedAtMs == 0 ? 0 : (System.currentTimeMillis() - currentViewAppearedAtMs);
            currentViewAppearedAtMs = 0;
            // The view is no longer on screen: a later mark has no zero point on it.
            currentViewVisible = false;
            currentViewHasLoadStart = false;
            viewInstanceId = currentViewInstanceId;
        }

        Map<String, Object> eventAttributes = new HashMap<>();
        eventAttributes.put(AnalyticsAttribute.MOBILE_VIEW_NAME_ATTRIBUTE, viewName);
        if (viewInstanceId != null) {
            eventAttributes.put(AnalyticsAttribute.MOBILE_VIEW_INSTANCE_ID_ATTRIBUTE, viewInstanceId);
        }
        eventAttributes.put(AnalyticsAttribute.MOBILE_VIEW_APPEARED_ATTRIBUTE, false);
        eventAttributes.put(AnalyticsAttribute.MOBILE_VIEW_TIME_VISIBLE_ATTRIBUTE, timeVisibleMs);

        boolean recorded = AnalyticsControllerImpl.getInstance().recordMobileViewEvent(viewName, eventAttributes);
        if (!recorded) {
            log.warn("MobileViewContext.onViewDisappeared(): [" + viewName + "] event was not recorded.");
        }
    }

    /**
     * Agents don't scrub view names, but they do bound them. Cuts at {@link #MAX_VIEW_NAME_LENGTH}
     * characters without splitting a surrogate pair.
     */
    static String truncateViewName(String viewName) {
        if (viewName == null || viewName.length() <= MAX_VIEW_NAME_LENGTH) {
            return viewName;
        }
        int end = MAX_VIEW_NAME_LENGTH;
        if (Character.isHighSurrogate(viewName.charAt(end - 1))) {
            end--;
        }
        return viewName.substring(0, end);
    }

    /**
     * @return an immutable copy of the visible view's identity and timing origins, or a snapshot
     * with {@link ViewTimingSnapshot#hasCurrentView()} false when no view is visible
     */
    public ViewTimingSnapshot snapshotForTiming() {
        synchronized (lock) {
            if (!currentViewVisible || currentView == null) {
                return ViewTimingSnapshot.noView();
            }
            return new ViewTimingSnapshot(currentView, currentViewInstanceId, previousView,
                    currentViewAppearedAtNs, currentViewLoadStartNs, currentViewHasLoadStart, true);
        }
    }

    /**
     * @return the name of the currently visible view, or null if none has been recorded
     */
    public String getCurrentView() {
        synchronized (lock) {
            return currentView;
        }
    }

    /**
     * @return the viewInstanceId of the currently visible view, or null if none has been recorded
     */
    public String getCurrentViewInstanceId() {
        synchronized (lock) {
            return currentViewInstanceId;
        }
    }

    /**
     * @return the name of the previously visible view, or null if none has been recorded
     */
    public String getPreviousView() {
        synchronized (lock) {
            return previousView;
        }
    }

    /**
     * @return the viewInstanceId of the previously visible view, or null if none has been recorded
     */
    public String getPreviousViewInstanceId() {
        synchronized (lock) {
            return previousViewInstanceId;
        }
    }

    /**
     * Resets context state. Intended for test use only.
     */
    public void clear() {
        synchronized (lock) {
            currentView = null;
            currentViewInstanceId = null;
            previousView = null;
            previousViewInstanceId = null;
            currentViewAppearedAtMs = 0;
            currentViewVisible = false;
            currentViewAppearedAtNs = 0;
            currentViewLoadStartNs = 0;
            currentViewHasLoadStart = false;
            everAppearedViewNames.clear();
        }
    }
}
