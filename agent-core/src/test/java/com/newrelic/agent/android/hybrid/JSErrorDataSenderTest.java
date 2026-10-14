/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.hybrid;

import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.logging.ConsoleAgentLog;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.payload.Payload;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.test.stub.StubAgentImpl;
import com.newrelic.agent.android.util.Constants;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Verifies that {@link JSErrorDataSender} threads an
 * {@code appVersionOverride} into the {@code X-NewRelic-App-Version} upload
 * header so the collector can pick the matching ProGuard mapping when
 * uploading errors recorded under an older app version.
 */
public class JSErrorDataSenderTest {

    private static final byte[] PAYLOAD_BYTES = "{}".getBytes(StandardCharsets.UTF_8);

    @BeforeClass
    public static void beforeClass() {
        AgentLogManager.setAgentLog(new ConsoleAgentLog());
        Agent.setImpl(new StubAgentImpl());
    }

    @AfterClass
    public static void afterClass() {
        Agent.setImpl(null);
    }

    @Before
    public void setUp() {
        AgentConfiguration.getInstance().setApplicationToken("DEAD-BEEF_BAAD-F00D");
        StatsEngine.reset();
    }

    private static final String RETRY_SUCCESS = MetricNames.SUPPORTABILITY_JSERROR + MetricNames.SUPPORTABILITY_RETRY_SUCCESS;
    private static final String RETRY_FAILED = MetricNames.SUPPORTABILITY_JSERROR + MetricNames.SUPPORTABILITY_RETRY_FAILED;

    /**
     * Upload a batch of stored errors the way the reporter does: a new payload, and so a new
     * payload UUID, for every attempt.
     */
    private void upload(String appVersion, int responseCode) throws Exception {
        final HttpURLConnection connection = Mockito.mock(HttpURLConnection.class);
        Mockito.doReturn(false).when(connection).getDoOutput();
        Mockito.doReturn(false).when(connection).getDoInput();
        Mockito.doReturn(responseCode).when(connection).getResponseCode();

        new JSErrorDataSender(new Payload(PAYLOAD_BYTES), AgentConfiguration.getInstance(), appVersion) {
            @Override
            protected HttpURLConnection getConnection() {
                return connection;
            }
        }.call();
    }

    private long count(String name) {
        return StatsEngine.get().getStatsMap().containsKey(name) ? StatsEngine.get().getStatsMap().get(name).getCount() : 0;
    }

    @Test
    public void retriesAreLinkedAcrossRebuiltPayloads() throws Exception {
        final String version = UUID.randomUUID().toString();

        upload(version, HttpURLConnection.HTTP_UNAVAILABLE);
        Assert.assertEquals(0, count(RETRY_FAILED));

        upload(version, HttpURLConnection.HTTP_UNAVAILABLE);
        Assert.assertEquals(1, count(RETRY_FAILED));

        upload(version, HttpURLConnection.HTTP_ACCEPTED);
        Assert.assertEquals(1, count(RETRY_SUCCESS));
        Assert.assertEquals(1, count(RETRY_FAILED));
    }

    @Test
    public void batchesForDifferentAppVersionsAreNotLinked() throws Exception {
        upload(UUID.randomUUID().toString(), HttpURLConnection.HTTP_UNAVAILABLE);
        upload(UUID.randomUUID().toString(), HttpURLConnection.HTTP_OK);

        Assert.assertEquals(0, count(RETRY_SUCCESS));
        Assert.assertEquals(0, count(RETRY_FAILED));
    }

    @Test
    public void getConnection_usesOverride_whenProvided() throws IOException {
        JSErrorDataSender sender = new JSErrorDataSender(
                new Payload(PAYLOAD_BYTES), AgentConfiguration.getInstance(), "9.9.9");

        HttpURLConnection conn = sender.getConnection();
        Map<String, List<String>> headers = conn.getRequestProperties();

        Assert.assertTrue(headers.containsKey(Constants.Network.APP_VERSION_HEADER));
        Assert.assertEquals(List.of("9.9.9"),
                headers.get(Constants.Network.APP_VERSION_HEADER));
    }

    @Test
    public void getConnection_fallsBackToCurrent_whenOverrideIsNull() throws IOException {
        JSErrorDataSender sender = new JSErrorDataSender(
                new Payload(PAYLOAD_BYTES), AgentConfiguration.getInstance(), null);

        HttpURLConnection conn = sender.getConnection();
        Map<String, List<String>> headers = conn.getRequestProperties();

        Assert.assertEquals(List.of(Agent.getApplicationInformation().getAppVersion()),
                headers.get(Constants.Network.APP_VERSION_HEADER));
    }

    @Test
    public void getConnection_fallsBackToCurrent_whenOverrideIsEmpty() throws IOException {
        JSErrorDataSender sender = new JSErrorDataSender(
                new Payload(PAYLOAD_BYTES), AgentConfiguration.getInstance(), "");

        HttpURLConnection conn = sender.getConnection();
        Map<String, List<String>> headers = conn.getRequestProperties();

        Assert.assertEquals(List.of(Agent.getApplicationInformation().getAppVersion()),
                headers.get(Constants.Network.APP_VERSION_HEADER));
    }

    @Test
    public void getConnection_legacyTwoArgConstructor_fallsBackToCurrent() throws IOException {
        JSErrorDataSender sender = new JSErrorDataSender(
                new Payload(PAYLOAD_BYTES), AgentConfiguration.getInstance());

        HttpURLConnection conn = sender.getConnection();
        Map<String, List<String>> headers = conn.getRequestProperties();

        Assert.assertEquals(List.of(Agent.getApplicationInformation().getAppVersion()),
                headers.get(Constants.Network.APP_VERSION_HEADER));
    }
}
