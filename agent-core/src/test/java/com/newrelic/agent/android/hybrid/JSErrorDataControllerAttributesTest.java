/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.hybrid;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.analytics.AnalyticsControllerImpl;
import com.newrelic.agent.android.analytics.AnalyticsEvent;
import com.newrelic.agent.android.harvest.ApplicationInformation;
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

    private MutableVersionAgentImpl mutableAgent;

    @Before
    public void setUp() {
        FeatureFlag.resetFeatures();
        store = new LatchingJSErrorStore();
        AgentConfiguration config = AgentConfiguration.getInstance();
        config.setJsErrorStore(store);
        config.setAnalyticsAttributeStore(new StubAnalyticsAttributeStore());
        PayloadController.initialize(config);
        AnalyticsControllerImpl.initialize(config, new StubAgentImpl());
        // Install a mutable agent so tests can vary appVersion at runtime to
        // simulate an upgrade between recordings.
        mutableAgent = new MutableVersionAgentImpl();
        Agent.setImpl(mutableAgent);
        JSErrorDataController.reset();
    }

    @After
    public void tearDown() {
        FeatureFlag.resetFeatures();
        JSErrorDataController.reset();
        AnalyticsControllerImpl.shutdown();
        PayloadController.shutdown();
        AgentConfiguration.getInstance().setJsErrorStore(null);
        Agent.setImpl(null);
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

    @Test
    public void appVersionAtRecord_isEmbeddedInPersistedEvent() throws Exception {
        mutableAgent.setAppVersion("1.2.3");

        JSErrorDataController.getInstance().sendJSErrorData(
                "TypeError", "boom", "stack", false, null);
        Assert.assertTrue(store.latch.await(STORE_WAIT_SECONDS, TimeUnit.SECONDS));

        JsonObject event = JsonParser.parseString(store.lastValue).getAsJsonObject();
        Assert.assertEquals("recorded app version must be embedded as a flat event-level field",
                "1.2.3", event.get(AnalyticsAttribute.APP_VERSION_AT_RECORD_ATTRIBUTE).getAsString());
    }

    @Test
    public void appVersionAtRecord_additionalAttributesWinOnCollision() throws Exception {
        mutableAgent.setAppVersion("1.2.3");

        Map<String, Object> extras = new HashMap<>();
        extras.put(AnalyticsAttribute.APP_VERSION_AT_RECORD_ATTRIBUTE, "user-override");

        JSErrorDataController.getInstance().sendJSErrorData(
                "TypeError", "boom", "stack", false, extras);
        Assert.assertTrue(store.latch.await(STORE_WAIT_SECONDS, TimeUnit.SECONDS));

        JsonObject event = JsonParser.parseString(store.lastValue).getAsJsonObject();
        Assert.assertEquals("user-supplied additionalAttributes must win over the snapshot",
                "user-override", event.get(AnalyticsAttribute.APP_VERSION_AT_RECORD_ATTRIBUTE).getAsString());
    }

    @Test
    public void getStoredJSErrorData_singleVersion_returnsOneSnapshot() throws Exception {
        mutableAgent.setAppVersion("1.0.0");

        recordAndAwait("TypeError", "msg-1");
        recordAndAwait("TypeError", "msg-2");
        recordAndAwait("TypeError", "msg-3");

        List<JSErrorDataController.HarvestSnapshot> snapshots =
                JSErrorDataController.getInstance().getStoredJSErrorData();

        Assert.assertEquals("single-version cache must produce one snapshot",
                1, snapshots.size());
        JSErrorDataController.HarvestSnapshot snap = snapshots.get(0);
        Assert.assertEquals("snapshot.appVersion must match the recorded version",
                "1.0.0", snap.appVersion);
        Assert.assertEquals("snapshot must include all three persisted IDs",
                3, snap.snapshotIds.size());
    }

    @Test
    public void getStoredJSErrorData_multiVersion_returnsSnapshotsPerVersion() throws Exception {
        mutableAgent.setAppVersion("1.0.0");
        recordAndAwait("TypeError", "v1-msg-1");
        recordAndAwait("TypeError", "v1-msg-2");

        // Simulate an app upgrade: persisted v1 entries remain in the store, but
        // subsequent recordings tag themselves with the new version.
        mutableAgent.setAppVersion("1.1.0");
        recordAndAwait("TypeError", "v11-msg-1");
        recordAndAwait("TypeError", "v11-msg-2");

        List<JSErrorDataController.HarvestSnapshot> snapshots =
                JSErrorDataController.getInstance().getStoredJSErrorData();

        Assert.assertEquals("multi-version cache must produce one snapshot per version",
                2, snapshots.size());

        // Map snapshots by appVersion for stable assertion regardless of group order
        Map<String, JSErrorDataController.HarvestSnapshot> byVersion = new HashMap<>();
        for (JSErrorDataController.HarvestSnapshot s : snapshots) {
            byVersion.put(s.appVersion, s);
        }
        Assert.assertTrue("group for 1.0.0 must exist", byVersion.containsKey("1.0.0"));
        Assert.assertTrue("group for 1.1.0 must exist", byVersion.containsKey("1.1.0"));
        Assert.assertEquals("v1.0.0 group must contain its 2 IDs",
                2, byVersion.get("1.0.0").snapshotIds.size());
        Assert.assertEquals("v1.1.0 group must contain its 2 IDs",
                2, byVersion.get("1.1.0").snapshotIds.size());

        // IDs must not bleed between groups
        for (String id : byVersion.get("1.0.0").snapshotIds) {
            Assert.assertFalse("v1.0.0 id [" + id + "] must not appear in v1.1.0 group",
                    byVersion.get("1.1.0").snapshotIds.contains(id));
        }
    }

    @Test
    public void buildErrorEnvelope_doesNotEmitEmptyLeadingEvent() throws Exception {
        // Regression: Error.asJsonObject() unconditionally seeds analyticsEvents
        // with the constructor's `event` HashMap (designed for the one-event-per-crash
        // case). For JSError that map is always empty, so prior behavior was to
        // append real events after a leading {} placeholder. The envelope must now
        // contain ONLY the recorded events, with no leading empty entry.
        recordAndAwait("TypeError", "real-msg-1");
        recordAndAwait("TypeError", "real-msg-2");

        List<JSErrorDataController.HarvestSnapshot> snapshots =
                JSErrorDataController.getInstance().getStoredJSErrorData();
        Assert.assertEquals(1, snapshots.size());

        JsonObject envelope = JsonParser.parseString(snapshots.get(0).payloadJson).getAsJsonObject();
        JsonArray events = envelope.getAsJsonArray("analyticsEvents");

        Assert.assertEquals("envelope must contain exactly the recorded events, no empty placeholder",
                2, events.size());
        for (int i = 0; i < events.size(); i++) {
            JsonObject e = events.get(i).getAsJsonObject();
            Assert.assertTrue("event[" + i + "] must contain errorName (rules out empty placeholder)",
                    e.has(AnalyticsAttribute.JSERROR_ERRORNAME));
        }
    }

    @Test
    public void buildErrorEnvelope_stripsAppVersionAtRecordFromWireEvents() throws Exception {
        // appVersionAtRecord is an internal-only marker — it stays on disk for
        // grouping but must not appear as an attribute on the wire event.
        mutableAgent.setAppVersion("1.0.0");
        recordAndAwait("TypeError", "msg");

        // Sanity: the persisted on-disk event still carries the marker.
        JsonObject persisted = JsonParser.parseString(store.lastValue).getAsJsonObject();
        Assert.assertEquals("1.0.0",
                persisted.get(AnalyticsAttribute.APP_VERSION_AT_RECORD_ATTRIBUTE).getAsString());

        // The wire envelope must NOT carry it on any analyticsEvents entry.
        List<JSErrorDataController.HarvestSnapshot> snapshots =
                JSErrorDataController.getInstance().getStoredJSErrorData();
        Assert.assertEquals(1, snapshots.size());
        JsonObject envelope = JsonParser.parseString(snapshots.get(0).payloadJson).getAsJsonObject();
        for (com.google.gson.JsonElement event : envelope.getAsJsonArray("analyticsEvents")) {
            Assert.assertFalse("wire event must not carry the internal appVersionAtRecord field",
                    event.getAsJsonObject().has(AnalyticsAttribute.APP_VERSION_AT_RECORD_ATTRIBUTE));
        }
    }

    @Test
    public void buildErrorEnvelope_appInfoVersion_matchesRecordedVersion() throws Exception {
        // Cached errors recorded under an older build must ship with the OLD
        // appVersion in BOTH the X-NewRelic-App-Version header and the envelope
        // body's appInfo.appVersion so the backend's symbolication path is
        // internally consistent.
        mutableAgent.setAppVersion("1.0.0");
        recordAndAwait("TypeError", "old-build");

        // Simulate an upgrade BEFORE the cached error gets flushed.
        mutableAgent.setAppVersion("2.0.0");

        List<JSErrorDataController.HarvestSnapshot> snapshots =
                JSErrorDataController.getInstance().getStoredJSErrorData();
        Assert.assertEquals(1, snapshots.size());

        JsonObject envelope = JsonParser.parseString(snapshots.get(0).payloadJson).getAsJsonObject();
        JsonObject appInfo = envelope.getAsJsonObject("appInfo");
        Assert.assertNotNull("envelope must contain appInfo", appInfo);
        Assert.assertEquals("appInfo.appVersion must reflect the recorded build, not the current runtime",
                "1.0.0", appInfo.get("appVersion").getAsString());
        Assert.assertEquals("snapshot.appVersion (used for the upload header) must match",
                "1.0.0", snapshots.get(0).appVersion);
    }

    @Test
    public void getStoredJSErrorData_missingVersion_fallsBackToCurrent() throws Exception {
        mutableAgent.setAppVersion("9.9.9");

        // Inject an entry shaped like a pre-snapshot legacy event (no
        // appVersionAtRecord field). It must group under the current app
        // version rather than under null/empty.
        store.store("legacy-id", "{\"errorName\":\"OldError\",\"errorMessage\":\"legacy\"}");

        List<JSErrorDataController.HarvestSnapshot> snapshots =
                JSErrorDataController.getInstance().getStoredJSErrorData();

        Assert.assertEquals(1, snapshots.size());
        Assert.assertEquals("missing version must fall back to current runtime version",
                "9.9.9", snapshots.get(0).appVersion);
        Assert.assertEquals(1, snapshots.get(0).snapshotIds.size());
    }

    private void recordAndAwait(String errorName, String message) throws Exception {
        final int before = store.count();
        JSErrorDataController.getInstance().sendJSErrorData(errorName, message, "stack", false, null);
        long deadline = System.currentTimeMillis() + STORE_WAIT_SECONDS * 1000;
        while (store.count() <= before && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        Assert.assertTrue("store did not receive new entry within timeout",
                store.count() > before);
    }

    /**
     * Test agent impl whose {@code appVersion} can be mutated mid-test to
     * simulate an app upgrade between recordings. Other fields are inherited
     * from {@link StubAgentImpl}.
     */
    private static final class MutableVersionAgentImpl extends StubAgentImpl {
        private volatile String appVersion = "0.0";

        void setAppVersion(String version) {
            this.appVersion = version;
        }

        @Override
        public ApplicationInformation getApplicationInformation() {
            return new ApplicationInformation("stub", appVersion, "stub", "1");
        }
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