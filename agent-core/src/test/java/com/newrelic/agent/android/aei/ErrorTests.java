package com.newrelic.agent.android.aei;


import com.google.gson.JsonObject;
import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.NewRelicConfig;
import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.analytics.AnalyticsControllerImpl;
import com.newrelic.agent.android.analytics.AnalyticsEvent;
import com.newrelic.agent.android.analytics.SessionEvent;
import com.newrelic.agent.android.background.ApplicationStateMonitor;
import com.newrelic.agent.android.crash.CrashTests;
import com.newrelic.agent.android.harvest.ApplicationInformation;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.logging.ConsoleAgentLog;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.test.stub.StubAgentImpl;
import com.newrelic.agent.android.test.stub.StubAnalyticsAttributeStore;
import com.newrelic.agent.android.error.Error;

import org.junit.After;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class ErrorTests {

    private final static String appName = "ErrorTestDummy";
    private final static String appVersion = "6.6";
    private final static String appBundleId = "com.newrelic.android.error";
    private final static String appBuild = "6";
    private Error error;
    private String buildId = "testBuildId";
    private long timestamp = System.currentTimeMillis();
    private HashMap<String, Object> event;
    private final long timeOfTests = System.currentTimeMillis();

    @BeforeClass
    public static void setUpClass() {
       TestStubAgentImpl.install();

        AgentConfiguration agentConfiguration = new AgentConfiguration();
        agentConfiguration.setApplicationToken(CrashTests.class.getSimpleName());
        agentConfiguration.setEnableAnalyticsEvents(true);
        agentConfiguration.setAnalyticsAttributeStore(new StubAnalyticsAttributeStore());

        AgentLogManager.setAgentLog(new ConsoleAgentLog());


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
    public void testErrorApplicationInfo() throws Exception {
        error = new Error(buildId, timestamp);

        JsonObject json = error.asJsonObject();
        Assert.assertTrue("Should contain applicationInfo struct", json.has("appInfo"));
        JsonObject appInfo = json.getAsJsonObject("appInfo");

        Assert.assertEquals("appName should be set", appName, appInfo.get("appName").getAsString());
        Assert.assertEquals("appVersion should be set", appVersion, appInfo.get("appVersion").getAsString());
        Assert.assertEquals("appBuild should be set", appBuild, appInfo.get("appBuild").getAsString());
        Assert.assertEquals("app package Id should be set", appBundleId, appInfo.get("bundleId").getAsString());
        Assert.assertNotNull("app process Id should be set", appInfo.get("processId"));
    }

    @Test
    public void testErrorAnalytics() throws Exception {
        final Set<AnalyticsAttribute> sessionAttributes = AnalyticsControllerImpl.getInstance().getSessionAttributes();
        final HashMap<String,Object> event = getApplicationExitInfoEvent();

        error = new Error(sessionAttributes, event);
        JsonObject json = error.asJsonObject();
        Assert.assertTrue("Should contain analytics struct", json.has("sessionAttributes") && json.has("analyticsEvents"));
        Assert.assertEquals("Should contain session attributes", json.getAsJsonObject("sessionAttributes").entrySet().size(), sessionAttributes.size()+1 );
        Assert.assertEquals("Should contain analytics events", json.getAsJsonArray("analyticsEvents").size(), 1);

        error = new Error(null, null);
        error.setSessionAttributes(sessionAttributes);
        error.setAnalyticsEvents(event);
        json = error.asJsonObject();
        Assert.assertTrue("Should contain analytics structs", json.has("sessionAttributes") && json.has("analyticsEvents"));
        // We add 1 to the sessionAttributes size since 'OBFUSCATED' is only added as a session attribute on crash
        Assert.assertEquals("Should contain session attributes", json.getAsJsonObject("sessionAttributes").entrySet().size(), sessionAttributes.size()+1);
        Assert.assertEquals("Should contain analytics events", json.getAsJsonArray("analyticsEvents").size(), 1);
    }

    @Test
    public void testErrorFromJson() throws Exception {
        error = new Error(buildId, timestamp);

        Error errorFromJson = Error.errorFromJsonString(error.asJsonObject().toString());
        Assert.assertEquals("Should contain timestamp", timeOfTests, errorFromJson.getTimestamp());


        final Set<AnalyticsAttribute> sessionAttributes = AnalyticsControllerImpl.getInstance().getSessionAttributes();
        final HashMap<String,Object> event = getApplicationExitInfoEvent();
        error = new Error( sessionAttributes, event);
        errorFromJson =  Error.errorFromJsonString(error.asJsonObject().toString());

        JsonObject json = errorFromJson.asJsonObject();
        Assert.assertTrue("Should contain analytics structs", json.has("sessionAttributes") && json.has("analyticsEvents"));
        // We add 1 to the sessionAttributes size since 'OBFUSCATED' is only added as a session attribute on crash
        Assert.assertEquals("Should contain session attributes", json.getAsJsonObject("sessionAttributes").entrySet().size(), sessionAttributes.size()+1);
        Assert.assertEquals("Should contain analytics events", json.getAsJsonArray("analyticsEvents").size(), 1);
    }

    private HashMap<String, Object> getApplicationExitInfoEvent() {

        HashMap<String, Object> event = new HashMap<>();
        event.put(AnalyticsAttribute.EVENT_TYPE_ATTRIBUTE, AnalyticsEvent.EVENT_TYPE_MOBILE_APPLICATION_EXIT);
        event.put(AnalyticsAttribute.APP_EXIT_TIMESTAMP_ATTRIBUTE, timeOfTests);
        event.put(AnalyticsAttribute.APP_EXIT_REASON_ATTRIBUTE, 6);
        event.put(AnalyticsAttribute.APP_EXIT_IMPORTANCE_ATTRIBUTE, 12);
        event.put(AnalyticsAttribute.APP_EXIT_IMPORTANCE_STRING_ATTRIBUTE, "ANR");
        event.put(AnalyticsAttribute.APP_EXIT_DESCRIPTION_ATTRIBUTE, "Application Not Responding");
        event.put(AnalyticsAttribute.APP_EXIT_PROCESS_NAME_ATTRIBUTE, "com.newrelic.android.error");

        event.put(AnalyticsAttribute.SESSION_ID_ATTRIBUTE,"testSessionId");
        event.put(AnalyticsAttribute.APP_EXIT_ID_ATTRIBUTE, "testExitId");
        event.put(AnalyticsAttribute.APP_EXIT_PROCESS_ID_ATTRIBUTE, 100);

        return event;
    }

    @Test
    public void testAndroidBuildId() throws Exception {
        error = new Error( "buildId", timeOfTests);
        String buildId = Error.getSafeBuildId();
        Assert.assertEquals("Android buildId should be injected", buildId, NewRelicConfig.getBuildId());

        // set in agent-core/build.gradle
        if (Agent.getMonoInstrumentationFlag().equals("YES")) {
            try {
                UUID uuid = UUID.fromString(buildId);
                Assert.assertEquals("Android buildId is a generated UUID", uuid.toString().toLowerCase(), buildId.toLowerCase());
            } catch (Exception e) {
                Assert.fail("Android buildId should be a generated UUID");
            }
        } else {
            Assert.assertEquals("Android buildId should be injected", buildId, NewRelicConfig.getBuildId());
        }
    }




    @Test
    public void testGetSafeBuildId() {
        String safeBuildId = Error.getSafeBuildId();
        assertNotNull(safeBuildId);
        assertFalse(safeBuildId.isEmpty());
    }

    @After
    public void tearDown() {
        FeatureFlag.disableFeature(FeatureFlag.BackgroundReporting);
        setAppInBackground(false);
    }

    @Test
    public void backgroundedTrueAddsAttributeWhenFeatureEnabled() {
        FeatureFlag.enableFeature(FeatureFlag.BackgroundReporting);
        AEISessionMapper.AEISessionMeta meta = new AEISessionMapper.AEISessionMeta("session-bg", 42, true);

        Error error = new Error(AnalyticsControllerImpl.getInstance().getSessionAttributes(), getApplicationExitInfoEvent(), meta);

        assertTrue("background attribute should reflect persisted backgrounded=true",
                error.getSessionAttributes().stream()
                        .anyMatch(a -> AnalyticsAttribute.BACKGROUND_ATTRIBUTE_NAME.equals(a.getName()) && a.getBooleanValue()));
    }

    @Test
    public void backgroundedFalseOmitsAttributeWhenFeatureEnabled() {
        FeatureFlag.enableFeature(FeatureFlag.BackgroundReporting);
        AEISessionMapper.AEISessionMeta meta = new AEISessionMapper.AEISessionMeta("session-fg", 42, false);

        Error error = new Error(AnalyticsControllerImpl.getInstance().getSessionAttributes(), getApplicationExitInfoEvent(), meta);

        assertFalse("background attribute should not be present when persisted backgrounded=false",
                error.getSessionAttributes().stream()
                        .anyMatch(a -> AnalyticsAttribute.BACKGROUND_ATTRIBUTE_NAME.equals(a.getName())));
    }

    @Test
    public void backgroundedTrueWithFeatureDisabledOmitsAttribute() {
        FeatureFlag.disableFeature(FeatureFlag.BackgroundReporting);
        AEISessionMapper.AEISessionMeta meta = new AEISessionMapper.AEISessionMeta("session-bg", 42, true);

        Error error = new Error(AnalyticsControllerImpl.getInstance().getSessionAttributes(), getApplicationExitInfoEvent(), meta);

        assertFalse("background attribute should not be set when BackgroundReporting is disabled",
                error.getSessionAttributes().stream()
                        .anyMatch(a -> AnalyticsAttribute.BACKGROUND_ATTRIBUTE_NAME.equals(a.getName())));
    }

    @Test
    public void backgroundedTrueIncrementsMetric() {
        FeatureFlag.enableFeature(FeatureFlag.BackgroundReporting);
        StatsEngine.notice().getStatsMap().clear();
        AEISessionMapper.AEISessionMeta meta = new AEISessionMapper.AEISessionMeta("session-bg", 42, true);

        new Error(AnalyticsControllerImpl.getInstance().getSessionAttributes(), getApplicationExitInfoEvent(), meta);

        assertTrue("BACKGROUND_CRASH_COUNT metric should be incremented",
                StatsEngine.notice().getStatsMap().containsKey(MetricNames.BACKGROUND_CRASH_COUNT));
    }

    @Test
    public void backgroundedFalseOverridesLiveBackgroundState() {
        FeatureFlag.enableFeature(FeatureFlag.BackgroundReporting);
        setAppInBackground(true); // live state says background, but persisted session was foreground
        AEISessionMapper.AEISessionMeta meta = new AEISessionMapper.AEISessionMeta("session-fg", 42, false);

        Error error = new Error(AnalyticsControllerImpl.getInstance().getSessionAttributes(), getApplicationExitInfoEvent(), meta);

        assertFalse("persisted backgrounded=false should override live background state",
                error.getSessionAttributes().stream()
                        .anyMatch(a -> AnalyticsAttribute.BACKGROUND_ATTRIBUTE_NAME.equals(a.getName())));
    }

    @Test
    public void nullSessionMetaLeavesBackgroundAttributeUnchanged() {
        FeatureFlag.enableFeature(FeatureFlag.BackgroundReporting);
        // With null sessionMeta and app in foreground (default), no background attr expected
        Error error = new Error(AnalyticsControllerImpl.getInstance().getSessionAttributes(), getApplicationExitInfoEvent(), null);

        assertFalse("no background attribute when app is in foreground and sessionMeta is null",
                error.getSessionAttributes().stream()
                        .anyMatch(a -> AnalyticsAttribute.BACKGROUND_ATTRIBUTE_NAME.equals(a.getName())));
    }

    private static void setAppInBackground(boolean background) {
        try {
            Field foregrounded = ApplicationStateMonitor.class.getDeclaredField("foregrounded");
            foregrounded.setAccessible(true);
            ((AtomicBoolean) foregrounded.get(ApplicationStateMonitor.getInstance())).set(!background);
        } catch (Exception e) {
            throw new RuntimeException("Could not set ApplicationStateMonitor background state", e);
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