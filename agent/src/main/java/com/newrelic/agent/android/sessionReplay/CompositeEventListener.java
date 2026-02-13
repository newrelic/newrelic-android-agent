/*
 * Copyright 2023-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay;

import com.newrelic.agent.android.analytics.AnalyticsEvent;
import com.newrelic.agent.android.analytics.EventListener;
import com.newrelic.agent.android.analytics.EventManager;

/**
 * Composite event listener that delegates to both SessionReplay and an optional user-provided listener.
 * This ensures SessionReplay always receives events while allowing users to provide their own listener.
 */
public class CompositeEventListener implements EventListener {
    private final EventListener sessionReplayListener;
    private volatile EventListener userListener;

    public CompositeEventListener(EventListener sessionReplayListener) {
        this.sessionReplayListener = sessionReplayListener;
        this.userListener = null;
    }

    /**
     * Sets the session replay listener (the primary listener that SessionReplay will use).
     * This is called when SessionReplay initializes or re-initializes.
     *
     * @param listener The SessionReplay listener
     */
    public void setSessionReplayListener(EventListener listener) {
        // Note: sessionReplayListener is immutable after construction, so we would need to refactor
        // if this method needs to actually change the listener. For now, this is a no-op as SessionReplay
        // should remain the primary listener throughout the session.
    }

    /**
     * Sets the user-provided event listener. This listener will receive events in addition to SessionReplay.
     *
     * @param listener The user's event listener, or null to remove it
     */
    public void setUserListener(EventListener listener) {
        this.userListener = listener;
    }

    @Override
    public boolean onEventAdded(AnalyticsEvent analyticsEvent) {
        boolean sessionReplayResult = true;
        boolean userResult = true;

        // Always call SessionReplay listener
        if (sessionReplayListener != null) {
            sessionReplayResult = sessionReplayListener.onEventAdded(analyticsEvent);
        }

        // Call user listener if set
        if (userListener != null) {
            userResult = userListener.onEventAdded(analyticsEvent);
        }

        // Return true only if both return true (be conservative)
        return sessionReplayResult && userResult;
    }

    @Override
    public boolean onEventOverflow(AnalyticsEvent analyticsEvent) {
        boolean sessionReplayResult = true;
        boolean userResult = true;

        if (sessionReplayListener != null) {
            sessionReplayResult = sessionReplayListener.onEventOverflow(analyticsEvent);
        }

        if (userListener != null) {
            userResult = userListener.onEventOverflow(analyticsEvent);
        }

        return sessionReplayResult && userResult;
    }

    @Override
    public boolean onEventEvicted(AnalyticsEvent analyticsEvent) {
        boolean sessionReplayResult = true;
        boolean userResult = true;

        if (sessionReplayListener != null) {
            sessionReplayResult = sessionReplayListener.onEventEvicted(analyticsEvent);
        }

        if (userListener != null) {
            userResult = userListener.onEventEvicted(analyticsEvent);
        }

        return sessionReplayResult && userResult;
    }

    @Override
    public void onEventQueueSizeExceeded(int currentQueueSize) {
        if (sessionReplayListener != null) {
            sessionReplayListener.onEventQueueSizeExceeded(currentQueueSize);
        }

        if (userListener != null) {
            userListener.onEventQueueSizeExceeded(currentQueueSize);
        }
    }

    @Override
    public void onEventQueueTimeExceeded(int maxBufferTimeInSec) {
        if (sessionReplayListener != null) {
            sessionReplayListener.onEventQueueTimeExceeded(maxBufferTimeInSec);
        }

        if (userListener != null) {
            userListener.onEventQueueTimeExceeded(maxBufferTimeInSec);
        }
    }

    @Override
    public void onEventFlush() {
        if (sessionReplayListener != null) {
            sessionReplayListener.onEventFlush();
        }

        if (userListener != null) {
            userListener.onEventFlush();
        }
    }

    @Override
    public void onStart(EventManager eventManager) {
        if (sessionReplayListener != null) {
            sessionReplayListener.onStart(eventManager);
        }

        if (userListener != null) {
            userListener.onStart(eventManager);
        }
    }

    @Override
    public void onShutdown() {
        if (sessionReplayListener != null) {
            sessionReplayListener.onShutdown();
        }

        if (userListener != null) {
            userListener.onShutdown();
        }
    }
}
