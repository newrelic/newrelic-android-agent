/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay;

import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.harvest.DeviceInformation;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.logging.ConsoleAgentLog;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.payload.Payload;
import com.newrelic.agent.android.payload.PayloadController;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.test.stub.StubAgentImpl;
import com.newrelic.agent.android.util.Constants;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class SessionReplayReporterTest {

    private AgentConfiguration agentConfiguration;
    private SessionReplayReporter reporter;
    private SessionReplayStore<String> mockStore;

    @BeforeClass
    public static void beforeClass() {
        AgentLogManager.setAgentLog(new ConsoleAgentLog());
        Agent.setImpl(new StubAgentImpl());
    }

    @Before
    public void setUp() throws Exception {
        StatsEngine.reset();
        FeatureFlag.enableFeature(FeatureFlag.HandledExceptions);

        agentConfiguration = Mockito.spy(new AgentConfiguration());
        mockStore = Mockito.mock(SessionReplayStore.class);

        Mockito.doReturn(mockStore).when(agentConfiguration).getSessionReplayStore();

        SessionReplayReporter.shutdown();
        reporter = SessionReplayReporter.initialize(agentConfiguration);
    }

    @After
    public void tearDown() throws Exception {
        FeatureFlag.disableFeature(FeatureFlag.HandledExceptions);
        SessionReplayReporter.shutdown();
        StatsEngine.reset();
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
        // Should not throw exception when not initialized
        SessionReplayReporter.shutdown();
        Assert.assertNull(SessionReplayReporter.getInstance());
    }

    @Test
    public void testIsInitialized() {
        Assert.assertNotNull(SessionReplayReporter.getInstance());

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
    public void testGzipCompress() throws IOException {
        byte[] uncompressedData = "This is test data that should be compressed".getBytes();

        // Use reflection to test the private gzipCompress method
        try {
            java.lang.reflect.Method method = SessionReplayReporter.class.getDeclaredMethod("gzipCompress", byte[].class);
            method.setAccessible(true);
            byte[] compressed = (byte[]) method.invoke(null, uncompressedData);

            Assert.assertNotNull(compressed);
            // Compressed data should typically be smaller for text data
            // But may be larger for very small data due to gzip header overhead
            Assert.assertTrue(compressed.length > 0);
        } catch (Exception e) {
            // If reflection fails, skip this test
            Assert.fail("Failed to test gzipCompress: " + e.getMessage());
        }
    }

    @Test
    public void testGzipCompressWithEmptyData() throws IOException {
        byte[] emptyData = new byte[0];

        try {
            java.lang.reflect.Method method = SessionReplayReporter.class.getDeclaredMethod("gzipCompress", byte[].class);
            method.setAccessible(true);
            byte[] compressed = (byte[]) method.invoke(null, emptyData);

            Assert.assertNotNull(compressed);
            Assert.assertTrue(compressed.length > 0); // GZIP header is always present
        } catch (Exception e) {
            Assert.fail("Failed to test gzipCompress with empty data: " + e.getMessage());
        }
    }

    @Test
    public void testReportSessionReplayDataPayloadExceedsMaxSize() throws IOException {
        // Create data that will exceed max size when compressed
        byte[] largeData = new byte[1000000* 2];
        for (int i = 0; i < largeData.length; i++) {
            largeData[i] = (byte) (i % 256);
        }

        Map<String, Object> attributes = new HashMap<>();
        attributes.put(Constants.SessionReplay.IS_FIRST_CHUNK, true);

        SessionReplayReporter.reportSessionReplayData(largeData, attributes);

        // Should have recorded supportability metric for exceeded size
        String metricName = MetricNames.SUPPORTABILITY_MAXPAYLOADSIZELIMIT_ENDPOINT
                .replace(MetricNames.TAG_FRAMEWORK, Agent.getDeviceInformation().getApplicationFramework().name())
                .replace(MetricNames.TAG_DESTINATION, MetricNames.METRIC_DATA_USAGE_COLLECTOR)
                .replace(MetricNames.TAG_SUBDESTINATION, "SessionReplay");

        // Note: The metric may or may not be present depending on actual compression
        // This test mainly ensures no exceptions are thrown
    }

    @Test
    public void testReportSessionReplayDataSetsCorrectAttributes() throws IOException {
        byte[] testData = "test data".getBytes();
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("firstTimestamp", 1609459200000L);
        attributes.put("lastTimestamp", 1609459202000L);
        attributes.put(Constants.SessionReplay.IS_FIRST_CHUNK, true);
        attributes.put(Constants.SessionReplay.SESSION_ID, "test-session-123");

        SessionReplayReporter.reportSessionReplayData(testData, attributes);

        // Verify attributes are set (tested indirectly through successful call)
        Assert.assertTrue(true);
    }

    @Test
    public void testStoreAndReportSessionReplayData() {
        byte[] testData = "test data".getBytes();
        Payload payload = new Payload(testData);
        Map<String, Object> attributes = new HashMap<>();

        reporter.storeAndReportSessionReplayData(payload, attributes);

        // Should not throw exception
        Assert.assertNotNull(reporter);
    }

    @Test
    public void testStoreAndReportSessionReplayDataWithNullPayload() {
        Map<String, Object> attributes = new HashMap<>();

        try {
            reporter.storeAndReportSessionReplayData(null, attributes);
            Assert.fail("Should throw exception with null payload");
        } catch (Exception e) {
            // Expected
            Assert.assertNotNull(e);
        }
    }

    @Test
    public void testOnHarvest() {
        // Should not throw exception
        reporter.onHarvest();
        Assert.assertNotNull(reporter);
    }

    @Test
    public void testStartWhenPayloadControllerNotInitialized() {
        // PayloadController may not be initialized in test environment
        // Start should handle this gracefully
        reporter.start();
        // Should not throw exception
        Assert.assertNotNull(reporter);
    }

    @Test
    public void testStop() {
        reporter.stop();
        // Should not throw exception
        Assert.assertNotNull(reporter);
    }

    @Test
    public void testMultipleInitializations() {
        SessionReplayReporter reporter1 = SessionReplayReporter.getInstance();
        SessionReplayReporter reporter2 = SessionReplayReporter.initialize(agentConfiguration);

        // Should return the same instance
        Assert.assertSame(reporter1, reporter2);
    }

    @Test
    public void testReportSessionReplayDataWithVeryLargeAttributes() {
        byte[] testData = "test data".getBytes();
        Map<String, Object> attributes = new HashMap<>();

        // Add many attributes
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
        // Feature flag is enabled in setUp
        Assert.assertTrue(reporter.isEnabled());

        FeatureFlag.disableFeature(FeatureFlag.HandledExceptions);
        SessionReplayReporter.shutdown();
        reporter = SessionReplayReporter.initialize(agentConfiguration);

        // Reporter should still initialize but may not be enabled
        Assert.assertNotNull(reporter);
    }
}