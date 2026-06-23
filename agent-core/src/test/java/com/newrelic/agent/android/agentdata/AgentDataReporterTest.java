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
import com.newrelic.agent.android.test.stub.StubAgentImpl;
import com.newrelic.agent.android.test.stub.StubAnalyticsAttributeStore;
import com.newrelic.agent.android.util.Constants;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.net.HttpURLConnection;
import java.nio.ByteBuffer;

import javax.net.ssl.HttpsURLConnection;

import com.newrelic.agent.android.payload.PayloadSender;
import com.newrelic.agent.android.payload.PayloadStore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Future;

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

        agentConfiguration.setPayloadStore(new TestPayloadStore());
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
        Agent.setImpl(new StubAgentImpl());
        ByteBuffer byteBuffer = flat.dataBuffer();
        Assert.assertNotNull(byteBuffer.array());

        Payload payload = new Payload(byteBuffer.array());
        boolean reported = AgentDataReporter.reportAgentData(payload.getBytes());
        Assert.assertTrue(reported);
    }

    @Test
    public void reportAgentDataReturnsFuture() throws Exception {
        Agent.setImpl(new StubAgentImpl());
        AgentDataReporter reporter = AgentDataReporter.initialize(agentConfiguration);

        // PayloadController queues payloads by default (opportunisticUploads=false), which returns
        // null from submitPayload. Enable opportunistic uploads so a Future is returned instead.
        java.lang.reflect.Field field = PayloadController.class.getDeclaredField("opportunisticUploads");
        field.setAccessible(true);
        field.set(null, true);

        Payload payload = new Payload(flat.dataBuffer().array());
        Future future = reporter.reportAgentData(payload);
        Assert.assertNotNull("reportAgentData should return a non-null Future", future);
    }

    @Test
    public void reportAgentDataReturnsNullWhenPayloadOversized() throws Exception {
        Agent.setImpl(new StubAgentImpl());
        AgentDataReporter reporter = AgentDataReporter.initialize(agentConfiguration);

        byte[] oversized = new byte[(int) Constants.Network.MAX_PAYLOAD_SIZE + 1];
        Payload payload = new Payload(oversized);
        Future future = reporter.reportAgentData(payload);
        Assert.assertNull("reportAgentData should return null for oversized payload", future);
    }

    @Test
    public void testPermanentlyRejectedPayloadDeletedFromStore() throws Exception {
        AgentDataReporter reporter = AgentDataReporter.initialize(agentConfiguration);
        Payload payload = new Payload(flat.dataBuffer().array());
        agentConfiguration.getPayloadStore().store(payload);
        Assert.assertEquals(1, agentConfiguration.getPayloadStore().count());

        PayloadSender mockSender = Mockito.mock(PayloadSender.class);
        Mockito.when(mockSender.isSuccessfulResponse()).thenReturn(false);
        Mockito.when(mockSender.getPayload()).thenReturn(payload);

        Mockito.when(mockSender.getResponseCode()).thenReturn(HttpsURLConnection.HTTP_FORBIDDEN);
        reporter.onAgentDataResponse(mockSender);
        Assert.assertEquals("Payload should be deleted on 403", 0, agentConfiguration.getPayloadStore().count());

        agentConfiguration.getPayloadStore().store(payload);
        Mockito.when(mockSender.getResponseCode()).thenReturn(HttpURLConnection.HTTP_BAD_REQUEST);
        reporter.onAgentDataResponse(mockSender);
        Assert.assertEquals("Payload should be deleted on 400", 0, agentConfiguration.getPayloadStore().count());
    }

    @Test
    public void testTransientErrorRetainsPayloadInStore() throws Exception {
        AgentDataReporter reporter = AgentDataReporter.initialize(agentConfiguration);
        Payload payload = new Payload(flat.dataBuffer().array());
        agentConfiguration.getPayloadStore().store(payload);
        Assert.assertEquals(1, agentConfiguration.getPayloadStore().count());

        PayloadSender mockSender = Mockito.mock(PayloadSender.class);
        Mockito.when(mockSender.isSuccessfulResponse()).thenReturn(false);
        Mockito.when(mockSender.getPayload()).thenReturn(payload);

        for (int code : new int[]{HttpURLConnection.HTTP_CLIENT_TIMEOUT, 429, HttpURLConnection.HTTP_UNAVAILABLE}) {
            Mockito.when(mockSender.getResponseCode()).thenReturn(code);
            reporter.onAgentDataResponse(mockSender);
            Assert.assertEquals("Payload should be retained on " + code, 1, agentConfiguration.getPayloadStore().count());
        }
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

    private static class TestPayloadStore implements PayloadStore<Payload> {
        private final Map<String, Payload> store = new HashMap<>();

        @Override
        public boolean store(Payload payload) {
            store.put(payload.getUuid(), payload);
            return true;
        }

        @Override
        public List<Payload> fetchAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public int count() {
            return store.size();
        }

        @Override
        public void clear() {
            store.clear();
        }

        @Override
        public void delete(Payload payload) {
            store.remove(payload.getUuid());
        }
    }

}