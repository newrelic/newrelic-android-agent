/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.analytics;

/**
 * In methods that pass an Event instance, the attribute set is mutable
 * Listener methods will be called on the thread that added the event
 */
public interface EventListener {

    /**
     * Notification that an event will be added to the queue.
     * Caller may mutate the event as desired but any changes must
     * adhere to event and attribute requirements.
     *
     * @param analyticsEvent that would be added to the buffer
     * @return true to add this event, false to ignore
     */
    boolean onEventAdded(AnalyticsEvent analyticsEvent);

    /**
     * Notification that adding this event would exceed the
     * configured queue size
     *
     * @param analyticsEvent that would be added to full buffer
     * @return true to ignore this event, false to add anyway
     */
    boolean onEventOverflow(AnalyticsEvent analyticsEvent);

    /**
     * Notification that this event will be removed from
     * the queue to satisfy size constraints
     *
     * @param analyticsEvent that would be removed make room in the buffer
     * @return true to evict this event, false to keep in buffer
     */
    boolean onEventEvicted(AnalyticsEvent analyticsEvent);

    /**
     * Notified when event buffer has surpassed the current configured size.
     * The caller can update the limit using {@link NewRelic.setMaxEventPoolSize()}
     *
     * @param currentQueueSize
     */
    void onEventQueueSizeExceeded(int currentQueueSize);

    /**
     * Notified when event buffer flush time has been exceeded. This normally occurs
     * between harvest cycles.
     *
     * The caller can update the limit using {@link NewRelic.setMaxEventBufferTime()}
     *
     * @param maxBufferTimeInSec
     */
    void onEventQueueTimeExceeded(int maxBufferTimeInSec);

    /**
     * Notified immediately prior to event buffer flush, when queued events
     * are transferred to a harvest payload. After flush, events are "in-flight"
     * (meaning not persisted and subject to harvest failures, app terminations, etc).
     */
    void onEventFlush();

    /**
     * Called when event manager has initialized and is ready for input.
     * Caller can populate event queue with saved events here, for example
     */
    void onStart(EventManager eventManager);

    /**
     * Called immediately prior to the event manager shut down. Since its no longer enabled,
     * caller should not submit new events, which would then be dropped by the manager
     */
    void onShutdown();
}
