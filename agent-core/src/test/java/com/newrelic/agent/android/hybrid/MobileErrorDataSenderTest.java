/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.hybrid;

import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.logging.ConsoleAgentLog;
import com.newrelic.agent.android.payload.Payload;
import com.newrelic.agent.android.test.stub.StubAgentImpl;
import com.newrelic.agent.android.util.Constants;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Verifies that {@link MobileErrorDataSender} threads an
 * {@code appVersionOverride} into the {@code X-NewRelic-App-Version} upload
 * header so the collector can pick the matching ProGuard mapping when
 * uploading errors recorded under an older app version.
 */
public class MobileErrorDataSenderTest {

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
    }

    @Test
    public void getConnection_usesOverride_whenProvided() throws IOException {
        MobileErrorDataSender sender = new MobileErrorDataSender(
                new Payload(PAYLOAD_BYTES), AgentConfiguration.getInstance(), "9.9.9");

        HttpURLConnection conn = sender.getConnection();
        Map<String, List<String>> headers = conn.getRequestProperties();

        Assert.assertTrue(headers.containsKey(Constants.Network.APP_VERSION_HEADER));
        Assert.assertEquals(List.of("9.9.9"),
                headers.get(Constants.Network.APP_VERSION_HEADER));
    }

    @Test
    public void getConnection_fallsBackToCurrent_whenOverrideIsNull() throws IOException {
        MobileErrorDataSender sender = new MobileErrorDataSender(
                new Payload(PAYLOAD_BYTES), AgentConfiguration.getInstance(), null);

        HttpURLConnection conn = sender.getConnection();
        Map<String, List<String>> headers = conn.getRequestProperties();

        Assert.assertEquals(List.of(Agent.getApplicationInformation().getAppVersion()),
                headers.get(Constants.Network.APP_VERSION_HEADER));
    }

    @Test
    public void getConnection_fallsBackToCurrent_whenOverrideIsEmpty() throws IOException {
        MobileErrorDataSender sender = new MobileErrorDataSender(
                new Payload(PAYLOAD_BYTES), AgentConfiguration.getInstance(), "");

        HttpURLConnection conn = sender.getConnection();
        Map<String, List<String>> headers = conn.getRequestProperties();

        Assert.assertEquals(List.of(Agent.getApplicationInformation().getAppVersion()),
                headers.get(Constants.Network.APP_VERSION_HEADER));
    }

    @Test
    public void getConnection_legacyTwoArgConstructor_fallsBackToCurrent() throws IOException {
        MobileErrorDataSender sender = new MobileErrorDataSender(
                new Payload(PAYLOAD_BYTES), AgentConfiguration.getInstance());

        HttpURLConnection conn = sender.getConnection();
        Map<String, List<String>> headers = conn.getRequestProperties();

        Assert.assertEquals(List.of(Agent.getApplicationInformation().getAppVersion()),
                headers.get(Constants.Network.APP_VERSION_HEADER));
    }
}
