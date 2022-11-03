/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.analytics;

import com.newrelic.agent.android.AgentConfiguration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public interface EventManager {

    /**
     * Initializes the event manager.
     */
    void initialize(AgentConfiguration agentConfiguration);

    /**
     * Shuts down the event manager.
     */
    void shutdown();

    /**
     * Returns the number of events currently enqueued.
     *
     * @return the number of events currently enqueued.
     */
    int size();

    /**
     * Removes all pending events.
     */
    void empty();

    boolean isTransmitRequired();

    /**
     * Provides a hard-flush mechanism callers can use to trigger a transmit condition
     * Flag will be reset the first time it is evaluated. Don't abuse it.
     */
    void setTransmitRequired();

    /**
     * Add an event.
     *
     * @param event The event to add.
     * @return true if the event was successfully added, false otherwise
     */
    boolean addEvent(AnalyticsEvent event);

    /**
     * @return the total number of events recorded since session start.
     */
    int getEventsRecorded();

    /**
     * @return the number of events evicted from the buffer since session start.
     */
    int getEventsEjected();

    /**
     * @return the number of events ignored since session start.
     */
    int getEventsDropped();

    /**
     * Returns whether the maximum event age has been reached for the event buffer.
     *
     * @return whether the maximum event age has been reached for the event buffer.
     */
    boolean isMaxEventBufferTimeExceeded();

    public boolean isMaxEventPoolSizeExceeded();

    /**
     * Returns the maximum size of the event queue.  Once this limit is reached all enqueued
     * events will be transmitted on the following data harvest.
     *
     * @return the maximum size of the event queue.
     */
    int getMaxEventPoolSize();

    /**
     * Sets the maximum size of the event queue.
     *
     * @param maxSize the maximum size of the event queue.
     */
    void setMaxEventPoolSize(int maxSize);

    /**
     * Returns the maximum amount of time (in seconds) an event can be enqueued.  Once this limit
     * is reached all enqueued events will be transmitted on the following data harvest.
     *
     * @return The maximum age of an event (in seconds).
     */
    int getMaxEventBufferTime();

    /**
     * Sets the maximum amount of time (in seconds) an event can be enqueued.
     *
     * @param maxBufferTimeInSec The maximum age of an event (in seconds).
     */
    void setMaxEventBufferTime(int maxBufferTimeInSec);

    /**
     * Returns an immutable collection of the events in the queue.
     *
     * @return immutable collection of the events in the queue.
     */
    Collection<AnalyticsEvent> getQueuedEvents();

    /**
     * Installs a callback interface to to be invoked as
     * events are added to the queue. This be invoked once by the caller, prior to
     * calling NewRelic.start()
     *
     * @param listener
     */
    void setEventListener(EventListener listener);

}
