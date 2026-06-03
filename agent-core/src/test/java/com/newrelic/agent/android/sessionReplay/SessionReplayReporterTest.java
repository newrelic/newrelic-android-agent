/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay;

import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.AgentImpl;
import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.logging.ConsoleAgentLog;
import com.newrelic.agent.android.payload.Payload;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.test.stub.StubAgentImpl;
import com.newrelic.agent.android.util.Constants;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class SessionReplayReporterTest {

    private AgentConfiguration agentConfiguration;
    private SessionReplayReporter reporter;
    private SessionReplayStore<String> mockStore;
    private OfflineSessionReplayStore mockOfflineStore;
    private AgentImpl previousAgentImpl;

    @BeforeClass
    public static void beforeClass() {
        AgentLogManager.setAgentLog(new ConsoleAgentLog());
        Agent.setImpl(new StubAgentImpl());
    }

    @Before
    public void setUp() throws Exception {
        StatsEngine.reset();
        FeatureFlag.enableFeature(FeatureFlag.HandledExceptions);

        previousAgentImpl = Agent.getImpl();

        agentConfiguration = Mockito.spy(new AgentConfiguration());
        mockStore = Mockito.mock(SessionReplayStore.class);
        mockOfflineStore = Mockito.mock(OfflineSessionReplayStore.class);

        Mockito.doReturn(mockStore).when(agentConfiguration).getSessionReplayStore();
        Mockito.doReturn(mockOfflineStore).when(agentConfiguration).getOfflineSessionReplayStore();

        SessionReplayReporter.shutdown();
        reporter = SessionReplayReporter.initialize(agentConfiguration);
    }

    @After
    public void tearDown() throws Exception {
        FeatureFlag.disableFeature(FeatureFlag.HandledExceptions);
        FeatureFlag.disableFeature(FeatureFlag.OfflineStorage);
        SessionReplayReporter.shutdown();
        StatsEngine.reset();
        Agent.setImpl(previousAgentImpl);
    }

    @Test
    public void testInitialize() {
        Assert.assertNotNull(reporter);
        Assert.assertEquals(reporter, SessionReplayReporter.getInstance());
    }

    @Test
    public void testGetInstance() {
        SessionReplayReporter instance = SessionReplayReporter.getInstance();
        Assert.assertNotNull(instance);
        Assert.assertSame(reporter, instance);
    }

    @Test
    public void testGetInstanceBeforeInitialization() {
        SessionReplayReporter.shutdown();
        Assert.assertNull(SessionReplayReporter.getInstance());
    }

    @Test
    public void testShutdown() {
        Assert.assertNotNull(SessionReplayReporter.getInstance());
        SessionReplayReporter.shutdown();
        Assert.assertNull(SessionReplayReporter.getInstance());
    }

    @Test
    public void testShutdownWhenNotInitialized() {
        SessionReplayReporter.shutdown();
        // safe to call again
        SessionReplayReporter.shutdown();
        Assert.assertNull(SessionReplayReporter.getInstance());
    }

    @Test
    public void testReportSessionReplayDataWhenInitialized() {
        byte[] testData = "test session replay data".getBytes();
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(Constants.SessionReplay.SESSION_ID, "test-session-id");
        attributes.put("firstTimestamp", 1609459200000L);
        attributes.put("lastTimestamp", 1609459202000L);
        attributes.put(Constants.SessionReplay.IS_FIRST_CHUNK, true);

        boolean result = SessionReplayReporter.reportSessionReplayData(testData, attributes);
        Assert.assertTrue(result);
    }

    @Test
    public void testReportSessionReplayDataWhenNotInitialized() {
        SessionReplayReporter.shutdown();

        byte[] testData = "test session replay data".getBytes();
        Map<String, Object> attributes = new HashMap<>();

        boolean result = SessionReplayReporter.reportSessionReplayData(testData, attributes);
        Assert.assertFalse(result);
    }

    @Test
    public void testReportSessionReplayDataWithEmptyData() {
        byte[] testData = new byte[0];
        Map<String, Object> attributes = new HashMap<>();

        boolean result = SessionReplayReporter.reportSessionReplayData(testData, attributes);
        Assert.assertTrue(result);
    }

    @Test
    public void testReportSessionReplayDataWithNullAttributes() {
        byte[] testData = "test data".getBytes();

        boolean result = SessionReplayReporter.reportSessionReplayData(testData, null);
        Assert.assertFalse(result);
    }

    @Test
    public void testReportSessionReplayDataWithEmptyAttributes() {
        byte[] testData = "test data".getBytes();
        Map<String, Object> attributes = new HashMap<>();

        boolean result = SessionReplayReporter.reportSessionReplayData(testData, attributes);
        Assert.assertTrue(result);
    }

    @Test
    public void testGzipCompress() throws Exception {
        byte[] uncompressedData = "This is test data that should be compressed".getBytes();
        Method method = SessionReplayReporter.class.getDeclaredMethod("gzipCompress", byte[].class);
        method.setAccessible(true);
        byte[] compressed = (byte[]) method.invoke(null, (Object) uncompressedData);

        Assert.assertNotNull(compressed);
        Assert.assertTrue(compressed.length > 0);
    }

    @Test
    public void testGzipCompressWithEmptyData() throws Exception {
        byte[] emptyData = new byte[0];
        Method method = SessionReplayReporter.class.getDeclaredMethod("gzipCompress", byte[].class);
        method.setAccessible(true);
        byte[] compressed = (byte[]) method.invoke(null, (Object) emptyData);

        Assert.assertNotNull(compressed);
        Assert.assertTrue(compressed.length > 0); // GZIP header is always present
    }

    @Test
    public void testStoreAndReportSessionReplayDataWithNullPayload() {
        Map<String, Object> attributes = new HashMap<>();
        try {
            reporter.storeAndReportSessionReplayData(null, attributes);
            Assert.fail("Should throw exception with null payload");
        } catch (Exception expected) {
            // Expected
        }
    }

    @Test
    public void testStartWhenPayloadControllerNotInitialized() {
        // PayloadController may not be initialized in test environment; should no-op safely.
        reporter.start();
        Assert.assertNotNull(reporter);
    }

    @Test
    public void testStop() {
        reporter.stop();
        Assert.assertNotNull(reporter);
    }

    @Test
    public void testMultipleInitializations() {
        SessionReplayReporter reporter1 = SessionReplayReporter.getInstance();
        SessionReplayReporter reporter2 = SessionReplayReporter.initialize(agentConfiguration);
        Assert.assertSame(reporter1, reporter2);
    }

    @Test
    public void testReportSessionReplayDataWithVeryLargeAttributes() {
        byte[] testData = "test data".getBytes();
        Map<String, Object> attributes = new HashMap<>();
        for (int i = 0; i < 100; i++) {
            attributes.put("key" + i, "value" + i);
        }
        boolean result = SessionReplayReporter.reportSessionReplayData(testData, attributes);
        Assert.assertTrue(result);
    }

    @Test
    public void testReportSessionReplayDataWithSpecialCharactersInAttributes() {
        byte[] testData = "test data".getBytes();
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("special_chars", "!@#$%^&*()_+-={}[]|:;<>?,./");
        attributes.put("unicode", "日本語");
        attributes.put("emoji", "😀🎉");

        boolean result = SessionReplayReporter.reportSessionReplayData(testData, attributes);
        Assert.assertTrue(result);
    }

    @Test
    public void testReportSessionReplayDataWithNullValuesInAttributes() {
        byte[] testData = "test data".getBytes();
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("nullKey", null);
        attributes.put(Constants.SessionReplay.SESSION_ID, "test-123");

        boolean result = SessionReplayReporter.reportSessionReplayData(testData, attributes);
        Assert.assertTrue(result);
    }

    @Test
    public void testReportSessionReplayDataWithDifferentDataTypes() {
        byte[] testData = "test data".getBytes();
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("string", "value");
        attributes.put("integer", 123);
        attributes.put("long", 123456789L);
        attributes.put("double", 123.456);
        attributes.put("boolean", true);

        boolean result = SessionReplayReporter.reportSessionReplayData(testData, attributes);
        Assert.assertTrue(result);
    }

    @Test
    public void testReporterIsEnabledBasedOnFeatureFlag() {
        // HandledExceptions is enabled in setUp.
        Assert.assertTrue(reporter.isEnabled());

        FeatureFlag.disableFeature(FeatureFlag.HandledExceptions);
        SessionReplayReporter.shutdown();
        SessionReplayReporter rebuilt = SessionReplayReporter.initialize(agentConfiguration);
        Assert.assertFalse(rebuilt.isEnabled());
    }

    // ---------------------------------------------------------------------
    // Offline cache drain — reportCachedSessionReplayData()
    // ---------------------------------------------------------------------

    @Test
    public void testReportCachedSessionReplay_noopWhenOfflineFlagDisabled() {
        FeatureFlag.disableFeature(FeatureFlag.OfflineStorage);
        Mockito.when(mockOfflineStore.count()).thenReturn(5);

        reporter.reportCachedSessionReplayData();

        // OfflineStorage flag gates the entire drain path.
        Mockito.verify(mockOfflineStore, Mockito.never()).count();
        Mockito.verify(mockOfflineStore, Mockito.never()).fetchAll();
        Mockito.verify(mockOfflineStore, Mockito.never()).delete(Mockito.any());
    }

    @Test
    public void testReportCachedSessionReplay_noopWhenOfflineStoreIsNull() {
        FeatureFlag.enableFeature(FeatureFlag.OfflineStorage);
        Mockito.doReturn(null).when(agentConfiguration).getOfflineSessionReplayStore();

        // Must not NPE when the offline store is unconfigured.
        reporter.reportCachedSessionReplayData();
    }

    @Test
    public void testReportCachedSessionReplay_noopWhenOfflineStoreEmpty() {
        FeatureFlag.enableFeature(FeatureFlag.OfflineStorage);
        Mockito.when(mockOfflineStore.count()).thenReturn(0);

        reporter.reportCachedSessionReplayData();

        Mockito.verify(mockOfflineStore).count();
        Mockito.verify(mockOfflineStore, Mockito.never()).fetchAll();
        Mockito.verify(mockOfflineStore, Mockito.never()).delete(Mockito.any());
    }

    @Test
    public void testReportCachedSessionReplay_skipsWhenNetworkUnreachable() {
        FeatureFlag.enableFeature(FeatureFlag.OfflineStorage);
        Mockito.when(mockOfflineStore.count()).thenReturn(2);
        Agent.setImpl(new UnreachableNetworkStubAgentImpl());

        reporter.reportCachedSessionReplayData();

        // Network-unreachable check happens after count(); fetchAll/delete must not run.
        Mockito.verify(mockOfflineStore, Mockito.never()).fetchAll();
        Mockito.verify(mockOfflineStore, Mockito.never()).delete(Mockito.any());
    }

    @Test
    public void testReportCachedSessionReplay_dropsStalePayloads() {
        FeatureFlag.enableFeature(FeatureFlag.OfflineStorage);

        long now = System.currentTimeMillis();
        long ttl = agentConfiguration.getPayloadTTL();
        // capturedAt older than TTL => stale.
        OfflineSessionReplayPayload stale = new OfflineSessionReplayPayload(
                "stale-uuid", now - ttl - 60_000L, now - ttl - 60_000L,
                Collections.<String, String>emptyMap(), new byte[]{1, 2, 3});

        Mockito.when(mockOfflineStore.count()).thenReturn(1);
        Mockito.when(mockOfflineStore.fetchAll()).thenReturn(Collections.singletonList(stale));

        reporter.reportCachedSessionReplayData();

        Mockito.verify(mockOfflineStore).delete(stale);
    }

    @Test
    public void testReportCachedSessionReplay_drainsStaleThenHaltsOnTransient() {
        // Verifies FIFO behavior: a stale entry at the head is dropped, then the loop
        // hits a fresh entry. Without an initialized PayloadController, sendCachedPayload
        // returns null which is treated as a transient failure -> break. Confirms the fresh
        // entry is NOT deleted (unlike the stale one).
        FeatureFlag.enableFeature(FeatureFlag.OfflineStorage);

        long now = System.currentTimeMillis();
        long ttl = agentConfiguration.getPayloadTTL();
        OfflineSessionReplayPayload stale = new OfflineSessionReplayPayload(
                "stale", now - ttl - 60_000L, now - ttl - 60_000L,
                Collections.<String, String>emptyMap(), new byte[]{1});
        OfflineSessionReplayPayload fresh = new OfflineSessionReplayPayload(
                "fresh", now, now,
                Collections.<String, String>emptyMap(), new byte[]{2});

        Mockito.when(mockOfflineStore.count()).thenReturn(2);
        Mockito.when(mockOfflineStore.fetchAll()).thenReturn(Arrays.asList(stale, fresh));

        reporter.reportCachedSessionReplayData();

        Mockito.verify(mockOfflineStore).delete(stale);
        Mockito.verify(mockOfflineStore, Mockito.never()).delete(fresh);
    }

    // ---------------------------------------------------------------------
    // Proactive offline persist on capture — reportSessionReplayData(Payload, Map)
    // ---------------------------------------------------------------------

    @Test
    public void testProactivePersist_whenOfflineFlagEnabledAndNetworkUnreachable() throws Exception {
        FeatureFlag.enableFeature(FeatureFlag.OfflineStorage);
        Agent.setImpl(new UnreachableNetworkStubAgentImpl());

        Payload payload = new Payload("test data".getBytes());
        Map<String, Object> attributes = freshAttributesMap();

        reporter.reportSessionReplayData(payload, attributes);

        Mockito.verify(mockOfflineStore).store(Mockito.any(OfflineSessionReplayPayload.class));
    }

    @Test
    public void testNoProactivePersist_whenOfflineFlagDisabled() throws Exception {
        // Flag disabled => the offline branch is skipped entirely, even if network is down.
        FeatureFlag.disableFeature(FeatureFlag.OfflineStorage);
        Agent.setImpl(new UnreachableNetworkStubAgentImpl());

        Payload payload = new Payload("test data".getBytes());
        Map<String, Object> attributes = freshAttributesMap();

        reporter.reportSessionReplayData(payload, attributes);

        Mockito.verify(mockOfflineStore, Mockito.never()).store(Mockito.any());
    }

    @Test
    public void testNoProactivePersist_whenOfflineStoreIsNull() throws Exception {
        FeatureFlag.enableFeature(FeatureFlag.OfflineStorage);
        Mockito.doReturn(null).when(agentConfiguration).getOfflineSessionReplayStore();
        Agent.setImpl(new UnreachableNetworkStubAgentImpl());

        Payload payload = new Payload("test data".getBytes());
        Map<String, Object> attributes = freshAttributesMap();

        // Must not NPE.
        reporter.reportSessionReplayData(payload, attributes);

        Mockito.verify(mockOfflineStore, Mockito.never()).store(Mockito.any());
    }

    // ---------------------------------------------------------------------
    // shouldPersistForRetry — pure response-code policy
    // ---------------------------------------------------------------------

    @Test
    public void testShouldPersistForRetry_responseCodes() throws Exception {
        // 0 = no roundtrip => persist (network failure)
        Assert.assertTrue(invokeShouldPersistForRetry(0));
        // 400, 403 = server reject => drop
        Assert.assertFalse(invokeShouldPersistForRetry(400));
        Assert.assertFalse(invokeShouldPersistForRetry(403));
        // 408, 429, 5xx = transient => persist
        Assert.assertTrue(invokeShouldPersistForRetry(408));
        Assert.assertTrue(invokeShouldPersistForRetry(429));
        Assert.assertTrue(invokeShouldPersistForRetry(500));
        Assert.assertTrue(invokeShouldPersistForRetry(502));
        Assert.assertTrue(invokeShouldPersistForRetry(599));
        // unknown / 2xx / 4xx other => conservative persist
        Assert.assertTrue(invokeShouldPersistForRetry(200));
        Assert.assertTrue(invokeShouldPersistForRetry(404));
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    private static Map<String, Object> freshAttributesMap() {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put(Constants.SessionReplay.SESSION_ID, "test-session-id");
        attributes.put("firstTimestamp", 1609459200000L);
        attributes.put("lastTimestamp", 1609459202000L);
        attributes.put(Constants.SessionReplay.IS_FIRST_CHUNK, true);
        return attributes;
    }

    private static boolean invokeShouldPersistForRetry(int code) throws Exception {
        Method m = SessionReplayReporter.class.getDeclaredMethod("shouldPersistForRetry", int.class);
        m.setAccessible(true);
        return (Boolean) m.invoke(null, code);
    }

    /**
     * Stub impl that reports the network as unreachable, regardless of host argument.
     * StubAgentImpl normally returns true for null hosts, which the caller passes.
     */
    private static class UnreachableNetworkStubAgentImpl extends StubAgentImpl {
        @Override
        public boolean hasReachableNetworkConnection(String reachableHost) {
            return false;
        }
    }
}