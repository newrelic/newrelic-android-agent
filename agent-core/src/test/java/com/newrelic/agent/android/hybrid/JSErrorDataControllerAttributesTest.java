/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.hybrid;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.analytics.AnalyticsControllerImpl;
import com.newrelic.agent.android.analytics.AnalyticsEvent;
import com.newrelic.agent.android.payload.PayloadController;
import com.newrelic.agent.android.test.stub.StubAgentImpl;
import com.newrelic.agent.android.test.stub.StubAnalyticsAttributeStore;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * NR-563075 Phase 1 core-attribute wire keys for MobileJSError events.
 *
 * Verifies that {@link JSErrorDataController#sendJSErrorData} emits the
 * required camelCase wire keys — {@code errorId}, {@code errorName},
 * {@code errorType}, {@code errorMessage} — and that the legacy
 * {@code description} key is no longer produced.
 */
public class JSErrorDataControllerAttributesTest {

    private static final long STORE_WAIT_SECONDS = 5;

    private LatchingJSErrorStore store;

    @Before
    public void setUp() {
        FeatureFlag.resetFeatures();
        store = new LatchingJSErrorStore();
        AgentConfiguration config = AgentConfiguration.getInstance();
        config.setJsErrorStore(store);
        config.setAnalyticsAttributeStore(new StubAnalyticsAttributeStore());
        PayloadController.initialize(config);
        AnalyticsControllerImpl.initialize(config, new StubAgentImpl());
        JSErrorDataController.reset();
    }

    @After
    public void tearDown() {
        FeatureFlag.resetFeatures();
        JSErrorDataController.reset();
        AnalyticsControllerImpl.shutdown();
        PayloadController.shutdown();
        AgentConfiguration.getInstance().setJsErrorStore(null);
    }

    @Test
    public void wireKeys_arePresentAndCamelCase() throws Exception {
        boolean queued = JSErrorDataController.getInstance().sendJSErrorData(
                "TypeError", "undefined is not an object", "stack-frames", false, null);

        Assert.assertTrue("sendJSErrorData should queue successfully", queued);
        Assert.assertTrue("store was not called within timeout",
                store.latch.await(STORE_WAIT_SECONDS, TimeUnit.SECONDS));

        JsonObject event = JsonParser.parseString(store.lastValue).getAsJsonObject();

        // Phase 1 core attributes — all camelCase per NR-563075
        Assert.assertEquals("errorId",      "errorId",      AnalyticsAttribute.JSERROR_ERRORID);
        Assert.assertEquals("errorName",    "errorName",    AnalyticsAttribute.JSERROR_ERRORNAME);
        Assert.assertEquals("errorMessage", "errorMessage", AnalyticsAttribute.JSERROR_ERRORMESSAGE);

        // Keys present on the wire
        Assert.assertTrue("errorId must be present",      event.has("errorId"));
        Assert.assertTrue("errorName must be present",    event.has("errorName"));
        Assert.assertTrue("errorMessage must be present", event.has("errorMessage"));

        Assert.assertEquals(store.lastId, event.get("errorId").getAsString());
        Assert.assertFalse("errorId must be non-empty",
                event.get("errorId").getAsString().isEmpty());
    }

    @Test
    public void errorName_reflectsNameArgument() throws Exception {
        JSErrorDataController.getInstance().sendJSErrorData(
                "ReferenceError", "x is not defined", "stack", false, null);
        Assert.assertTrue(store.latch.await(STORE_WAIT_SECONDS, TimeUnit.SECONDS));

        JsonObject event = JsonParser.parseString(store.lastValue).getAsJsonObject();
        Assert.assertEquals("ReferenceError",
                event.get(AnalyticsAttribute.JSERROR_ERRORNAME).getAsString());
    }

    @Test
    public void errorMessage_reflectsMessageArgument() throws Exception {
        JSErrorDataController.getInstance().sendJSErrorData(
                "SyntaxError", "Unexpected token", "stack", false, null);
        Assert.assertTrue(store.latch.await(STORE_WAIT_SECONDS, TimeUnit.SECONDS));

        JsonObject event = JsonParser.parseString(store.lastValue).getAsJsonObject();
        Assert.assertEquals("Unexpected token",
                event.get(AnalyticsAttribute.JSERROR_ERRORMESSAGE).getAsString());
    }

    @Test
    public void descriptionKey_isNoLongerWritten() throws Exception {
        JSErrorDataController.getInstance().sendJSErrorData(
                "TypeError", "boom", "stack", true, null);
        Assert.assertTrue(store.latch.await(STORE_WAIT_SECONDS, TimeUnit.SECONDS));

        JsonObject event = JsonParser.parseString(store.lastValue).getAsJsonObject();
        Assert.assertFalse("legacy 'description' wire key must not be written",
                event.has("description"));
    }

    @Test
    public void companionAttributesStillPopulated() throws Exception {
        JSErrorDataController.getInstance().sendJSErrorData(
                "TypeError", "boom", "stack-frames", true, null);
        Assert.assertTrue(store.latch.await(STORE_WAIT_SECONDS, TimeUnit.SECONDS));

        JsonObject event = JsonParser.parseString(store.lastValue).getAsJsonObject();
        Assert.assertEquals("stack-frames",
                event.get(AnalyticsAttribute.JSERROR_THREADS).getAsString());
        Assert.assertTrue(event.get(AnalyticsAttribute.JSERROR_ISFATAL).getAsBoolean());
        Assert.assertEquals(AnalyticsEvent.EVENT_TYPE_MOBILE_JSERROR,
                event.get(AnalyticsAttribute.EVENT_TYPE_ATTRIBUTE).getAsString());
        Assert.assertTrue("timestamp must be present",
                event.has(AnalyticsAttribute.JSERROR_TIMESTAMP));
    }

    @Test
    public void additionalAttributes_doNotOverrideReservedWireKeys() throws Exception {
        Map<String, Object> extras = new HashMap<>();
        extras.put(AnalyticsAttribute.JSERROR_ERRORNAME, "HijackedName");
        extras.put(AnalyticsAttribute.JSERROR_ERRORMESSAGE, "HijackedMessage");
        extras.put(AnalyticsAttribute.JSERROR_ERRORID, "HijackedId");
        extras.put("customKey", "customValue");

        JSErrorDataController.getInstance().sendJSErrorData(
                "TypeError", "legit message", "stack", false, extras);
        Assert.assertTrue(store.latch.await(STORE_WAIT_SECONDS, TimeUnit.SECONDS));

        JsonObject event = JsonParser.parseString(store.lastValue).getAsJsonObject();
        Assert.assertEquals("TypeError",
                event.get(AnalyticsAttribute.JSERROR_ERRORNAME).getAsString());

        Assert.assertEquals("legit message",
                event.get(AnalyticsAttribute.JSERROR_ERRORMESSAGE).getAsString());
        Assert.assertNotEquals("HijackedId",
                event.get(AnalyticsAttribute.JSERROR_ERRORID).getAsString());
        // Non-reserved additional attributes are preserved
        Assert.assertEquals("customValue", event.get("customKey").getAsString());
    }

    @Test
    public void nullMessage_isNormalizedToEmptyString() throws Exception {
        JSErrorDataController.getInstance().sendJSErrorData(
                "TypeError", null, "stack", false, null);
        Assert.assertTrue(store.latch.await(STORE_WAIT_SECONDS, TimeUnit.SECONDS));

        JsonObject event = JsonParser.parseString(store.lastValue).getAsJsonObject();
        Assert.assertEquals("",
                event.get(AnalyticsAttribute.JSERROR_ERRORMESSAGE).getAsString());
    }

    @Test
    public void sessionAttributes_areEmbeddedInPersistedEvent() throws Exception {
        AnalyticsControllerImpl.getInstance().setAttribute("customKey", "customValue", false);

        JSErrorDataController.getInstance().sendJSErrorData(
                "TypeError", "boom", "stack", false, null);
        Assert.assertTrue(store.latch.await(STORE_WAIT_SECONDS, TimeUnit.SECONDS));

        JsonObject event = JsonParser.parseString(store.lastValue).getAsJsonObject();
        Assert.assertEquals("user attribute must be embedded as a flat event-level field",
                "customValue", event.get("customKey").getAsString());
        Assert.assertTrue("system attribute (osName) from the snapshot must be present",
                event.has(AnalyticsAttribute.OS_NAME_ATTRIBUTE));
        Assert.assertTrue("system attribute (sessionId) from the snapshot must be present",
                event.has(AnalyticsAttribute.SESSION_ID_ATTRIBUTE));
    }

    @Test
    public void reservedJSErrorKeys_winOverSessionAttributesOnCollision() throws Exception {
        // 'errorName' is not on the AnalyticsValidator reserved list, so a user
        // attribute can be set under that name — but the JSError reserved key
        // must still win when persisted.
        AnalyticsControllerImpl.getInstance().setAttribute(
                AnalyticsAttribute.JSERROR_ERRORNAME, "session-bogus", false);

        JSErrorDataController.getInstance().sendJSErrorData(
                "RealErrorName", "boom", "stack", false, null);
        Assert.assertTrue(store.latch.await(STORE_WAIT_SECONDS, TimeUnit.SECONDS));

        JsonObject event = JsonParser.parseString(store.lastValue).getAsJsonObject();
        Assert.assertEquals("RealErrorName",
                event.get(AnalyticsAttribute.JSERROR_ERRORNAME).getAsString());
    }

    @Test
    public void additionalAttributes_winOverSessionAttributesOnCollision() throws Exception {
        AnalyticsControllerImpl.getInstance().setAttribute("conflictKey", "from-session", false);

        Map<String, Object> extras = new HashMap<>();
        extras.put("conflictKey", "from-additional");

        JSErrorDataController.getInstance().sendJSErrorData(
                "TypeError", "boom", "stack", false, extras);
        Assert.assertTrue(store.latch.await(STORE_WAIT_SECONDS, TimeUnit.SECONDS));

        JsonObject event = JsonParser.parseString(store.lastValue).getAsJsonObject();
        Assert.assertEquals("from-additional", event.get("conflictKey").getAsString());
    }

    @Test
    public void snapshot_reflectsRecordTimeNotSendTime() throws Exception {
        AnalyticsControllerImpl ctrl = AnalyticsControllerImpl.getInstance();
        ctrl.setAttribute("snapshotKey", "OLD", false);

        JSErrorDataController.getInstance().sendJSErrorData(
                "TypeError", "boom", "stack", false, null);
        Assert.assertTrue(store.latch.await(STORE_WAIT_SECONDS, TimeUnit.SECONDS));

        JsonObject persistedAtRecordTime = JsonParser.parseString(store.lastValue).getAsJsonObject();
        Assert.assertEquals("OLD", persistedAtRecordTime.get("snapshotKey").getAsString());

        // Mutating the controller after recording must not change the persisted bytes.
        ctrl.setAttribute("snapshotKey", "NEW", false);
        JsonObject persistedStillFrozen = JsonParser.parseString(store.lastValue).getAsJsonObject();
        Assert.assertEquals("OLD", persistedStillFrozen.get("snapshotKey").getAsString());
    }

    @Test
    public void snapshot_includesBothSystemAndUserSessionAttributes() throws Exception {
        AnalyticsControllerImpl.getInstance().setAttribute("userTag", "user-value", false);

        JSErrorDataController.getInstance().sendJSErrorData(
                "TypeError", "boom", "stack", false, null);
        Assert.assertTrue(store.latch.await(STORE_WAIT_SECONDS, TimeUnit.SECONDS));

        JsonObject event = JsonParser.parseString(store.lastValue).getAsJsonObject();
        // User attribute
        Assert.assertEquals("user-value", event.get("userTag").getAsString());
        // System attributes populated from the StubAgentImpl during controller init
        Assert.assertEquals("Android", event.get(AnalyticsAttribute.OS_NAME_ATTRIBUTE).getAsString());
        Assert.assertEquals("StubAgent", event.get(AnalyticsAttribute.DEVICE_MODEL_ATTRIBUTE).getAsString());
        Assert.assertEquals("Fake", event.get(AnalyticsAttribute.DEVICE_MANUFACTURER_ATTRIBUTE).getAsString());
    }

    private static final class LatchingJSErrorStore implements JSErrorStore {
        final CountDownLatch latch = new CountDownLatch(1);
        final Map<String, String> data = new HashMap<>();
        volatile String lastId;
        volatile String lastValue;

        @Override
        public boolean store(String id, String value) {
            this.lastId = id;
            this.lastValue = value;
            data.put(id, value);
            latch.countDown();
            return true;
        }

        @Override
        public List<String> fetchAll() {
            return new java.util.ArrayList<>(data.values());
        }

        @Override
        public Map<String, String> fetchAllEntries() {
            return new HashMap<>(data);
        }

        @Override
        public void delete(String id) {
            data.remove(id);
        }

        @Override
        public int count() {
            return data.size();
        }

        @Override
        public void clear() {
            data.clear();
        }
    }
}