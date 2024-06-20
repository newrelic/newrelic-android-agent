/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.logging;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.internal.verification.VerificationModeFactory.atLeastOnce;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.util.Streams;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import javax.net.ssl.HttpsURLConnection;

@Ignore("Until LogReporting GA")
public class LogForwarderTest extends LoggingTests {

    private File logDataReport;
    private LogForwarder logForwarder;

    @BeforeClass
    public static void beforeClass() throws Exception {
        LoggingTests.beforeClass();
    }

    @Before
    public void setUp() throws Exception {
        StatsEngine.reset();

        FeatureFlag.enableFeature(FeatureFlag.LogReporting);
        LogReporter.initialize(reportsDir, AgentConfiguration.getInstance());

        logDataReport = seedLogData(1, 12).iterator().next();
        logForwarder = Mockito.spy(new LogForwarder(logDataReport, AgentConfiguration.getInstance()));

        doReturn(Mockito.spy(logForwarder.getConnection())).when(logForwarder).getConnection();
    }

    @Test
    public void getPayload() throws IOException {
        Assert.assertTrue(Arrays.equals(Streams.readAllBytes(logDataReport), logForwarder.getPayload().getBytes()));
    }

    @Test
    public void getPayloadSize() {
        Assert.assertTrue(logForwarder.getPayload().getBytes().length == logDataReport.length());
    }

    @Test
    public void shouldUploadOpportunistically() throws Exception {
        Assert.assertTrue(logForwarder.shouldUploadOpportunistically());
        doReturn(false).when(logForwarder).shouldUploadOpportunistically();
        Assert.assertFalse(logForwarder.shouldUploadOpportunistically());
        Assert.assertFalse("Should not make upload request", logForwarder.call().isSuccessfulResponse());
    }

    @Test
    public void setPayload() throws IOException {
        String payloadData = "The load carried"; // by an aircraft or spacecraft consisting of people or " +
        // "things (such as passengers or instruments) necessary to the purpose of the flight.";

        logForwarder.setPayload(payloadData.getBytes(StandardCharsets.UTF_8));
        Assert.assertTrue(Arrays.equals(payloadData.getBytes(StandardCharsets.UTF_8), logForwarder.getPayload().getBytes()));
    }

    @Test
    public void shouldRetry() {
        Assert.assertTrue(logForwarder.shouldRetry());
    }

    @Test
    public void getConnection() throws IOException {
        Assert.assertTrue(logForwarder.getConnection() instanceof HttpURLConnection);
    }

    @Test
    public void onRequestResponse() throws IOException {
        HttpURLConnection connection = logForwarder.getConnection();
        doReturn(429).when(connection).getResponseCode();

        logForwarder.onRequestResponse(connection);
        Assert.assertTrue("Should contain 429 supportability metric",
                StatsEngine.SUPPORTABILITY.getStatsMap().containsKey(MetricNames.SUPPORTABILITY_LOG_UPLOAD_THROTTLED));

        verify(logForwarder, atLeastOnce()).onFailedUpload(anyString());
    }

    @Test
    public void onFailedUpload() throws IOException {
        HttpURLConnection connection = logForwarder.getConnection();
        doReturn(503).when(connection).getResponseCode();

        logForwarder.onRequestResponse(connection);
        Assert.assertTrue("Should contain filed upload supportability metric",
                StatsEngine.SUPPORTABILITY.getStatsMap().containsKey(MetricNames.SUPPORTABILITY_LOG_FAILED_UPLOAD));
    }

    @Test
    public void onRequestException() {
        Exception e = new RuntimeException("Upload failed");
        logForwarder.onRequestException(e);
        Assert.assertTrue("Should contain filed upload supportability metric",
                StatsEngine.SUPPORTABILITY.getStatsMap().containsKey(MetricNames.SUPPORTABILITY_LOG_FAILED_UPLOAD));
    }

    @Test
    public void uploadRequest() throws Exception {
        HttpURLConnection connection = logForwarder.getConnection();

        doReturn(HttpsURLConnection.HTTP_OK).when(connection).getResponseCode();
        logForwarder.onRequestResponse(connection);
        Assert.assertTrue("Should contain 200 supportability metric",
                StatsEngine.SUPPORTABILITY.getStatsMap().containsKey(MetricNames.SUPPORTABILITY_LOG_UPLOAD_TIME));

        Mockito.reset();
        StatsEngine.SUPPORTABILITY.getStatsMap().clear();
        doReturn(HttpsURLConnection.HTTP_ACCEPTED).when(connection).getResponseCode();
        logForwarder.onRequestResponse(connection);
        Assert.assertTrue("Should contain 202 supportability metric",
                StatsEngine.SUPPORTABILITY.getStatsMap().containsKey(MetricNames.SUPPORTABILITY_LOG_UPLOAD_TIME));

        Mockito.reset();
        StatsEngine.SUPPORTABILITY.getStatsMap().clear();
        doReturn(HttpsURLConnection.HTTP_CREATED).when(connection).getResponseCode();
        logForwarder.onRequestResponse(connection);
        Assert.assertFalse("Should not contain 202 supportability metric",
                StatsEngine.SUPPORTABILITY.getStatsMap().containsKey(MetricNames.SUPPORTABILITY_LOG_UPLOAD_TIME));
        Assert.assertTrue("Should contain 202 supportability metric",
                StatsEngine.SUPPORTABILITY.getStatsMap().containsKey(MetricNames.SUPPORTABILITY_LOG_FAILED_UPLOAD));
    }

    @Test
    public void failedUploadRequest() throws Exception {
        HttpURLConnection connection = logForwarder.getConnection();

        doReturn(HttpsURLConnection.HTTP_INTERNAL_ERROR).when(connection).getResponseCode();
        logForwarder.onRequestResponse(connection);
        Assert.assertTrue("Should contain 500 supportability metric",
                StatsEngine.SUPPORTABILITY.getStatsMap().containsKey(MetricNames.SUPPORTABILITY_LOG_REMOVED_REJECTED));
    }

    @Test
    public void timedOutUploadRequest() throws Exception {
        HttpURLConnection connection = logForwarder.getConnection();

        doReturn(HttpsURLConnection.HTTP_CLIENT_TIMEOUT).when(connection).getResponseCode();
        logForwarder.onRequestResponse(connection);
        Assert.assertTrue("Should contain 408 supportability metric",
                StatsEngine.SUPPORTABILITY.getStatsMap().containsKey(MetricNames.SUPPORTABILITY_LOG_UPLOAD_TIMEOUT));
    }

    @Test
    public void throttledUploadRequest() throws Exception {
        HttpURLConnection connection = logForwarder.getConnection();

        doReturn(429).when(connection).getResponseCode();
        logForwarder.onRequestResponse(connection);
        Assert.assertTrue("Should contain 429 supportability metric",
                StatsEngine.SUPPORTABILITY.getStatsMap().containsKey(MetricNames.SUPPORTABILITY_LOG_UPLOAD_THROTTLED));
    }

    @Test
    public void recordSupportabilityMetrics() throws Exception {
        HttpURLConnection connection = logForwarder.getConnection();

        when(connection.getResponseCode()).thenReturn(HttpsURLConnection.HTTP_OK);
        logForwarder.onRequestResponse(connection);
        Assert.assertTrue("Should contain 200 supportability metric", StatsEngine.SUPPORTABILITY.getStatsMap().containsKey(MetricNames.SUPPORTABILITY_LOG_UPLOAD_TIME));

        Mockito.reset();
        StatsEngine.SUPPORTABILITY.getStatsMap().clear();
        when(connection.getResponseCode()).thenReturn(HttpsURLConnection.HTTP_ACCEPTED);
        logForwarder.onRequestResponse(connection);
        Assert.assertTrue("Should contain 202 supportability metric", StatsEngine.SUPPORTABILITY.getStatsMap().containsKey(MetricNames.SUPPORTABILITY_LOG_UPLOAD_TIME));

        Mockito.reset();
        StatsEngine.SUPPORTABILITY.getStatsMap().clear();
        when(connection.getResponseCode()).thenReturn(HttpsURLConnection.HTTP_CREATED);
        logForwarder.onRequestResponse(connection);
        Assert.assertFalse("Should not contain 201 success supportability metric", StatsEngine.SUPPORTABILITY.getStatsMap().containsKey(MetricNames.SUPPORTABILITY_LOG_UPLOAD_TIME));

        logForwarder.onFailedUpload("boo");
        Assert.assertTrue("Should contain 500 supportability metric", StatsEngine.SUPPORTABILITY.getStatsMap().containsKey(MetricNames.SUPPORTABILITY_LOG_FAILED_UPLOAD));
    }
}
