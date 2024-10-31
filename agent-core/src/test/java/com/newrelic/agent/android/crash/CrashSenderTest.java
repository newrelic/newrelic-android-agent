/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.crash;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.internal.verification.VerificationModeFactory.atLeastOnce;

import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.test.stub.StubAnalyticsAttributeStore;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.HttpURLConnection;

import javax.net.ssl.HttpsURLConnection;

public class CrashSenderTest {
    private AgentConfiguration agentConfiguration;
    private Crash crash;
    private CrashSender crashSender;
    private TestCrashStore crashStore;

    @Before
    public void setUp() throws Exception {
        crashStore = spy(new TestCrashStore());

        agentConfiguration = new AgentConfiguration();
        agentConfiguration.setApplicationToken(CrashReporterTests.class.getSimpleName());
        agentConfiguration.setEnableAnalyticsEvents(true);
        agentConfiguration.setReportCrashes(false);
        agentConfiguration.setCrashStore(crashStore);
        agentConfiguration.setAnalyticsAttributeStore(new StubAnalyticsAttributeStore());

        CrashReporter.initialize(agentConfiguration);

        crash = new Crash(new RuntimeException("testStoreExistingCrashes"));
        crashSender = spy(new CrashSender(crash, agentConfiguration));
    }

    @Test
    public void testGetConnection() throws Exception {
        HttpURLConnection connection = crashSender.getConnection();

        Assert.assertEquals("Should set connection protocol", connection.getURL().getProtocol(), agentConfiguration.useSsl() ? "https" : "http");
        Assert.assertEquals("Should connect to crash collector service", connection.getURL().getHost(), agentConfiguration.getCrashCollectorHost());
        Assert.assertEquals("Should set content type in headers", connection.getRequestProperty("Content-Type"), "application/json");
        Assert.assertEquals("Should set app token in headers", connection.getRequestProperty(agentConfiguration.getAppTokenHeader()), agentConfiguration.getApplicationToken());
        Assert.assertEquals("Should set O/S name in headers", connection.getRequestProperty(agentConfiguration.getDeviceOsNameHeader()), Agent.getDeviceInformation().getOsName());
        Assert.assertEquals("Should set app version in headers", connection.getRequestProperty(agentConfiguration.getAppVersionHeader()), Agent.getApplicationInformation().getAppVersion());

        Assert.assertEquals("Should set connection timeout", connection.getConnectTimeout(), CrashSender.CRASH_COLLECTOR_TIMEOUT);
        Assert.assertEquals("Should set read timeout", connection.getReadTimeout(), CrashSender.CRASH_COLLECTOR_TIMEOUT);
    }

    @Test
    public void call() throws Exception {
        crash.incrementUploadCount();
        int preUploadCount = crash.getUploadCount();
        crashSender.call();
        Assert.assertTrue("Should increment upload count", preUploadCount < crash.getUploadCount());
    }

    @Test
    public void testRequestResponse() throws Exception {
        HttpURLConnection connection = getMockedConnection(crashSender);

        Mockito.doReturn(HttpsURLConnection.HTTP_OK).when(connection).getResponseCode();
        crashSender.onRequestResponse(connection);
        Assert.assertTrue("Should contain 200 supportability metric", StatsEngine.get().getStatsMap().containsKey(MetricNames.SUPPORTABILITY_CRASH_UPLOAD_TIME));

        Mockito.reset();
        Mockito.doReturn(HttpsURLConnection.HTTP_ACCEPTED).when(connection).getResponseCode();
        crashSender.onRequestResponse(connection);
        Assert.assertTrue("Should contain 202 supportability metric", StatsEngine.get().getStatsMap().containsKey(MetricNames.SUPPORTABILITY_CRASH_UPLOAD_TIME));

        Mockito.reset();
        Mockito.doReturn(HttpsURLConnection.HTTP_CREATED).when(connection).getResponseCode();
        crashSender.onRequestResponse(connection);
        Assert.assertTrue("Should contain 201 supportability metric", StatsEngine.get().getStatsMap().containsKey(MetricNames.SUPPORTABILITY_CRASH_FAILED_UPLOAD));

        Mockito.reset();
        Mockito.doReturn(HttpsURLConnection.HTTP_INTERNAL_ERROR).when(connection).getResponseCode();
        crashSender.onRequestResponse(connection);
        Assert.assertTrue("Should contain 500 supportability metric", StatsEngine.get().getStatsMap().containsKey(MetricNames.SUPPORTABILITY_CRASH_FAILED_UPLOAD));

        Mockito.reset();
        Mockito.doReturn(HttpsURLConnection.HTTP_BAD_REQUEST).when(connection).getResponseCode();
        crashSender.onRequestResponse(connection);
        verify(crashSender, atLeastOnce()).onFailedUpload(anyString());
    }

    @Test
    public void testFailedUpload() throws Exception {
        CrashSender crashSender = spy(new CrashSender(crash, agentConfiguration));

        crashSender.onFailedUpload("Upload failure");
        Assert.assertTrue("Should contain 500 supportability metric", StatsEngine.get().getStatsMap().containsKey(MetricNames.SUPPORTABILITY_CRASH_FAILED_UPLOAD));

    }

    @Test
    public void testRequestException() throws Exception {
        CrashSender crashSender = spy(new CrashSender(crash, agentConfiguration));
        Mockito.doReturn(null).when(crashSender).getConnection();
        crashSender.call();
        verify(crashSender, atLeastOnce()).onFailedUpload(any(String.class));
    }

    private HttpURLConnection getMockedConnection(CrashSender crashSender) throws IOException {
        HttpURLConnection connection = Mockito.spy(crashSender.getConnection());

        Mockito.doReturn(false).when(connection).getDoOutput();
        Mockito.doReturn(false).when(connection).getDoInput();
        Mockito.doReturn(HttpsURLConnection.HTTP_OK).when(connection).getResponseCode();
        Mockito.doNothing().when(connection).connect();

        return connection;
    }
}