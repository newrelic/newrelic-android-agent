/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.analytics;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.logging.ConsoleAgentLog;
import com.newrelic.agent.android.test.stub.StubAnalyticsAttributeStore;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@RunWith(JUnit4.class)
public class EventManagerTests implements EventListener {

    private static ConsoleAgentLog log;
    private static AtomicLong seqNo = new AtomicLong(0);
    private static AgentConfiguration agentConfiguration;
    private EventManagerImpl manager = null;
    private AnalyticsValidator validator = new AnalyticsValidator();
    private AnalyticsEventStore eventStore;

    @BeforeClass
    public static void setupClass() {
        log = new ConsoleAgentLog();
        log.setLevel(5);
        AgentLogManager.setAgentLog(log);

        agentConfiguration = new AgentConfiguration();
        agentConfiguration.setEventStore(new TestEventStore());
        agentConfiguration.setEnableAnalyticsEvents(true);
        agentConfiguration.setAnalyticsAttributeStore(new StubAnalyticsAttributeStore());
    }

    @Before
    public void setUp() throws Exception {
        seqNo = new AtomicLong(0);
        manager = Mockito.spy(new TestEventManagerImpl());
        manager.initialize(agentConfiguration);
        manager.setEventListener(this);
        eventStore = agentConfiguration.getEventStore();
    }

    @After
    public void tearDown() throws Exception {
        eventStore.clear();
        manager.shutdown();
    }

    @Test
    public void testAddEvent() {
        eventStore.clear();
        Assert.assertEquals("EventQueue initial size should be 0", 0, manager.size());

        manager.addEvent(new CustomEvent("test", null));
        Assert.assertEquals("EventQueue size should be 1", 1, manager.size());

        manager.addEvent(new CustomEvent("test2", null));
        Assert.assertEquals("EventQueue size should be 2", 2, manager.size());

        FeatureFlag.disableFeature(FeatureFlag.EventPersistence);
        eventStore.clear();
        manager.addEvent(new CustomEvent("test", null));
        Assert.assertEquals("No event added to the store", 0, eventStore.fetchAll().size());

        manager.addEvent(new CustomEvent("test2", null));
        Assert.assertEquals("No event added to the store", 0, eventStore.fetchAll().size());

        FeatureFlag.enableFeature(FeatureFlag.EventPersistence);
        eventStore.clear();
        manager.addEvent(new CustomEvent("test", null));
        Assert.assertEquals("No event added to the store", 1, eventStore.fetchAll().size());

        manager.addEvent(new CustomEvent("test2", null));
        Assert.assertEquals("No event added to the store", 2, eventStore.fetchAll().size());

    }

    @Test
    public void testEmpty() {
        eventStore.clear();
        manager.initialize(agentConfiguration);
        manager.addEvent(new CustomEvent("test", null));
        manager.addEvent(new CustomEvent("test2", null));
        Assert.assertEquals("EventQueue initial size should be 2", 2, manager.size());

        manager.empty();
        Assert.assertEquals("EventQueue size should be 0", 0, manager.size());
    }

    @Test
    public void testMaxBufferTime() throws Exception {
        Map<String, AnalyticsAttribute> attrs = new HashMap<String, AnalyticsAttribute>();
        attrs.put(AnalyticsAttribute.EVENT_NAME_ATTRIBUTE, new AnalyticsAttribute(AnalyticsAttribute.EVENT_NAME_ATTRIBUTE, "test"));
        manager.addEvent(new CustomEvent("test"));

        manager.setMaxEventBufferTime(2);
        Assert.assertFalse("Maximum buffer time should not yet be exceeded", manager.isMaxEventBufferTimeExceeded());

        Thread.sleep(1000);
        Assert.assertFalse("Maximum buffer time should not yet be exceeded", manager.isMaxEventBufferTimeExceeded());

        Thread.sleep(1500);
        Assert.assertTrue("Maximum buffer time should be exceeded", manager.isMaxEventBufferTimeExceeded());
    }

    @Test
    public void testIsTransmitRequired() throws Exception {
        final int nDelayInSecs = 3;

        manager.setMaxEventBufferTime(nDelayInSecs);
        Assert.assertFalse("Should not trigger upload until events added", manager.isTransmitRequired());

        manager.addEvent(new CustomEvent("test", null));
        Assert.assertFalse("Should not trigger upload until timeout exceeded", manager.isTransmitRequired());

        Thread.sleep((nDelayInSecs + 1) * 1000);
        Assert.assertTrue("Should trigger upload on timeout", manager.isTransmitRequired());

        // flush the events
        manager.empty();
        Assert.assertFalse("Should not trigger upload once emptied", manager.isTransmitRequired());

        Thread.sleep(nDelayInSecs * 1000);
        Assert.assertFalse("Should not trigger upload after delay", manager.isTransmitRequired());

        // test trigger
        manager.setTransmitRequired();
        Assert.assertTrue("Should trigger on first evaluation", manager.isTransmitRequired());
        Assert.assertFalse("Should not trigger after evaluation", manager.isTransmitRequired());
    }

    @Test
    public void testValidateEventTypeName() throws Exception {
        Assert.assertTrue("Event type should only contain allowable characters", validator.isValidEventType("Alpha: 6 6 6 _ . :"));
        Assert.assertFalse("Should not record event types containing illegal characters", validator.isValidEventType("\t \n \r \b Alpha 6 6 6 : _"));
    }

    @Test
    public void testCountRecorded() {
        eventStore.clear();
        manager.initialize(agentConfiguration);
        Assert.assertEquals(0, manager.size());
        manager.setMaxEventPoolSize(2);
        manager.addEvent(new AnalyticsEvent("test"));
        Assert.assertEquals(1, manager.getEventsRecorded());
        manager.addEvent(new AnalyticsEvent("test"));
        manager.addEvent(new AnalyticsEvent("test"));
        manager.addEvent(new AnalyticsEvent("test"));
        Assert.assertEquals((double) 2, (double) manager.getEventsRecorded(), 1);
        Assert.assertEquals(2, manager.getEventsDropped() + manager.getEventsEjected());
    }

    @Test
    public void testCountEjected() {
        eventStore.clear();
        Assert.assertEquals(0, manager.size());
        manager.setMaxEventPoolSize(3);
        manager.addEvent(new AnalyticsEvent("test"));
        manager.addEvent(new AnalyticsEvent("test"));
        manager.addEvent(new AnalyticsEvent("test"));
        manager.addEvent(new AnalyticsEvent("test"));
        manager.addEvent(new AnalyticsEvent("test"));
        Assert.assertEquals((double) 3, (double) manager.getEventsRecorded(), 0);
        Assert.assertEquals(2, manager.getEventsDropped() + manager.getEventsEjected());
    }

    @Test
    public void testGetQueuedEvents() {
        eventStore.clear();
        manager.initialize(agentConfiguration);
        manager.addEvent(new AnalyticsEvent("test"));
        manager.addEvent(new AnalyticsEvent("test"));
        manager.addEvent(new AnalyticsEvent("test"));
        Collection<AnalyticsEvent> queued = manager.getQueuedEvents();
        Assert.assertEquals(queued.size(), 3);
        Assert.assertEquals(manager.size(), 3);

        manager.addEvent(new AnalyticsEvent("test"));
        manager.addEvent(new AnalyticsEvent("test"));
        Assert.assertEquals(queued.size(), 5);
        Assert.assertEquals(manager.size(), 5);

        manager.shutdown();
        manager.addEvent(new AnalyticsEvent("test"));
        manager.addEvent(new AnalyticsEvent("test"));
        Assert.assertEquals(5, manager.getEventsRecorded());
        Assert.assertEquals(2, manager.getEventsDropped());
    }

    @Test
    public void testGetQueuedEventsSnapshot() {
        eventStore.clear();
        manager.addEvent(new AnalyticsEvent("test"));
        manager.addEvent(new AnalyticsEvent("test"));
        manager.addEvent(new AnalyticsEvent("test"));
        Collection<AnalyticsEvent> queued = manager.getQueuedEventsSnapshot();
        Assert.assertEquals(queued.size(), 3);
        Assert.assertEquals(manager.size(), 0);

        manager.addEvent(new AnalyticsEvent("test"));
        manager.addEvent(new AnalyticsEvent("test"));
        Assert.assertEquals(queued.size(), 3);
        Assert.assertEquals(manager.size(), 2);

        manager.shutdown();
        manager.addEvent(new AnalyticsEvent("test"));
        manager.addEvent(new AnalyticsEvent("test"));
        Assert.assertEquals(5, manager.getEventsRecorded());
        Assert.assertEquals(2, manager.getEventsDropped());
    }

    @Test
    public void testSubmitAfterShutdown() {
        eventStore.clear();
        manager.initialize(agentConfiguration);
        manager.addEvent(new AnalyticsEvent("test"));
        manager.addEvent(new AnalyticsEvent("test"));
        manager.addEvent(new AnalyticsEvent("test"));
        Assert.assertEquals(3, manager.getEventsRecorded());
        manager.shutdown();
        manager.addEvent(new AnalyticsEvent("test"));
        manager.addEvent(new AnalyticsEvent("test"));
        manager.addEvent(new AnalyticsEvent("test"));
        Assert.assertEquals(3, manager.getEventsRecorded());
        Assert.assertEquals(3, manager.getEventsDropped());
    }


    Runnable runnable = new Runnable() {
        @Override
        public void run() {
            HashMap<String, Object> attrs = new HashMap<>();
            attrs.put("seqNo", seqNo.getAndIncrement());
            manager.addEvent(new AnalyticsEvent("test"));
        }
    };

    @Test
    public void testConcurrentEventSubmission() throws InterruptedException {
        eventStore.clear();
        manager.initialize(agentConfiguration);
        final ScheduledExecutorService executor = Executors.newScheduledThreadPool(4);
        final Collection<AnalyticsEvent> snapshot = manager.getQueuedEvents();

        Assert.assertTrue(snapshot.isEmpty());
        manager.setMaxEventPoolSize(567);

        // create some thread chaos
        try {
            executor.scheduleAtFixedRate(runnable, 10, 20, TimeUnit.MILLISECONDS);
            executor.scheduleAtFixedRate(runnable, 7, 27, TimeUnit.MILLISECONDS);
            executor.scheduleAtFixedRate(runnable, 13, 7, TimeUnit.MILLISECONDS);

            // inject the kill pill
            executor.schedule(new Runnable() {
                @Override
                public void run() {
                    manager.shutdown();
                    snapshot.addAll(manager.getQueuedEventsSnapshot());
                }
            }, (long) (Math.random() * 3000), TimeUnit.MILLISECONDS);

            // throw unpooled threads at it
            long count = (long) Math.ceil(Math.random() * 100);
            ArrayList<Thread> threads = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                Thread t = new Thread(runnable);
                threads.add(t);
                t.start();
                Thread.yield();
            }

            // now wait up to 5 seconds
            Thread.sleep(TimeUnit.MILLISECONDS.convert(5, TimeUnit.SECONDS));
            manager.shutdown();

            log.debug("Joining [" + threads.size() + "] threads:");
            for (Thread thread : threads) {
                thread.join();
            }

            executor.shutdown();

            Assert.assertEquals(seqNo.get(), snapshot.size() + manager.getQueuedEvents().size() + manager.getEventsEjected() + manager.getEventsDropped());
            Assert.assertEquals(seqNo.get(), manager.getEventsRecorded() + manager.getEventsDropped());

        } catch (Exception e) {
            Assert.fail(e.getLocalizedMessage());

        } finally {
            executor.awaitTermination(3, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testOldEventHandoff() throws InterruptedException {
        eventStore.clear();
        manager.initialize(agentConfiguration);
        final ScheduledExecutorService executor = Executors.newScheduledThreadPool(8);
        final Collection<AnalyticsEvent> snapshot = manager.getQueuedEvents();
        Assert.assertTrue(snapshot.isEmpty());

        try {
            // create some thread chaos
            executor.scheduleAtFixedRate(runnable, 4, 24, TimeUnit.MILLISECONDS);
            executor.scheduleAtFixedRate(runnable, 7, 17, TimeUnit.MILLISECONDS);
            executor.scheduleAtFixedRate(runnable, 13, 7, TimeUnit.MILLISECONDS);

            // inject the kill pill
            executor.schedule(new Runnable() {
                @Override
                public void run() {
                    snapshot.addAll(manager.getQueuedEvents());
                    manager.empty();
                }
            }, (long) (Math.random() * 2000), TimeUnit.MILLISECONDS);


            // now wait up to 5 seconds
            Thread.sleep(TimeUnit.MILLISECONDS.convert(5, TimeUnit.SECONDS));

            executor.shutdown();
            manager.shutdown();

            Assert.assertNotEquals(seqNo.get(), snapshot.size() + manager.getQueuedEvents().size() + manager.getEventsEjected() + manager.getEventsDropped());

        } catch (Exception e) {
            Assert.fail(e.getLocalizedMessage());

        } finally {
            executor.awaitTermination(3, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testEventTriggerGuards() {
        manager = Mockito.spy(new EventManagerImpl());
        manager.initialize(agentConfiguration);
        manager.setEventListener(this);

        manager.setMaxEventBufferTime(1);
        Assert.assertEquals("Should protect from too low a trigger", manager.getMaxEventBufferTime(), EventManagerImpl.DEFAULT_MIN_EVENT_BUFFER_TIME);
        manager.setMaxEventBufferTime(Integer.MAX_VALUE);
        Assert.assertEquals("Should protect from too high a trigger", manager.getMaxEventBufferTime(), EventManagerImpl.DEFAULT_MAX_EVENT_BUFFER_TIME);

        manager.setMaxEventPoolSize(1);
        Assert.assertEquals("Should protect from too small an event buffer", manager.getMaxEventPoolSize(), EventManagerImpl.DEFAULT_MIN_EVENT_BUFFER_SIZE);
        manager.setMaxEventPoolSize(Integer.MAX_VALUE);
        Assert.assertTrue("Should allow (for now) too large an event buffer", manager.getMaxEventPoolSize() > EventManagerImpl.DEFAULT_MAX_EVENT_BUFFER_SIZE);

    }

    @Test
    public void eventListenerRemainsBetweenAppStates() {
        // presume agent has started
        Assert.assertEquals(this, manager.getListener());

        // set listener
        EventListener listener = new EventTransformAdapter();

        manager.setEventListener(listener);
        Assert.assertEquals(listener, manager.getListener());

        // b/g
        manager.shutdown();
        Assert.assertEquals(listener, manager.getListener());

        // f/g
        manager.initialize(agentConfiguration);
        Assert.assertEquals(listener, manager.getListener());

        // reset
        manager.setEventListener(null);     // reset the listener
        Assert.assertEquals(manager, manager.getListener());
    }

    @Test
    public void setEventListener() {
        // default listener is the manager itself
        Assert.assertEquals(this, manager.getListener());

        // set listener
        EventListener listener = new EventTransformAdapter();

        manager.setEventListener(listener);
        Assert.assertEquals(listener, manager.getListener());

        // replace
        EventTransformAdapter anotherListener = new EventTransformAdapter();
        manager.setEventListener(anotherListener);
        Assert.assertEquals(anotherListener, manager.getListener());

        // reset
        manager.setEventListener(null);
        Assert.assertEquals(manager, manager.getListener());
    }

    @Test
    public void testEventManagerInitWithEvents() {
        eventStore.clear();
        manager.initialize(agentConfiguration);
        eventStore.store(new AnalyticsEvent("event1"));
        eventStore.store(new AnalyticsEvent("event2"));
        Assert.assertEquals(2, eventStore.fetchAll().size());

        manager.initialize(agentConfiguration);
        Assert.assertEquals(2, manager.getQueuedEvents().size());
        Assert.assertEquals(2, manager.getEventsRecorded());
    }

    @Override
    public boolean onEventAdded(AnalyticsEvent eventToBeAdded) {
        return true;
    }

    @Override
    public boolean onEventOverflow(AnalyticsEvent eventToBeAdded) {
        return true;
    }

    @Override
    public boolean onEventEvicted(AnalyticsEvent eventToBeEvicted) {
        return true;
    }

    @Override
    public void onEventQueueSizeExceeded(int currentQueueSize) {
    }

    @Override
    public void onEventQueueTimeExceeded(int maxBufferTimeInSec) {
    }

    @Override
    public void onEventFlush() {
    }

    @Override
    public void onStart(EventManager eventManager) {
    }

    @Override
    public void onShutdown() {
    }

    private class TestEventManagerImpl extends EventManagerImpl {

        @Override
        public void setMaxEventPoolSize(int maxSize) {
            this.maxEventPoolSize = maxSize;
        }

        @Override
        public void setMaxEventBufferTime(int maxBufferTimeInSec) {
            this.maxBufferTimeInSec = maxBufferTimeInSec;
        }
    }
}
