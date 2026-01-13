/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay;

import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.analytics.AnalyticsControllerImpl;
import com.newrelic.agent.android.harvest.HarvestConfiguration;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.logging.ConsoleAgentLog;
import com.newrelic.agent.android.metric.MetricNames;
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

public class SessionReplaySenderTest {

    private SessionReplaySender sender;
    private AgentConfiguration agentConfiguration;
    private HarvestConfiguration harvestConfiguration;
    private Payload payload;
    private Map<String, Object> replayDataMap;

    @BeforeClass
    public static void beforeClass() {
        AgentLogManager.setAgentLog(new ConsoleAgentLog());
        Agent.setImpl(new StubAgentImpl());
    }

    @Before
    public void setUp() throws Exception {
        StatsEngine.reset();

        agentConfiguration = AgentConfiguration.getInstance();
        agentConfiguration.setApplicationToken("TEST-TOKEN-12345");
        agentConfiguration.setCollectorHost("mobile-collector.newrelic.com");
        agentConfiguration.setEntityGuid("test-entity-guid");

        harvestConfiguration = HarvestConfiguration.getDefaultHarvestConfiguration();
        harvestConfiguration.setApplication_id("12345");

        byte[] testData = "test session replay data".getBytes();
        payload = new Payload(testData);

        replayDataMap = new HashMap<>();
        replayDataMap.put(Constants.SessionReplay.IS_FIRST_CHUNK, true);
        replayDataMap.put(Constants.SessionReplay.DECOMPRESSED_BYTES, 1000);
        replayDataMap.put(Constants.SessionReplay.SESSION_ID, "test-session-123");
        replayDataMap.put("firstTimestamp", 1609459200000L);
        replayDataMap.put("lastTimestamp", 1609459202000L);
        replayDataMap.put(Constants.SessionReplay.HAS_META, true);

        sender = Mockito.spy(new SessionReplaySender(payload, agentConfiguration, harvestConfiguration, replayDataMap));
    }

    @After
    public void tearDown() {
        StatsEngine.reset();
    }

    @Test
    public void testConstructorWithPayload() throws IOException {
        Assert.assertNotNull(sender);
        Assert.assertNotNull(sender.getPayload());
    }

    @Test
    public void testConstructorWithBytes() {
        byte[] testData = "test data".getBytes();
        SessionReplaySender byteSender = new SessionReplaySender(testData, agentConfiguration);
        Assert.assertNotNull(byteSender);
    }

    @Test
    public void testGetCollectorURI() {
        URI uri = sender.getCollectorURI();
        Assert.assertNotNull(uri);
        Assert.assertTrue(uri.toString().contains("mobile-collector.newrelic.com"));
        Assert.assertTrue(uri.toString().contains("/mobile/blobs"));
    }

    @Test
    public void testGetCollectorURIProtocol() {
        URI uri = sender.getCollectorURI();
        Assert.assertTrue(uri.toString().startsWith("https://"));
    }

    @Test
    public void testGetConnection() throws IOException {
        HttpURLConnection connection = sender.getConnection();

        Assert.assertNotNull(connection);
        Assert.assertEquals("POST", connection.getRequestMethod());
        Assert.assertTrue(connection.getDoOutput());
    }

    @Test
    public void testGetConnectionWithRequiredHeaders() throws IOException {
        HttpURLConnection connection = sender.getConnection();

        Map<String, java.util.List<String>> headers = connection.getRequestProperties();

        Assert.assertTrue(headers.containsKey(agentConfiguration.getAppTokenHeader()));
        Assert.assertTrue(headers.containsKey(Constants.Network.CONTENT_TYPE_HEADER));
        Assert.assertEquals(java.util.List.of(Constants.Network.ContentType.OCTET_STREAM),
                headers.get(Constants.Network.CONTENT_TYPE_HEADER));
    }

    @Test
    public void testGetConnectionWithSessionReplayAttributes() throws IOException {
        HttpURLConnection connection = sender.getConnection();
        String url = connection.getURL().toString();

        // Verify URL contains required parameters
        Assert.assertTrue(url.contains("type="));
        Assert.assertTrue(url.contains("protocol_version="));
        Assert.assertTrue(url.contains("timestamp="));
        Assert.assertTrue(url.contains("attributes="));
    }

    @Test
    public void testGetConnectionWithEntityGuid() throws IOException {
        HttpURLConnection connection = sender.getConnection();
        String url = connection.getURL().toString();

        Assert.assertTrue(url.contains("entityGuid"));
        Assert.assertTrue(url.contains("test-entity-guid"));
    }

    @Test
    public void testGetConnectionWithSessionAttributes() throws IOException {
        // Setup analytics controller with session attributes
        AnalyticsControllerImpl analyticsController = Mockito.mock(AnalyticsControllerImpl.class);
        Mockito.when(analyticsController.getSessionAttributes()).thenReturn(new HashSet<>());

        HttpURLConnection connection = sender.getConnection();
        Assert.assertNotNull(connection);
    }

    @Test
    public void testOnRequestResponseWithSuccess() throws IOException {
        HttpURLConnection connection = getMockedConnection();
        Mockito.doReturn(HttpsURLConnection.HTTP_OK).when(connection).getResponseCode();

        sender.onRequestResponse(connection);

        Assert.assertTrue(StatsEngine.get().getStatsMap().containsKey(MetricNames.SUPPORTABILITY_SESSION_REPLAY_UPLOAD_TIME));
    }

    @Test
    public void testOnRequestResponseWithAccepted() throws IOException {
        HttpURLConnection connection = getMockedConnection();
        Mockito.doReturn(HttpsURLConnection.HTTP_ACCEPTED).when(connection).getResponseCode();

        sender.onRequestResponse(connection);

        Assert.assertTrue(StatsEngine.get().getStatsMap().containsKey(MetricNames.SUPPORTABILITY_SESSION_REPLAY_UPLOAD_TIME));
    }

    @Test
    public void testOnRequestResponseWithTimeout() throws IOException {
        HttpURLConnection connection = getMockedConnection();
        Mockito.doReturn(HttpURLConnection.HTTP_CLIENT_TIMEOUT).when(connection).getResponseCode();

        sender.onRequestResponse(connection);

        Assert.assertTrue(StatsEngine.get().getStatsMap().containsKey(MetricNames.SUPPORTABILITY_SESSION_REPLAY_UPLOAD_TIMEOUT));
    }

    @Test
    public void testOnRequestResponseWithThrottled() throws IOException {
        HttpURLConnection connection = getMockedConnection();
        Mockito.doReturn(429).when(connection).getResponseCode();

        sender.onRequestResponse(connection);

        Assert.assertTrue(StatsEngine.get().getStatsMap().containsKey(MetricNames.SUPPORTABILITY_SESSION_REPLAY_UPLOAD_THROTTLED));
    }

    @Test
    public void testOnRequestResponseWithInternalError() throws IOException {
        HttpURLConnection connection = getMockedConnection();
        Mockito.doReturn(HttpsURLConnection.HTTP_INTERNAL_ERROR).when(connection).getResponseCode();

        sender.onRequestResponse(connection);

        Assert.assertTrue(StatsEngine.SUPPORTABILITY.getStatsMap().containsKey(MetricNames.SUPPORTABILITY_SESSION_REPLAY_UPLOAD_REJECTED));
    }

    @Test
    public void testOnRequestResponseWithForbidden() throws IOException {
        HttpURLConnection connection = getMockedConnection();
        Mockito.doReturn(HttpURLConnection.HTTP_FORBIDDEN).when(connection).getResponseCode();

        sender.onRequestResponse(connection);

        Assert.assertTrue(StatsEngine.get().getStatsMap().containsKey(MetricNames.SUPPORTABILITY_SESSION_REPLAY_FAILED_UPLOAD));
    }

    @Test
    public void testOnRequestResponseWithRequestTooLong() throws IOException {
        HttpURLConnection connection = getMockedConnection();
        Mockito.doReturn(HttpURLConnection.HTTP_REQ_TOO_LONG).when(connection).getResponseCode();

        sender.onRequestResponse(connection);

        Assert.assertTrue(StatsEngine.get().getStatsMap().containsKey(MetricNames.SUPPORTABILITY_SESSION_REPLAY_URL_SIZE_LIMIT_EXCEEDED));
    }

    @Test
    public void testOnRequestResponseWithUnknownCode() throws IOException {
        HttpURLConnection connection = getMockedConnection();
        Mockito.doReturn(999).when(connection).getResponseCode();

        sender.onRequestResponse(connection);

        // Should handle unknown codes gracefully
        Assert.assertNotNull(sender);
    }

    @Test
    public void testOnFailedUpload() {
        sender.onFailedUpload("Test error message");

        Assert.assertTrue(StatsEngine.get().getStatsMap().containsKey(MetricNames.SUPPORTABILITY_SESSION_REPLAY_FAILED_UPLOAD));
    }

//    @Test
//    public void testCallWithSuccessfulUpload() throws Exception {
//        Mockito.doReturn(true).when(sender).shouldUploadOpportunistically();
//
//        // Mock the parent call method to avoid actual network calls
//        Mockito.doReturn(sender).when(sender).call();
//
//        sender.call();
//
//        Mockito.verify(sender, Mockito.atLeastOnce()).shouldUploadOpportunistically();
//    }

//    @Test
//    public void testCallWithoutOpportunisticUpload() throws Exception {
//        Mockito.doReturn(false).when(sender).shouldUploadOpportunistically();
//
//        sender.call();
//
//        Mockito.verify(sender).shouldUploadOpportunistically();
//    }

    @Test
    public void testGetPayloadSize() {
        int size = sender.getPayloadSize();
        Assert.assertTrue(size > 0);
    }

    @Test
    public void testGetPayload() {
        Payload retrievedPayload = sender.getPayload();
        Assert.assertNotNull(retrievedPayload);
        Assert.assertEquals(payload.getUuid(), retrievedPayload.getUuid());
    }

    @Test
    public void testSetPayload() {
        byte[] newData = "new test data".getBytes();
        sender.setPayload(newData);

        Assert.assertEquals(newData.length, sender.getPayloadSize());
    }

    @Test
    public void testEncodeValueWithSpecialCharacters() throws Exception {
        // Use reflection to test the private encodeValue method
        java.lang.reflect.Method method = SessionReplaySender.class.getDeclaredMethod("encodeValue", String.class);
        method.setAccessible(true);

        String encoded = (String) method.invoke(sender, "test value with spaces");
        Assert.assertNotNull(encoded);
        Assert.assertFalse(encoded.contains(" "));
    }

    @Test
    public void testEncodeValueWithUnicodeCharacters() throws Exception {
        java.lang.reflect.Method method = SessionReplaySender.class.getDeclaredMethod("encodeValue", String.class);
        method.setAccessible(true);

        String encoded = (String) method.invoke(sender, "日本語");
        Assert.assertNotNull(encoded);
        Assert.assertFalse(encoded.equals("日本語")); // Should be URL encoded
    }

    @Test
    public void testReplayDataMapAttributes() {
        Assert.assertEquals(true, replayDataMap.get(Constants.SessionReplay.IS_FIRST_CHUNK));
        Assert.assertEquals(1000, replayDataMap.get(Constants.SessionReplay.DECOMPRESSED_BYTES));
        Assert.assertEquals("test-session-123", replayDataMap.get(Constants.SessionReplay.SESSION_ID));
        Assert.assertEquals(1609459200000L, replayDataMap.get("firstTimestamp"));
        Assert.assertEquals(1609459202000L, replayDataMap.get("lastTimestamp"));
    }

    @Test
    public void testReplayDataMapWithNullSessionId() throws IOException {
        replayDataMap.remove(Constants.SessionReplay.SESSION_ID);
        SessionReplaySender senderWithoutSessionId = new SessionReplaySender(
                payload, agentConfiguration, harvestConfiguration, replayDataMap);

        HttpURLConnection connection = senderWithoutSessionId.getConnection();
        Assert.assertNotNull(connection);
    }

    @Test
    public void testReplayDataMapWithMissingTimestamps() throws IOException {
        replayDataMap.remove("firstTimestamp");
        replayDataMap.remove("lastTimestamp");

        SessionReplaySender senderWithoutTimestamps = new SessionReplaySender(
                payload, agentConfiguration, harvestConfiguration, replayDataMap);

        HttpURLConnection connection = senderWithoutTimestamps.getConnection();
        Assert.assertNotNull(connection);
    }

    @Test
    public void testSuccessResponseRecordsMetrics() throws IOException {
        HttpURLConnection connection = getMockedConnection();
        Mockito.doReturn(HttpsURLConnection.HTTP_OK).when(connection).getResponseCode();

        sender.onRequestResponse(connection);

        // Should record upload time and compressed/uncompressed sizes
        Assert.assertTrue(StatsEngine.get().getStatsMap().containsKey(MetricNames.SUPPORTABILITY_SESSION_REPLAY_UPLOAD_TIME));
        Assert.assertTrue(StatsEngine.SUPPORTABILITY.getStatsMap().containsKey(MetricNames.SUPPORTABILITY_SESSION_REPLAY_COMPRESSED));
        Assert.assertTrue(StatsEngine.SUPPORTABILITY.getStatsMap().containsKey(MetricNames.SUPPORTABILITY_SESSION_REPLAY_UNCOMPRESSED));
    }

    @Test
    public void testConnectionURLFormat() throws IOException {
        HttpURLConnection connection = sender.getConnection();
        String url = connection.getURL().toString();

        // Verify URL format
        Assert.assertTrue(url.startsWith("https://"));
        Assert.assertTrue(url.contains("/mobile/blobs?"));
        Assert.assertTrue(url.contains("&"));
    }

    @Test
    public void testConnectionWithEmptyEntityGuid() throws IOException {
        agentConfiguration.setEntityGuid("");
        sender = new SessionReplaySender(payload, agentConfiguration, harvestConfiguration, replayDataMap);

        HttpURLConnection connection = sender.getConnection();
        Assert.assertNotNull(connection);
    }

    @Test
    public void testConnectionWithNullEntityGuid() throws IOException {
        agentConfiguration.setEntityGuid(null);
        sender = new SessionReplaySender(payload, agentConfiguration, harvestConfiguration, replayDataMap);

        HttpURLConnection connection = sender.getConnection();
        Assert.assertNotNull(connection);
    }

    // Helper method to create a mocked connection
    private HttpURLConnection getMockedConnection() throws IOException {
        HttpURLConnection connection = Mockito.mock(HttpURLConnection.class);

        Mockito.doReturn(false).when(connection).getDoOutput();
        Mockito.doReturn(false).when(connection).getDoInput();
        Mockito.doNothing().when(connection).connect();
        Mockito.doReturn(HttpsURLConnection.HTTP_OK).when(connection).getResponseCode();
        Mockito.doReturn(new ByteArrayOutputStream(1)).when(connection).getOutputStream();
        Mockito.doReturn(new ByteArrayInputStream("SessionReplaySenderTest".getBytes()))
                .when(connection).getInputStream();

        return connection;
    }
}