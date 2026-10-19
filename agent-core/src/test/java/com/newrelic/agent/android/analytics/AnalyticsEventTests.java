/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.analytics;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.background.ApplicationStateMonitor;
import com.newrelic.agent.android.metric.Metric;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.stats.StatsEngine;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.newrelic.agent.android.analytics.AnalyticsAttributeTests.getAttributeByName;

@RunWith(JUnit4.class)
public class AnalyticsEventTests {

    protected static Set<AnalyticsAttribute> eventAttributes;
    protected static ArrayList<AnalyticsEvent> analyticsEvents;

    @BeforeClass
    public static void setUp() throws Exception {
        eventAttributes = new HashSet<AnalyticsAttribute>();
        eventAttributes.add(new AnalyticsAttribute("string", "string"));
        eventAttributes.add(new AnalyticsAttribute("float", -1f));
        eventAttributes.add(new AnalyticsAttribute("boolean", false));

        analyticsEvents = new ArrayList<AnalyticsEvent>();
        analyticsEvents.add(new AnalyticsEvent(new AnalyticsEvent("cloned")));
        analyticsEvents.add(new AnalyticsEvent("analyticsEvent1"));
        analyticsEvents.add(new AnalyticsEvent("analyticsEvent2", AnalyticsEventCategory.Crash));
        analyticsEvents.add(new AnalyticsEvent("analyticsEvent3", AnalyticsEventCategory.Crash, "analyticsEventType", null));
        analyticsEvents.add(new AnalyticsEvent("analyticsEvent4", AnalyticsEventCategory.Crash, "analyticsEventType", eventAttributes));
    }

    private ApplicationStateMonitor monitor;

    @Before
    public void installApplicationStateMonitor() {
        // A monitor of our own, so a test can move the app between foreground and background
        monitor = new ApplicationStateMonitor();
        ApplicationStateMonitor.setInstance(monitor);
    }

    @After
    public void restoreDefaults() {
        FeatureFlag.disableFeature(FeatureFlag.BackgroundReporting);
        monitor.getExecutor().shutdownNow();
        ApplicationStateMonitor.setInstance(null);
    }

    // The monitor applies a transition on its own thread: wait for it so the next event sees it
    private void moveToBackground() {
        monitor.uiHidden();
        drainMonitor();
    }

    private void moveToForeground() {
        monitor.activityStarted();
        drainMonitor();
    }

    private void drainMonitor() {
        try {
            monitor.getExecutor().submit(() -> {
            }).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new AssertionError("Application state monitor did not settle", e);
        }
    }

    @Test
    public void testShouldCreateAnalyticsEvent() throws Exception {
        AnalyticsEvent analyticEvent;

        analyticEvent = new AnalyticsEvent(new AnalyticsEvent("cloned"));
        Assert.assertEquals("Should create mobile event type", analyticEvent.getEventType(), AnalyticsEvent.EVENT_TYPE_MOBILE);
        Assert.assertEquals("Should create custom event category", analyticEvent.getCategory(), AnalyticsEventCategory.Custom);
        Assert.assertEquals("Should create attributes", 4, analyticEvent.getAttributeSet().size());
        Assert.assertTrue("Should create valid timestamp", (analyticEvent.getTimestamp() > 0 && analyticEvent.getTimestamp() <= System.currentTimeMillis()));

        analyticEvent = new AnalyticsEvent("analyticsEvent");
        Assert.assertEquals("Should create named event", analyticEvent.getName(), "analyticsEvent");
        Assert.assertEquals("Should create mobile event type", analyticEvent.getEventType(), AnalyticsEvent.EVENT_TYPE_MOBILE);
        Assert.assertEquals("Should create custom event category", analyticEvent.getCategory(), AnalyticsEventCategory.Custom);
        Assert.assertEquals("Should create attributes", 4, analyticEvent.getAttributeSet().size());

        analyticEvent = new AnalyticsEvent("analyticsEvent", AnalyticsEventCategory.Crash);
        Assert.assertEquals("Should create custom event category", analyticEvent.getCategory(), AnalyticsEventCategory.Crash);
        Assert.assertEquals("Should create attributes", 4, analyticEvent.getAttributeSet().size());

        analyticEvent = new AnalyticsEvent("analyticsEvent", AnalyticsEventCategory.Crash, "analyticsEventType", null);
        Assert.assertEquals("Should create mobile event type", analyticEvent.getEventType(), "analyticsEventType");
        Assert.assertEquals("Should create attributes", 4, analyticEvent.getAttributeSet().size());

        analyticEvent = new AnalyticsEvent("analyticsEvent", AnalyticsEventCategory.Crash, "analyticsEventType", eventAttributes);
        Assert.assertEquals("Should create attributes", 7, analyticEvent.getAttributeSet().size());
        analyticEvent.addAttributes(eventAttributes);
        Assert.assertEquals("Should not create duplicate attributes", 7, analyticEvent.getAttributeSet().size());
    }

    @Test
    public void testNewFromJson() throws Exception {
        JsonArray eventArray = new JsonArray();

        for (AnalyticsEvent event : analyticsEvents) {
            eventArray.add(event.asJsonObject());
        }
        Assert.assertEquals("Should serialize from analytics events object", eventArray.size(), analyticsEvents.size());

        Collection<AnalyticsEvent> events = AnalyticsEvent.newFromJson(eventArray);
        Assert.assertNotNull("Should serialize Json to AnalyticsEvents collection", events);
        Assert.assertEquals("Should serialize Json to same size AnalyticsEvents collection", analyticsEvents.size(), events.size());

        for (AnalyticsEvent event : events) {
            JsonObject elem = findJsonElement(eventArray, event.getName(), event.getEventType(), event.getCategory(), event.getTimestamp());
            Assert.assertNotNull("Should find event in Json", elem);
            if (event.getName() == null) {
                Assert.assertEquals("Null event names should match", event.getName(), elem.get(AnalyticsAttribute.EVENT_NAME_ATTRIBUTE));
            } else {
                Assert.assertEquals("Event names should match", event.getName(), elem.get(AnalyticsAttribute.EVENT_NAME_ATTRIBUTE).getAsString());
            }
            Assert.assertEquals("Event types should match", event.getEventType(), elem.get(AnalyticsAttribute.EVENT_TYPE_ATTRIBUTE).getAsString());
            Assert.assertEquals("Event categories should match", event.getCategory().name(), elem.get(AnalyticsAttribute.EVENT_CATEGORY_ATTRIBUTE).getAsString());
            Assert.assertEquals("Event timestamp should match", event.getTimestamp(), elem.get(AnalyticsAttribute.EVENT_TIMESTAMP_ATTRIBUTE).getAsLong());
        }
    }


    private JsonObject findJsonElement(JsonArray eventArray, String name, String eventType, AnalyticsEventCategory category, long timestamp) {
        JsonObject jsonObject = null;
        for (int i = 0; i < eventArray.size(); i++) {
            JsonObject elem = eventArray.get(i).getAsJsonObject();
            boolean bMatches = true;

            if (name != null) {
                bMatches &= (elem.has(AnalyticsAttribute.EVENT_NAME_ATTRIBUTE) && (elem.get(AnalyticsAttribute.EVENT_NAME_ATTRIBUTE).getAsString() == name));
            }

            if (eventType != null) {
                bMatches &= (elem.has(AnalyticsAttribute.EVENT_TYPE_ATTRIBUTE) && (elem.get(AnalyticsAttribute.EVENT_TYPE_ATTRIBUTE).getAsString() == eventType));
            }

            if (category != null) {
                bMatches &= (elem.has(AnalyticsAttribute.EVENT_CATEGORY_ATTRIBUTE) && (elem.get(AnalyticsAttribute.EVENT_CATEGORY_ATTRIBUTE).getAsString() == category.name()));
            }

            bMatches &= (elem.has(AnalyticsAttribute.EVENT_TIMESTAMP_ATTRIBUTE) && (elem.get(AnalyticsAttribute.EVENT_TIMESTAMP_ATTRIBUTE).getAsLong() == timestamp));

            if (bMatches) {
                jsonObject = elem;
                break;
            }
        }

        return jsonObject;
    }

    @Test
    public void getMutableAttributeSet() {
        Collection<AnalyticsAttribute> immutableAttrs = analyticsEvents.get(0).getAttributeSet();
        Collection<AnalyticsAttribute> mutableAttrs = analyticsEvents.get(0).getMutableAttributeSet();

        Assert.assertTrue(analyticsEvents.get(0).getAttributeSet().size() == mutableAttrs.size());
        Assert.assertNull(getAttributeByName(mutableAttrs, AnalyticsAttribute.MUTABLE));

        try {
            getAttributeByName(immutableAttrs, "name").setStringValue("mutated");
            immutableAttrs.add(new AnalyticsAttribute("mutable", true));
            Assert.fail("Attributes should be immutable");
        } catch (Exception e) {
            // ignored
        }

        try {
            mutableAttrs.add(new AnalyticsAttribute("mutable", true));
        } catch (Exception e) {
            Assert.fail("Attributes should be mutable");
        }
    }

    @Test
    public void testEventUUID() {
        //validate UUID creation
        AnalyticsEvent origEvent = new AnalyticsEvent("testEvent");
        Assert.assertNotNull(origEvent.getEventUUID());

        Assert.assertNotNull("Should create UUID", origEvent.getEventUUID());
        Assert.assertFalse("Should create UUID", origEvent.getEventUUID().isEmpty());
        try {
            Assert.assertNotNull("Should create valid UUID", UUID.fromString(origEvent.getEventUUID()));
        } catch (Exception e) {
            Assert.fail();
        }

        //validate change UUID of events
        origEvent.setEventUUID("TestSetUUID");
        Assert.assertEquals("Should set to new UUID", "TestSetUUID", origEvent.getEventUUID());

        //validate UUID consistence
        String origUUID = origEvent.getEventUUID();
        AnalyticsEvent newEvent = AnalyticsEvent.eventFromJsonString(origUUID, origEvent.toJsonString());
        Assert.assertEquals("UUID should stay the same", origUUID, newEvent.getEventUUID());
    }

    @Test
    public void putAttributesUncheckedReplacesByNameAndAllowsReserved() {
        AnalyticsEvent event = new AnalyticsEvent("e");

        // Reserved name (sessionId) is accepted here — the public addAttributes() would reject it.
        Set<AnalyticsAttribute> first = new HashSet<AnalyticsAttribute>();
        first.add(new AnalyticsAttribute(AnalyticsAttribute.SESSION_ID_ATTRIBUTE, "S_OLD", false));
        first.add(new AnalyticsAttribute("userId", "u1"));
        event.putAttributesUnchecked(first);
        Assert.assertEquals("S_OLD", attrValue(event, AnalyticsAttribute.SESSION_ID_ATTRIBUTE));
        Assert.assertEquals("u1", attrValue(event, "userId"));

        // A same-named attribute is replaced (supplied value wins); others are preserved.
        Set<AnalyticsAttribute> second = new HashSet<AnalyticsAttribute>();
        second.add(new AnalyticsAttribute(AnalyticsAttribute.SESSION_ID_ATTRIBUTE, "S_NEW", false));
        event.putAttributesUnchecked(second);
        Assert.assertEquals("S_NEW", attrValue(event, AnalyticsAttribute.SESSION_ID_ATTRIBUTE));
        Assert.assertEquals("u1", attrValue(event, "userId"));
    }

    @Test
    public void foregroundEventIsStampedBackgroundFalse() {
        FeatureFlag.enableFeature(FeatureFlag.BackgroundReporting);

        Assert.assertEquals(Boolean.FALSE, backgroundOf(new AnalyticsEvent("foreground")));
    }

    @Test
    public void backgroundEventIsStampedBackgroundTrue() {
        FeatureFlag.enableFeature(FeatureFlag.BackgroundReporting);
        moveToBackground();

        Assert.assertEquals(Boolean.TRUE, backgroundOf(new AnalyticsEvent("background")));
    }

    @Test
    public void stampSurvivesLaterLifecycleTransitions() {
        FeatureFlag.enableFeature(FeatureFlag.BackgroundReporting);

        AnalyticsEvent foregroundEvent = new AnalyticsEvent("foreground");
        moveToBackground();
        AnalyticsEvent backgroundEvent = new AnalyticsEvent("background");
        moveToForeground();

        Assert.assertEquals("Recorded in the foreground", Boolean.FALSE, backgroundOf(foregroundEvent));
        Assert.assertEquals("Recorded in the background", Boolean.TRUE, backgroundOf(backgroundEvent));
    }

    @Test
    public void noStampWhenBackgroundReportingIsDisabled() {
        FeatureFlag.disableFeature(FeatureFlag.BackgroundReporting);

        Assert.assertNull(backgroundOf(new AnalyticsEvent("foreground")));
        moveToBackground();
        Assert.assertNull(backgroundOf(new AnalyticsEvent("background")));
    }

    @Test
    public void attributeSuppliedByCallerIsKept() {
        FeatureFlag.enableFeature(FeatureFlag.BackgroundReporting);

        Set<AnalyticsAttribute> attributes = new HashSet<AnalyticsAttribute>();
        attributes.add(new AnalyticsAttribute(AnalyticsAttribute.BACKGROUND_ATTRIBUTE_NAME, true));

        Assert.assertEquals(Boolean.TRUE,
                backgroundOf(new AnalyticsEvent("supplied", AnalyticsEventCategory.Custom, "type", attributes)));
    }

    @Test
    public void allEventTypesAreStamped() {
        FeatureFlag.enableFeature(FeatureFlag.BackgroundReporting);

        Assert.assertEquals(Boolean.FALSE, backgroundOf(new CustomEvent("custom")));
        Assert.assertEquals(Boolean.FALSE, backgroundOf(new SessionEvent()));

        moveToBackground();

        Assert.assertEquals(Boolean.TRUE, backgroundOf(new CustomEvent("custom")));
        Assert.assertEquals(Boolean.TRUE, backgroundOf(new SessionEvent()));
    }

    @Test
    public void copyKeepsTheStateTheEventWasRecordedWith() {
        FeatureFlag.enableFeature(FeatureFlag.BackgroundReporting);

        AnalyticsEvent foregroundEvent = new AnalyticsEvent("foreground");
        FeatureFlag.disableFeature(FeatureFlag.BackgroundReporting);
        AnalyticsEvent unflaggedEvent = new AnalyticsEvent("unflagged");

        moveToBackground();
        FeatureFlag.enableFeature(FeatureFlag.BackgroundReporting);

        Assert.assertEquals("Copied while in the background", Boolean.FALSE, backgroundOf(new AnalyticsEvent(foregroundEvent)));
        Assert.assertNull("No state to copy, none invented", backgroundOf(new AnalyticsEvent(unflaggedEvent)));
    }

    @Test
    public void restoredEventKeepsTheStateItWasRecordedWith() {
        FeatureFlag.enableFeature(FeatureFlag.BackgroundReporting);

        JsonObject foregroundJson = new AnalyticsEvent("foreground").asJsonObject();
        FeatureFlag.disableFeature(FeatureFlag.BackgroundReporting);
        JsonObject unflaggedJson = new AnalyticsEvent("unflagged").asJsonObject();

        moveToBackground();
        FeatureFlag.enableFeature(FeatureFlag.BackgroundReporting);

        Assert.assertEquals("Restored while in the background", Boolean.FALSE, backgroundOf(AnalyticsEvent.newFromJson(foregroundJson)));
        Assert.assertNull("No state stored, none invented", backgroundOf(AnalyticsEvent.newFromJson(unflaggedJson)));
    }

    @Test
    public void backgroundEventCountOnlyCountsBackgroundEvents() {
        FeatureFlag.enableFeature(FeatureFlag.BackgroundReporting);

        final long before = backgroundEventCount();

        new AnalyticsEvent("foreground");
        Assert.assertEquals("Foreground events are not counted", before, backgroundEventCount());

        moveToBackground();
        new AnalyticsEvent("background");
        Assert.assertEquals("Background events are counted", before + 1, backgroundEventCount());
    }

    private static long backgroundEventCount() {
        Metric metric = StatsEngine.notice().getStatsMap().get(MetricNames.BACKGROUND_EVENT_COUNT);
        return (metric == null) ? 0 : metric.getCount();
    }

    private static Boolean backgroundOf(AnalyticsEvent event) {
        AnalyticsAttribute attribute = getAttributeByName(event.getAttributeSet(), AnalyticsAttribute.BACKGROUND_ATTRIBUTE_NAME);
        return (attribute == null) ? null : attribute.getBooleanValue();
    }

    private static String attrValue(AnalyticsEvent event, String name) {
        for (AnalyticsAttribute a : event.getAttributeSet()) {
            if (name.equals(a.getName())) {
                return a.getStringValue();
            }
        }
        return null;
    }
}
