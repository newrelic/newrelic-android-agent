/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.aei;

import static com.newrelic.agent.android.aei.AEITraceSender.AEI_COLLECTOR_PATH;
import static org.mockito.Mockito.doReturn;
import static org.mockito.internal.verification.VerificationModeFactory.atLeastOnce;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.logging.ConsoleAgentLog;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.payload.FileBackedPayload;
import com.newrelic.agent.android.payload.Payload;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.util.Streams;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

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
    }

    @Before
    public void setUp() throws Exception {
        StatsEngine.reset();

        FeatureFlag.enableFeature(FeatureFlag.ApplicationExitReporting);
        AEITraceReporter.initialize(reportsDir, AgentConfiguration.getInstance());

        traceDataReport = seedTraceData(1).iterator().next();
        traceDataReport.setWritable(true);

        traceSender = Mockito.spy(new AEITraceSender(traceDataReport, AgentConfiguration.getInstance()));

        doReturn(Mockito.spy(traceSender.getConnection())).when(traceSender).getConnection();
    }

    @After
    public void tearDown() throws Exception {
        FeatureFlag.disableFeature(FeatureFlag.ApplicationExitReporting);
        Streams.list(AEITraceReporter.getInstance().traceStore).forEach(file -> file.delete());
        AEITraceReporter.getInstance().traceStore.delete();
        AEITraceReporter.instance.set(null);
    }

    @AfterClass
    public static void afterClass() throws Exception {
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
        Assert.assertTrue(traceSender.getConnection() instanceof HttpsURLConnection);
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
        HttpURLConnection connection = traceSender.getConnection();

        Mockito.doReturn(HttpsURLConnection.HTTP_OK).when(connection).getResponseCode();
        // when(connection.getResponseCode()).thenReturn(HttpsURLConnection.HTTP_OK);
        traceSender.onRequestResponse(connection);
/*      FIXME
        Assert.assertTrue("Should contain 200 supportability metric", StatsEngine.SUPPORTABILITY.getStatsMap().containsKey(MetricNames.SUPPORTABILITY_LOG_UPLOAD_TIME));
*/

        Mockito.reset();
        StatsEngine.SUPPORTABILITY.getStatsMap().clear();
        Mockito.doReturn(HttpsURLConnection.HTTP_ACCEPTED).when(connection).getResponseCode();
        traceSender.onRequestResponse(connection);
/*      FIXME
        Assert.assertTrue("Should contain 202 supportability metric", StatsEngine.SUPPORTABILITY.getStatsMap().containsKey(MetricNames.SUPPORTABILITY_LOG_UPLOAD_TIME));
*/

        Mockito.reset();
        StatsEngine.SUPPORTABILITY.getStatsMap().clear();
        Mockito.doReturn(HttpsURLConnection.HTTP_CREATED).when(connection).getResponseCode();
        traceSender.onRequestResponse(connection);
/*      FIXME
        Assert.assertFalse("Should not contain 201 success supportability metric", StatsEngine.SUPPORTABILITY.getStatsMap().containsKey(MetricNames.SUPPORTABILITY_LOG_UPLOAD_TIME));
*/

        traceSender.onFailedUpload("boo");
/*      FIXME
        Assert.assertTrue("Should contain 500 supportability metric", StatsEngine.SUPPORTABILITY.getStatsMap().containsKey(MetricNames.SUPPORTABILITY_LOG_FAILED_UPLOAD));
*/
        Mockito.verify(traceSender, atLeastOnce()).onFailedUpload(Mockito.anyString());
    }

    @Test
    public void onFailedUpload() throws IOException {
        HttpURLConnection connection = traceSender.getConnection();
        Mockito.doReturn(503).when(connection).getResponseCode();

        traceSender.onRequestResponse(connection);
        Assert.assertTrue("Should contain filed upload supportability metric",
                StatsEngine.SUPPORTABILITY.getStatsMap().containsKey(MetricNames.SUPPORTABILITY_LOG_FAILED_UPLOAD));
    }

    @Test
    public void onRequestException() {
        Exception e = new RuntimeException("Upload failed");
        traceSender.onRequestException(e);
        Assert.assertTrue("Should contain filed upload supportability metric",
                StatsEngine.SUPPORTABILITY.getStatsMap().containsKey(MetricNames.SUPPORTABILITY_LOG_FAILED_UPLOAD));
    }

    @Test
    public void uploadRequest() throws Exception {
        HttpURLConnection connection = traceSender.getConnection();

        Mockito.doReturn(HttpsURLConnection.HTTP_OK).when(connection).getResponseCode();
        traceSender.onRequestResponse(connection);
/*      FIXME
        Assert.assertTrue("Should contain 200 supportability metric",
                StatsEngine.SUPPORTABILITY.getStatsMap().containsKey(MetricNames.SUPPORTABILITY_CRASH_UPLOAD_TIME));
*/

        Mockito.reset();
        StatsEngine.SUPPORTABILITY.getStatsMap().clear();
        Mockito.doReturn(HttpsURLConnection.HTTP_ACCEPTED).when(connection).getResponseCode();
        traceSender.onRequestResponse(connection);
/*      FIXME
        Assert.assertTrue("Should contain 202 supportability metric",
                StatsEngine.SUPPORTABILITY.getStatsMap().containsKey(MetricNames.SUPPORTABILITY_CRASH_UPLOAD_TIME));
*/

        Mockito.reset();
        StatsEngine.SUPPORTABILITY.getStatsMap().clear();
        Mockito.doReturn(HttpsURLConnection.HTTP_CREATED).when(connection).getResponseCode();
        traceSender.onRequestResponse(connection);
/*      FIXME
        Assert.assertFalse("Should not contain 202 supportability metric",
                StatsEngine.SUPPORTABILITY.getStatsMap().containsKey(MetricNames.SUPPORTABILITY_CRASH_UPLOAD_TIME));
        Assert.assertTrue("Should contain 202 supportability metric",
                StatsEngine.SUPPORTABILITY.getStatsMap().containsKey(MetricNames.SUPPORTABILITY_CRASH_FAILED_UPLOAD));
*/
    }

    @Test
    public void failedUploadRequest() throws Exception {
        HttpURLConnection connection = traceSender.getConnection();

        Mockito.doReturn(HttpsURLConnection.HTTP_INTERNAL_ERROR).when(connection).getResponseCode();
        traceSender.onRequestResponse(connection);
/*      FIXME
        Assert.assertTrue("Should contain 500 supportability metric",
                StatsEngine.SUPPORTABILITY.getStatsMap().containsKey(MetricNames.SUPPORTABILITY_LOG_REMOVED_REJECTED));
*/
    }

    @Test
    public void timedOutUploadRequest() throws Exception {
        HttpURLConnection connection = traceSender.getConnection();

        Mockito.doReturn(HttpsURLConnection.HTTP_CLIENT_TIMEOUT).when(connection).getResponseCode();
        traceSender.onRequestResponse(connection);
/*      FIXME
        Assert.assertTrue("Should contain 408 supportability metric",
                StatsEngine.SUPPORTABILITY.getStatsMap().containsKey(MetricNames.SUPPORTABILITY_LOG_UPLOAD_TIMEOUT));
*/
    }

    @Test
    public void throttledUploadRequest() throws Exception {
        HttpURLConnection connection = traceSender.getConnection();

        doReturn(429).when(connection).getResponseCode();
        traceSender.onRequestResponse(connection);
/*      FIXME
        Assert.assertTrue("Should contain 429 supportability metric",
                StatsEngine.SUPPORTABILITY.getStatsMap().containsKey(MetricNames.SUPPORTABILITY_LOG_UPLOAD_THROTTLED));
*/
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
        Assert.assertEquals("https://" + AgentConfiguration.getInstance().getCollectorHost() + AEI_COLLECTOR_PATH, traceSender.getCollectorURI().toString());
    }

    @Test
    public void shouldRetry() {
        Assert.assertTrue(traceSender.shouldRetry());
    }

    @Test
    public void recordSupportabilityMetrics() throws Exception {
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


}