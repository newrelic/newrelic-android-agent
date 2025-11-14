/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.crash;

import com.google.gson.JsonObject;
import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.analytics.AnalyticsControllerImpl;
import com.newrelic.agent.android.harvest.DataToken;
import com.newrelic.agent.android.harvest.Harvest;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.payload.PayloadController;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.test.mock.Providers;
import com.newrelic.agent.android.test.stub.StubAgentImpl;
import com.newrelic.agent.android.test.stub.StubAnalyticsAttributeStore;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.concurrent.Future;

import javax.net.ssl.HttpsURLConnection;

@RunWith(JUnit4.class)
public class CrashReporterTests {

    private static AgentConfiguration agentConfiguration;
    private static CrashStore crashStore;

    private CrashReporter crashReporter;
    private Crash crash;
    private CrashSender crashSender;

    @Before
    public void setUp() throws Exception {
        crashStore = new TestCrashStore();

        agentConfiguration = new AgentConfiguration();
        agentConfiguration.setApplicationToken(CrashReporterTests.class.getSimpleName());
        agentConfiguration.setEnableAnalyticsEvents(true);
        agentConfiguration.setReportCrashes(true);
        agentConfiguration.setCrashStore(crashStore);
        agentConfiguration.setAnalyticsAttributeStore(new StubAnalyticsAttributeStore());

        crashReporter = TestCrashReporter.initialize(agentConfiguration);

        Harvest.initialize(agentConfiguration);
        Harvest.setHarvestConfiguration(Providers.provideHarvestConfiguration());

        crash = Mockito.spy(new Crash(new RuntimeException("testStoreSupportabilityMetrics")));
        crashSender = Mockito.spy(new CrashSender(crash, agentConfiguration));

        TestCrashReporter.resetForTesting();
        StatsEngine.reset();
    }

    @After
    public void tearDown() throws Exception {
        AnalyticsControllerImpl.shutdown();
    }

    @Test
    public void testInitialization() {
        agentConfiguration.setReportCrashes(true);
        TestCrashReporter.initialize(agentConfiguration);
        Assert.assertEquals("Should initialize with agent configuration.", agentConfiguration, TestCrashReporter.getInstance().getAgentConfiguration());
        Assert.assertTrue("Should default to crash reporting enabled.", TestCrashReporter.getInstance().getAgentConfiguration().getReportCrashes());
    }

    @Test
    public void testDisableCrashReporting() {
        FeatureFlag.disableFeature(FeatureFlag.CrashReporting);
        TestCrashReporter.initialize(agentConfiguration);
        Assert.assertFalse("Should disable crash reporting on init.", TestCrashReporter.getInstance().isEnabled());
        Assert.assertNull("Should not install exception handler.", Thread.getDefaultUncaughtExceptionHandler());
    }

    @Test
    public void testDisableJustInTimeCrashReporting() {
        agentConfiguration.setReportCrashes(false);
        crashReporter = TestCrashReporter.initialize(agentConfiguration);
        Assert.assertTrue("Should enable crash reporting on init.", TestCrashReporter.getInstance().isEnabled());

        crashReporter.start();
        Assert.assertFalse("Should disable JIT crash reporting on start.", TestCrashReporter.getReportCrashes());

        crashReporter.storeAndReportCrash(new Crash(new RuntimeException("testStoreExistingCrashes")),false);
        crashReporter.storeAndReportCrash(new Crash(new RuntimeException("testStoreExistingCrashes")),false);
        crashReporter.storeAndReportCrash(new Crash(new RuntimeException("testStoreExistingCrashes")),false);
        Assert.assertEquals("CrashStore should contain 3 crash.", 3, crashStore.count());
        Mockito.verify(crashReporter, Mockito.never()).reportCrash(ArgumentMatchers.any(Crash.class));
    }

    @Test
    public void testStoreExistingCrashes() {
        Crash crash = new Crash(new RuntimeException("testStoreExistingCrashes"));
        crashStore.store(crash);
        Assert.assertEquals("CrashStore should contain 1 crash.", 1, crashStore.count());

        agentConfiguration.setCrashStore(crashStore);
        TestCrashReporter.initialize(agentConfiguration);

        Assert.assertEquals("CrashReporter should contain 1 crash.", 1, TestCrashReporter.getStoredCrashCount());
        Assert.assertTrue("Should not duplicate stored crash.", TestCrashReporter.fetchAllCrashes().contains(crash));
    }

    @Test
    public void testReportSavedCrashes() {
        TestCrashReporter.initialize(agentConfiguration);

        for (Integer i = 0; i < 3; i++) {
            Crash crash = new Crash(new RuntimeException("testStoreExistingCrash" + i));
            crashStore.store(crash);
        }
        Assert.assertEquals("Should contain 3 crashes", 3, TestCrashReporter.getStoredCrashCount());

        crashReporter.reportSavedCrashes();
        Mockito.verify(crashReporter, Mockito.times(3)).reportCrash(ArgumentMatchers.any(Crash.class));
    }

    @Test
    public void testRemoveStaleCrashes() {
        TestCrashReporter.initialize(agentConfiguration);

        for (Integer i = 0; i < 3; i++) {
            Crash crash = new Crash(new RuntimeException("testStoreExistingCrash" + i));
            for (int j = 0; j < Crash.MAX_UPLOAD_COUNT; j++) {
                crash.incrementUploadCount();
            }
            crashStore.store(crash);
        }
        Assert.assertEquals("Should contain 3 crashes", 3, TestCrashReporter.getStoredCrashCount());

        crashReporter.reportSavedCrashes();

        Assert.assertEquals("Should contain no crashes", 0, TestCrashReporter.getStoredCrashCount());
        Assert.assertTrue("Should contain 'stale crash removed' supportability metric", StatsEngine.get().getStatsMap().containsKey(MetricNames.SUPPORTABILITY_CRASH_REMOVED_STALE));

        Mockito.verify(crashReporter, Mockito.times(0)).reportCrash(ArgumentMatchers.any(Crash.class));
    }

    @Test
    public void testCrashUpload() throws Exception {
        HttpURLConnection connection = getMockedConnection();

        Mockito.doReturn(HttpsURLConnection.HTTP_OK).when(connection).getResponseCode();
        crashSender.onRequestResponse(connection);
        Assert.assertTrue("Should contain 200 supportability metric", StatsEngine.get().getStatsMap().containsKey(MetricNames.SUPPORTABILITY_CRASH_UPLOAD_TIME));

        Mockito.reset();
        StatsEngine.get().getStatsMap().clear();
        Mockito.doReturn(HttpsURLConnection.HTTP_ACCEPTED).when(connection).getResponseCode();
        crashSender.onRequestResponse(connection);
        Assert.assertTrue("Should contain 202 supportability metric", StatsEngine.get().getStatsMap().containsKey(MetricNames.SUPPORTABILITY_CRASH_UPLOAD_TIME));

        Mockito.reset();
        StatsEngine.get().getStatsMap().clear();
        Mockito.doReturn(HttpsURLConnection.HTTP_CREATED).when(connection).getResponseCode();
        crashSender.onRequestResponse(connection);
        Assert.assertFalse("Should not contain 202 supportability metric", StatsEngine.get().getStatsMap().containsKey(MetricNames.SUPPORTABILITY_CRASH_UPLOAD_TIME));
    }

    @Test
    public void testFailedCrashUpload() throws Exception {
        HttpURLConnection connection = getMockedConnection();

        Mockito.doReturn(HttpsURLConnection.HTTP_INTERNAL_ERROR).when(connection).getResponseCode();
        crashSender.onRequestResponse(connection);
        Assert.assertTrue("Should contain 500 supportability metric", StatsEngine.get().getStatsMap().containsKey(MetricNames.SUPPORTABILITY_CRASH_FAILED_UPLOAD));
    }

    @Test
    public void testTimeoutCrashUpload() throws Exception {
        HttpURLConnection connection = getMockedConnection();

        Mockito.doReturn(HttpsURLConnection.HTTP_CLIENT_TIMEOUT).when(connection).getResponseCode();
        crashSender.onRequestResponse(connection);
        Assert.assertTrue("Should contain 408 supportability metric",
                StatsEngine.get().getStatsMap().containsKey(MetricNames.SUPPORTABILITY_CRASH_UPLOAD_TIMEOUT));
    }

    @Test
    public void testThrottledCrashUpload() throws Exception {
        HttpURLConnection connection = getMockedConnection();

        Mockito.doReturn(429).when(connection).getResponseCode();
        crashSender.onRequestResponse(connection);
        Assert.assertTrue("Should contain 429 supportability metric",
                StatsEngine.get().getStatsMap().containsKey(MetricNames.SUPPORTABILITY_CRASH_UPLOAD_THROTTLED));
    }

    @Test
    public void testStoreSupportabilityMetrics() throws Exception {
        HttpURLConnection connection = getMockedConnection();

        Mockito.doReturn(HttpsURLConnection.HTTP_OK).when(connection).getResponseCode();
        crashSender.onRequestResponse(connection);
        Assert.assertTrue("Should contain 200 supportability metric", StatsEngine.get().getStatsMap().containsKey(MetricNames.SUPPORTABILITY_CRASH_UPLOAD_TIME));

        Mockito.reset();
        StatsEngine.get().getStatsMap().clear();
        Mockito.doReturn(HttpsURLConnection.HTTP_ACCEPTED).when(connection).getResponseCode();
        crashSender.onRequestResponse(connection);
        Assert.assertTrue("Should contain 202 supportability metric", StatsEngine.get().getStatsMap().containsKey(MetricNames.SUPPORTABILITY_CRASH_UPLOAD_TIME));

        Mockito.reset();
        StatsEngine.get().getStatsMap().clear();
        Mockito.doReturn(HttpsURLConnection.HTTP_CREATED).when(connection).getResponseCode();
        crashSender.onRequestResponse(connection);
        Assert.assertFalse("Should not contain 201 success supportability metric", StatsEngine.get().getStatsMap().containsKey(MetricNames.SUPPORTABILITY_CRASH_UPLOAD_TIME));

        crashSender.onFailedUpload("boo");
        Assert.assertTrue("Should contain 500 supportability metric", StatsEngine.get().getStatsMap().containsKey(MetricNames.SUPPORTABILITY_CRASH_FAILED_UPLOAD));
    }

    @Test
    public void testReportSessionAttributes() throws Exception {
        agentConfiguration.setEnableAnalyticsEvents(false);

        Harvest harvest = new Harvest();
        Harvest.setInstance(harvest);
        harvest.initializeHarvester(agentConfiguration);
        Harvest.start();

        Thread.sleep(10);   // the session needs some duration (was too fast)

        AnalyticsControllerImpl.initialize(agentConfiguration, new StubAgentImpl());

        TestCrashReporter.getUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), new RuntimeException());

        Harvest.stop();

        CrashStore store = agentConfiguration.getCrashStore();
        Assert.assertEquals("Should contain 1 crash", 1, crashStore.count());
        JsonObject json = store.fetchAll().get(0).asJsonObject();
        Assert.assertTrue("Should contain session attributes", json.has("sessionAttributes"));
        Assert.assertTrue("Should contain session duration attribute", json.get("sessionAttributes").getAsJsonObject().has(AnalyticsAttribute.SESSION_DURATION_ATTRIBUTE));
    }

    @Test
    public void testEventsAndAttrsInCrashPayload() throws Exception {
        agentConfiguration.setEnableAnalyticsEvents(false);

        Harvest harvest = new Harvest();
        Harvest.setInstance(harvest);
        harvest.initializeHarvester(agentConfiguration);
        Harvest.start();

        // THe harvest timer runs on its own thread, which may not get started before we i
        // generate a crash. So wait a while so sessionDuraation is non-zero
        Thread.sleep(3000);

        AnalyticsControllerImpl.initialize(agentConfiguration, new StubAgentImpl());

        TestCrashReporter.setReportCrashes(false);  // don't upload the crash for testing
        TestCrashReporter.getUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), new RuntimeException());

        Harvest.stop();

        CrashStore store = agentConfiguration.getCrashStore();
        Assert.assertEquals("Should contain 1 crash", 1, crashStore.count());
        JsonObject json = store.fetchAll().get(0).asJsonObject();
        Assert.assertTrue("Should contain session attributes", json.has("sessionAttributes"));
        Assert.assertTrue("Should contain analytics events", json.has("analyticsEvents"));
    }

    @Test
    public void testOpportunisticUpload() throws Exception {
        Crash crash = new Crash(new RuntimeException("testStoreSupportabilityMetrics"));
        CrashSender crashSender = new CrashSender(crash, agentConfiguration);
        Assert.assertEquals(crashSender.shouldUploadOpportunistically(), Agent.hasReachableNetworkConnection(null));
    }

    @Test
    public void shouldNotReportCrashIfDataTokenIsInvalid() throws Exception {
        PayloadController.initialize(agentConfiguration);
        TestCrashReporter.setReportCrashes(true);

        crash.setDataToken(new DataToken(0, 0));

        Future future = TestCrashReporter.getInstance().reportCrash(crash);

        Assert.assertNull("No crash should be reported, there is no valid data token.", future);
    }

    @Test
    public void testShouldNotReportSavedCrashesIfDataTokenIsInvalid() throws Exception {
        TestCrashReporter.initialize(agentConfiguration);

        Crash crash = new Crash(new RuntimeException("testStoreExistingCrash"));
        crashStore.store(crash);
        Assert.assertEquals("Should contain 1 crashes", 1, TestCrashReporter.getStoredCrashCount());

        crashReporter.reportSavedCrashes();
        Mockito.verify(crashReporter, Mockito.times(1)).reportCrash(ArgumentMatchers.any(Crash.class));
        Assert.assertEquals("Should still contain 1 crashes", 1, TestCrashReporter.getStoredCrashCount());
    }


    @Test
    public void shouldReportCrashImmediately() throws Exception {
        Harvest.initialize(agentConfiguration);
        Harvest.setHarvestConfiguration(Providers.provideHarvestConfiguration());
        PayloadController.initialize(agentConfiguration);
        TestCrashReporter.setReportCrashes(true);

        Future future = TestCrashReporter.getInstance().reportCrash(crash);

        Assert.assertNotNull("Crash should be immediately placed on executor", future);
    }

    @Test
    public void shouldNotChangeDataTokenAfterHarvestConfigurationChange() throws Exception {
        Harvest.initialize(agentConfiguration);
        Harvest.setHarvestConfiguration(Providers.provideHarvestConfiguration());
        PayloadController.initialize(agentConfiguration);

        Crash crash1 = new Crash(new RuntimeException("testStoreSupportabilityMetrics"));

        JsonObject crash1Json = crash1.asJsonObject();

        // Change harvest configuration after Connect
        Harvest.setHarvestConfiguration(Providers.provideHarvestConfigurationAfterConnect());

        Crash crash2 = Crash.crashFromJsonString(crash1Json.toString());


        Assert.assertArrayEquals("Crash should have the same data token", crash1.getDataToken().asIntArray(), crash2.getDataToken().asIntArray());
        Crash crash3 = new Crash(new RuntimeException("testStoreSupportabilityMetrics"));

        Assert.assertNotEquals("Crash should have a different data token", crash1.getDataToken().getAgentId(), crash3.getDataToken().getAgentId());

    }


    private HttpURLConnection getMockedConnection() throws IOException {
        HttpURLConnection connection = Mockito.spy(crashSender.getConnection());

        Mockito.doReturn(false).when(connection).getDoOutput();
        Mockito.doReturn(false).when(connection).getDoInput();
        Mockito.doReturn(HttpsURLConnection.HTTP_OK).when(connection).getResponseCode();
        Mockito.doNothing().when(connection).connect();

        return connection;
    }

    private static class TestCrashReporter extends CrashReporter {
        public static CrashReporter initialize(AgentConfiguration agentConfiguration) {
            CrashReporter.initialize(agentConfiguration);
            instance.set(Mockito.spy(new TestCrashReporter(agentConfiguration)));
            return instance.get();
        }

        public TestCrashReporter(AgentConfiguration agentConfiguration) {
            super(agentConfiguration);
        }

        public static int getStoredCrashCount() {
            if (isInitialized()) {
                return instance.get().crashStore.count();
            }
            return 0;
        }

        public static List<Crash> fetchAllCrashes() {
            if (isInitialized()) {
                return instance.get().crashStore.fetchAll();
            }
            return null;
        }

        public static void resetForTesting() {
            Thread.setDefaultUncaughtExceptionHandler(null);
            FeatureFlag.enableFeature(FeatureFlag.CrashReporting);
        }
    }
}
