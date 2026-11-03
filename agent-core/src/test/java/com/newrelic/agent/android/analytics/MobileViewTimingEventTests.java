/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.analytics;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.logging.ConsoleAgentLog;
import com.newrelic.agent.android.test.stub.StubAgentImpl;
import com.newrelic.agent.android.test.stub.StubAnalyticsAttributeStore;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@RunWith(JUnit4.class)
public class MobileViewTimingEventTests {
    private AnalyticsControllerImpl controller;

    @Before
    public void setUp() {
        AgentLogManager.setAgentLog(new ConsoleAgentLog());
        AgentLogManager.getAgentLog().setLevel(0);

        AgentConfiguration config = new AgentConfiguration();
        config.setEnableAnalyticsEvents(true);
        config.setEventStore(new TestEventStore());
        config.setAnalyticsAttributeStore(new StubAnalyticsAttributeStore());

        controller = (AnalyticsControllerImpl) AnalyticsControllerImpl.getInstance();
        controller.shutdown();
        controller.initialize(config, new StubAgentImpl());
    }

    @Test
    public void factoryCreatesMobileViewTimingEvent() {
        AnalyticsEvent event = AnalyticsEventFactory.createEvent(null, AnalyticsEventCategory.MobileViewTiming,
                AnalyticsEvent.EVENT_TYPE_MOBILE_VIEW_TIMING, new HashSet<AnalyticsAttribute>());

        Assert.assertTrue("Factory should create a MobileViewTimingEvent.", event instanceof MobileViewTimingEvent);
        Assert.assertEquals(AnalyticsEvent.EVENT_TYPE_MOBILE_VIEW_TIMING, event.getEventType());
        Assert.assertEquals(AnalyticsEventCategory.MobileViewTiming, event.getCategory());
    }

    @Test
    public void categoryParsesFromString() {
        Assert.assertEquals(AnalyticsEventCategory.MobileViewTiming, AnalyticsEventCategory.fromString("MobileViewTiming"));
        Assert.assertEquals(AnalyticsEventCategory.MobileView, AnalyticsEventCategory.fromString("MobileView"));
    }

    @Test
    public void viewEventsKeepTheirCategoryAndTypeWhenRestoredFromJson() {
        Set<AnalyticsAttribute> attributes = new HashSet<>();
        attributes.add(new AnalyticsAttribute(AnalyticsAttribute.MOBILE_VIEW_TIMING_NAME_ATTRIBUTE, "timeToInitialDisplay"));

        AnalyticsEvent timing = AnalyticsEvent.newFromJson(new MobileViewTimingEvent(null, attributes).asJsonObject());
        Assert.assertEquals(AnalyticsEventCategory.MobileViewTiming, timing.getCategory());
        Assert.assertEquals(AnalyticsEvent.EVENT_TYPE_MOBILE_VIEW_TIMING, timing.getEventType());
        Assert.assertEquals("timeToInitialDisplay", attribute(timing, AnalyticsAttribute.MOBILE_VIEW_TIMING_NAME_ATTRIBUTE).getStringValue());

        AnalyticsEvent view = AnalyticsEvent.newFromJson(new MobileViewEvent("ViewOne", new HashSet<AnalyticsAttribute>()).asJsonObject());
        Assert.assertEquals(AnalyticsEventCategory.MobileView, view.getCategory());
        Assert.assertEquals(AnalyticsEvent.EVENT_TYPE_MOBILE_VIEW, view.getEventType());
    }

    @Test
    public void agentOwnedEventsAreValidDespiteReservedTypes() {
        Set<AnalyticsAttribute> attributes = new HashSet<>();
        Assert.assertTrue("MobileView must stay valid once its type is reserved.",
                new MobileViewEvent("ViewOne", attributes).isValid());
        Assert.assertTrue("MobileViewTiming must stay valid once its type is reserved.",
                new MobileViewTimingEvent(null, attributes).isValid());
    }

    @Test
    public void recordMobileViewTimingEventQueuesNamelessEventWithAttributes() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(AnalyticsAttribute.MOBILE_VIEW_TIMING_NAME_ATTRIBUTE, "timeToInitialDisplay");
        attributes.put(AnalyticsAttribute.MOBILE_VIEW_TIMING_VALUE_ATTRIBUTE, 250.5);
        attributes.put(AnalyticsAttribute.MOBILE_VIEW_NAME_ATTRIBUTE, "ViewOne");

        Assert.assertTrue(controller.recordMobileViewTimingEvent(attributes));

        Collection<AnalyticsEvent> events = controller.getEventManager().getQueuedEvents();
        Assert.assertEquals(1, events.size());
        AnalyticsEvent event = events.iterator().next();
        Assert.assertEquals(AnalyticsEvent.EVENT_TYPE_MOBILE_VIEW_TIMING, event.getEventType());
        Assert.assertNull("Timing events are identified by timingName, not an event name.",
                attribute(event, AnalyticsAttribute.EVENT_NAME_ATTRIBUTE));
        Assert.assertEquals("timeToInitialDisplay", attribute(event, AnalyticsAttribute.MOBILE_VIEW_TIMING_NAME_ATTRIBUTE).getStringValue());
        Assert.assertEquals(250.5, attribute(event, AnalyticsAttribute.MOBILE_VIEW_TIMING_VALUE_ATTRIBUTE).getDoubleValue(), 0.0);
        Assert.assertEquals("ViewOne", attribute(event, AnalyticsAttribute.MOBILE_VIEW_NAME_ATTRIBUTE).getStringValue());
    }

    @Test
    public void recordMobileViewEventStillRecordsOnceReserved() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(AnalyticsAttribute.MOBILE_VIEW_NAME_ATTRIBUTE, "ViewOne");

        Assert.assertTrue(controller.recordMobileViewEvent("ViewOne", attributes));
        Assert.assertEquals(1, controller.getEventManager().getQueuedEvents().size());
    }

    @Test
    public void recordCustomEventRejectsMobileViewAndMobileViewTiming() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("a", "b");

        Assert.assertFalse(controller.recordCustomEvent(AnalyticsEvent.EVENT_TYPE_MOBILE_VIEW, attributes));
        Assert.assertFalse(controller.recordCustomEvent(AnalyticsEvent.EVENT_TYPE_MOBILE_VIEW_TIMING, attributes));
        Assert.assertEquals("Neither reserved event should have been queued.", 0, controller.getEventManager().getQueuedEvents().size());

        Assert.assertTrue("A non-reserved type must still be accepted.", controller.recordCustomEvent("MyCustomEvent", attributes));
    }

    private static AnalyticsAttribute attribute(AnalyticsEvent event, String name) {
        for (AnalyticsAttribute a : event.getAttributeSet()) {
            if (name.equals(a.getName())) {
                return a;
            }
        }
        return null;
    }
}
