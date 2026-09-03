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
import java.util.Map;

/**
 * Shared state for MobileView tracking, written to by all MobileView producers
 * (Activity/Fragment lifecycle, Compose navigation, and the manual API). Stamps
 * referrer ("previousView") information onto MobileView events.
 */
public class MobileViewContext {
    private static final AgentLog log = AgentLogManager.getAgentLog();
    private static final MobileViewContext instance = new MobileViewContext();

    private final Object lock = new Object();
    private String currentView;
    private String previousView;
    private long currentViewAppearedAtMs;

    private MobileViewContext() {
    }

    public static MobileViewContext getInstance() {
        return instance;
    }

    /**
     * Record that a view has appeared, emitting a MobileView event and updating
     * the current/previous view referrer chain.
     *
     * @param viewName   Human-readable name for the view (e.g. Activity/Fragment simple name, or Compose route)
     * @param viewClass  Fully-qualified class name backing the view, or null if not applicable (e.g. Compose route)
     * @param attributes Optional caller-supplied attributes to merge into the event
     */
    public void onViewAppeared(String viewName, String viewClass, Map<String, Object> attributes) {
        onViewAppeared(viewName, viewClass, attributes, null);
    }

    /**
     * Record that a view has appeared, emitting a MobileView event and updating
     * the current/previous view referrer chain.
     *
     * @param viewName   Human-readable name for the view (e.g. Activity/Fragment simple name, or Compose route)
     * @param viewClass  Fully-qualified class name backing the view, or null if not applicable (e.g. Compose route)
     * @param attributes Optional caller-supplied attributes to merge into the event
     * @param loadTimeMs Elapsed time from view creation to this appearance, or null if not applicable/known
     */
    public void onViewAppeared(String viewName, String viewClass, Map<String, Object> attributes, Long loadTimeMs) {
        if (viewName == null || viewName.isEmpty()) {
            log.warn("MobileViewContext.onViewAppeared(): view name is null or empty, ignoring.");
            return;
        }

        if (!AnalyticsControllerImpl.getInstance().isInitializedAndEnabled()) {
            log.debug("MobileViewContext.onViewAppeared(): analytics controller is not initialized or enabled, ignoring.");
            return;
        }

        final String referrer;
        final long now = System.currentTimeMillis();

        synchronized (lock) {
            referrer = currentView;
            previousView = currentView;
            currentView = viewName;
            currentViewAppearedAtMs = now;
        }

        Map<String, Object> eventAttributes = new HashMap<>();
        if (attributes != null) {
            eventAttributes.putAll(attributes);
        }
        eventAttributes.put(AnalyticsAttribute.MOBILE_VIEW_NAME_ATTRIBUTE, viewName);
        if (viewClass != null && !viewClass.isEmpty()) {
            eventAttributes.put(AnalyticsAttribute.MOBILE_VIEW_CLASS_ATTRIBUTE, viewClass);
        }
        if (referrer != null && !referrer.isEmpty()) {
            eventAttributes.put(AnalyticsAttribute.MOBILE_VIEW_PREVIOUS_VIEW_ATTRIBUTE, referrer);
        }
        if (loadTimeMs != null && loadTimeMs >= 0) {
            eventAttributes.put(AnalyticsAttribute.MOBILE_VIEW_LOAD_TIME_ATTRIBUTE, loadTimeMs.doubleValue());
        }

        AnalyticsControllerImpl.getInstance().recordMobileViewEvent(viewName, eventAttributes);
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
        }

        Map<String, Object> eventAttributes = new HashMap<>();
        eventAttributes.put(AnalyticsAttribute.MOBILE_VIEW_NAME_ATTRIBUTE, viewName);
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
     * @return the name of the previously visible view, or null if none has been recorded
     */
    public String getPreviousView() {
        synchronized (lock) {
            return previousView;
        }
    }

    /**
     * Resets context state. Intended for test use only.
     */
    public void clear() {
        synchronized (lock) {
            currentView = null;
            previousView = null;
            currentViewAppearedAtMs = 0;
        }
    }
}
