/**
 * Copyright 2021 New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.stores;

import static org.mockito.Mockito.spy;

import android.content.Context;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.SpyContext;
import com.newrelic.agent.android.analytics.AnalyticsEvent;
import com.newrelic.agent.android.analytics.EventListener;
import com.newrelic.agent.android.analytics.EventManager;
import com.newrelic.agent.android.analytics.EventManagerImpl;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class SharedPrefsEventStoreTest implements EventListener {
    private SharedPrefsEventStore eventStore;
    private Context context = new SpyContext().getContext();
    private AgentConfiguration agentConfiguration;
    private EventManagerImpl manager = null;

    @Before
    public void setUp() throws Exception {
        eventStore = spy(new SharedPrefsEventStore(context));

        agentConfiguration = new AgentConfiguration();
        agentConfiguration.setApplicationToken(SharedPrefsEventStoreTest.class.getSimpleName());
        agentConfiguration.setEventStore(eventStore);
        agentConfiguration.setEnableAnalyticsEvents(true);

        manager = Mockito.spy(new EventManagerImpl());
        manager.initialize(agentConfiguration);
        manager.setEventListener(this);
    }

    @After
    public void tearDown() throws Exception {
        eventStore.clear();
        Assert.assertTrue(eventStore.count() == 0);
    }

    @Test
    public void store() throws Exception {
        AnalyticsEvent event = new AnalyticsEvent("storeEvent");
        eventStore.store(event);
        List<AnalyticsEvent> list = eventStore.fetchAll();
        Assert.assertNotNull("Should store event", list.contains(event));
    }

    @Test
    public void fetchAll() throws Exception {
        AnalyticsEvent event1 = new AnalyticsEvent("event1");
        AnalyticsEvent event2 = new AnalyticsEvent("event2");
        eventStore.store(event1);
        eventStore.store(event2);
        List<AnalyticsEvent> list = eventStore.fetchAll();
        Assert.assertNotNull("Should store event", list.contains(event1));
        Assert.assertEquals("Should not store duplicates", 2, eventStore.fetchAll().size());
    }

    @Test
    public void count() throws Exception {
        AnalyticsEvent event1 = new AnalyticsEvent("event1");
        AnalyticsEvent event2 = new AnalyticsEvent("event2");
        eventStore.store(event1);
        eventStore.store(event2);
        List<AnalyticsEvent> list = eventStore.fetchAll();
        Assert.assertEquals("Should not store duplicates", 2, list.size());
    }

    @Test
    public void clear() throws Exception {
        AnalyticsEvent event1 = new AnalyticsEvent("event1");
        AnalyticsEvent event2 = new AnalyticsEvent("event2");
        eventStore.store(event1);
        eventStore.store(event2);
        Assert.assertEquals("Should contains event(s)", 2, eventStore.fetchAll().size());

        eventStore.clear();
        Assert.assertTrue("Should remove all event(s)", eventStore.fetchAll().isEmpty());
    }

    @Test
    public void delete() throws Exception {
        AnalyticsEvent event1 = new AnalyticsEvent("event1");
        AnalyticsEvent event2 = new AnalyticsEvent("event2");
        eventStore.store(event1);
        eventStore.store(event2);
        eventStore.store(event1);
        Assert.assertEquals("Should contain 2 events", 2, eventStore.fetchAll().size());

        eventStore.delete(event1);
        Assert.assertFalse("Should remove testEvent1", eventStore.fetchAll().contains(event1));

        eventStore.delete(event2);
        Assert.assertFalse("Should remove testEvent2", eventStore.fetchAll().contains(event2));

        Assert.assertEquals("Should contain 0 event(s)", 0, eventStore.fetchAll().size());
    }

    @Test
    public void testStoreLotsOfEvents() throws Exception {
        int reps = 20;
        for (Integer i = 0; i < reps; i++) {
            AnalyticsEvent event = new AnalyticsEvent("event" + i);
            manager.addEvent(event);
        }
        // eventStore.store() is an async call, so it's possible the last element was not written
        // by the time the assert is hit. Use a delta of 1 in the assertion.
        Assert.assertEquals("Should contain " + Integer.valueOf(reps).toString() + " events", reps, eventStore.count(), 20);
    }

    @Override
    public boolean onEventAdded(AnalyticsEvent analyticsEvent) {
        return true;
    }

    @Override
    public boolean onEventOverflow(AnalyticsEvent analyticsEvent) {
        return true;
    }

    @Override
    public boolean onEventEvicted(AnalyticsEvent analyticsEvent) {
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
}
