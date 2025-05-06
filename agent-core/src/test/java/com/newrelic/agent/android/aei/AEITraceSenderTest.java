/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.aei;

import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.harvest.Harvest;
import com.newrelic.agent.android.harvest.HarvestConfiguration;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.logging.ConsoleAgentLog;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.payload.FileBackedPayload;
import com.newrelic.agent.android.payload.Payload;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.test.stub.StubAgentImpl;
import com.newrelic.agent.android.util.Constants;
import com.newrelic.agent.android.util.Streams;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import javax.net.ssl.HttpsURLConnection;

public class AEITraceSenderTest {

    private static File reportsDir;
    private AEITraceSender traceSender;
    private File traceDataReport;

    @BeforeClass
    public static void beforeClass() throws Exception {
        reportsDir = Files.createTempDirectory("ApplicationExitInfo-").toFile();
        reportsDir.mkdirs();

        AgentLogManager.setAgentLog(new ConsoleAgentLog());
        Agent.setImpl(new StubAgentImpl());
    }

    @Before
    public void setUp() throws Exception {
        StatsEngine.reset();

        FeatureFlag.enableFeature(FeatureFlag.ApplicationExitReporting);
        AEITraceReporter.initialize(reportsDir, AgentConfiguration.getInstance());

        traceDataReport = seedTraceData(1).iterator().next();
        traceDataReport.setWritable(true);

        traceSender = Mockito.spy(new AEITraceSender(traceDataReport, AgentConfiguration.getInstance()));

    }

    @After
    public void tearDown() throws Exception {
        FeatureFlag.disableFeature(FeatureFlag.ApplicationExitReporting);
        Streams.list(AEITraceReporter.getInstance().traceStore).forEach(file -> file.delete());
        AEITraceReporter.getInstance().traceStore.delete();
        AEITraceReporter.instance.set(null);
    }

    @AfterClass
    public static void afterClass() {
        Assert.assertTrue(reportsDir.delete());
    }

    @Test
    public void aeiSenderFromString() throws IOException {
        String sysTrace = Streams.slurpString(AEITraceTest.class.getResource("/applicationExitInfo/systrace").openStream());
        traceSender = new AEITraceSender(sysTrace, AgentConfiguration.getInstance());
        Assert.assertTrue(traceSender.getPayload() instanceof Payload);
    }

    @Test
    public void aeiSenderFromFile() {
        traceSender = new AEITraceSender(traceDataReport, AgentConfiguration.getInstance());
        Assert.assertTrue(traceSender.getPayload() instanceof FileBackedPayload);
    }

    @Test
    public void getConnection() throws IOException {
        final HarvestConfiguration harvestConfiguration = Harvest.getHarvestConfiguration();
        harvestConfiguration.setEntity_guid("!-am_a.baaD-entity");

        final AgentConfiguration agentConfiguration = AgentConfiguration.getInstance();
        agentConfiguration.setApplicationToken("DEAD-BEEF_BAAD-F00D");

        HttpURLConnection conn = traceSender.getConnection();
        Assert.assertTrue(conn instanceof HttpsURLConnection);
        Assert.assertTrue(conn.getDoInput());
        Assert.assertTrue(conn.getDoOutput());
        Assert.assertEquals("POST", conn.getRequestMethod());
        Assert.assertEquals(AEITraceSender.COLLECTOR_TIMEOUT, conn.getReadTimeout());
        Assert.assertEquals(AEITraceSender.COLLECTOR_TIMEOUT, conn.getConnectTimeout());

        Map<String, List<String>> requestProperties = conn.getRequestProperties();
        Assert.assertTrue(requestProperties.containsKey(Constants.Network.CONTENT_ENCODING_HEADER));
        Assert.assertTrue(requestProperties.containsKey(Constants.Network.CONTENT_TYPE_HEADER));
        Assert.assertTrue(requestProperties.containsKey(Constants.Network.APPLICATION_LICENSE_HEADER));
        Assert.assertTrue(requestProperties.containsKey(Constants.Network.ACCOUNT_ID_HEADER));
        Assert.assertTrue(requestProperties.containsKey(Constants.Network.TRUSTED_ACCOUNT_ID_HEADER));
        Assert.assertTrue(requestProperties.containsKey(Constants.Network.ENTITY_GUID_HEADER));
        Assert.assertTrue(requestProperties.containsKey(Constants.Network.DEVICE_OS_NAME_HEADER));
        Assert.assertTrue(requestProperties.containsKey(Constants.Network.APP_VERSION_HEADER));

        Assert.assertNotNull(requestProperties.get(Constants.Network.ENTITY_GUID_HEADER));
        Assert.assertNotEquals(List.of(""), requestProperties.get(Constants.Network.ENTITY_GUID_HEADER));

        Assert.assertEquals(List.of(Constants.Network.Encoding.IDENTITY), requestProperties.get(Constants.Network.CONTENT_ENCODING_HEADER));
        Assert.assertEquals(List.of(Constants.Network.ContentType.JSON), requestProperties.get(Constants.Network.CONTENT_TYPE_HEADER));
        Assert.assertEquals(List.of(agentConfiguration.getApplicationToken()), requestProperties.get(Constants.Network.APPLICATION_LICENSE_HEADER));
        Assert.assertEquals(List.of(harvestConfiguration.getAccount_id()), requestProperties.get(Constants.Network.ACCOUNT_ID_HEADER));
        Assert.assertEquals(List.of(harvestConfiguration.getTrusted_account_key()), requestProperties.get(Constants.Network.TRUSTED_ACCOUNT_ID_HEADER));
        Assert.assertEquals(List.of(harvestConfiguration.getEntity_guid()), requestProperties.get(Constants.Network.ENTITY_GUID_HEADER));
        Assert.assertEquals(List.of(Agent.getDeviceInformation().getOsName()), requestProperties.get(Constants.Network.DEVICE_OS_NAME_HEADER));
        Assert.assertEquals(List.of(Agent.getApplicationInformation().getAppVersion()), requestProperties.get(Constants.Network.APP_VERSION_HEADER));

        // verify the headers passed on the harvest config as well:
        Assert.assertNotNull(harvestConfiguration.getRequest_headers_map());
        harvestConfiguration.getRequest_headers_map().forEach(new BiConsumer<String, String>() {
            @Override
            public void accept(String s, String s2) {
                Assert.assertTrue(requestProperties.containsKey(s));
            }
        });
    }

    @Test
    public void getPayload() throws IOException {
        Assert.assertTrue(Arrays.equals(Streams.readAllBytes(traceDataReport), traceSender.getPayload().getBytes()));
    }

    @Test
    public void setPayload() {
        String payloadData = "the part of a vehicle's load, especially an aircraft's, from which revenue is derived; passengers and cargo.";
        traceSender.setPayload(payloadData.getBytes(StandardCharsets.UTF_8));
        Assert.assertTrue(Arrays.equals(payloadData.getBytes(StandardCharsets.UTF_8), traceSender.getPayload().getBytes()));
    }

    @Test
    public void getPayloadSize() {
        Assert.assertTrue(traceSender.getPayload().getBytes().length == traceDataReport.length());
    }

    @Test
    public void call() {
    }

    @Test
    public void onRequestResponse() throws IOException {
        HttpURLConnection connection = getMockedConnection(traceSender);

        Mockito.doReturn(HttpsURLConnection.HTTP_OK).when(connection).getResponseCode();
        traceSender.onRequestResponse(connection);
        Assert.assertTrue("Should contain 200 supportability metric", StatsEngine.SUPPORTABILITY.getStatsMap().containsKey(MetricNames.SUPPORTABILITY_AEI_UPLOAD_TIME));

        Mockito.reset();
        StatsEngine.SUPPORTABILITY.getStatsMap().clear();
        Mockito.doReturn(HttpsURLConnection.HTTP_ACCEPTED).when(connection).getResponseCode();
        traceSender.onRequestResponse(connection);
        Assert.assertTrue("Should contain 202 supportability metric", StatsEngine.SUPPORTABILITY.getStatsMap().containsKey(MetricNames.SUPPORTABILITY_AEI_UPLOAD_TIME));

        Mockito.reset();
        StatsEngine.SUPPORTABILITY.getStatsMap().clear();
        Mockito.doReturn(HttpsURLConnection.HTTP_CREATED).when(connection).getResponseCode();
        traceSender.onRequestResponse(connection);
        Assert.assertFalse("Should not contain 201 success supportability metric", StatsEngine.SUPPORTABILITY.getStatsMap().containsKey(MetricNames.SUPPORTABILITY_AEI_UPLOAD_TIME));

        traceSender.onFailedUpload("boo");
        Assert.assertTrue("Should contain 500 supportability metric", StatsEngine.SUPPORTABILITY.getStatsMap().containsKey(MetricNames.SUPPORTABILITY_AEI_FAILED_UPLOAD));
        Mockito.verify(traceSender, Mockito.atLeastOnce()).onFailedUpload(Mockito.anyString());
    }

    @Test
    public void onFailedUpload() throws IOException {
        HttpURLConnection connection = getMockedConnection(traceSender);
        Mockito.doReturn(503).when(connection).getResponseCode();

        traceSender.onRequestResponse(connection);
        Assert.assertTrue("Should contain filed upload supportability metric",
                StatsEngine.SUPPORTABILITY.getStatsMap().containsKey(MetricNames.SUPPORTABILITY_AEI_FAILED_UPLOAD));
    }

    @Test
    public void onRequestException() {
        Exception e = new RuntimeException("Upload failed");
        traceSender.onRequestException(e);
        Assert.assertTrue("Should contain filed upload supportability metric",
                StatsEngine.SUPPORTABILITY.getStatsMap().containsKey(MetricNames.SUPPORTABILITY_AEI_FAILED_UPLOAD));
    }

    @Test
    public void uploadRequest() throws Exception {
        HttpURLConnection connection = getMockedConnection(traceSender);

        Mockito.doReturn(HttpsURLConnection.HTTP_OK).when(connection).getResponseCode();
        traceSender.onRequestResponse(connection);
        Assert.assertTrue("Should contain 200 supportability metric",
                StatsEngine.SUPPORTABILITY.getStatsMap().containsKey(MetricNames.SUPPORTABILITY_AEI_UPLOAD_TIME));

        Mockito.reset();
        StatsEngine.SUPPORTABILITY.getStatsMap().clear();
        Mockito.doReturn(HttpsURLConnection.HTTP_ACCEPTED).when(connection).getResponseCode();
        traceSender.onRequestResponse(connection);
        Assert.assertTrue("Should contain 202 supportability metric",
                StatsEngine.SUPPORTABILITY.getStatsMap().containsKey(MetricNames.SUPPORTABILITY_AEI_UPLOAD_TIME));

        Mockito.reset();
        StatsEngine.SUPPORTABILITY.getStatsMap().clear();
        Mockito.doReturn(HttpsURLConnection.HTTP_CREATED).when(connection).getResponseCode();
        traceSender.onRequestResponse(connection);
        Assert.assertFalse("Should contain 201 supportability metric",
                StatsEngine.SUPPORTABILITY.getStatsMap().containsKey(MetricNames.SUPPORTABILITY_AEI_UPLOAD_TIME));
    }

    @Test
    public void failedUploadRequest() throws Exception {
        HttpURLConnection connection = getMockedConnection(traceSender);

        Mockito.doReturn(HttpsURLConnection.HTTP_INTERNAL_ERROR).when(connection).getResponseCode();
        traceSender.onRequestResponse(connection);
        Mockito.verify(traceSender, Mockito.times(1)).onFailedUpload(Mockito.anyString());
    }

    @Test
    public void timedOutUploadRequest() throws Exception {
        HttpURLConnection connection = getMockedConnection(traceSender);

        Mockito.doReturn(HttpsURLConnection.HTTP_CLIENT_TIMEOUT).when(connection).getResponseCode();
        traceSender.onRequestResponse(connection);
        Mockito.verify(traceSender, Mockito.times(1)).onFailedUpload(Mockito.anyString());
    }

    @Test
    public void rejectedUploadRequest() throws Exception {
        HttpURLConnection connection = getMockedConnection(traceSender);

        Mockito.doReturn(HttpsURLConnection.HTTP_ENTITY_TOO_LARGE).when(connection).getResponseCode();
        traceSender.onRequestResponse(connection);
        Mockito.verify(traceSender, Mockito.times(1)).onFailedUpload(Mockito.anyString());

        Assert.assertTrue(((FileBackedPayload) traceSender.getPayload()).isCompressed());
        Assert.assertTrue(FileBackedPayload.isCompressed(traceDataReport));
    }

    @Test
    public void throttledUploadRequest() throws Exception {
        HttpURLConnection connection = getMockedConnection(traceSender);

        Mockito.doReturn(429).when(connection).getResponseCode();
        traceSender.onRequestResponse(connection);
        Mockito.verify(traceSender, Mockito.times(1)).onFailedUpload(Mockito.anyString());
    }

    @Test
    public void shouldUploadOpportunistically() {
        Assert.assertTrue(traceSender.shouldUploadOpportunistically());
        Mockito.doReturn(false).when(traceSender).shouldUploadOpportunistically();
        Assert.assertFalse(traceSender.shouldUploadOpportunistically());
        Assert.assertFalse("Should not make upload request", traceSender.call().isSuccessfulResponse());
    }

    @Test
    public void getCollectorURI() {
        Assert.assertEquals("https://" + AgentConfiguration.getInstance().getCollectorHost() + AEITraceSender.AEI_COLLECTOR_PATH, traceSender.getCollectorURI().toString());
    }

    @Test
    public void shouldRetry() {
        Assert.assertTrue(traceSender.shouldRetry());
    }

    @Test
    public void compressedPayload() throws IOException {
        Assert.assertTrue(traceSender.getPayload() instanceof FileBackedPayload);
        FileBackedPayload payload = (FileBackedPayload) traceSender.getPayload();
        Assert.assertFalse(payload.isCompressed());
        long payloadSize = payload.size();
        File compressedPayloadFile = payload.compress(false);
        Assert.assertTrue(compressedPayloadFile.exists());
        Assert.assertTrue(compressedPayloadFile.getAbsolutePath().endsWith(".gz"));
        Assert.assertFalse(payload.isCompressed());
        Assert.assertTrue(payloadSize > compressedPayloadFile.length());
        Assert.assertTrue(payloadSize == payload.size());

        File replacementPayloadFile = payload.compress(traceDataReport, true);
        payload = (FileBackedPayload) traceSender.getPayload();
        Assert.assertEquals(traceDataReport, replacementPayloadFile);
        Assert.assertTrue(payload.isCompressed());
        Assert.assertTrue(payloadSize > payload.size());
    }

    Set<File> seedTraceData(int numFiles) throws Exception {
        final HashSet<File> reportSet = new HashSet<>();
        final AEITraceReporter traceReporter = AEITraceReporter.getInstance();
        String sysTrace = Streams.slurpString(AEITraceTest.class.getResource("/applicationExitInfo/systrace").openStream());

        for (int file = 0; file < numFiles; file++) {
            File traceFile = traceReporter.generateUniqueDataFilename((int) (Math.random() * 10000) + 1);
            traceReporter.reportAEITrace(sysTrace, traceFile);
            reportSet.add(traceFile);
        }

        return reportSet;
    }

    private HttpURLConnection getMockedConnection(AEITraceSender traceSender) throws IOException {
        HttpURLConnection connection = Mockito.spy(traceSender.getConnection());

        Mockito.doReturn(false).when(connection).getDoOutput();
        Mockito.doReturn(false).when(connection).getDoInput();
        Mockito.doNothing().when(connection).connect();
        Mockito.doReturn(HttpsURLConnection.HTTP_OK).when(connection).getResponseCode();
        Mockito.doReturn(new ByteArrayOutputStream(1)).when(connection).getOutputStream();
        Mockito.doReturn(new ByteArrayInputStream("AEITraceSenderTest error:".getBytes(StandardCharsets.UTF_8)))
                .when(connection).getInputStream();

        return connection;
    }

}