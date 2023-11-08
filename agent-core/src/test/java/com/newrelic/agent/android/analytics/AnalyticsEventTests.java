/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.analytics;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

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
        Assert.assertNotNull(getAttributeByName(mutableAttrs, AnalyticsAttribute.MUTABLE));

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
        AnalyticsEvent event = new AnalyticsEvent("testEvent");
        Assert.assertNotNull(event.getEventUUID());
    }
}
