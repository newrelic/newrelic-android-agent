/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.agentdata;

import com.google.flatbuffers.FlatBufferBuilder;
import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.agentdata.builder.AgentDataBuilder;
import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.analytics.AnalyticsControllerImpl;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.logging.ConsoleAgentLog;
import com.newrelic.agent.android.test.stub.StubAgentImpl;
import com.newrelic.agent.android.test.stub.StubAnalyticsAttributeStore;
import com.newrelic.mobile.fbs.HexAgentData;
import com.newrelic.mobile.fbs.HexAgentDataBundle;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class AgentDataControllerTest {

    private AgentConfiguration config;

    @BeforeClass
    public static void setUpClass() throws Exception {
        Agent.start();
    }

    @AfterClass
    public static void teardownClass() throws Exception {
        Agent.stop();
    }

    @Before
    public void setup() {
        AgentLogManager.setAgentLog(new ConsoleAgentLog());
        AgentLogManager.getAgentLog().setLevel(0);

        config = new AgentConfiguration();
        config.setEnableAnalyticsEvents(true);
        config.setAnalyticsAttributeStore(new StubAnalyticsAttributeStore());

        AnalyticsControllerImpl.initialize(config, new StubAgentImpl());
    }

    @After
    public void tearDown() throws Exception {
        AnalyticsControllerImpl.shutdown();
    }

    @Test
    public void threadSetFromStackElements() throws Exception {
        Throwable throwable = new Throwable("root");
        Exception e = new Exception("new exception", throwable);
        List<Map<String, Object>> threadSet = AgentDataController.threadSetFromStackElements(e.getStackTrace());
        Map<String, Object> frames = new LinkedHashMap<>(); //linked for preserving order
        for (Map<String, Object> frame : threadSet) {
            for (Map.Entry<String, Object> frameElement : frame.entrySet()) {
                final String key = frameElement.getKey();
                final Object val = frameElement.getValue();
                frames.put(key, val);
                assertNotNull(key);
            }
        }

        assertTrue(frames.containsKey(HexAttribute.HEX_ATTR_FILENAME));
        assertTrue(frames.containsKey(HexAttribute.HEX_ATTR_LINE_NUMBER));
        assertTrue(frames.containsKey(HexAttribute.HEX_ATTR_METHOD_NAME));
        assertTrue(frames.containsKey(HexAttribute.HEX_ATTR_CLASS_NAME));
        assertNotNull(frames.get(HexAttribute.HEX_ATTR_FILENAME));
        assertTrue(frames.get(HexAttribute.HEX_ATTR_FILENAME) instanceof String);
        assertNotNull(frames.get(HexAttribute.HEX_ATTR_LINE_NUMBER));
        assertTrue(frames.get(HexAttribute.HEX_ATTR_LINE_NUMBER) instanceof Integer);
        assertNotNull(frames.get(HexAttribute.HEX_ATTR_METHOD_NAME));
        assertTrue(frames.get(HexAttribute.HEX_ATTR_METHOD_NAME) instanceof String);
        assertNotNull(frames.get(HexAttribute.HEX_ATTR_CLASS_NAME));
        assertTrue(frames.get(HexAttribute.HEX_ATTR_CLASS_NAME) instanceof String);
    }

    @Test
    public void threadTestStackElementCount() throws Exception {
        Throwable throwable = new Throwable("root");
        Exception e = new RuntimeException("new r/t exception", throwable);
        List<Map<String, Object>> threadSet = AgentDataController.threadSetFromStackElements(e.getStackTrace());
        assertEquals(e.getStackTrace().length, threadSet.size());
    }

    @Test
    public void buildAgentDataFromHandledException() throws Exception {
        Throwable throwable = new Throwable("root");
        Exception e = new Exception("new exception", throwable);
        //builder under test
        FlatBufferBuilder flat = AgentDataController.buildAgentDataFromHandledException(e);

        //flatbuffer API that gives us a fully formed AgentData object
        HexAgentData agentData = HexAgentDataBundle.getRootAsHexAgentDataBundle(flat.dataBuffer()).hexAgentData(0); //we know our test only has one agentData in the bundle

        assertTrue(agentData.handledExceptions(0).message().equals("new exception"));
        assertTrue(agentData.handledExceptions(0).cause().equals("java.lang.Throwable: root"));
    }

    @Test
    public void buildAgentDataFromHandledExceptionWithAttributes() throws Exception {
        HashMap<String, Object> exceptionAttributes = new HashMap<>();
        exceptionAttributes.put("module", "Junit");
        exceptionAttributes.put("message", "Overrides default message");
        exceptionAttributes.put("deviceModel", "JUnit test");

        FlatBufferBuilder flat = AgentDataController.buildAgentDataFromHandledException(new ConcurrentModificationException("wut?"), exceptionAttributes);
        HexAgentData agentData = HexAgentDataBundle.getRootAsHexAgentDataBundle(flat.dataBuffer()).hexAgentData(0); //we know our test only has one agentData in the bundle

        Map<String, Object> attributeMap = AgentDataBuilder.attributesMapFromAgentData(agentData);
        assertTrue("Should override default exception attribute", agentData.handledExceptions(0).message().equals("Overrides default message"));
        assertEquals("Should override default session attribute", attributeMap.get("deviceModel"), "JUnit test");
        assertTrue("Should add user-provided attributes", attributeMap.containsKey("module"));
    }

    @Test
    public void testExceptionContainsSessionAttributes() throws Exception {
        Throwable throwable = new Throwable("root");
        Exception e = new Exception("new exception", throwable);
        //builder under test
        FlatBufferBuilder flat = AgentDataController.buildAgentDataFromHandledException(e);

        //flatbuffer API that gives us a fully formed AgentData object
        HexAgentData agentData = HexAgentDataBundle.getRootAsHexAgentDataBundle(flat.dataBuffer()).hexAgentData(0); //we know our test only has one agentData in the bundle

        Map<String, Object> attributeMap = AgentDataBuilder.attributesMapFromAgentData(agentData);
        for (AnalyticsAttribute sessionAttr : AnalyticsControllerImpl.getInstance().getSessionAttributes()) {
            Assert.assertTrue("Should contain essential session attributes", attributeMap.containsKey(sessionAttr.getName()));
        }
    }

    @Test
    public void testExceptionContainsWhiteListSessionAttributes() throws Exception {
        Throwable throwable = new Throwable("root");
        Exception e = new Exception("new exception", throwable);
        //builder under test
        FlatBufferBuilder flat = AgentDataController.buildAgentDataFromHandledException(e);

        //flatbuffer API that gives us a fully formed AgentData object
        HexAgentData agentData = HexAgentDataBundle.getRootAsHexAgentDataBundle(flat.dataBuffer()).hexAgentData(0); //we know our test only has one agentData in the bundle
        Map<String, Object> agentDataMap = AgentDataBuilder.attributesMapFromAgentData(agentData);
        Set<AnalyticsAttribute> sessionAttributeMap = AnalyticsControllerImpl.getInstance().getSessionAttributes();

        for (String attr : agentDataMap.keySet()) {
            if (sessionAttributeMap.contains(new AnalyticsAttribute(attr, 0))) {
                Assert.assertTrue("Should contain whitelisted session attributes", HexAttribute.HEX_SESSION_ATTR_WHITELIST.contains(attr));
            }
        }

        // a few that should *not* be included
        Assert.assertFalse("Should not contain session attributes NOT in whitelist", agentDataMap.keySet().contains(AnalyticsAttribute.APP_INSTALL_ATTRIBUTE));
        Assert.assertFalse("Should not contain session attributes NOT in whitelist", agentDataMap.keySet().contains(AnalyticsAttribute.APP_UPGRADE_ATTRIBUTE));
        Assert.assertFalse("Should not contain session attributes NOT in whitelist", agentDataMap.keySet().contains(AnalyticsAttribute.INTERACTION_DURATION_ATTRIBUTE));
    }

    @Test
    public void testRootCause() throws Exception {
        Throwable cause = new Throwable("root cause");
        Throwable throwable = new Throwable("root exception", cause);
        AgentDataController.getRootCause(throwable);
        Assert.assertEquals(throwable.getCause(), cause);
    }

    @Test
    public void testHexContainsRequiredAttributes() throws Exception {
        Throwable throwable = new Throwable("root");
        Exception e = new Exception("new exception", throwable);
        FlatBufferBuilder flat = AgentDataController.buildAgentDataFromHandledException(e);

        HexAgentData agentData = HexAgentDataBundle.getRootAsHexAgentDataBundle(flat.dataBuffer()).hexAgentData(0); //we know our test only has one agentData in the bundle
        Map<String, Object> agentDataMap = AgentDataBuilder.attributesMapFromAgentData(agentData);

        for (String attr : HexAttribute.HEX_REQUIRED_ATTRIBUTES) {
            Assert.assertTrue(agentDataMap.containsKey(attr));
        }
    }

    @Test
    public void testHexThreadStateAttributes() throws Exception {
        Throwable throwable = new Throwable("root");
        Exception e = new Exception("new exception", throwable);
        FlatBufferBuilder flat = AgentDataController.buildAgentDataFromHandledException(e);

        HexAgentData agentData = HexAgentDataBundle.getRootAsHexAgentDataBundle(flat.dataBuffer()).hexAgentData(0); //we know our test only has one agentData in the bundle
        Map<String, Object> agentDataMap = AgentDataBuilder.attributesMapFromAgentData(agentData);
        Assert.assertNotNull(agentDataMap);
        Assert.assertTrue(agentDataMap.keySet().contains(HexAttribute.HEX_ATTR_THREAD + " 0"));

        @SuppressWarnings("unchecked")
        HashMap<String, Object> threadData = (HashMap<String, Object>) agentDataMap.get(HexAttribute.HEX_ATTR_THREAD + " 0");
        Assert.assertNotNull(threadData);
        Assert.assertTrue(threadData.keySet().contains(HexAttribute.HEX_ATTR_THREAD_NUMBER));
        Assert.assertTrue(threadData.keySet().contains(HexAttribute.HEX_ATTR_THREAD_ID));
        Assert.assertTrue(threadData.keySet().contains(HexAttribute.HEX_ATTR_THREAD_CRASHED));
        Assert.assertTrue(threadData.keySet().contains(HexAttribute.HEX_ATTR_THREAD_PRI));
        Assert.assertTrue(threadData.keySet().contains(HexAttribute.HEX_ATTR_THREAD_STATE));
    }

    @Test
    public void testStackFrameOrder() throws Exception {
        try {
            // generate divide by zero exception
            @SuppressWarnings("unused")
            int dbz = 33 / 0;
        } catch (Exception e) {
            ArrayList<String> stack = new ArrayList<>();

            for (StackTraceElement stackTraceElement : e.getStackTrace()) {
                stack.add(stackTraceElement.getClassName() + ":"
                        + stackTraceElement.getMethodName() + ":"
                        + (stackTraceElement.getFileName() == null ? "" : stackTraceElement.getFileName()) + ":"
                        + stackTraceElement.getLineNumber());
            }

            FlatBufferBuilder flat = AgentDataController.buildAgentDataFromHandledException(e);
            HexAgentData agentData = HexAgentDataBundle.getRootAsHexAgentDataBundle(flat.dataBuffer()).hexAgentData(0); //we know our test only has one agentData in the bundle

            Map<String, Object> agentDataMap = AgentDataBuilder.attributesMapFromAgentData(agentData);
            Assert.assertTrue(agentDataMap.keySet().contains(HexAttribute.HEX_ATTR_THREAD + " 0"));

            @SuppressWarnings("unchecked")
            HashMap<String, Object> threadData = (HashMap<String, Object>) agentDataMap.get(HexAttribute.HEX_ATTR_THREAD + " 0");
            Assert.assertNotNull(threadData);

            for (int i = 0; i < e.getStackTrace().length; i++) {
                @SuppressWarnings("unchecked")
                Map<String, Object> frame = (Map<String, Object>) threadData.get("frame " + i);
                Assert.assertEquals(stack.get(i), frame.get(HexAttribute.HEX_ATTR_CLASS_NAME) + ":"
                        + frame.get(HexAttribute.HEX_ATTR_METHOD_NAME) + ":"
                        + frame.get(HexAttribute.HEX_ATTR_FILENAME) + ":"
                        + frame.get(HexAttribute.HEX_ATTR_LINE_NUMBER));
            }
        }
    }

}