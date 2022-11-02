/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.agentdata;

import com.google.flatbuffers.FlatBufferBuilder;
import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.agentdata.builder.AgentDataBuilder;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.test.stub.StubAnalyticsAttributeStore;
import com.newrelic.agent.android.util.Constants;
import com.newrelic.agent.android.util.NamedThreadFactory;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HttpsURLConnection;

import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.internal.verification.VerificationModeFactory.atLeastOnce;

public class AgentDataSenderTest {
    private static ExecutorService executor = Executors.newCachedThreadPool(new NamedThreadFactory("agentDataUploader"));
    private static AgentConfiguration agentConfiguration;

    private FlatBufferBuilder flat;

    @Before
    public void setUp() throws Exception {

        agentConfiguration = new AgentConfiguration();
        agentConfiguration.setApplicationToken(AgentDataSender.class.getSimpleName());
        agentConfiguration.setEnableAnalyticsEvents(true);
        agentConfiguration.setReportCrashes(true);
        agentConfiguration.setReportHandledExceptions(true);
        agentConfiguration.setAnalyticsAttributeStore(new StubAnalyticsAttributeStore());
        agentConfiguration.setUseSsl(false);

        final Map<String, Object> sessionAttributes = new HashMap<String, Object>() {{
            put("a string", "hello");
            put("dbl", 666.6666666);
            put("lng", 12323435456463233L);
            put("yes", true);
            put("int", 3);
        }};

        final Map<String, Object> hex = new HashMap<String, Object>() {{
            put(HexAttribute.HEX_ATTR_SESSION_ID, "bad-beef");
            put(HexAttribute.HEX_ATTR_CLASS_NAME, "HexDemo");
            put(HexAttribute.HEX_ATTR_METHOD_NAME, "demo");
            put(HexAttribute.HEX_ATTR_LINE_NUMBER, 100L);
            put(HexAttribute.HEX_ATTR_TIMESTAMP_MS, System.currentTimeMillis());
            put(HexAttribute.HEX_ATTR_NAME, "NullPointerException");
            put(HexAttribute.HEX_ATTR_MESSAGE, "Handled");
            put(HexAttribute.HEX_ATTR_CAUSE, "idk");
        }};

        UUID buildUuid = UUID.fromString("aaa27edf-ed2c-0ed6-60c9-a6e3be5d403b");
        hex.put("appUuidHigh", buildUuid.getMostSignificantBits());
        hex.put("appUuidLow", buildUuid.getLeastSignificantBits());

        Set<Map<String, Object>> set = new HashSet<>();
        set.add(hex);

        flat = AgentDataBuilder.startAndFinishAgentData(sessionAttributes, set);
    }

    @Test
    public void run() throws Exception {
        AgentDataSender agentDataSender = new AgentDataSender(flat.dataBuffer().slice().array(), agentConfiguration);
        executor.submit(agentDataSender);
        //without a local collector to point at this is expected to fail
        boolean executed = executor.awaitTermination(agentConfiguration.getHexCollectorTimeout(), TimeUnit.MILLISECONDS);
        assertFalse(executed);
    }

    @Test
    public void testGetConnection() throws Exception {
        AgentDataSender agentDataSender = new AgentDataSender(flat.dataBuffer().slice().array(), agentConfiguration);

        HttpURLConnection connection = agentDataSender.getConnection();
        Assert.assertEquals("Should set connection protocol", connection.getURL().getProtocol(), agentConfiguration.useSsl() ? "https" : "http");
        Assert.assertEquals("Should connect to flatbuffer service", connection.getURL().getHost(), agentConfiguration.getHexCollectorHost());
        Assert.assertEquals("Should set content type in headers", connection.getRequestProperty(Constants.Network.CONTENT_TYPE_HEADER), Constants.Network.ContentType.OCTET_STREAM);
        Assert.assertEquals("Should set app token in headers", connection.getRequestProperty(agentConfiguration.getAppTokenHeader()), agentConfiguration.getApplicationToken());
        Assert.assertEquals("Should set O/S name in headers", connection.getRequestProperty(agentConfiguration.getDeviceOsNameHeader()), Agent.getDeviceInformation().getOsName());
        Assert.assertEquals("Should set app version in headers", connection.getRequestProperty(agentConfiguration.getAppVersionHeader()), Agent.getApplicationInformation().getAppVersion());

        Assert.assertEquals("Should set connection timeout", connection.getConnectTimeout(), agentConfiguration.getHexCollectorTimeout());
        Assert.assertEquals("Should set read timeout", connection.getReadTimeout(), agentConfiguration.getHexCollectorTimeout());
    }

    /**
     * If this test fails, it could be due to Charles running with macOS proxy enabled. beware
     *
     * @throws Exception
     */
    @Test
    public void testRequestResponse() throws Exception {
        AgentDataSender agentDataSender = spy(new AgentDataSender(flat.dataBuffer().slice().array(), agentConfiguration));
        HttpURLConnection connection = spy(agentDataSender.getConnection());

        when(connection.getResponseCode()).thenReturn(HttpsURLConnection.HTTP_OK);
        agentDataSender.onRequestResponse(connection);
        Assert.assertTrue("Should contain 200 supportability metric", StatsEngine.get().getStatsMap().containsKey(MetricNames.SUPPORTABILITY_HEX_UPLOAD_TIME));

        Mockito.reset();
        StatsEngine.get().getStatsMap().clear();
        when(connection.getResponseCode()).thenReturn(HttpsURLConnection.HTTP_ACCEPTED);
        agentDataSender.onRequestResponse(connection);
        Assert.assertTrue("Should contain 202 supportability metric", StatsEngine.get().getStatsMap().containsKey(MetricNames.SUPPORTABILITY_HEX_UPLOAD_TIME));

        Mockito.reset();
        StatsEngine.get().getStatsMap().clear();
        when(connection.getResponseCode()).thenReturn(HttpsURLConnection.HTTP_CREATED);
        agentDataSender.onRequestResponse(connection);
        Assert.assertTrue("Should contain 201 supportability metric", StatsEngine.get().getStatsMap().containsKey(MetricNames.SUPPORTABILITY_HEX_FAILED_UPLOAD));

        Mockito.reset();
        StatsEngine.get().getStatsMap().clear();
        when(connection.getResponseCode()).thenReturn(HttpsURLConnection.HTTP_INTERNAL_ERROR);
        agentDataSender.onRequestResponse(connection);
        Assert.assertTrue("Should contain 500 supportability metric", StatsEngine.get().getStatsMap().containsKey(MetricNames.SUPPORTABILITY_HEX_FAILED_UPLOAD));

        Mockito.reset();
        when(connection.getResponseCode()).thenReturn(HttpsURLConnection.HTTP_BAD_REQUEST);
        agentDataSender.onRequestResponse(connection);
        verify(agentDataSender, atLeastOnce()).onFailedUpload(anyString());
    }

    @Test
    public void testFailedUpload() {
        AgentDataSender agentDataSender = spy(new AgentDataSender(flat.dataBuffer().slice().array(), agentConfiguration));

        agentDataSender.onFailedUpload("Upload failure");
        Assert.assertTrue("Should contain 500 supportability metric", StatsEngine.get().getStatsMap().containsKey(MetricNames.SUPPORTABILITY_HEX_FAILED_UPLOAD));
    }

    @Test
    public void testTimedoutUpload() throws Exception {
        AgentDataSender agentDataSender = spy(new AgentDataSender(flat.dataBuffer().slice().array(), agentConfiguration));
        HttpURLConnection connection = spy(agentDataSender.getConnection());

        when(connection.getResponseCode()).thenReturn(HttpsURLConnection.HTTP_CLIENT_TIMEOUT);
        agentDataSender.onRequestResponse(connection);
        Assert.assertTrue("Should contain 408 supportability metric",
                StatsEngine.get().getStatsMap().containsKey(MetricNames.SUPPORTABILITY_HEX_UPLOAD_TIMEOUT));
        verify(agentDataSender, atLeastOnce()).onFailedUpload(anyString());
    }

    @Test
    public void testThrottledUpload() throws Exception {
        AgentDataSender agentDataSender = spy(new AgentDataSender(flat.dataBuffer().slice().array(), agentConfiguration));
        HttpURLConnection connection = spy(agentDataSender.getConnection());

        when(connection.getResponseCode()).thenReturn(429);
        agentDataSender.onRequestResponse(connection);
        Assert.assertTrue("Should contain 429 supportability metric",
                StatsEngine.get().getStatsMap().containsKey(MetricNames.SUPPORTABILITY_HEX_UPLOAD_THROTTLED));
        verify(agentDataSender, atLeastOnce()).onFailedUpload(anyString());
    }

}
