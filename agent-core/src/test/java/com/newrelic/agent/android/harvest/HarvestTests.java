/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.harvest;

import static com.newrelic.agent.android.analytics.AnalyticsAttributeTests.getAttributeByName;
import static com.newrelic.agent.android.harvest.type.HarvestErrorCodes.NSURLErrorBadServerResponse;

import com.google.gson.JsonArray;
import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.analytics.AnalyticsControllerImpl;
import com.newrelic.agent.android.analytics.AnalyticsEvent;
import com.newrelic.agent.android.background.ApplicationStateMonitor;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.logging.ConsoleAgentLog;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.test.mock.Providers;
import com.newrelic.agent.android.test.mock.TestTrustManager;
import com.newrelic.agent.android.test.stub.StubAgentImpl;
import com.newrelic.agent.android.test.stub.StubAnalyticsAttributeStore;
import com.newrelic.agent.android.tracing.ActivityTrace;
import com.newrelic.agent.android.tracing.Trace;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@RunWith(JUnit4.class)
public class HarvestTests {

    private final static Lock lock = new ReentrantLock();
    private final static String COLLECTOR_HOST = "staging-mobile-collector.newrelic.com";
    private final static String ENABLED_APP_TOKEN_STAGING = "AAa2d4baa1094bf9049bb22895935e46f85c45c211";
    private final static String DISABLED_APP_TOKEN_PROD = "AA06d1964231f6c881cedeaa44e837bde4079c683d";
    private static AgentConfiguration config;

    @BeforeClass
    public static void setUpClass() {
        config = Providers.provideAgentConfiguration();
        config.setEnableAnalyticsEvents(true);
        config.setAnalyticsAttributeStore(new StubAnalyticsAttributeStore());
        AgentLogManager.setAgentLog(new ConsoleAgentLog());
        TestTrustManager.setUpSocketFactory();
    }

    @Before
    public void setUp() throws Exception {
        TestHarvest.setInstance(new TestHarvest());
        Harvest.initialize(config);
        StatsEngine.reset();
    }

    @After
    public void tearDown() throws Exception {
        Harvest.shutdown();
    }

    @Test
    public void testUninitializedToConnected() {
        Harvester harvester = createTestHarvester(ENABLED_APP_TOKEN_STAGING, COLLECTOR_HOST);
        harvester.getHarvestConnection().useSsl(false);

        Assert.assertEquals(Harvester.State.UNINITIALIZED, harvester.getCurrentState());

        // DISCONNECTED -> CONNECTED
        harvester.execute();
        Assert.assertEquals(Harvester.State.CONNECTED, harvester.getCurrentState());
    }

    @Test
    public void testUninitializedToDisconnectedToDisabled() {
        Harvester harvester = createTestHarvester(DISABLED_APP_TOKEN_PROD, null);

        HarvestResponse mockedResponse = Mockito.spy(new HarvestResponse());
        Mockito.doReturn(HarvestResponse.Code.FORBIDDEN).when(mockedResponse).getResponseCode();
        Mockito.doReturn("DISABLE_NEW_RELIC").when(mockedResponse).getResponseBody();
        Mockito.doReturn(mockedResponse).when(harvester.getHarvestConnection()).sendConnect();

        Assert.assertEquals(Harvester.State.UNINITIALIZED, harvester.getCurrentState());

        harvester.execute();
        Assert.assertEquals(Harvester.State.DISABLED, harvester.getCurrentState());
    }

    @Test
    public void testConnectAndSendData() throws Exception {
        Harvester harvester = createTestHarvester(ENABLED_APP_TOKEN_STAGING, COLLECTOR_HOST);

        harvester.getHarvestConnection().useSsl(false);
        Assert.assertEquals(Harvester.State.UNINITIALIZED, harvester.getCurrentState());

        harvester.execute();
        Assert.assertEquals(Harvester.State.CONNECTED, harvester.getCurrentState());

        addHarvestData(harvester.getHarvestData());
        harvester.execute();
        Thread.sleep(100);
        harvester.execute();

        Assert.assertEquals(Harvester.State.CONNECTED, harvester.getCurrentState());
    }

    @Test
    public void testDisconnectTriggersReconnect() throws Exception {
        Harvester harvester = createTestHarvester(ENABLED_APP_TOKEN_STAGING, COLLECTOR_HOST);
        harvester.getHarvestConnection().useSsl(false);

        Assert.assertEquals(Harvester.State.UNINITIALIZED, harvester.getCurrentState());

        harvester.execute();
        Assert.assertEquals(Harvester.State.CONNECTED, harvester.getCurrentState());

        // Nuke the DataToken which should cause a disconnect.
        HarvestData harvestData = harvester.getHarvestData();
        harvestData.setDataToken(new DataToken(-1, 2118782));
        addHarvestData(harvester.getHarvestData());
        harvester.execute();
        Thread.sleep(100);
        Assert.assertEquals(Harvester.State.DISCONNECTED, harvester.getCurrentState());
    }

    @Test
    public void testHarvestListener() {
        TestHarvestAdapter testAdapter = new TestHarvestAdapter();
        Harvester harvester = createTestHarvester(ENABLED_APP_TOKEN_STAGING, COLLECTOR_HOST);
        harvester.addHarvestListener(testAdapter);

        harvester.start();
        Assert.assertTrue(testAdapter.didStart());

        harvester.execute();
        Assert.assertTrue(testAdapter.didHarvest());

        harvester.stop();
        Assert.assertTrue(testAdapter.didStop());
    }

    @Test
    public void testHarvestWhenDisabledListener() {
        TestHarvestAdapter testAdapter = new TestHarvestAdapter();
        Harvester harvester = createTestHarvester("bogustoken", null);

        HarvestResponse mockedResponse = Mockito.spy(new HarvestResponse());
        Mockito.doReturn(HarvestResponse.Code.FORBIDDEN).when(mockedResponse).getResponseCode();
        Mockito.doReturn(mockedResponse).when(harvester.getHarvestConnection()).sendConnect();

        harvester.addHarvestListener(testAdapter);
        harvester.execute();
        Assert.assertTrue(testAdapter.didError());

        Mockito.when(mockedResponse.getResponseBody()).thenReturn("DISABLE_NEW_RELIC");
        harvester.execute();
        Assert.assertTrue(testAdapter.disabled());
    }

    @Test
    public void testHarvestTimeOut() {
        Harvester harvester = createTestHarvester(ENABLED_APP_TOKEN_STAGING, COLLECTOR_HOST);
        HarvestResponse mockedResponse = Mockito.spy(new HarvestResponse());
        TestHarvestAdapter testAdapter = new TestHarvestAdapter();

        Mockito.doReturn(true).when(mockedResponse).isError();
        Mockito.doReturn(HarvestResponse.Code.REQUEST_TIMEOUT).when(mockedResponse).getResponseCode();
        Mockito.doReturn(mockedResponse).when(harvester.getHarvestConnection()).sendData(Mockito.any(HarvestData.class));

        harvester.addHarvestListener(testAdapter);
        harvester.transition(Harvester.State.CONNECTED);
        harvester.execute();
        Assert.assertTrue(testAdapter.didDisconnect());
        Assert.assertTrue("Should contain invalid data token supportability metric",
                StatsEngine.SUPPORTABILITY.getStatsMap().containsKey(MetricNames.SUPPORTABILITY_INVALID_DATA_TOKEN));
    }

    @Test
    public void testHarvestThrottled() {
        TestHarvestAdapter testAdapter = new TestHarvestAdapter();
        Harvester harvester = createTestHarvester(ENABLED_APP_TOKEN_STAGING, COLLECTOR_HOST);
        HarvestResponse mockedResponse = Mockito.spy(new HarvestResponse());

        Mockito.doReturn(true).when(mockedResponse).isError();
        Mockito.doReturn(HarvestResponse.Code.TOO_MANY_REQUESTS).when(mockedResponse).getResponseCode();
        Mockito.doReturn(mockedResponse).when(harvester.getHarvestConnection()).sendData(Mockito.any(HarvestData.class));

        harvester.addHarvestListener(testAdapter);
        harvester.transition(Harvester.State.CONNECTED);
        harvester.execute();
        Assert.assertTrue(testAdapter.didDisconnect());
        Assert.assertTrue("Should contain invalid data token supportability metric",
                StatsEngine.SUPPORTABILITY.getStatsMap().containsKey(MetricNames.SUPPORTABILITY_INVALID_DATA_TOKEN));
    }

    @Test
    public void testActivityTraceConfigurationHarvestLimit() {
        TestHarvest testHarvest = new TestHarvest();

        TestHarvest.setInstance(testHarvest);

        TestHarvest.initialize(new AgentConfiguration());
        TestHarvest.setHarvestConfiguration(new HarvestConfiguration());

        TestHarvest.addActivityTrace(new TestActivityTrace());
        TestHarvest.addActivityTrace(new TestActivityTrace());

        Assert.assertEquals(testHarvest.getActivityTraceLimit(), testHarvest.getActivityTraceCount());
    }

    @Test
    public void testShouldCollectActivityTraces() {
        TestHarvest testHarvest = new TestHarvest();
        TestHarvest.setInstance(testHarvest);
        TestHarvest.initialize(new AgentConfiguration());

        Assert.assertTrue(TestHarvest.shouldCollectActivityTraces());

        // All fields should be zero/empty
        HarvestConfiguration configuration = testHarvest.getConfiguration();
        Assert.assertNotNull(configuration.getAt_capture());
        configuration.getAt_capture().setMaxTotalTraceCount(0);

        Assert.assertFalse(TestHarvest.shouldCollectActivityTraces());
    }

    @Test
    public void testExpireHttpTransactions() {
        lock.lock();
        try {
            TestHarvest testHarvest = new TestHarvest();
            TestHarvest.setInstance(testHarvest);

            TestHarvest.initialize(new AgentConfiguration());

            HttpTransaction txn = new HttpTransaction();
            txn.setTimestamp(System.currentTimeMillis());


            HarvestConfiguration configuration = TestHarvest.getHarvestConfiguration();
            configuration.setReport_max_transaction_count(5);
            configuration.setReport_max_transaction_age(1);

            TestHarvest.addHttpTransaction(txn);

            testHarvest.getHarvester().expireHarvestData();
            Assert.assertEquals(1, testHarvest.getHarvestData().getHttpTransactions().count());
            Thread.sleep(1100);
            testHarvest.getHarvester().execute();
            Assert.assertEquals(0, testHarvest.getHarvestData().getHttpTransactions().count());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }
    }

    @Test
    public void testRequestErrorHarvestCreatesEvents() {
        FeatureFlag.enableFeature(FeatureFlag.NetworkRequests);
        FeatureFlag.enableFeature(FeatureFlag.NetworkErrorRequests);

        lock.lock();
        try {
            AnalyticsControllerImpl controller = AnalyticsControllerImpl.getInstance();
            AnalyticsControllerImpl.initialize(config, new StubAgentImpl());
            controller.getEventManager().empty();

            Collection<AnalyticsEvent> events = controller.getEventManager().getQueuedEvents();
            Assert.assertEquals("No network events representing the transaction should be present in the queue.", 0, events.size());

            controller.getEventManager().empty();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }

        FeatureFlag.disableFeature(FeatureFlag.NetworkRequests);
        FeatureFlag.disableFeature(FeatureFlag.NetworkErrorRequests);
    }

    @Test
    public void testRequestFailureHarvestCreatesEvents() {
        FeatureFlag.enableFeature(FeatureFlag.NetworkRequests);
        FeatureFlag.enableFeature(FeatureFlag.NetworkErrorRequests);

        lock.lock();
        try {
            AnalyticsControllerImpl controller = AnalyticsControllerImpl.getInstance();
            AnalyticsControllerImpl.initialize(config, new StubAgentImpl());
            controller.getEventManager().empty();

            Collection<AnalyticsEvent> events = controller.getEventManager().getQueuedEvents();
            Assert.assertEquals("No events representing the Http error should be present in the queue.", 0, events.size());

            Iterator<AnalyticsEvent> iter = events.iterator();
            AnalyticsEvent event = iter.next();
            Assert.assertEquals("Events should contain network error event.", AnalyticsEvent.EVENT_TYPE_MOBILE_REQUEST_ERROR, event.getEventType());

            AnalyticsAttribute errorCode = getAttributeByName(event.getAttributeSet(), AnalyticsAttribute.NETWORK_ERROR_CODE_ATTRIBUTE);
            Assert.assertTrue("Event should have a error code of NSURLErrorBadServerResponse", errorCode.getDoubleValue() == NSURLErrorBadServerResponse);

            controller.getEventManager().empty();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }

        FeatureFlag.disableFeature(FeatureFlag.NetworkRequests);
        FeatureFlag.disableFeature(FeatureFlag.NetworkErrorRequests);
    }

    @Test
    public void testTransactionFailureHarvestCreatesEvents() {
        FeatureFlag.enableFeature(FeatureFlag.NetworkRequests);
        FeatureFlag.enableFeature(FeatureFlag.NetworkErrorRequests);

        lock.lock();
        try {

            AnalyticsControllerImpl controller = AnalyticsControllerImpl.getInstance();
            AnalyticsControllerImpl.initialize(config, new StubAgentImpl());
            controller.getEventManager().empty();

            HttpTransaction txn = Providers.provideHttpTransaction();
            txn.setErrorCode(-1110);
            Harvest.addHttpTransaction(txn);

            Collection<AnalyticsEvent> events = controller.getEventManager().getQueuedEvents();
            Assert.assertEquals("One network transaction event should be present in the queue.", 1, events.size());

            Iterator<AnalyticsEvent> iter = events.iterator();
            AnalyticsEvent event = iter.next();
            Assert.assertEquals("Events should contain a NetworkRequestError.", AnalyticsEvent.EVENT_TYPE_MOBILE_REQUEST_ERROR, event.getEventType());

            AnalyticsAttribute networkErrorCode = getAttributeByName(event.getAttributeSet(), AnalyticsAttribute.NETWORK_ERROR_CODE_ATTRIBUTE);
            Assert.assertTrue("Event should have an errorCode of -1110", networkErrorCode.getDoubleValue() == -1110);

            controller.getEventManager().empty();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }

        FeatureFlag.disableFeature(FeatureFlag.NetworkRequests);
        FeatureFlag.disableFeature(FeatureFlag.NetworkErrorRequests);
    }

    @Test
    public void testTransactionHarvestNetworkRequest() {
        FeatureFlag.enableFeature(FeatureFlag.NetworkRequests);
        FeatureFlag.enableFeature(FeatureFlag.NetworkErrorRequests);

        lock.lock();
        try {
            AnalyticsControllerImpl controller = AnalyticsControllerImpl.getInstance();
            AnalyticsControllerImpl.initialize(config, new StubAgentImpl());
            controller.getEventManager().empty();
            HttpTransaction txn = Providers.provideHttpTransaction();
            txn.setStatusCode(200);
            Harvest.addHttpTransaction(txn);

            Collection<AnalyticsEvent> events = controller.getEventManager().getQueuedEvents();

            Assert.assertEquals("One network transaction event should be present in the queue.", 1, events.size());

            Iterator<AnalyticsEvent> iter = events.iterator();
            AnalyticsEvent event = iter.next();

            Assert.assertNull("An HttpError event should not be queued.", getAttributeByName(events.iterator().next().getAttributeSet(), AnalyticsEvent.EVENT_TYPE_MOBILE_REQUEST_ERROR));

            Assert.assertEquals("Events should contain a MobileRequest.", AnalyticsEvent.EVENT_TYPE_MOBILE_REQUEST, event.getEventType());

            controller.getEventManager().empty();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }

        FeatureFlag.disableFeature(FeatureFlag.NetworkRequests);
        FeatureFlag.disableFeature(FeatureFlag.NetworkErrorRequests);
    }

    @Test
    public void testExpireActivityTraces() {
        lock.lock();
        try {
            TestHarvest testHarvest = new TestHarvest();
            TestHarvest.setInstance(testHarvest);
            TestHarvest.initialize(new AgentConfiguration());

            ActivityTrace activityTrace = new TestActivityTrace();

            TestHarvest.addActivityTrace(activityTrace);

            testHarvest.getConfiguration().setActivity_trace_max_report_attempts(1);

            Assert.assertEquals(1, testHarvest.getActivityTraceCount());
            activityTrace.incrementReportAttemptCount();
            testHarvest.getHarvester().expireActivityTraces();
            Assert.assertEquals(0, testHarvest.getActivityTraceCount());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }
    }

    @Test
    public void testExpireAnalyticsEvents() {
        lock.lock();
        try {
            TestHarvest testHarvest = new TestHarvest();
            TestHarvest.setInstance(testHarvest);

            AgentConfiguration mockedConfig = Mockito.spy(config);
            Mockito.doReturn(1000).when(mockedConfig.getPayloadTTL());
            TestHarvest.initialize(config);

            Assert.assertEquals(1, testHarvest.getHarvestData().getAnalyticsEvents().size());
            testHarvest.getHarvester().expireAnalyticsEvents();
            Thread.sleep(1100);
            Assert.assertEquals(0, testHarvest.getHarvestData().getAnalyticsEvents().size());

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }
    }

    @Test
    public void testSendBadAgentId() throws Exception {
        Harvester harvester = createTestHarvester(ENABLED_APP_TOKEN_STAGING, COLLECTOR_HOST);
        harvester.getHarvestConnection().useSsl(false);

        Assert.assertEquals(Harvester.State.UNINITIALIZED, harvester.getCurrentState());

        harvester.execute();
        Assert.assertEquals(Harvester.State.CONNECTED, harvester.getCurrentState());

        harvester.getHarvestData().getDataToken().setAgentId(0);
        addHarvestData(harvester.getHarvestData());

        harvester.execute();
        Thread.sleep(100);

        // Harvester should remain connected even after a bad Agent ID is set.
        // This may change in the future
        Assert.assertEquals(Harvester.State.DISCONNECTED, harvester.getCurrentState());
    }

    @Test
    public void testSendAgentIdForAnotherAccount() throws Exception {
        Harvester harvester = createTestHarvester(ENABLED_APP_TOKEN_STAGING, COLLECTOR_HOST);

        harvester.getHarvestConnection().useSsl(false);

        Assert.assertEquals(Harvester.State.UNINITIALIZED, harvester.getCurrentState());

        harvester.execute();
        Assert.assertEquals(Harvester.State.CONNECTED, harvester.getCurrentState());

        harvester.getHarvestData().getDataToken().setAgentId(90);
        addHarvestData(harvester.getHarvestData());

        harvester.execute();
        Thread.sleep(100);

        Assert.assertEquals(Harvester.State.CONNECTED, harvester.getCurrentState());
        Assert.assertEquals(90, harvester.getHarvestData().getDataToken().getAgentId());
    }

    @Test
    public void testSendBadLicenseKeyForDataPost() throws Exception {
        Harvester harvester = createTestHarvester(ENABLED_APP_TOKEN_STAGING, COLLECTOR_HOST);

        harvester.getHarvestConnection().useSsl(false);

        Assert.assertEquals(Harvester.State.UNINITIALIZED, harvester.getCurrentState());

        harvester.execute();
        Assert.assertEquals(Harvester.State.CONNECTED, harvester.getCurrentState());

        harvester.getHarvestConnection().setApplicationToken("BAD TOKEN");
        addHarvestData(harvester.getHarvestData());
        harvester.execute();
        Thread.sleep(100);
        Assert.assertEquals(Harvester.State.DISCONNECTED, harvester.getCurrentState());
    }

    @Test
    public void testSendBadLicenseKeyForConnect() {
        Harvester harvester = createTestHarvester("BAD TOKEN", COLLECTOR_HOST);

        harvester.getHarvestConnection().useSsl(false);
        Assert.assertEquals(Harvester.State.UNINITIALIZED, harvester.getCurrentState());

        harvester.execute();
        Assert.assertEquals(Harvester.State.DISCONNECTED, harvester.getCurrentState());
    }

    /**
     * Instances are used without checking throughout the code.
     * Don't allow null instances through the settor
     */
    @Test
    public void testInvalidInstance() {
        Assert.assertNotNull("Harvest instance should not be null", Harvest.getInstance());
        Harvest.setInstance(null);
        Assert.assertNotNull("Harvest instance should not be null", Harvest.getInstance());
    }

    @Test
    public void shouldNotHarvestInBackground() throws Exception {
        ApplicationStateMonitor applicationStateMonitor = ApplicationStateMonitor.getInstance();

        applicationStateMonitor.uiHidden();

        Thread.sleep(100);
        TestHarvest testHarvest = new TestHarvest();
        TestHarvest.setInstance(testHarvest);
        TestHarvest.initialize(new AgentConfiguration());
        TestHarvest.start();

        HarvestTimer harvestTimer = TestHarvest.getInstance().getHarvestTimer();
        Assert.assertFalse("Harvest should not start in background", harvestTimer.isRunning());

        TestHarvestAdapter testAdapter = new TestHarvestAdapter();
        Harvest.addHarvestListener(testAdapter);

        harvestTimer.tickNow();
        Assert.assertFalse("Harvest should not execute when app in background", testAdapter.didStart());
    }

    @Test
    public void testStartStop() {
        try {
            Harvest.start();
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Harvest start failed when harvest timer is null");
        }

        try {
            Harvest.stop();
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Harvest stop failed when harvest timer is null");
        }
    }

    static class TestHarvestAdapter extends HarvestAdapter {
        HashSet<String> events = new HashSet<>();

        @Override
        public void onHarvestStart() {
            events.add("started");
        }

        @Override
        public void onHarvestStop() {
            events.add("stopped");
        }

        @Override
        public void onHarvestBefore() {
            events.add("harvested");
        }

        @Override
        public void onHarvestError() {
            events.add("errored");
        }

        @Override
        public void onHarvestDisabled() {
            events.add("disabled");
        }

        @Override
        public void onHarvestDisconnected() {
            events.add("disconnected");
        }

        @Override
        public void onHarvestConfigurationChanged() {
            events.add("configUpdated");
        }

        @Override
        public void onHarvestComplete() {
            events.add("completed");
        }

        boolean didStart() {
            return events.contains("started");
        }

        boolean didStop() {
            return events.contains("stopped");
        }

        boolean didHarvest() {
            return events.contains("harvested");
        }

        boolean didComplete() {
            return events.contains("completed");
        }

        boolean didError() {
            return events.contains("errored");
        }

        boolean disabled() {
            return events.contains("disabled");
        }

        boolean didDisconnect() {
            return events.contains("disconnected");
        }

        boolean didUpdateConfig() {
            return events.contains("configUpdated");
        }

        public void reset() {
            events = new HashSet<>();
        }
    }

    static class TestHarvest extends Harvest {

        public TestHarvest() {
        }

        public int getActivityTraceLimit() {
            return instance.getActivityTraceConfiguration().getMaxTotalTraceCount();
        }

        public int getActivityTraceCount() {
            return instance.getHarvestData().getActivityTraces().count();
        }

        public Harvester getHarvester() {
            return super.getHarvester();
        }

    }

    static class TestActivityTrace extends ActivityTrace {

        public TestActivityTrace() {
            rootTrace = new Trace();
            rootTrace.childExclusiveTime = 1000;
        }

        @Override
        public void complete() {
        }

        @Override
        public boolean isComplete() {
            return true;
        }

        @Override
        public JsonArray asJsonArray() {
            return new JsonArray();
        }
    }

    private Harvester createTestHarvester(String token, String host) {
        HarvestConnection connection = Harvest.getInstance().getHarvestConnection();
        connection.setConnectInformation(new ConnectInformation(Agent.getApplicationInformation(), Agent.getDeviceInformation()));
        Harvest.getInstance().setHarvestConnection(Mockito.spy(connection));

        AgentConfiguration agentConfiguration = config;
        agentConfiguration.setApplicationToken(token);
        if (host != null) {
            agentConfiguration.setCollectorHost(host);
        }

        Harvester harvester = new Harvester();
        harvester.setAgentConfiguration(agentConfiguration);
        harvester.setHarvestConnection(Harvest.getInstance().getHarvestConnection());
        harvester.setHarvestData(new HarvestData());

        return harvester;
    }

    private HarvestData addHarvestData(HarvestData harvestData) {
        // Device information
        DeviceInformation devInfo = new DeviceInformation();
        devInfo.setOsName("Android");
        devInfo.setOsVersion("2.3");
        devInfo.setManufacturer("Dell");
        devInfo.setModel("Streak");
        devInfo.setAgentName("AndroidAgent");
        devInfo.setAgentVersion("2.123");
        devInfo.setDeviceId("389C9738-A761-44DE-8A66-1668CFD67DA1");

        harvestData.setDeviceInformation(devInfo);

        // Time since last harvest
        harvestData.setHarvestTimeDelta(59.9);

        // HTTP Transactions
        HttpTransactions transactions = new HttpTransactions();

        harvestData.setHttpTransactions(transactions);

        // Machine Measurements
        MachineMeasurements machineMeasurements = new MachineMeasurements();

        machineMeasurements.addMetric("CPU/System/Utilization", 0.1);
        machineMeasurements.addMetric("CPU/User/Utilization", 0.1);
        machineMeasurements.addMetric(MetricNames.SUPPORTABILITY_COLLECTOR + "ResponseStatusCodes/200", 1);
        machineMeasurements.addMetric("Memory/Used", 19.76);

        harvestData.setMachineMeasurements(machineMeasurements);

        return harvestData;
    }

}
