/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.agentdata;

import static org.junit.Assert.assertTrue;

import com.google.flatbuffers.FlatBufferBuilder;
import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.agentdata.builder.AgentDataBuilder;
import com.newrelic.agent.android.crash.CrashReporterTests;
import com.newrelic.agent.android.payload.Payload;
import com.newrelic.agent.android.payload.PayloadController;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.test.stub.StubAnalyticsAttributeStore;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class AgentDataReporterTest {
    private static AgentConfiguration agentConfiguration;
    private FlatBufferBuilder flat;

    @Before
    public void setUp() throws Exception {
        agentConfiguration = new AgentConfiguration();
        agentConfiguration.setApplicationToken(CrashReporterTests.class.getSimpleName());
        agentConfiguration.setEnableAnalyticsEvents(true);
        agentConfiguration.setReportCrashes(true);
        agentConfiguration.setReportHandledExceptions(true);
        agentConfiguration.setAnalyticsAttributeStore(new StubAnalyticsAttributeStore());

        FeatureFlag.enableFeature(FeatureFlag.HandledExceptions);

        PayloadController.initialize(agentConfiguration);

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
        hex.put(HexAttribute.HEX_ATTR_APP_UUID_HI, buildUuid.getMostSignificantBits());
        hex.put(HexAttribute.HEX_ATTR_APP_UUID_LO, buildUuid.getLeastSignificantBits());

        Set<Map<String, Object>> set = new HashSet<>();
        set.add(hex);

        flat = AgentDataBuilder.startAndFinishAgentData(sessionAttributes, set);
        StatsEngine.reset();
    }

    @After
    public void tearDown() throws Exception {
        Agent.stop();
    }

    @Test
    public void initialize() throws Exception {
        TestAgentDataReporter testAgentDataReporter = new TestAgentDataReporter(agentConfiguration);
        assertTrue("AgentDataReporter is enabled by default", testAgentDataReporter.isEnabled());

        testAgentDataReporter.start();
        assertTrue("AgentDataReporter should be started", testAgentDataReporter.isRunning());
    }

    @Test
    public void testDisableReportHandledExceptions() throws Exception {
        FeatureFlag.disableFeature(FeatureFlag.HandledExceptions);

        agentConfiguration.setReportHandledExceptions(false);

        TestAgentDataReporter testAgentDataReporter = new TestAgentDataReporter(agentConfiguration);
        Assert.assertFalse("AgentDataReporter should be disabled on init", testAgentDataReporter.isEnabled());

        testAgentDataReporter.start();
        Assert.assertFalse("AgentDataReporter should not be started", testAgentDataReporter.isRunning());
    }

    @Test
    public void reportAgentData() throws Exception {

    }

    @Test
    public void reportSavedAgentData() throws Exception {
    }

    @Test
    public void shouldPersistFlatBufferToPayloadStore() throws Exception {
        ByteBuffer byteBuffer = flat.dataBuffer();
        Assert.assertNotNull(byteBuffer.array());


        Payload payload = new Payload(byteBuffer.array());
        Assert.assertTrue(agentConfiguration.getPayloadStore().store(payload));
    }

    private static class TestAgentDataReporter extends AgentDataReporter {
        public TestAgentDataReporter(AgentConfiguration agentConfiguration) {
            super(agentConfiguration);
        }

        public boolean isRunning() {
            return isStarted.get();
        }
    }

}