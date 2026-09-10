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

    private final Object lock = new Object();
    private String currentView;
    private String currentViewInstanceId;
    private String previousView;
    private String previousViewInstanceId;
    private long currentViewAppearedAtMs;
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
        String viewName = appearance.getViewName();

        if (viewName == null || viewName.isEmpty()) {
            log.warn("MobileViewContext.onViewAppeared(): view name is null or empty, ignoring.");
            return null;
        }

        if (appearance.getUiPlatform() == null) {
            log.warn("MobileViewContext.onViewAppeared(): uiPlatform is null, ignoring.");
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

        synchronized (lock) {
            referrer = currentView;
            referrerInstanceId = currentViewInstanceId;
            restarted = !everAppearedViewNames.add(viewName);
            previousView = currentView;
            previousViewInstanceId = currentViewInstanceId;
            currentView = viewName;
            currentViewInstanceId = viewInstanceId;
            currentViewAppearedAtMs = now;
        }

        Map<String, Object> eventAttributes = new HashMap<>();
        if (appearance.getAttributes() != null) {
            eventAttributes.putAll(appearance.getAttributes());
        }
        eventAttributes.put(AnalyticsAttribute.MOBILE_VIEW_NAME_ATTRIBUTE, viewName);
        eventAttributes.put(AnalyticsAttribute.MOBILE_VIEW_INSTANCE_ID_ATTRIBUTE, viewInstanceId);
        eventAttributes.put(AnalyticsAttribute.MOBILE_VIEW_APPEARED_ATTRIBUTE, true);
        eventAttributes.put(AnalyticsAttribute.MOBILE_VIEW_RESTARTED_ATTRIBUTE, restarted);
        eventAttributes.put(AnalyticsAttribute.MOBILE_VIEW_UI_PLATFORM_ATTRIBUTE, appearance.getUiPlatform().getWireValue());
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
        Long loadTimeMs = appearance.getLoadTimeMs();
        if (loadTimeMs != null && loadTimeMs >= 0 && !restarted) {
            // loadTime is only meaningful for a genuine first construction (IDD §5.4) - a
            // screen that resurfaced without being rebuilt has nothing to time.
            eventAttributes.put(AnalyticsAttribute.MOBILE_VIEW_LOAD_TIME_ATTRIBUTE, loadTimeMs.doubleValue());
        }

        AnalyticsControllerImpl.getInstance().recordMobileViewEvent(viewName, eventAttributes);

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
            viewInstanceId = currentViewInstanceId;
        }

        Map<String, Object> eventAttributes = new HashMap<>();
        eventAttributes.put(AnalyticsAttribute.MOBILE_VIEW_NAME_ATTRIBUTE, viewName);
        if (viewInstanceId != null) {
            eventAttributes.put(AnalyticsAttribute.MOBILE_VIEW_INSTANCE_ID_ATTRIBUTE, viewInstanceId);
        }
        eventAttributes.put(AnalyticsAttribute.MOBILE_VIEW_APPEARED_ATTRIBUTE, false);
        eventAttributes.put(AnalyticsAttribute.MOBILE_VIEW_TIME_VISIBLE_ATTRIBUTE, timeVisibleMs);

        AnalyticsControllerImpl.getInstance().recordMobileViewEvent(viewName, eventAttributes);
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
            everAppearedViewNames.clear();
        }
    }
}
