/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.mobileview;

/**
 * An immutable copy of the current view's identity and timing origins, taken under
 * {@link MobileViewContext}'s lock so a timing can be admitted and emitted after the lock is
 * released. Emitting while holding the lock risks deadlock.
 * <p>
 * The two origins are held together because every MobileViewTiming row on a view resolves against
 * them: the baseline measures load start to appear, and a customer mark measures load start (or
 * appear, when no construction start was observed) to now. All times are {@code System.nanoTime()}
 * values, which are monotonic.
 */
public final class ViewTimingSnapshot {
    private static final ViewTimingSnapshot NO_VIEW = new ViewTimingSnapshot(null, null, null, 0, 0, false, false);

    private final String viewName;
    private final String viewInstanceId;
    private final String previousView;
    private final long appearNs;
    private final long loadStartNs;
    private final boolean hasLoadStart;
    private final boolean hasCurrentView;

    ViewTimingSnapshot(String viewName, String viewInstanceId, String previousView,
                       long appearNs, long loadStartNs, boolean hasLoadStart, boolean hasCurrentView) {
        this.viewName = viewName;
        this.viewInstanceId = viewInstanceId;
        this.previousView = previousView;
        this.appearNs = appearNs;
        this.loadStartNs = loadStartNs;
        this.hasLoadStart = hasLoadStart;
        this.hasCurrentView = hasCurrentView;
    }

    static ViewTimingSnapshot noView() {
        return NO_VIEW;
    }

    public String getViewName() {
        return viewName;
    }

    public String getViewInstanceId() {
        return viewInstanceId;
    }

    public String getPreviousView() {
        return previousView;
    }

    public long getAppearNs() {
        return appearNs;
    }

    public long getLoadStartNs() {
        return loadStartNs;
    }

    /**
     * @return true if the producer observed this view's construction, so {@link #getLoadStartNs()} is meaningful
     */
    public boolean hasLoadStart() {
        return hasLoadStart;
    }

    /**
     * @return true if a view is currently visible; false means the other fields carry no identity
     */
    public boolean hasCurrentView() {
        return hasCurrentView;
    }
}
