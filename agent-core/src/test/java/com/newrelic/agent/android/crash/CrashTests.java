/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.crash;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.NewRelicConfig;
import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.analytics.AnalyticsControllerImpl;
import com.newrelic.agent.android.analytics.AnalyticsEvent;
import com.newrelic.agent.android.analytics.SessionEvent;
import com.newrelic.agent.android.harvest.ApplicationInformation;
import com.newrelic.agent.android.harvest.crash.ExceptionInfo;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.logging.ConsoleAgentLog;
import com.newrelic.agent.android.test.stub.StubAgentImpl;
import com.newrelic.agent.android.test.stub.StubAnalyticsAttributeStore;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RunWith(JUnit4.class)
public class CrashTests {

    private final static String appName = "CrashTestDummy";
    private final static String appVersion = "6.6";
    private final static String appBundleId = "com.newrelic.android.crash";
    private final static String appBuild = "6";
    private final static UUID uuid = UUID.randomUUID();

    private final long timeOfTests = System.currentTimeMillis();

    private Crash crash;

    @BeforeClass
    public static void setUpClass() {
        TestStubAgentImpl.install();

        AgentConfiguration agentConfiguration = new AgentConfiguration();
        agentConfiguration.setApplicationToken(CrashTests.class.getSimpleName());
        agentConfiguration.setCrashStore(new TestCrashStore());
        agentConfiguration.setEnableAnalyticsEvents(true);
        agentConfiguration.setAnalyticsAttributeStore(new StubAnalyticsAttributeStore());

        AgentLogManager.setAgentLog(new ConsoleAgentLog());

        CrashReporter.initialize(agentConfiguration);

        AnalyticsControllerImpl.initialize(agentConfiguration, new StubAgentImpl());
        AnalyticsEvent sessionEvent = new SessionEvent();
        Set<AnalyticsAttribute> sessionAttributes = new HashSet<AnalyticsAttribute>();
        sessionAttributes.add(new AnalyticsAttribute(AnalyticsAttribute.SESSION_DURATION_ATTRIBUTE, 0.0180f));
        sessionAttributes.add(new AnalyticsAttribute(AnalyticsAttribute.SESSION_TIME_SINCE_LOAD_ATTRIBUTE, 0.026f));
        sessionAttributes.add(new AnalyticsAttribute(AnalyticsAttribute.APP_INSTALL_ATTRIBUTE, true));
        sessionEvent.addAttributes(sessionAttributes);
        AnalyticsControllerImpl.getInstance().getEventManager().addEvent(sessionEvent);
    }

    @Test
    public void testCrashAppUUID() throws Exception {
        crash = new Crash(uuid, "buildId", timeOfTests);
        Assert.assertEquals(uuid, crash.getUuid());
    }

    @Test
    public void testCrashApplicationInfo() throws Exception {
        crash = new Crash(new RuntimeException("testCrashApplicationInfo"));

        JsonObject json = crash.asJsonObject();
        Assert.assertTrue("Should contain applicationInfo struct", json.has("appInfo"));
        JsonObject appInfo = json.getAsJsonObject("appInfo");

        Assert.assertEquals("appName should be set", appName, appInfo.get("appName").getAsString());
        Assert.assertEquals("appVersion should be set", appVersion, appInfo.get("appVersion").getAsString());
        Assert.assertEquals("appBuild should be set", appBuild, appInfo.get("appBuild").getAsString());
        Assert.assertEquals("app package Id should be set", appBundleId, appInfo.get("bundleId").getAsString());
        Assert.assertNotNull("app process Id should be set", appInfo.get("processId"));
    }

    @Test
    public void testAndroidBuildId() throws Exception {
        crash = new Crash(uuid, "buildId", timeOfTests);
        String buildId = Crash.getSafeBuildId();
        Assert.assertEquals("Android buildId should be injected", buildId, NewRelicConfig.getBuildId());

    }

    @Test
    public void testNonAnalyticsCrash() throws Exception {
        crash = new Crash(uuid, "buildId", timeOfTests);

        JsonObject json = crash.asJsonObject();
        Assert.assertTrue("Should always contain analytics", json.has("sessionAttributes") && json.has("analyticsEvents"));
    }

    @Test
    public void testCrashAnalytics() throws Exception {
        final Set<AnalyticsAttribute> sessionAttributes = AnalyticsControllerImpl.getInstance().getSessionAttributes();
        final Collection<AnalyticsEvent> events = AnalyticsControllerImpl.getInstance().getEventManager().getQueuedEvents();

        crash = new Crash(new RuntimeException("testCrashAnalytics"), sessionAttributes, events, true);
        JsonObject json = crash.asJsonObject();
        Assert.assertTrue("Should contain analytics struct", json.has("sessionAttributes") && json.has("analyticsEvents"));
        Assert.assertEquals("Should contain session attributes", json.getAsJsonObject("sessionAttributes").entrySet().size(), sessionAttributes.size());
        Assert.assertEquals("Should contain analytics events", json.getAsJsonArray("analyticsEvents").size(), events.size());

        crash = new Crash(new RuntimeException("testCrashAnalytics"), null, null, true);
        crash.setSessionAttributes(sessionAttributes);
        crash.setAnalyticsEvents(events);
        json = crash.asJsonObject();
        Assert.assertTrue("Should contain analytics structs", json.has("sessionAttributes") && json.has("analyticsEvents"));
        Assert.assertEquals("Should contain session attributes", json.getAsJsonObject("sessionAttributes").entrySet().size(), sessionAttributes.size());
        Assert.assertEquals("Should contain analytics events", json.getAsJsonArray("analyticsEvents").size(), events.size());
    }

    @Test
    public void testCrashThrowable() throws Exception {
        crash = new Crash(new RuntimeException("testCrashThrowable"));

        ExceptionInfo exceptionInfo = crash.getExceptionInfo();
        Assert.assertNotNull("Should contain exception info", exceptionInfo);
        Assert.assertEquals("Should contain exception name", RuntimeException.class.getName(), exceptionInfo.getClassName());
        Assert.assertEquals("Should contain exception cause", "testCrashThrowable", exceptionInfo.getMessage());
    }

    @Test
    public void testDetermineRootCause() throws Exception {
        Throwable cause = new NullPointerException("NPE");
        Throwable throwable = new RuntimeException("testDetermineRootCause");
        throwable.initCause(cause);
        TestCrash testCrash = new TestCrash(null);
        Throwable rootCause = testCrash.getRootCause(throwable);
        Assert.assertEquals("Root cause NullPointerException", cause, rootCause);
        Assert.assertTrue("Root cause detailMessage was NPE", rootCause.getMessage().equalsIgnoreCase("NPE"));
    }

    @Test
    public void testNullRootCause() throws Exception {
        TestCrash testCrash = new TestCrash(new RuntimeException("throwable"));
        Throwable rootCause = testCrash.getRootCause(null);
        Assert.assertTrue("Root cause is unknown.", rootCause.getMessage().contains("Unknown cause"));
    }

    @Test
    public void testDetermineRootCauseThrows() throws Exception {
        Throwable cause = new NullPointerException("NPE") {
            @Override
            public synchronized Throwable getCause() {
                throw new RuntimeException("Duplicate found in causal chain so cropping to prevent loop ...");
            }
        };

        try {
            TestCrash testCrash = new TestCrash(null);
            Throwable rootCause = testCrash.getRootCause(cause);
            Assert.assertEquals("Root cause NullPointerException", cause, rootCause);
            Assert.assertTrue("Root cause detailMessage was NPE", rootCause.getMessage().equalsIgnoreCase("NPE"));
        } catch (Exception e) {
            Assert.fail("Should not throw");
        }
    }

    @Test
    public void testThreadsAsJson() throws Exception {
        JsonArray threads = new TestCrash(new RuntimeException("testThreadsAsJson")).getThreadsAsJson();
        Assert.assertNotNull(threads);
        Assert.assertNotEquals("Threads from throwable should not be empty.", 0, threads.size());

        threads = new TestCrash(uuid, "buildId", timeOfTests).getThreadsAsJson();
        Assert.assertNotNull(threads);
        Assert.assertEquals("Threads from default ctor should be empty.", 0, threads.size());
    }

    @Test
    public void testCrashfromJson() throws Exception {
        crash = new Crash(uuid, "buildId", timeOfTests);

        Crash crashFromJson = Crash.crashFromJsonString(crash.asJsonObject().toString());
        Assert.assertEquals("Should contain UUID", uuid, crashFromJson.getUuid());
        Assert.assertEquals("Should contain timestamp", timeOfTests, crashFromJson.getTimestamp());
        Assert.assertEquals("Should contain base type", crash.getType(), crashFromJson.getType());

        final Set<AnalyticsAttribute> sessionAttributes = AnalyticsControllerImpl.getInstance().getSessionAttributes();
        final Collection<AnalyticsEvent> events = AnalyticsControllerImpl.getInstance().getEventManager().getQueuedEvents();
        crash = new Crash(new RuntimeException("testCrashJson"), sessionAttributes, events, true);
        crashFromJson = Crash.crashFromJsonString(crash.asJsonObject().toString());

        JsonObject json = crashFromJson.asJsonObject();
        Assert.assertTrue("Should contain analytics structs", json.has("sessionAttributes") && json.has("analyticsEvents"));
        Assert.assertEquals("Should contain session attributes", json.getAsJsonObject("sessionAttributes").entrySet().size(), sessionAttributes.size());
        Assert.assertEquals("Should contain analytics events", json.getAsJsonArray("analyticsEvents").size(), events.size());
    }

    private static class TestCrash extends Crash {
        public TestCrash(UUID uuid, String buildId, long timestamp) {
            super(uuid, buildId, timestamp);
        }

        public TestCrash(Throwable throwable) {
            super(throwable);
        }

        public JsonArray getThreadsAsJson() {
            return super.getThreadsAsJson();
        }
    }

    private static class TestCrashStore implements CrashStore {
        @Override
        public boolean store(Crash crash) {
            return false;
        }

        @Override
        public List<Crash> fetchAll() {
            return new ArrayList<Crash>();
        }

        @Override
        public int count() {
            return 0;
        }

        @Override
        public void clear() {
        }

        @Override
        public void delete(Crash crash) {
        }
    }

    private static class TestStubAgentImpl extends StubAgentImpl {
        public static StubAgentImpl install() {
            final StubAgentImpl agent = new TestStubAgentImpl();
            Agent.setImpl(agent);
            return agent;
        }

        @Override
        public ApplicationInformation getApplicationInformation() {
            return new ApplicationInformation(appName, appVersion, appBundleId, appBuild);
        }
    }

}
