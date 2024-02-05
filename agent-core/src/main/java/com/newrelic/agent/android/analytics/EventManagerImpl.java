/*
 * Copyright 2021-present, New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.analytics;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.harvest.HarvestTimer;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.stats.StatsEngine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class EventManagerImpl implements EventManager, EventListener {
    private static final AgentLog log = AgentLogManager.getAgentLog();
    public static final int DEFAULT_MAX_EVENT_BUFFER_TIME = 600;    // 600 seconds (10 minutes)
    public static final int DEFAULT_MAX_EVENT_BUFFER_SIZE = 1000;   // 1000 as the default

    public static final int DEFAULT_MIN_EVENT_BUFFER_SIZE = 64;
    public static final int DEFAULT_MIN_EVENT_BUFFER_TIME = (int) (HarvestTimer.DEFAULT_HARVEST_PERIOD / 1000);     // 60 seconds (1 minutes, same as harvest)

    private AtomicReference<List<AnalyticsEvent>> events;
    int maxEventPoolSize;
    int maxBufferTimeInSec;
    private long firstEventTimestamp;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicInteger eventsRecorded = new AtomicInteger(0);
    private final AtomicInteger eventsEvicted = new AtomicInteger(0);
    private final AtomicInteger eventsDropped = new AtomicInteger(0);
    private final AtomicBoolean transmitRequired = new AtomicBoolean(true);
    private final AtomicReference<EventListener> listener = new AtomicReference<EventListener>(this);
    AtomicReference<EventManager> instance = new AtomicReference<>(null);

    public EventManagerImpl() {
        // Currently using 1000 max events, 5 minutes max buffer age
        this(DEFAULT_MAX_EVENT_BUFFER_SIZE, DEFAULT_MAX_EVENT_BUFFER_TIME);
    }

    public EventManagerImpl(int maxEventPoolSize, int maxBufferTimeInSec) {
        this.events = new AtomicReference<List<AnalyticsEvent>>(Collections.synchronizedList(new ArrayList<AnalyticsEvent>(maxEventPoolSize)));
        this.maxBufferTimeInSec = maxBufferTimeInSec;
        this.maxEventPoolSize = maxEventPoolSize;
        this.firstEventTimestamp = 0;
        this.eventsRecorded.set(0);
        this.eventsEvicted.set(0);
        this.eventsDropped.set(0);

        instance.compareAndSet(null, this);
    }

    @Override
    public void initialize(AgentConfiguration agentConfiguration) {
        if (!initialized.compareAndSet(false, true)) {
            log.verbose("EventManagerImpl.initialize(): Has already been initialized. Bypassing...");
            return;
        }

        firstEventTimestamp = 0;
        eventsRecorded.set(0);
        eventsEvicted.set(0);
        empty();

        listener.get().onStart(this);
    }

    @Override
    public void shutdown() {
        listener.get().onShutdown();
        initialized.set(false);
    }

    @Override
    public int size() {
        return events.get().size();
    }

    @Override
    public void empty() {
        Collection<AnalyticsEvent> droppedEvents = getQueuedEventsSnapshot();
        if (droppedEvents.size() > 0) {
            log.warn("EventManager.empty(): dropped [" + droppedEvents.size() + "] events");
        }
        droppedEvents.clear();
        firstEventTimestamp = 0;
    }

    public void empty(Collection<AnalyticsEvent> harvestedEvents) {
        events.get().removeAll(harvestedEvents);
    }

    @Override
    public boolean isTransmitRequired() {
        // Transmit is required if:
        //  * there are events in the buffer and the the event manager is not initialized
        //      (this can only occur on session termination),
        //  * the manual hard flush was called, or
        //  * the max buffer time or size has been exceeded.
        return (!initialized.get() && events.get().size() > 0) ||
                transmitRequired.compareAndSet(true, false) ||
                isMaxEventPoolSizeExceeded() ||
                isMaxEventBufferTimeExceeded();
    }

    /**
     * Trip the flag to dump the events buffer on next transmit check
     */
    @Override
    public void setTransmitRequired() {
        transmitRequired.set(true);
    }

    @Override
    public boolean addEvent(AnalyticsEvent event) {

        if (!initialized.get()) {
            eventsDropped.incrementAndGet();
            return false;
        }

        if (!listener.get().onEventAdded(event)) {
            log.warn("Listener dropped new event[" + event.getName() + "]");
            eventsDropped.incrementAndGet();
            return false;
        }

        if (isMaxEventBufferTimeExceeded()) {
            listener.get().onEventQueueTimeExceeded(maxBufferTimeInSec);
        }

        synchronized (events.get()) {
            final int snapshotSize = events.get().size();

            if (snapshotSize == 0) {
                firstEventTimestamp = System.currentTimeMillis();
                log.debug("EventManager.addEvent(): Queue is empty, setting first event timestamp to " + firstEventTimestamp);
            }

            if (snapshotSize >= maxEventPoolSize) {
                try {
                    if (listener.get().onEventOverflow(event)) {
                        log.warn("Listener dropped overflow event[" + event.getName() + "]");
                        eventsDropped.incrementAndGet();
                        return false;
                    }

                    // Choose a random event to throw away such that the queue size is constant.
                    // This eviction algorithm is based on a similar implementation used by the Ruby agent.
                    // It is designed to give equal probability that new events are stored, to avoid
                    // filling the queue with old events and never evicting, or always evicting and filling
                    // the queue with only new events.

                    int index = (int) (Math.random() * eventsRecorded.get());
                    if (index >= maxEventPoolSize) {
                        if (listener.get().onEventEvicted(event)) {
                            // Drop the new event
                            eventsDropped.incrementAndGet();
                            return false;
                        }
                    } else {
                        AnalyticsEvent evicted = events.get().get(index);
                        if (listener.get().onEventEvicted(evicted)) {
                            // Drop the event at the random index
                            events.get().remove(index);
                            eventsEvicted.incrementAndGet();
                        }
                    }

                    // notify the listener
                    listener.get().onEventQueueSizeExceeded(snapshotSize);

                } finally {
                    log.debug("Event queue is full, scheduling harvest");
                }
            }

            if (events.get().add(event)) {
                // log.audit("Event added: [" + event.asJson() + "]");
                eventsRecorded.incrementAndGet();
                return true;
            }

            return false;
        }
    }

    @Override
    public int getEventsRecorded() {
        return eventsRecorded.get();
    }

    @Override
    public int getEventsEjected() {
        return eventsEvicted.get();
    }

    public int getEventsDropped() {
        return eventsDropped.get();
    }

    @Override
    public boolean isMaxEventBufferTimeExceeded() {
        if (firstEventTimestamp > 0) {
            return (System.currentTimeMillis() - firstEventTimestamp) > (maxBufferTimeInSec * 1000);
        } else {
            return false;
        }
    }

    @Override
    public boolean isMaxEventPoolSizeExceeded() {
        return events.get().size() > maxEventPoolSize;
    }

    @Override
    public int getMaxEventPoolSize() {
        return maxEventPoolSize;
    }

    @Override
    public void setMaxEventPoolSize(int maxSize) {
        if (maxSize < DEFAULT_MIN_EVENT_BUFFER_SIZE) {
            log.error("Event queue cannot be smaller than " + DEFAULT_MIN_EVENT_BUFFER_SIZE);
            maxSize = DEFAULT_MIN_EVENT_BUFFER_SIZE;
        }

        if (maxSize > DEFAULT_MAX_EVENT_BUFFER_SIZE) {
            log.warn("Event queue should not be larger than " + DEFAULT_MAX_EVENT_BUFFER_SIZE);
        }

        this.maxEventPoolSize = maxSize;
    }

    @Override
    public void setMaxEventBufferTime(int maxBufferTimeInSec) {
        if (maxBufferTimeInSec < DEFAULT_MIN_EVENT_BUFFER_TIME) {
            log.error("Event buffer time cannot be shorter than " + DEFAULT_MIN_EVENT_BUFFER_TIME + " seconds");
            maxBufferTimeInSec = DEFAULT_MIN_EVENT_BUFFER_TIME;
        }

        if (maxBufferTimeInSec > DEFAULT_MAX_EVENT_BUFFER_TIME) {
            log.warn("Event buffer time should not be longer than " + DEFAULT_MAX_EVENT_BUFFER_TIME + " seconds");
            maxBufferTimeInSec = DEFAULT_MAX_EVENT_BUFFER_TIME;
        }

        this.maxBufferTimeInSec = maxBufferTimeInSec;
    }

    @Override
    public int getMaxEventBufferTime() {
        return maxBufferTimeInSec;
    }

    @Override
    public Collection<AnalyticsEvent> getQueuedEvents() {
        synchronized (events.get()) {
            return Collections.unmodifiableCollection(events.get());
        }
    }

    /**
     * Atomically return the current collection and replace with an empty one
     **/
    Collection<AnalyticsEvent> getQueuedEventsSnapshot() {
        synchronized (events.get()) {
            listener.get().onEventFlush();
            transmitRequired.set(false);
            return events.getAndSet(Collections.synchronizedList(new ArrayList<AnalyticsEvent>(maxEventPoolSize)));
        }
    }

    /**
     * Set listener to be invoked on key events. The listener is valid for the lifespan of
     * the process, and does not need to be reset during app transitions.
     *
     * @param listener
     */
    @Override
    public void setEventListener(EventListener listener) {
        if (listener != null) {
            this.listener.set(listener);
        } else {
            this.listener.set(this);
        }
    }

    /**
     * Return current listener
     *
     * @returns listener
     */
    public EventListener getListener() {
        return listener.get();
    }


    /**
     * Notification that an event will be added to the queue.
     * Caller may mutate the event as desired but any changes must
     * adhere to event and attribute requirements.
     *
     * @param event that would be added to the buffer
     * @return true to add this event, false to ignore
     */
    @Override
    public boolean onEventAdded(final AnalyticsEvent event) {
        log.debug("Event [" + event.getCategory() + "] added to queue");
        StatsEngine.get().inc(MetricNames.SUPPORTABILITY_EVENT_ADDED);
        return true;
    }

    /**
     * Notification that adding this event would exceed the
     * configured queue size
     *
     * @param event that would be added to the full buffer
     * @return true to ignore this event, false to add anyway
     */
    @Override
    public boolean onEventOverflow(AnalyticsEvent event) {
        log.warn("Event queue overflow adding event [" + event.getName() + "]");
        StatsEngine.get().inc(MetricNames.SUPPORTABILITY_EVENT_OVERFLOW);
        transmitRequired.set(true);
        return false;
    }

    /**
     * Notification that this event will be removed from
     * the queue to satisfy size constraints
     *
     * @param event that would be removed make room in the buffer
     * @return true to evict this event, false to keep in buffer
     */
    @Override
    public boolean onEventEvicted(AnalyticsEvent event) {
        log.warn("Event [" + event.getName() + "] evicted from queue");
        StatsEngine.get().inc(MetricNames.SUPPORTABILITY_EVENT_EVICTED);
        transmitRequired.set(true);
        return true;
    }

    /**
     * Notified when event buffer has surpassed the current configured size.
     * The caller can update the limit using @link NewRelic.setMaxEventPoolSize()
     *
     * @param currentQueueSize
     */
    @Override
    public void onEventQueueSizeExceeded(int currentQueueSize) {
        log.warn("Event queue size [" + currentQueueSize + "] exceeded max[" + maxEventPoolSize + "]");
        StatsEngine.get().inc(MetricNames.SUPPORTABILITY_EVENT_QUEUE_SIZE_EXCEEDED);
        transmitRequired.set(true);
    }

    /**
     * Notified when event buffer flush time has been exceeded. This normally occurs
     * between harvest cycles.
     *
     * The caller can update the limit using @link NewRelic.setMaxEventBufferTime()
     *
     * @param maxBufferTimeInSec
     */
    @Override
    public void onEventQueueTimeExceeded(int maxBufferTimeInSec) {
        log.warn("Event queue time [" + maxBufferTimeInSec + "] exceeded");
        StatsEngine.get().inc(MetricNames.SUPPORTABILITY_EVENT_QUEUE_TIME_EXCEEDED);
        transmitRequired.set(true);
    }

    /**
     * Notified immediately prior to event buffer flush, when queued events
     * are transferred to a harvest payload. After flush, events are "in-flight"
     * (meaning not persisted and subject to harvest failures, app terminations, etc).
     */
    @Override
    public void onEventFlush() {
    }

    /**
     * Called when event manager has initialized and ready for input.
     * Caller can populate event queue with saved events here, for example
     */
    @Override
    public void onStart(EventManager eventManager) {
    }

    /**
     * Called when event manager is shutting down. Since its no longer enabled,
     * caller should not submit new events, which would then be dropped by the manager
     */
    @Override
    public void onShutdown() {
        if (!events.get().isEmpty()) {
            log.warn("Event manager is shutting down with [" + events.get().size() + "] events remaining in the queue");
        }
        transmitRequired.set(true);
    }

}
