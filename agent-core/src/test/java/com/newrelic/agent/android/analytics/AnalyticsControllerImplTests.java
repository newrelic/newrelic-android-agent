/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.analytics;

import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.harvest.DeviceInformation;
import com.newrelic.agent.android.harvest.Harvest;
import com.newrelic.agent.android.harvest.HarvestData;
import com.newrelic.agent.android.harvest.Harvester;
import com.newrelic.agent.android.harvest.HttpTransaction;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.logging.ConsoleAgentLog;
import com.newrelic.agent.android.test.mock.Providers;
import com.newrelic.agent.android.test.stub.StubAgentImpl;
import com.newrelic.agent.android.test.stub.StubAnalyticsAttributeStore;
import com.newrelic.agent.android.tracing.TraceMachine;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static com.newrelic.agent.android.analytics.AnalyticsAttributeTests.getAttributeByName;

@RunWith(JUnit4.class)
@SuppressWarnings("static")
public class AnalyticsControllerImplTests {
    private static final ArrayList<String> DEFAULT_ATTRIBUTES = new ArrayList<String>(Arrays.asList(
            AnalyticsAttribute.EVENT_TIMESTAMP_ATTRIBUTE,
            AnalyticsAttribute.EVENT_CATEGORY_ATTRIBUTE,
            AnalyticsAttribute.EVENT_NAME_ATTRIBUTE,
            AnalyticsAttribute.EVENT_TYPE_ATTRIBUTE,
            AnalyticsAttribute.SESSION_TIME_SINCE_LOAD_ATTRIBUTE));


    private AnalyticsControllerImpl controller;
    private AgentConfiguration config;

    @Before
    public void preTest() {
        AgentLogManager.setAgentLog(new ConsoleAgentLog());
        AgentLogManager.getAgentLog().setLevel(0);
        config = new AgentConfiguration();
        config.setEnableAnalyticsEvents(true);
        config.setEventStore(new TestEventStore());
        config.setAnalyticsAttributeStore(new StubAnalyticsAttributeStore());
        controller = (AnalyticsControllerImpl) AnalyticsControllerImpl.getInstance();
        controller.shutdown();
    }

    @Before
    public void defaultFeatures() {
        FeatureFlag.enableFeature(FeatureFlag.NetworkRequests);
        FeatureFlag.enableFeature(FeatureFlag.NetworkErrorRequests);
    }

    @After
    public void postTest() {
        FeatureFlag.enableFeature(FeatureFlag.HttpResponseBodyCapture);
        FeatureFlag.enableFeature(FeatureFlag.CrashReporting);
        FeatureFlag.enableFeature(FeatureFlag.AnalyticsEvents);
        FeatureFlag.enableFeature(FeatureFlag.InteractionTracing);
        FeatureFlag.enableFeature(FeatureFlag.DefaultInteractions);
    }

    @After
    public void tearDown() throws Exception {

    }

    @Test
    public void testInitialize() throws Exception {
        Assert.assertFalse("Uninitialized controller should fail to set attributes.", controller.setAttribute("test", "value"));

        config.setEnableAnalyticsEvents(false);
        controller.initialize(config, new StubAgentImpl());

        Assert.assertFalse("Disabled controller should fail to set attributes.", controller.setAttribute("test", "value"));

        controller = (AnalyticsControllerImpl) AnalyticsControllerImpl.getInstance();
        controller.shutdown();
        config.setEnableAnalyticsEvents(true);
        controller.initialize(config, new StubAgentImpl());

        config.setEnableAnalyticsEvents(true);
        Assert.assertTrue("Enabled controller should set attributes.", controller.setAttribute("test", "value"));
    }

    @Test
    public void testReservedNames() throws Exception {
        config.setEnableAnalyticsEvents(true);
        controller.initialize(config, new StubAgentImpl());

        Assert.assertFalse("Controller should fail to set attributes using reserved names.", controller.setAttribute(AnalyticsAttribute.EVENT_TYPE_ATTRIBUTE, "value"));
        Assert.assertFalse("Controller should fail to set attributes using reserved names.", controller.setAttribute(AnalyticsAttribute.TYPE_ATTRIBUTE, "value"));
        Assert.assertFalse("Controller should fail to set attributes using reserved names.", controller.setAttribute(AnalyticsAttribute.EVENT_TIMESTAMP_ATTRIBUTE, "value"));
        Assert.assertFalse("Controller should fail to set attributes using reserved names.", controller.setAttribute(AnalyticsAttribute.EVENT_CATEGORY_ATTRIBUTE, "value"));
        Assert.assertFalse("Controller should fail to set attributes using reserved names.", controller.setAttribute(AnalyticsAttribute.ACCOUNT_ID_ATTRIBUTE, "value"));
        Assert.assertFalse("Controller should fail to set attributes using reserved names.", controller.setAttribute(AnalyticsAttribute.APP_ID_ATTRIBUTE, "value"));
        Assert.assertFalse("Controller should fail to set attributes using reserved names.", controller.setAttribute(AnalyticsAttribute.APP_NAME_ATTRIBUTE, "value"));
        Assert.assertFalse("Controller should fail to set attributes using reserved names.", controller.setAttribute(AnalyticsAttribute.UUID_ATTRIBUTE, "value"));
        Assert.assertFalse("Controller should fail to set attributes using reserved names.", controller.setAttribute(AnalyticsAttribute.SESSION_ID_ATTRIBUTE, "value"));
        Assert.assertFalse("Controller should fail to set attributes using reserved names.", controller.setAttribute(AnalyticsAttribute.OS_NAME_ATTRIBUTE, "value"));
        Assert.assertFalse("Controller should fail to set attributes using reserved names.", controller.setAttribute(AnalyticsAttribute.OS_VERSION_ATTRIBUTE, "value"));
        Assert.assertFalse("Controller should fail to set attributes using reserved names.", controller.setAttribute(AnalyticsAttribute.OS_MAJOR_VERSION_ATTRIBUTE, "value"));
        Assert.assertFalse("Controller should fail to set attributes using reserved names.", controller.setAttribute(AnalyticsAttribute.DEVICE_MANUFACTURER_ATTRIBUTE, "value"));
        Assert.assertFalse("Controller should fail to set attributes using reserved names.", controller.setAttribute(AnalyticsAttribute.DEVICE_MODEL_ATTRIBUTE, "value"));
        Assert.assertFalse("Controller should fail to set attributes using reserved names.", controller.setAttribute(AnalyticsAttribute.MEM_USAGE_MB_ATTRIBUTE, "value"));
        Assert.assertFalse("Controller should fail to set attributes using reserved names.", controller.setAttribute(AnalyticsAttribute.CARRIER_ATTRIBUTE, "value"));
        Assert.assertFalse("Controller should fail to set attributes using reserved names.", controller.setAttribute(AnalyticsAttribute.NEW_RELIC_VERSION_ATTRIBUTE, "value"));
        Assert.assertFalse("Controller should fail to set attributes using reserved names.", controller.setAttribute(AnalyticsAttribute.INTERACTION_DURATION_ATTRIBUTE, "value"));
        Assert.assertFalse("Controller should fail to set attributes using reserved names.", controller.setAttribute(AnalyticsAttribute.APPLICATION_PLATFORM_ATTRIBUTE, "value"));
        Assert.assertFalse("Controller should fail to set attributes using reserved names.", controller.setAttribute(AnalyticsAttribute.APPLICATION_PLATFORM_VERSION_ATTRIBUTE, "value"));
        Assert.assertFalse("Controller should fail to set attributes using reserved names.", controller.setAttribute(AnalyticsAttribute.LAST_INTERACTION_ATTRIBUTE, "value"));
        Assert.assertFalse("Controller should fail to set attributes using reserved names.", controller.setAttribute(AnalyticsAttribute.OS_BUILD_ATTRIBUTE, "value"));
        Assert.assertFalse("Controller should fail to set attributes using reserved names.", controller.setAttribute(AnalyticsAttribute.RUNTIME_ATTRIBUTE, "value"));
        Assert.assertFalse("Controller should fail to set attributes using reserved names.", controller.setAttribute(AnalyticsAttribute.ARCHITECTURE_ATTRIBUTE, "value"));
        Assert.assertFalse("Controller should fail to set attributes using reserved names.", controller.setAttribute(AnalyticsAttribute.APP_BUILD_ATTRIBUTE, "value"));
    }

    @Test
    public void testSetAttribute() throws Exception {
        controller.initialize(config, new StubAgentImpl());

        Assert.assertEquals("Unpopulated analytics controller should return an attribute count of 0", 0, controller.getUserAttributeCount());

        controller.setAttribute("testAttribute", "testValue");
        Assert.assertEquals("Analytics controller should return an attribute count of 1", 1, controller.getUserAttributeCount());
        Assert.assertEquals("Should find attribute", "testValue", controller.getAttribute("testAttribute").valueAsString());

        controller.setAttribute("testAttribute2", "testValue2");
        Assert.assertEquals("Analytics controller should return an attribute count of 2", 2, controller.getUserAttributeCount());
        Assert.assertEquals("Should find attribute", "testValue2", controller.getAttribute("testAttribute2").valueAsString());

        controller.setAttribute("testAttribute2", "differentTestValue2");
        Assert.assertEquals("Analytics controller should return an attribute count of 2", 2, controller.getUserAttributeCount());
        Assert.assertEquals("Should find attribute", "differentTestValue2", controller.getAttribute("testAttribute2").valueAsString());

        controller.setAttribute("doubleTestAttribute", 1.23f);
        Assert.assertEquals("Analytics controller should return an attribute count of 3.", 3, controller.getUserAttributeCount());

        controller.setAttribute("doubleTestAttribute", 2.34f);
        Assert.assertEquals("Analytics controller should return an attribute count of 3.", 3, controller.getUserAttributeCount());

        controller.setAttribute("testAttribute", "testValue", true);

        Assert.assertEquals("Analytics controller should return an attribute count of 3.", 3, controller.getUserAttributeCount());
        Assert.assertNotNull("Attribute testAttribute should not be null.", controller.getAttribute("testAttribute"));
        Assert.assertTrue("Test attribute should be persistent", controller.getAttribute("testAttribute").isPersistent());

        controller.setAttribute("testAttribute", "testValue", false);

        Assert.assertEquals("Analytics controller should return an attribute count of 3.", 3, controller.getUserAttributeCount());
        Assert.assertFalse("Test attribute should not be persistent.", controller.getAttribute("testAttribute").isPersistent());

        controller.setAttribute("doubleTestAttribute", 1.23f, true);

        Assert.assertEquals("Analytics controller should return an attribute count of 3.", 3, controller.getUserAttributeCount());
        Assert.assertTrue("Double test attribute should be persistent.", controller.getAttribute("doubleTestAttribute").isPersistent());

        controller.setAttribute("doubleTestAttribute", 1.23f, false);

        Assert.assertEquals("Analytics controller should return an attribute count of 3.", 3, controller.getUserAttributeCount());
        Assert.assertFalse("Double test attribute should not be persistent.", controller.getAttribute("doubleTestAttribute").isPersistent());
    }

    @Test
    public void testGetAttribute() throws Exception {
        controller.initialize(config, new StubAgentImpl());

        controller.setAttribute("testAttribute", "testValue");

        AnalyticsAttribute attribute = controller.getAttribute("testAttribute");
        Assert.assertNotNull("AnalyticsAttribute should not be null", attribute);
        Assert.assertEquals("AnalyticsAttribute should have name testAttribute.", "testAttribute", attribute.getName());

        controller.setAttribute("testAttribute2", "testValue2");

        attribute = controller.getAttribute("testAttribute2");
        Assert.assertNotNull("AnalyticsAttribute should not be null", attribute);
        Assert.assertEquals("AnalyticsAttribute should have name testAttribute.", "testAttribute2", attribute.getName());

        controller.addAttributeUnchecked(new AnalyticsAttribute("testAttribute3", "testValue3"), true);

        attribute = controller.getAttribute("testAttribute3");
        Assert.assertNotNull("AnalyticsAttribute should not be null", attribute);
        Assert.assertEquals("AnalyticsAttribute should have name testAttribute.", "testAttribute3", attribute.getName());

    }

    @Test
    public void testBooleanAttribute() throws Exception {
        controller.initialize(config, new StubAgentImpl());

        controller.setAttribute("booleanAttribute", true);

        AnalyticsAttribute attribute = controller.getAttribute("booleanAttribute");
        Assert.assertNotNull("AnalyticsAttribute should not be null", attribute);
        Assert.assertTrue("Should store boolean attribute", attribute.getAttributeDataType() == AnalyticsAttribute.AttributeDataType.BOOLEAN);
        Assert.assertTrue("Should store boolean attribute (true)", attribute.getBooleanValue());
        Assert.assertEquals("Should not return string attribute", attribute.getStringValue(), null);
        Assert.assertEquals("Should not return float attribute", attribute.getDoubleValue(), Float.NaN, 0);
        Assert.assertTrue("JSON should contain boolean element", attribute.toString().toLowerCase().contains("booleanvalue=true"));

        controller.setAttribute("booleanAttribute", false);
        attribute = controller.getAttribute("booleanAttribute");
        Assert.assertFalse("Should store boolean attribute (false)", attribute.getBooleanValue());
        Assert.assertTrue("JSON should contain boolean element", attribute.toString().toLowerCase().contains("booleanvalue=false"));
    }

    @Test
    public void testIncrementAttribute() throws Exception {
        controller.initialize(config, new StubAgentImpl());
        Assert.assertEquals("Unpopulated analytics controller should return an attribute count of 0.", 0, controller.getUserAttributeCount());

        controller.setAttribute("testAttribute", 1.00f);

        AnalyticsAttribute attribute = controller.getAttribute("testAttribute");
        Assert.assertNotNull("AnalyticsAttribute should not be null.", attribute);
        Assert.assertEquals(1.00, attribute.getDoubleValue(), 0.01);

        controller.incrementAttribute("testAttribute", 1.00f);
        attribute = controller.getAttribute("testAttribute");
        Assert.assertNotNull("AnalyticsAttribute should not be null.", attribute);
        Assert.assertEquals(2.00, attribute.getDoubleValue(), 0.01);

        controller.incrementAttribute("testAttribute", 10.00f);
        attribute = controller.getAttribute("testAttribute");
        Assert.assertNotNull("AnalyticsAttribute should not be null.", attribute);
        Assert.assertEquals(12.00, attribute.getDoubleValue(), 0.01);

        controller.setAttribute("testBooleanAttribute", true);
        Assert.assertFalse("Should not increment boolean value", controller.incrementAttribute("testBooleanAttribute", 1f));
    }

    @Test
    public void testRemoveAttribute() throws Exception {
        controller.initialize(config, new StubAgentImpl());

        controller.setAttribute("testAttribute", "testValue");

        AnalyticsAttribute attribute = controller.getAttribute("testAttribute");
        Assert.assertNotNull("AnalyticsAttribute should not be null.", attribute);
        Assert.assertEquals("AnalyticsAttribute should have name testAttribute.", "testAttribute", attribute.getName());

        controller.removeAttribute("testAttribute");
        attribute = controller.getAttribute("testAttribute");
        Assert.assertNull("AnalyticsAttribute should be null.", attribute);
    }

    @Test
    public void testAddEvent() throws Exception {
        controller.initialize(config, new StubAgentImpl());

        controller.setAttribute("testAttribute", "testValue");

        Assert.assertTrue(controller.addEvent("testEvent", new HashSet<AnalyticsAttribute>()));
        Assert.assertEquals("Event queue should have a size of 1.", 1, controller.getEventManager().size());

        Collection<AnalyticsEvent> events = controller.getEventManager().getQueuedEvents();
        Assert.assertEquals("Queued event collection should have a size of 1.", 1, events.size());
        AnalyticsEvent event = events.iterator().next();
        Assert.assertNotNull(event);
        Assert.assertEquals("Event name is invalid.", "testEvent", event.getName());
        Assert.assertEquals("Event category is incorrect.", AnalyticsEventCategory.Custom, event.getCategory());
        Assert.assertEquals("Event attribute set is the wrong size.", 5, event.getAttributeSet().size());
        for (AnalyticsAttribute attribute : event.getAttributeSet()) {
            if (attribute.getName().equals("name")) {
                Assert.assertEquals("Event name attribute incorrect.", "testEvent", attribute.getStringValue());
            }
            if (attribute.getName().equals("eventType")) {
                Assert.assertEquals("Event eventType attribute incorrect.", "Mobile", attribute.getStringValue());
            }
            if (attribute.getName().equals("category")) {
                Assert.assertEquals("Event category attribute incorrect.", "Custom", attribute.getStringValue());
            }
            if (attribute.getName().equals("osName")) {
                Assert.assertEquals("Event osName attribute incorrect.", "Android", attribute.getStringValue());
            }
            if (attribute.getName().equals("osVersion")) {
                Assert.assertEquals("Event osVersion attribute incorrect.", "2.3", attribute.getStringValue());
            }
            if (attribute.getName().equals("osMajorVersion")) {
                Assert.assertEquals("Event osMajorVersion attribute incorrect.", "2", attribute.getStringValue());
            }
            if (attribute.getName().equals("carrier")) {
                Assert.assertEquals("Event carrier attribute incorrect.", "wifi", attribute.getStringValue());
            }
            if (attribute.getName().equals("sessionId")) {
                Assert.assertEquals("Event sessionId attribute incorrect.", config.getSessionID(), attribute.getStringValue());
            }
            if (attribute.getName().equals("deviceModel")) {
                Assert.assertEquals("Event deviceModel attribute incorrect.", "StubAgent", attribute.getStringValue());
            }
            if (attribute.getName().equals("deviceManufacturer")) {
                Assert.assertEquals("Event deviceManufacturer attribute incorrect.", "Fake", attribute.getStringValue());
            }
            if (attribute.getName().equals("memUsageMb")) {
                Assert.assertEquals("Event memUsageMb attribute incorrect.", 0.0f, attribute.getDoubleValue(), 0.1f);
            }
            if (attribute.getName().equals("newRelicVersion")) {
                Assert.assertEquals("Event newRelicVersion attribute incorrect.", "2.123", attribute.getStringValue());
            }
            if (attribute.getName().equals("timestamp")) {
                Assert.assertEquals("Event timestamp attribute incorrect.", String.valueOf(event.getTimestamp()), attribute.getStringValue());
            }
        }
    }

    @Test
    public void testRecordEvent() throws Exception {
        controller.initialize(config, new StubAgentImpl());

        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put("String", String.valueOf(1));
        Assert.assertEquals("Event attributes can support String.", true, controller.recordEvent("testEvent", attributes));

        attributes = new HashMap<String, Object>();
        attributes.put("Boolean", Boolean.valueOf(true));
        Assert.assertEquals("Event attributes can support boolean.", true, controller.recordEvent("testEvent", attributes));

        attributes = new HashMap<String, Object>();
        attributes.put("Float", Float.valueOf(1));
        Assert.assertEquals("Event attributes can support Float.", true, controller.recordEvent("testEvent", attributes));

        attributes = new HashMap<String, Object>();
        attributes.put("Double", Double.valueOf(1));
        Assert.assertEquals("Event attributes can cast Double.", true, controller.recordEvent("testEvent", attributes));

        attributes = new HashMap<String, Object>();
        attributes.put("Integer", Integer.valueOf(1));
        Assert.assertEquals("Event attributes can cast Integer.", true, controller.recordEvent("testEvent", attributes));

        attributes = new HashMap<String, Object>();
        attributes.put("Integer", Integer.valueOf(1));
        Assert.assertEquals("Event attributes can cast Integer.", true, controller.recordEvent("testEvent", attributes));

        attributes = new HashMap<String, Object>();
        attributes.put("Short", Short.valueOf((short) 1));
        Assert.assertEquals("Event attributes can cast Short.", true, controller.recordEvent("testEvent", attributes));

        attributes = new HashMap<String, Object>();
        attributes.put("Long", Long.valueOf(1));
        Assert.assertEquals("Event attributes can cast Long.", true, controller.recordEvent("testEvent", attributes));

        attributes = new HashMap<String, Object>();
        attributes.put("BigDecimal", BigDecimal.valueOf(1));
        Assert.assertEquals("Event attributes can cast BigDecimal.", true, controller.recordEvent("testEvent", attributes));

        attributes = new HashMap<String, Object>();
        attributes.put("BigInteger", BigInteger.valueOf(1));
        Assert.assertEquals("Event attributes can cast BigInteger.", true, controller.recordEvent("testEvent", attributes));

        attributes = new HashMap<String, Object>();
        attributes.put("AtomicLong", new AtomicLong(1));
        Assert.assertEquals("Event attributes can't cast AtomicLong.", true, controller.recordEvent("testEvent", attributes));

        attributes = new HashMap<String, Object>();
        attributes.put("AtomicLong", new AtomicInteger(1));
        Assert.assertEquals("Event attributes can't cast AtomicInteger.", true, controller.recordEvent("testEvent", attributes));
    }

    @Test
    public void testRecordEventWithInvalidAttributes() throws Exception {
        controller.initialize(config, new StubAgentImpl());
        String eventType = "Mobile";

        Assert.assertFalse("Mobile event should not be recorded with null attributes", controller.recordEvent(eventType, null));
        Assert.assertTrue("Mobile event should still be recorded with null attributes", controller.internalRecordEvent("invalid", AnalyticsEventCategory.UserAction, eventType, null));
        controller.getEventManager().empty();

        String superLongAttributeName = "superLongAttributeName";
        while (superLongAttributeName.length() < AnalyticsAttribute.ATTRIBUTE_NAME_MAX_LENGTH) {
            superLongAttributeName += superLongAttributeName;
        }

        String superLongAttributeValue = "superLongAttributeValue";
        while (superLongAttributeValue.length() < AnalyticsAttribute.ATTRIBUTE_VALUE_MAX_LENGTH) {
            superLongAttributeValue += superLongAttributeValue;
        }

        Map<String, Object> invalidAttributes = new HashMap<>();
        invalidAttributes.put("category", "reserved");
        invalidAttributes.put("InvalidNumericType", new AtomicInteger(1));
        invalidAttributes.put(null, 1);
        invalidAttributes.put("", "emptyKey");
        invalidAttributes.put(superLongAttributeName, "superLongAttributeName");
        invalidAttributes.put("superLongAttributeValue", superLongAttributeValue);

        Assert.assertTrue("Mobile event should be recorded, but filter out invalid attributes", controller.recordEvent(eventType, invalidAttributes));

        Collection<AnalyticsEvent> events = controller.getEventManager().getQueuedEvents();
        Assert.assertEquals("Queued event collection should have a size of 1.", 1, events.size());

        AnalyticsEvent event = events.iterator().next();
        Collection<AnalyticsAttribute> attrs = event.getAttributeSet();

        Assert.assertTrue("Event should contain attributes", !attrs.isEmpty());
        Assert.assertEquals("Event should be of type " + eventType, eventType, event.getEventType());
        Assert.assertEquals("Should filter out all invalid attributes", DEFAULT_ATTRIBUTES.size(), attrs.size());
        Assert.assertNotNull(getAttributeByName(attrs, AnalyticsAttribute.EVENT_TIMESTAMP_ATTRIBUTE));
        Assert.assertNotNull(getAttributeByName(attrs, AnalyticsAttribute.EVENT_CATEGORY_ATTRIBUTE));
        Assert.assertNotNull(getAttributeByName(attrs, AnalyticsAttribute.EVENT_NAME_ATTRIBUTE));
        Assert.assertNotNull(getAttributeByName(attrs, AnalyticsAttribute.EVENT_TYPE_ATTRIBUTE));
        Assert.assertNotNull(getAttributeByName(attrs, AnalyticsAttribute.SESSION_TIME_SINCE_LOAD_ATTRIBUTE));
    }

    @Test
    public void testRecordEventOfType() throws Exception {
        controller.initialize(config, new StubAgentImpl());

        Collection<AnalyticsEvent> events = controller.getEventManager().getQueuedEvents();
        Assert.assertEquals("Queued event collection should have a size of 0.", 0, events.size());
    }

    @Test
    public void testRecordCustomEvent() throws Exception {
        controller.initialize(config, new StubAgentImpl());
        String eventType = "MyCustomEventType";
        String customAttributeKey = "CustomAttributeKey";
        String customValue = "CustomValue";
        Map<String, Object> customAttributes = new HashMap<>();
        customAttributes.put(customAttributeKey, customValue);

        Assert.assertTrue("Custom event should be recorded", controller.recordCustomEvent(eventType, customAttributes));

        Collection<AnalyticsEvent> events = controller.getEventManager().getQueuedEvents();
        Assert.assertEquals("Queued event collection should have a size of 1.", 1, events.size());

        AnalyticsEvent event = events.iterator().next();
        Collection<AnalyticsAttribute> attrs = event.getAttributeSet();

        Assert.assertTrue("Event should contain attributes", !attrs.isEmpty());
        Assert.assertEquals("Event should be of type " + eventType, eventType, event.getEventType());

        AnalyticsAttribute attribute = null;
        for (AnalyticsAttribute attr : attrs) {
            if (attr.getName().equals(customAttributeKey)) {
                attribute = attr;
                break;
            }
        }

        Assert.assertEquals("Event should contain a key of " + customAttributeKey, customAttributeKey, attribute.getName());
        Assert.assertEquals("Event should contain a key value of " + customValue, customValue, attribute.getStringValue());
    }

    @Test
    public void testRecordCustomEventWithInvalidAttributes() throws Exception {
        controller.initialize(config, new StubAgentImpl());
        String eventType = "CustomEventType";

        Assert.assertFalse("Custom event should not be recorded with null attributes", controller.recordCustomEvent(eventType, null));

        String superLongAttributeName = "superLongAttributeName";
        while (superLongAttributeName.length() < AnalyticsAttribute.ATTRIBUTE_NAME_MAX_LENGTH) {
            superLongAttributeName += superLongAttributeName;
        }

        String superLongAttributeValue = "superLongAttributeValue";
        while (superLongAttributeValue.length() < AnalyticsAttribute.ATTRIBUTE_VALUE_MAX_LENGTH) {
            superLongAttributeValue += superLongAttributeValue;
        }

        Map<String, Object> invalidAttributes = new HashMap<>();
        invalidAttributes.put(AnalyticsAttribute.EVENT_TIMESTAMP_ATTRIBUTE, "reserved");
        invalidAttributes.put("InvalidNumericType", new AtomicInteger(1));
        invalidAttributes.put(null, 1);
        invalidAttributes.put("", "emptyKey");
        invalidAttributes.put(superLongAttributeName, "superLongAttributeName");
        invalidAttributes.put("superLongAttributeValue", superLongAttributeValue);

        Assert.assertTrue("Custom event should be recorded, but filter out invalid attributes", controller.recordCustomEvent(eventType, invalidAttributes));

        Collection<AnalyticsEvent> events = controller.getEventManager().getQueuedEvents();
        Assert.assertEquals("Queued event collection should have a size of 1.", 1, events.size());

        AnalyticsEvent event = events.iterator().next();
        Collection<AnalyticsAttribute> attrs = event.getAttributeSet();

        Assert.assertTrue("Event should contain attributes", !attrs.isEmpty());
        Assert.assertEquals("Event should be of type " + eventType, eventType, event.getEventType());
        Assert.assertEquals("Should filter out all invalid attributes", DEFAULT_ATTRIBUTES.size(), attrs.size());
        Assert.assertNotNull(getAttributeByName(attrs, AnalyticsAttribute.EVENT_TIMESTAMP_ATTRIBUTE));
        Assert.assertNotNull(getAttributeByName(attrs, AnalyticsAttribute.EVENT_CATEGORY_ATTRIBUTE));
        Assert.assertNotNull(getAttributeByName(attrs, AnalyticsAttribute.EVENT_NAME_ATTRIBUTE));
        Assert.assertNotNull(getAttributeByName(attrs, AnalyticsAttribute.EVENT_TYPE_ATTRIBUTE));
        Assert.assertNotNull(getAttributeByName(attrs, AnalyticsAttribute.SESSION_TIME_SINCE_LOAD_ATTRIBUTE));
    }

    @Test
    public void testRecordBreadcrumbWithInvalidAttributes() throws Exception {
        controller.initialize(config, new StubAgentImpl());
        String eventType = "MobileBreadcrumb";

        Assert.assertTrue("Custom event should still be recorded with null attributes", controller.recordBreadcrumb(eventType, null));
        controller.getEventManager().empty();

        String superLongAttributeName = "superLongAttributeName";
        while (superLongAttributeName.length() < AnalyticsAttribute.ATTRIBUTE_NAME_MAX_LENGTH) {
            superLongAttributeName += superLongAttributeName;
        }

        String superLongAttributeValue = "superLongAttributeValue";
        while (superLongAttributeValue.length() < AnalyticsAttribute.ATTRIBUTE_VALUE_MAX_LENGTH) {
            superLongAttributeValue += superLongAttributeValue;
        }

        Map<String, Object> invalidAttributes = new HashMap<>();
        invalidAttributes.put(AnalyticsAttribute.SESSION_ID_ATTRIBUTE, "reserved");
        invalidAttributes.put(null, "null");
        invalidAttributes.put("InvalidNumericType", new AtomicReference<Double>(1.));
        invalidAttributes.put("", "emptyKey");
        invalidAttributes.put(superLongAttributeName, "superLongAttributeName");
        invalidAttributes.put("superLongAttributeValue", superLongAttributeValue);

        Assert.assertTrue("Breadcrumb event should be recorded, but filter out invalid attributes", controller.recordBreadcrumb(eventType, invalidAttributes));

        Collection<AnalyticsEvent> events = controller.getEventManager().getQueuedEvents();
        Assert.assertEquals("Queued event collection should have a size of 1.", 1, events.size());

        AnalyticsEvent event = events.iterator().next();
        Collection<AnalyticsAttribute> attrs = event.getAttributeSet();

        Assert.assertTrue("Event should contain attributes", !attrs.isEmpty());
        Assert.assertEquals("Event should be of type " + eventType, eventType, event.getEventType());
        Assert.assertEquals("Should filter out all invalid attributes", DEFAULT_ATTRIBUTES.size(), attrs.size());
        Assert.assertNotNull(getAttributeByName(attrs, AnalyticsAttribute.EVENT_TIMESTAMP_ATTRIBUTE));
        Assert.assertNotNull(getAttributeByName(attrs, AnalyticsAttribute.EVENT_CATEGORY_ATTRIBUTE));
        Assert.assertNotNull(getAttributeByName(attrs, AnalyticsAttribute.EVENT_NAME_ATTRIBUTE));
        Assert.assertNotNull(getAttributeByName(attrs, AnalyticsAttribute.EVENT_TYPE_ATTRIBUTE));
        Assert.assertNotNull(getAttributeByName(attrs, AnalyticsAttribute.SESSION_TIME_SINCE_LOAD_ATTRIBUTE));
    }

    @Test
    public void testCreateHttpErrorEvent() throws Exception {
        AnalyticsControllerImpl.initialize(config, new StubAgentImpl());
        HttpTransaction txn = Providers.provideHttpTransaction();
        txn.setStatusCode(418);
        txn.setErrorCode(-1100);

        controller.createHttpErrorEvent(txn);
        Collection<AnalyticsEvent> events = controller.getEventManager().getQueuedEvents();
        Assert.assertEquals("Queued event collection should have a size of 1.", 1, events.size());

        Iterator<AnalyticsEvent> eventIt = events.iterator();
        Collection<AnalyticsAttribute> eventAttrs = eventIt.next().getAttributeSet();
        AnalyticsAttribute statusCode = getAttributeByName(eventAttrs, AnalyticsAttribute.STATUS_CODE_ATTRIBUTE);
        Assert.assertNull("Transaction error code should not exist on http error event.", getAttributeByName(eventAttrs, AnalyticsAttribute.NETWORK_ERROR_CODE_ATTRIBUTE));
        Assert.assertNotNull("Transaction status code should exist.", statusCode);
        Assert.assertTrue(statusCode.getDoubleValue() == 418f);
    }

    @Test
    public void testCreateHttpErrorEventInvalidURL() throws Exception {
        AnalyticsControllerImpl.initialize(config, new StubAgentImpl());
        controller.getEventManager().empty();
        HttpTransaction txn = Providers.provideHttpRequestError();
        txn.setUrl("ThisIsNotAURL");

        controller.createHttpErrorEvent(txn);
        Collection<AnalyticsEvent> events = controller.getEventManager().getQueuedEvents();
        Assert.assertEquals("Queued event collection should have a size of 1.", 1, events.size());

        Iterator<AnalyticsEvent> eventIt = events.iterator();
        Collection<AnalyticsAttribute> eventAttrs = eventIt.next().getAttributeSet();

        AnalyticsAttribute requestDomain = getAttributeByName(eventAttrs, AnalyticsAttribute.REQUEST_DOMAIN_ATTRIBUTE);
        Assert.assertNull("Transaction provided an invalid URL and should have no domain attribute.", requestDomain);

        AnalyticsAttribute requestPath = getAttributeByName(eventAttrs, AnalyticsAttribute.REQUEST_PATH_ATTRIBUTE);
        Assert.assertNull("Transaction provided an invalid URL and should have no path attribute.", requestPath);

        AnalyticsAttribute requestUrl = getAttributeByName(eventAttrs, AnalyticsAttribute.REQUEST_URL_ATTRIBUTE);
        Assert.assertEquals("Transaction URL should match attribute requestUrl.", "ThisIsNotAURL", requestUrl.getStringValue());
    }

    @Test
    public void testCreateNetworkFailureEvent() throws Exception {
        controller.initialize(config, new StubAgentImpl());
        AnalyticsControllerImpl.initialize(config, new StubAgentImpl());
        controller.getEventManager().empty();
        HttpTransaction txn = Providers.provideHttpTransaction();
        txn.setErrorCode(-1100);

        controller.createNetworkFailureEvent(txn);
        Collection<AnalyticsEvent> events = controller.getEventManager().getQueuedEvents();
        Assert.assertEquals("Queued event collection should have a size of 1.", 1, events.size());

        Iterator<AnalyticsEvent> eventIt = events.iterator();
        Collection<AnalyticsAttribute> eventAttrs = eventIt.next().getAttributeSet();
        AnalyticsAttribute errorCode = getAttributeByName(eventAttrs, AnalyticsAttribute.NETWORK_ERROR_CODE_ATTRIBUTE);
        Assert.assertNull("Transaction status code should not exist on network failure event.", getAttributeByName(eventAttrs, AnalyticsAttribute.STATUS_CODE_ATTRIBUTE));
        Assert.assertNotNull("Transaction error code should exist.", errorCode);
        Assert.assertTrue(errorCode.getDoubleValue() == -1100f);
    }

    @Test
    public void testCreateNetworkFailureEventInvalidURL() throws Exception {
        AnalyticsControllerImpl.initialize(config, new StubAgentImpl());
        controller.getEventManager().empty();
        HttpTransaction txn = Providers.provideHttpRequestFailure();
        txn.setUrl("ThisIsNotAURL");

        controller.createNetworkFailureEvent(txn);
        Collection<AnalyticsEvent> events = controller.getEventManager().getQueuedEvents();
        Assert.assertEquals("Queued event collection should have a size of 1.", 1, events.size());

        Iterator<AnalyticsEvent> eventIt = events.iterator();
        Collection<AnalyticsAttribute> eventAttrs = eventIt.next().getAttributeSet();

        AnalyticsAttribute requestDomain = getAttributeByName(eventAttrs, AnalyticsAttribute.REQUEST_DOMAIN_ATTRIBUTE);
        Assert.assertNull("Transaction provided an invalid URL and should have no domain attribute.", requestDomain);

        AnalyticsAttribute requestPath = getAttributeByName(eventAttrs, AnalyticsAttribute.REQUEST_PATH_ATTRIBUTE);
        Assert.assertNull("Transaction provided an invalid URL and should have no path attribute.", requestPath);

        AnalyticsAttribute requestUrl = getAttributeByName(eventAttrs, AnalyticsAttribute.REQUEST_URL_ATTRIBUTE);
        Assert.assertEquals("Transaction URL should match attribute requestUrl.", "ThisIsNotAURL", requestUrl.getStringValue());
    }

    @Test
    public void testCreateNetworkEvent() throws Exception {
        AnalyticsControllerImpl.initialize(config, new StubAgentImpl());
        HttpTransaction txn = Providers.provideHttpTransaction();

        controller.createNetworkRequestEvent(txn);
        Collection<AnalyticsEvent> events = controller.getEventManager().getQueuedEvents();
        Assert.assertEquals("Queued event collection should have a size of 1", 1, events.size());

        Iterator<AnalyticsEvent> eventIt = events.iterator();
        AnalyticsEvent event = eventIt.next();
        Assert.assertEquals("Should contain network request event", AnalyticsEvent.EVENT_TYPE_MOBILE_REQUEST, event.getEventType());

        Collection<AnalyticsAttribute> eventAttrs = event.getAttributeSet();

        Assert.assertNotNull("Should contain status code", getAttributeByName(eventAttrs, AnalyticsAttribute.STATUS_CODE_ATTRIBUTE));
        Assert.assertNotNull("Should contain request domain", getAttributeByName(eventAttrs, AnalyticsAttribute.REQUEST_DOMAIN_ATTRIBUTE));
        Assert.assertNotNull("Should contain path", getAttributeByName(eventAttrs, AnalyticsAttribute.REQUEST_PATH_ATTRIBUTE));
        Assert.assertNotNull("Should contain request url", getAttributeByName(eventAttrs, AnalyticsAttribute.REQUEST_URL_ATTRIBUTE));
        Assert.assertNotNull("Should contain connection type", getAttributeByName(eventAttrs, AnalyticsAttribute.CONNECTION_TYPE_ATTRIBUTE));
        Assert.assertNotNull("Should contain request method", getAttributeByName(eventAttrs, AnalyticsAttribute.REQUEST_METHOD_ATTRIBUTE));
        Assert.assertNotNull("Should contain response time", getAttributeByName(eventAttrs, AnalyticsAttribute.RESPONSE_TIME_ATTRIBUTE));
        Assert.assertNotNull("Should contain bytes sent", getAttributeByName(eventAttrs, AnalyticsAttribute.BYTES_SENT_ATTRIBUTE));
        Assert.assertNotNull("Should contain bytes received", getAttributeByName(eventAttrs, AnalyticsAttribute.BYTES_RECEIVED_ATTRIBUTE));
    }

    @Test
    public void testCreateNetworkErrorEvent() throws Exception {
        AnalyticsControllerImpl.initialize(config, new StubAgentImpl());
        HttpTransaction txn = Providers.provideHttpRequestError();

        txn.setBytesSent(-1);
        txn.setStatusCode(404);
        txn.setResponseBody("404 NOT FOUND");

        FeatureFlag.enableFeature(FeatureFlag.HttpResponseBodyCapture);

        controller.createHttpErrorEvent(txn);
        Collection<AnalyticsEvent> events = controller.getEventManager().getQueuedEvents();
        Assert.assertEquals("Queued event collection should have a size of 1", 1, events.size());

        Iterator<AnalyticsEvent> eventIt = events.iterator();
        AnalyticsEvent event = eventIt.next();
        Assert.assertEquals("Should contain network request error event", AnalyticsEvent.EVENT_TYPE_MOBILE_REQUEST_ERROR, event.getEventType());

        Collection<AnalyticsAttribute> eventAttrs = event.getAttributeSet();

        Assert.assertNotNull("Should contain status code", getAttributeByName(eventAttrs, AnalyticsAttribute.STATUS_CODE_ATTRIBUTE));
        Assert.assertNotNull("Should contain request domain", getAttributeByName(eventAttrs, AnalyticsAttribute.REQUEST_DOMAIN_ATTRIBUTE));
        Assert.assertNotNull("Should contain path", getAttributeByName(eventAttrs, AnalyticsAttribute.REQUEST_PATH_ATTRIBUTE));
        Assert.assertNotNull("Should contain request url", getAttributeByName(eventAttrs, AnalyticsAttribute.REQUEST_URL_ATTRIBUTE));
        Assert.assertNotNull("Should contain connection type", getAttributeByName(eventAttrs, AnalyticsAttribute.CONNECTION_TYPE_ATTRIBUTE));
        Assert.assertNotNull("Should contain request method", getAttributeByName(eventAttrs, AnalyticsAttribute.REQUEST_METHOD_ATTRIBUTE));
        Assert.assertNotNull("Should contain response time", getAttributeByName(eventAttrs, AnalyticsAttribute.RESPONSE_TIME_ATTRIBUTE));
        Assert.assertNotNull("Should contain bytes sent", getAttributeByName(eventAttrs, AnalyticsAttribute.BYTES_SENT_ATTRIBUTE));
        Assert.assertNotNull("Should contain bytes received", getAttributeByName(eventAttrs, AnalyticsAttribute.BYTES_RECEIVED_ATTRIBUTE));
        Assert.assertNotNull("Should contain response body", getAttributeByName(eventAttrs, AnalyticsAttribute.RESPONSE_BODY_ATTRIBUTE));
    }

    @Test
    public void testSessionAttributes() throws Exception {
        controller.initialize(config, new StubAgentImpl());

        controller.setAttribute("userAttribute", 1);
        Assert.assertEquals("Should contain one user attribute.", 1, controller.getUserAttributeCount());

        int sysAttrCnt = controller.getSystemAttributeCount();
        int userAttrCnt = controller.getUserAttributeCount();
        int sessionAttrCnt = controller.getSessionAttributeCount();

        Assert.assertEquals("Session attribute count should be system + user counts.", sessionAttrCnt, sysAttrCnt + userAttrCnt);

        Set<AnalyticsAttribute> sessionAttributes = controller.getSessionAttributes();
        Assert.assertTrue("Session attributes contains system attributes.", sessionAttributes.containsAll(controller.getSystemAttributes()));
        Assert.assertTrue("Session attributes contains user attributes.", sessionAttributes.containsAll(controller.getUserAttributes()));
    }

    @Test
    public void testSessionDurationAttributeShouldBeAbsentOnSessionStart() throws Exception {
        StubAgentImpl agentImpl = new StubAgentImpl();

        controller.initialize(config, agentImpl);
        controller.setAttribute(AnalyticsAttribute.SESSION_DURATION_ATTRIBUTE, 1.f);
        controller.setAttribute(AnalyticsAttribute.SESSION_TIME_SINCE_LOAD_ATTRIBUTE, 1.f);
        controller.shutdown();
        controller.initialize(config, agentImpl);
        Set<AnalyticsAttribute> userAttrs = controller.getUserAttributes();
        for (AnalyticsAttribute attr : userAttrs) {
            Assert.assertFalse("User attributes should not contain session duration on new session.", attr.getName().equalsIgnoreCase(AnalyticsAttribute.SESSION_DURATION_ATTRIBUTE));
        }
    }

    @Test
    public void testEventShouldContainTimeSinceLoadAttribute() throws Exception {
        controller.initialize(config, new StubAgentImpl());

        AnalyticsEvent analyticsEvent = new AnalyticsEvent("dummy");
        controller.addEvent(analyticsEvent);
        Collection<AnalyticsEvent> events = controller.getEventManager().getQueuedEvents();
        Assert.assertEquals("Collection should contain one event.", 1, events.size());
        Iterator<AnalyticsEvent> eventIt = events.iterator();
        Collection<AnalyticsAttribute> eventAttrs = eventIt.next().getAttributeSet();
        Assert.assertEquals("Event should contain 5 attributes (4 default + 1 added).", 5, eventAttrs.size());
        boolean bFound = false;
        for (AnalyticsAttribute eventAttr : eventAttrs) {
            bFound |= eventAttr.getName().equalsIgnoreCase(AnalyticsAttribute.SESSION_TIME_SINCE_LOAD_ATTRIBUTE);
        }
        Assert.assertTrue("The event should contain SESSION_TIME_SINCE_LOAD_ATTRIBUTE.", bFound);
    }

    @Test
    public void testSessionAttributesShouldContainBuildIdentifier() throws Exception {
        controller.initialize(config, new StubAgentImpl());

        AnalyticsEvent analyticsEvent = new AnalyticsEvent("dummy");
        controller.addEvent(analyticsEvent);
        Assert.assertNotNull("Application Build Identifier should not be null.", controller.getAttribute(AnalyticsAttribute.APP_BUILD_ATTRIBUTE));
    }

    @Test
    public void testSessionAttributesShouldContainCustomBuildIdentifier() throws Exception {
        config.setCustomBuildIdentifier("customAppBuildIdentifier");
        controller.initialize(config, new StubAgentImpl());

        AnalyticsEvent analyticsEvent = new AnalyticsEvent("dummy");
        controller.addEvent(analyticsEvent);
        Assert.assertNotNull("Application Build Identifier should not be null.", controller.getAttribute(AnalyticsAttribute.APP_BUILD_ATTRIBUTE));
        Assert.assertEquals("Application Build Identifier should be equal to: customAppBuildIdentifier",
                "customAppBuildIdentifier",
                controller.getAttribute(AnalyticsAttribute.APP_BUILD_ATTRIBUTE).getStringValue());
    }

    @Test
    public void testShouldParseOSVersion() throws Exception {
        TestStubAgentImpl agentImpl = new TestStubAgentImpl();
        AnalyticsAttribute osVersion;
        AnalyticsAttribute majorVersion;

        controller.initialize(config, agentImpl);
        osVersion = controller.getAttribute(AnalyticsAttribute.OS_VERSION_ATTRIBUTE);
        majorVersion = controller.getAttribute(AnalyticsAttribute.OS_MAJOR_VERSION_ATTRIBUTE);
        Assert.assertEquals("Version should equal 2.3", "2.3", osVersion.getStringValue());
        Assert.assertEquals("Major version should equal 2", "2", majorVersion.getStringValue());
        controller.shutdown();

        agentImpl.deviceInformation.setOsVersion("M");
        controller.initialize(config, agentImpl);
        osVersion = controller.getAttribute(AnalyticsAttribute.OS_VERSION_ATTRIBUTE);
        majorVersion = controller.getAttribute(AnalyticsAttribute.OS_MAJOR_VERSION_ATTRIBUTE);
        Assert.assertEquals("Version should equal M", "M", osVersion.getStringValue());
        Assert.assertEquals("Major version should equal M", "M", majorVersion.getStringValue());
        controller.shutdown();

        agentImpl.deviceInformation.setOsVersion("2.1.0-HOTFIX:RC 1");
        controller.initialize(config, agentImpl);
        osVersion = controller.getAttribute(AnalyticsAttribute.OS_VERSION_ATTRIBUTE);
        majorVersion = controller.getAttribute(AnalyticsAttribute.OS_MAJOR_VERSION_ATTRIBUTE);
        Assert.assertEquals("Version should equal 2.1.0-HOTFIX:RC1", "2.1.0-HOTFIX:RC1", osVersion.getStringValue());
        Assert.assertEquals("Major version should equal 2", "2", majorVersion.getStringValue());
        controller.shutdown();

        agentImpl.deviceInformation.setOsVersion("this is gonna hurt");
        controller.initialize(config, agentImpl);
        osVersion = controller.getAttribute(AnalyticsAttribute.OS_VERSION_ATTRIBUTE);
        majorVersion = controller.getAttribute(AnalyticsAttribute.OS_MAJOR_VERSION_ATTRIBUTE);
        Assert.assertEquals("Version should equal 'thisisgonnahurt'", "thisisgonnahurt", osVersion.getStringValue());
        Assert.assertEquals("Major version should equal 'thisisgonnahurt", "thisisgonnahurt", majorVersion.getStringValue());
        controller.shutdown();

        agentImpl.deviceInformation.setOsVersion("10");
        controller.initialize(config, agentImpl);
        osVersion = controller.getAttribute(AnalyticsAttribute.OS_VERSION_ATTRIBUTE);
        majorVersion = controller.getAttribute(AnalyticsAttribute.OS_MAJOR_VERSION_ATTRIBUTE);
        Assert.assertEquals("Version should equal '10'", "10", osVersion.getStringValue());
        Assert.assertEquals("Major version should equal '10", "10", majorVersion.getStringValue());
        controller.shutdown();

        agentImpl.deviceInformation.setOsVersion("9.");
        controller.initialize(config, agentImpl);
        osVersion = controller.getAttribute(AnalyticsAttribute.OS_VERSION_ATTRIBUTE);
        majorVersion = controller.getAttribute(AnalyticsAttribute.OS_MAJOR_VERSION_ATTRIBUTE);
        Assert.assertEquals("Version should equal 9.", "9.", osVersion.getStringValue());
        Assert.assertEquals("Major version should equal 9", "9", majorVersion.getStringValue());
        controller.shutdown();

        agentImpl.deviceInformation.setOsVersion(" 8.7.6-5 ");
        controller.initialize(config, agentImpl);
        osVersion = controller.getAttribute(AnalyticsAttribute.OS_VERSION_ATTRIBUTE);
        majorVersion = controller.getAttribute(AnalyticsAttribute.OS_MAJOR_VERSION_ATTRIBUTE);
        Assert.assertEquals("Version should equal 8.7.6-5", "8.7.6-5", osVersion.getStringValue());
        Assert.assertEquals("Major version should equal 8", "8", majorVersion.getStringValue());
        controller.shutdown();

        agentImpl.deviceInformation.setOsVersion(" ");
        controller.initialize(config, agentImpl);
        osVersion = controller.getAttribute(AnalyticsAttribute.OS_VERSION_ATTRIBUTE);
        majorVersion = controller.getAttribute(AnalyticsAttribute.OS_MAJOR_VERSION_ATTRIBUTE);
        Assert.assertNotNull("Version should be undefined", osVersion);
        Assert.assertEquals("Version should equal 'undefined'", "undefined", osVersion.getStringValue());
        Assert.assertNull("Major version should be undefined", majorVersion);
        controller.shutdown();
    }

    @Test
    public void testShouldNotPersisteExcludedAttributes() throws Exception {
        StubAgentImpl agentImpl = new StubAgentImpl();

        controller.initialize(config, agentImpl);
        for (String attr : AnalyticsValidator.excludedAttributeNames) {
            controller.setAttribute(attr, "excluded (non-persisted)", false);
        }
        controller.shutdown();

        controller.initialize(config, agentImpl);
        Set<AnalyticsAttribute> userAttrs = controller.getUserAttributes();
        Assert.assertTrue("Excluded user attributes are not persisted.", userAttrs.isEmpty());
    }

    @Test
    public void testShouldAddUncheckedAttributes() throws Exception {
        controller.initialize(config, new StubAgentImpl());
        int systemAttributeCount = controller.getSystemAttributeCount();

        AnalyticsAttribute attribute = new AnalyticsAttribute("attribute", "value");
        controller.addAttributeUnchecked(attribute, false);
        Assert.assertEquals("Should contain 1 attribute", systemAttributeCount + 1, controller.getSystemAttributeCount());
        Assert.assertEquals("Should find unchecked attribute", attribute.getStringValue(), controller.getAttribute("attribute").valueAsString());

        attribute = new AnalyticsAttribute("attribute", "replacementValue");
        controller.addAttributeUnchecked(attribute, false);
        Assert.assertEquals("Should allow attribute replacement", systemAttributeCount + 1, controller.getSystemAttributeCount());
        Assert.assertEquals("Should allow attribute value replacement", attribute.getStringValue(), controller.getAttribute("attribute").getStringValue());

        Assert.assertTrue("Should allow reserved attribute names",
                controller.addAttributeUnchecked(new AnalyticsAttribute(AnalyticsAttribute.APP_INSTALL_ATTRIBUTE, true), false));
        Assert.assertEquals("Should find reserved attribute", true,
                controller.getAttribute(AnalyticsAttribute.APP_INSTALL_ATTRIBUTE).getBooleanValue());

        String superLongAttributeName = "attrName";
        while (superLongAttributeName.length() < AnalyticsAttribute.ATTRIBUTE_NAME_MAX_LENGTH) {
            superLongAttributeName += superLongAttributeName;
        }
        Assert.assertFalse("Unchecked attributes should not allow attribute names > ATTRIBUTE_NAME_MAX_LENGTH",
                controller.addAttributeUnchecked(new AnalyticsAttribute(superLongAttributeName, "true", false), false));

        String superLongAttributeValue = "attrValue";
        while (superLongAttributeValue.length() < AnalyticsAttribute.ATTRIBUTE_VALUE_MAX_LENGTH) {
            superLongAttributeValue += superLongAttributeValue;
        }
        Assert.assertFalse("Unchecked attributes should not allow attribute names > ATTRIBUTE_VALUE_MAX_LENGTH",
                controller.addAttributeUnchecked(new AnalyticsAttribute("superLongAttributeValue", superLongAttributeValue, false), false));

        Integer ctr = controller.getUserAttributeCount() + 1;
        while (AnalyticsControllerImpl.MAX_ATTRIBUTES > controller.getUserAttributeCount()) {
            controller.setAttribute("duplicateAttr" + ctr.toString(), "duplicateValue" + ctr.toString());
            ctr++;
        }
        attribute = new AnalyticsAttribute("oneMoreAttribute", "value");
        controller.addAttributeUnchecked(attribute, false);
        Assert.assertEquals("Should contain MAX_ATTRIBUTES attributes", AnalyticsControllerImpl.MAX_ATTRIBUTES, controller.getUserAttributeCount());
    }

    @Test
    public void testShouldIncludePlatformInfo() throws Exception {
        controller.initialize(config, new StubAgentImpl());

        int sysAttrCnt = controller.getSystemAttributeCount();

        Set<AnalyticsAttribute> systemAttributes = controller.getSystemAttributes();
        Assert.assertTrue("Session attributes contains system attributes.", systemAttributes.containsAll(controller.getSystemAttributes()));

        boolean bPlatformFound = false;
        boolean bPlatformVersionFound = false;
        for (AnalyticsAttribute attr : systemAttributes) {
            bPlatformFound |= attr.getName().equalsIgnoreCase(AnalyticsAttribute.APPLICATION_PLATFORM_ATTRIBUTE);
            bPlatformVersionFound |= attr.getName().equalsIgnoreCase(AnalyticsAttribute.APPLICATION_PLATFORM_VERSION_ATTRIBUTE);
        }
        Assert.assertTrue("The event should contain APPLICATION_PLATFORM_ATTRIBUTE", bPlatformFound);
        Assert.assertTrue("The event should contain APPLICATION_PLATFORM_VERSION_ATTRIBUTE", bPlatformVersionFound);

        Assert.assertEquals("Default platform version is agent version", config.getApplicationFrameworkVersion(), Agent.getVersion());
        config.setApplicationFrameworkVersion("9.9");
        Assert.assertEquals("Updated platform version", config.getApplicationFrameworkVersion(), "9.9");
    }

    @Test
    public void testConcurrentModification() throws Exception {
        final int nThreads = 25;
        final int nDuration = 5 * 1000;
        final ExecutorService executor = Executors.newFixedThreadPool(nThreads * 2);
        final AgentLog log = new ConsoleAgentLog();
        final AtomicBoolean cancelled = new AtomicBoolean(false);

        class ReadRunner implements Callable<Double> {
            @Override
            public Double call() {
                long tStart = System.currentTimeMillis();
                Integer i = 0;
                while (!cancelled.get()) {
                    controller.setAttribute("attributeBool", true);
                    controller.setAttribute("attribute", "attributeValue", true);
                    Assert.assertFalse("Should not contain more than 128 attributes", controller.getUserAttributeCount() > AnalyticsControllerImpl.MAX_ATTRIBUTES);
                    controller.getAttribute("attribute");
                    controller.getAttribute("attributeFloat");
                    controller.getAttribute(AnalyticsAttribute.OS_NAME_ATTRIBUTE);
                    Thread.yield();
                    controller.getAttribute(AnalyticsAttribute.NEW_RELIC_VERSION_ATTRIBUTE);
                    controller.incrementAttribute("attribute" + i.toString(), 0.01f);
                    controller.removeAttribute("attributeFloat");
                    Thread.yield();
                    if (controller.getUserAttributeCount() >= AnalyticsControllerImpl.MAX_ATTRIBUTES) {
                        controller.removeAllAttributes();
                        controller.loadPersistentAttributes();
                        Assert.assertFalse("Should not contain more than 128 attributes", controller.getUserAttributeCount() > AnalyticsControllerImpl.MAX_ATTRIBUTES);
                    }
                    Assert.assertFalse("Should not contain more than 128 attributes", controller.getUserAttributes().size() > AnalyticsControllerImpl.MAX_ATTRIBUTES);
                    i++;
                }
                Double dTime = (System.currentTimeMillis() - tStart) / i.doubleValue();
                log.debug(String.format("[ReadRunner] %f ms.", dTime));

                return dTime;
            }
        }

        class WriteRunner implements Callable<Double> {
            @Override
            public Double call() {
                long tStart = System.currentTimeMillis();
                Integer i = 0;
                while (!cancelled.get()) {
                    controller.setAttribute("attributeBool", false);
                    controller.setAttribute("attribute", "attributeValue", false);
                    controller.setAttribute("attribute" + i.toString(), "attributeValue", false);
                    controller.setAttribute("attributeFloat", (float) i);
                    Thread.yield();
                    controller.setAttribute(AnalyticsAttribute.NEW_RELIC_VERSION_ATTRIBUTE, Agent.getVersion());
                    controller.setAttribute("attribute" + i.toString(), "attributeValue", true);
                    controller.incrementAttribute("attributeFloat", 0.01f);
                    Thread.yield();

                    Assert.assertFalse("Should not contain more than " + AnalyticsControllerImpl.MAX_ATTRIBUTES + " attributes", controller.getUserAttributeCount() > AnalyticsControllerImpl.MAX_ATTRIBUTES);
                    i++;
                }

                Double dTime = (System.currentTimeMillis() - tStart) / i.doubleValue();
                log.debug(String.format("[WriteRunner] %f ms.", dTime));

                return dTime;
            }
        }

        // uncomment to view timing
        log.setLevel(AgentLog.DEBUG);

        controller.initialize(config, new StubAgentImpl());

        try {
            List<Future> futures = new ArrayList<>();

            for (int t = 0; t < nThreads; t++) {
                futures.add(executor.submit(new ReadRunner()));
                futures.add(executor.submit(new WriteRunner()));
            }

            executor.shutdown();
            Thread.sleep(nDuration);
            cancelled.set(true);
            Thread.sleep(30);
            for (Future f : futures) {
                f.get();
            }
        } catch (ConcurrentModificationException e) {
            Assert.fail("Should not throw ConcurrentModificationError: " + e.getMessage());
        }
    }

    @Test
    public void shoudlRecordLastInteraction() throws Exception {
        controller.initialize(config, new StubAgentImpl());

        AnalyticsAttribute attr;

        FeatureFlag.disableFeature(FeatureFlag.InteractionTracing);
        TraceMachine.startTracing("Trace0");
        attr = getAttributeByName(controller.getUserAttributes(), AnalyticsAttribute.LAST_INTERACTION_ATTRIBUTE);
        Assert.assertNull("Should not record last interaction if tracing disabled", attr);

        FeatureFlag.enableFeature(FeatureFlag.InteractionTracing);
        TraceMachine.clearActivityHistory();
        TraceMachine.startTracing("Trace1");
        TraceMachine.startTracing("Trace2");
        TraceMachine.startTracing("Trace3");

        attr = getAttributeByName(controller.getSystemAttributes(), AnalyticsAttribute.LAST_INTERACTION_ATTRIBUTE);
        Assert.assertNotNull("Should contain last interaction attribute", attr);
        Assert.assertEquals("Last interaction should be display trace", attr.getStringValue(), TraceMachine.formatActivityDisplayName("Trace3"));

        TraceMachine.startTracing("Trace4", true);
        attr = getAttributeByName(controller.getSystemAttributes(), AnalyticsAttribute.LAST_INTERACTION_ATTRIBUTE);
        Assert.assertEquals("Last interaction should be custom trace name", attr.getStringValue(), "Trace4");

        TraceMachine.startTracing("Trace5", true, true);
        attr = getAttributeByName(controller.getSystemAttributes(), AnalyticsAttribute.LAST_INTERACTION_ATTRIBUTE);
        Assert.assertEquals("Last interaction should be custom trace name", attr.getStringValue(), "Trace5");

        TraceMachine.setCurrentDisplayName("Trace6");
        attr = getAttributeByName(controller.getSystemAttributes(), AnalyticsAttribute.LAST_INTERACTION_ATTRIBUTE);
        Assert.assertEquals("Last interaction should be custom trace name", attr.getStringValue(), "Trace6");

        FeatureFlag.disableFeature(FeatureFlag.DefaultInteractions);
        TraceMachine.startTracing("Trace7");
        attr = getAttributeByName(controller.getSystemAttributes(), AnalyticsAttribute.LAST_INTERACTION_ATTRIBUTE);
        Assert.assertEquals("Should not record last interaction if DefaultInteractions disabled", attr.getStringValue(), "Trace6");

        TraceMachine.startTracing("Trace8", true, true);
        attr = getAttributeByName(controller.getSystemAttributes(), AnalyticsAttribute.LAST_INTERACTION_ATTRIBUTE);
        Assert.assertEquals("Should record last interaction if InteractionTracing is overridden", attr.getStringValue(), "Trace8");

        Harvest.setInstance(new HarvestStub());
        TraceMachine.startTracing("Trace9", true, true);
        attr = getAttributeByName(controller.getSystemAttributes(), AnalyticsAttribute.LAST_INTERACTION_ATTRIBUTE);
        Assert.assertEquals("Should not record last interaction if harvest is disabled", attr.getStringValue(), "Trace8");

        FeatureFlag.enableFeature(FeatureFlag.DefaultInteractions);
    }

    @Test
    public void nameShouldLimitUserAttributes() throws Exception {
        final int nThreads = 5;
        final int nDuration = 5 * 1000;

        controller.initialize(config, new StubAgentImpl());

        final ExecutorService executor = Executors.newFixedThreadPool(2);
        final List<Future> futures = new ArrayList<>();

        for (int i = 0; i < nThreads; i++) {
            futures.add(executor.submit(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    for (Integer i = 0; i < AnalyticsControllerImpl.MAX_ATTRIBUTES * 2; i++) {
                        controller.setAttribute("attribute" + i.toString(), "attributeValue" + i.toString());
                        Assert.assertFalse("Should not contain more than " + AnalyticsControllerImpl.MAX_ATTRIBUTES + " attributes", controller.getUserAttributeCount() > AnalyticsControllerImpl.MAX_ATTRIBUTES);
                    }
                    return true;
                }
            }));
        }

        executor.shutdown();
        Thread.sleep(nDuration);
        for (Future f : futures) {
            f.get();
        }

        Assert.assertFalse("Should not contain more than " + AnalyticsControllerImpl.MAX_ATTRIBUTES + " attributes", controller.getUserAttributes().size() > AnalyticsControllerImpl.MAX_ATTRIBUTES);
    }

    @Test
    public void testValidateEventTypeName() throws Exception {
        Map<String, Object> attributes = new HashMap<>();

        controller.initialize(config, new StubAgentImpl());

        Assert.assertTrue("Event type should only contain allowable characters", controller.recordCustomEvent("Álphá: 1 2 3 5 : . _", attributes));
        Assert.assertFalse("Should not record event types containing allowable characters", controller.recordCustomEvent("\t\n\r\b Álphá: + 1 + 2 + 3 + 5)(*&^%_", attributes));
    }

    @Test
    public void shouldHandleNullImpl() {
        try {
            controller.initialize(config, null);
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void shouldHandleNullConfig() {
        try {
            controller.initialize(null, new StubAgentImpl());
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void testEventDeletionOnHarvest() {
        FeatureFlag.enableFeature(FeatureFlag.AnalyticsEvents);
        FeatureFlag.enableFeature(FeatureFlag.EventPersistence);
        AnalyticsEventStore eventStore = config.getEventStore();
        eventStore.clear();

        Harvest.initialize(config);
        HarvestData data = Harvest.getInstance().getHarvestData();
        ArrayList<AnalyticsEvent> events = new ArrayList<>();
        TestStubAgentImpl agentImpl = new TestStubAgentImpl();
        controller.initialize(config, agentImpl);
        controller.getEventManager().setTransmitRequired();
        AnalyticsEvent event1 = new AnalyticsEvent("event1");
        AnalyticsEvent event2 = new AnalyticsEvent("event2");
        controller.addEvent(event1);
        controller.addEvent(event2);
        events.add(event1);
        events.add(event2);
        data.setAnalyticsEvents(events);
        data.setAnalyticsEnabled(true);
        Assert.assertEquals(2, eventStore.count());

        controller.onHarvest();
        Assert.assertEquals(0, eventStore.count());

        FeatureFlag.disableFeature(FeatureFlag.EventPersistence);
        eventStore.clear();

        Harvest.initialize(config);
        AnalyticsEvent event3 = new AnalyticsEvent("event3");
        AnalyticsEvent event4 = new AnalyticsEvent("event4");
        controller.addEvent(event3);
        controller.addEvent(event4);
        events.add(event3);
        events.add(event4);
        data.setAnalyticsEvents(events);
        data.setAnalyticsEnabled(true);
        Assert.assertEquals(0, eventStore.count());

        controller.onHarvest();
        Assert.assertEquals(0, eventStore.count());

    }

    private static class TestStubAgentImpl extends StubAgentImpl {
        public DeviceInformation deviceInformation;

        public TestStubAgentImpl() {
            super();
            this.deviceInformation = super.getDeviceInformation();
        }

        @Override
        public DeviceInformation getDeviceInformation() {
            return deviceInformation;
        }
    }

    private static class HarvestStub extends Harvest {
        private static class HarvesterStub extends Harvester {
            @Override
            public boolean isDisabled() {
                return true;
            }
        }

        @Override
        protected Harvester getHarvester() {
            return new HarvesterStub();
        }
    }
}
