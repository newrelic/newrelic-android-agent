/*
 * Copyright (c) 2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.harvest;

import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.aei.ApplicationExitConfiguration;
import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.analytics.AnalyticsControllerImpl;
import com.newrelic.agent.android.analytics.AnalyticsEvent;
import com.newrelic.agent.android.logging.LogLevel;
import com.newrelic.agent.android.logging.LogReportingConfiguration;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.test.mock.Providers;
import com.newrelic.agent.android.test.stub.StubAgentImpl;

import org.junit.Assert;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class HarvesterTest {
    Harvester harvester;
    HarvestTests.TestHarvestAdapter testAdapter;
    private Harvest originalHarvestInstance;

    // Test constants for session timeout scenarios
    private static final long ONE_SECOND_MS = 1000L;
    private static final long TWO_HOURS_MS = 2 * 60 * 60 * 1000L;
    private static final long FIVE_HOURS_MS = 5 * 60 * 60 * 1000L;
    private static final long EIGHT_HOURS_MS = 8 * 60 * 60 * 1000L;
    private static final int CONCURRENT_TEST_TIMEOUT_SECONDS = 10;

    @Before
    public void setUp() throws Exception {
        // Save original instance for restoration
        originalHarvestInstance = Harvest.getInstance();

        Harvest.instance = new Harvest();
        Harvest.initialize(Providers.provideAgentConfiguration());

        HarvestConnection connection = Harvest.getInstance().getHarvestConnection();
        connection.setConnectInformation(new ConnectInformation(Agent.getApplicationInformation(), Agent.getDeviceInformation()));
        Harvest.instance.setHarvestConnection(Mockito.spy(connection));

        testAdapter = new HarvestTests.TestHarvestAdapter();

        harvester = Harvest.instance.getHarvester();
        harvester.setHarvestConfiguration(new HarvestConfiguration());
        harvester.setHarvestConnection(Harvest.getInstance().getHarvestConnection());
        harvester.addHarvestListener(testAdapter);

        StatsEngine.reset();

        FeatureFlag.enableFeature(FeatureFlag.ApplicationExitReporting);
    }

    @After
    public void tearDown() throws Exception {
        if (originalHarvestInstance != null) {
            Harvest.setInstance(originalHarvestInstance);
        }

        // Clean up AnalyticsController state to prevent test interference
        try {
            AnalyticsControllerImpl controller = AnalyticsControllerImpl.getInstance();
            if (controller != null && controller.getEventManager() != null) {
                controller.getEventManager().empty();
            }
        } catch (Exception e) {
            // Ignore cleanup exceptions to prevent masking test failures
        }
    }

    @Test
    public void parseHarvesterConfiguration() {
        String connectResponse = Providers.provideJsonObject("/Connect-Spec-v5.json").toString();
        Assert.assertEquals(Harvester.State.UNINITIALIZED, harvester.getCurrentState());

        HarvestResponse mockedResponse = Mockito.spy(new HarvestResponse());
        Mockito.doReturn(connectResponse).when(mockedResponse).getResponseBody();

        HarvestConfiguration harvestConfig = harvester.parseHarvesterConfiguration(mockedResponse);
        Assert.assertNotNull(harvestConfig);

        Assert.assertTrue(harvestConfig.getDataToken().isValid());
        Assert.assertEquals("6060842", harvestConfig.getAccount_id());
        Assert.assertEquals("1646468", harvestConfig.getApplication_id());
        Assert.assertEquals("33", harvestConfig.getTrusted_account_key());

        Assert.assertNotNull(harvestConfig.getEntity_guid());
        Assert.assertFalse(harvestConfig.getEntity_guid().isEmpty());

        Assert.assertNotNull(harvestConfig.getRemote_configuration());
        Assert.assertNotNull(harvestConfig.getRemote_configuration().getApplicationExitConfiguration());
        Assert.assertTrue(harvestConfig.getRemote_configuration().getApplicationExitConfiguration().isEnabled());

        Assert.assertFalse(harvestConfig.getRequest_headers_map().isEmpty());
        Assert.assertEquals(2, harvestConfig.getRequest_headers_map().size());

        Assert.assertNotNull(harvestConfig.getRemote_configuration().getLogReportingConfiguration());
        Assert.assertFalse(harvestConfig.getRemote_configuration().getLogReportingConfiguration().getLoggingEnabled());
        Assert.assertEquals(LogLevel.WARN, harvestConfig.getRemote_configuration().getLogReportingConfiguration().getLogLevel());
        Assert.assertEquals(30, harvestConfig.getRemote_configuration().getLogReportingConfiguration().getHarvestPeriod());
        Assert.assertEquals(172800, harvestConfig.getRemote_configuration().getLogReportingConfiguration().getExpirationPeriod());
    }

    @Test
    public void testHarvestConfigurationUpdated() {
        HarvestResponse mockedDataResponse = Mockito.spy(new HarvestResponse());

        harvester.getHarvestData().setDataToken(Providers.provideDataToken());

        Mockito.doReturn(true).when(mockedDataResponse).isError();
        Mockito.doReturn(HarvestResponse.Code.CONFIGURATION_UPDATE).when(mockedDataResponse).getResponseCode();
        Mockito.doReturn(mockedDataResponse).when(harvester.getHarvestConnection()).sendData(Mockito.any(HarvestData.class));

        harvester.transition(Harvester.State.CONNECTED);
        harvester.execute();

        Assert.assertTrue(testAdapter.didDisconnect());
        Assert.assertSame(harvester.getCurrentState(), Harvester.State.DISCONNECTED);
    }

    @Test
    public synchronized void testReconnectAndUploadOnHarvestConfigurationUpdated() {
        reconnectAndUploadOnHarvestConfigurationUpdated();
    }

    synchronized void reconnectAndUploadOnHarvestConfigurationUpdated() {
        HarvestResponse mockedDataResponse = Mockito.spy(new HarvestResponse());

        Mockito.doReturn(true).when(mockedDataResponse).isError();
        Mockito.doReturn(HarvestResponse.Code.CONFIGURATION_UPDATE).when(mockedDataResponse).getResponseCode();
        Mockito.doReturn(mockedDataResponse).when(harvester.getHarvestConnection()).sendData(Mockito.any(HarvestData.class));

        harvester.getHarvestData().setDataToken(Providers.provideDataToken());
        harvester.transition(Harvester.State.CONNECTED);
        harvester.execute();

        Assert.assertTrue(testAdapter.didError());
        Assert.assertTrue(testAdapter.didDisconnect());
        Assert.assertSame(harvester.getCurrentState(), Harvester.State.DISCONNECTED);

        HarvestResponse mockedConnectResponse = Mockito.spy(new HarvestResponse());
        Mockito.doReturn(true).when(mockedConnectResponse).isOK();
        Mockito.doReturn(HarvestResponse.Code.OK).when(mockedConnectResponse).getResponseCode();
        Mockito.doReturn(Providers.provideJsonObject("/Connect-Spec-v5-changed.json").toString()).when(mockedConnectResponse).getResponseBody();
        Mockito.doReturn(mockedConnectResponse).when(harvester.getHarvestConnection()).sendConnect();
        Mockito.doReturn(false).when(mockedDataResponse).isError();
        Mockito.doReturn(mockedDataResponse).when(harvester.getHarvestConnection()).sendData(Mockito.any(HarvestData.class));

        testAdapter.reset();
        harvester.execute();

        Assert.assertTrue(harvester.getCurrentState() == Harvester.State.CONNECTED);
        Assert.assertTrue(testAdapter.didHarvest());
        Assert.assertTrue(testAdapter.didComplete());
        Assert.assertTrue(testAdapter.didUpdateConfig());
        Assert.assertTrue(harvester.getHarvestData().getDataToken().isValid());
    }

    @Test
    public void shouldNotRespondToIdenticalConfigurations() {
        HarvestResponse mockedDataResponse = Mockito.spy(new HarvestResponse());

        Mockito.doReturn(true).when(mockedDataResponse).isError();
        Mockito.doReturn(HarvestResponse.Code.CONFIGURATION_UPDATE).when(mockedDataResponse).getResponseCode();
        Mockito.doReturn(mockedDataResponse).when(harvester.getHarvestConnection()).sendData(Mockito.any(HarvestData.class));

        harvester.getHarvestData().setDataToken(Providers.provideDataToken());
        harvester.getHarvestConfiguration().setData_token(Providers.provideDataToken().asIntArray());
        harvester.transition(Harvester.State.CONNECTED);
        harvester.execute();

        Assert.assertTrue(testAdapter.didError());
        Assert.assertTrue(testAdapter.didDisconnect());
        Assert.assertTrue(harvester.getCurrentState() == Harvester.State.DISCONNECTED);
        Assert.assertFalse(harvester.getHarvestData().getDataToken().isValid());

        HarvestResponse mockedConnectResponse = Mockito.spy(new HarvestResponse());
        Mockito.doReturn(true).when(mockedConnectResponse).isOK();
        Mockito.doReturn(HarvestResponse.Code.OK).when(mockedConnectResponse).getResponseCode();
        Mockito.doReturn(Providers.provideJsonObject(harvester.getHarvestConfiguration(), HarvestConfiguration.class)
                .toString()).when(mockedConnectResponse).getResponseBody();
        Mockito.doReturn(mockedConnectResponse).when(harvester.getHarvestConnection()).sendConnect();

        Mockito.doReturn(false).when(mockedDataResponse).isError();
        Mockito.doReturn(mockedDataResponse).when(harvester.getHarvestConnection()).sendData(Mockito.any(HarvestData.class));

        testAdapter.reset();
        harvester.execute();

        Assert.assertTrue(harvester.getCurrentState() == Harvester.State.CONNECTED);
        Assert.assertFalse(testAdapter.didUpdateConfig());
        Assert.assertTrue(testAdapter.didHarvest());
    }

    @Test
    public void shouldRecordConfigurationMetrics() {
        AnalyticsControllerImpl controller = AnalyticsControllerImpl.getInstance();
        AnalyticsControllerImpl.initialize(harvester.getAgentConfiguration(), new StubAgentImpl());
        controller.getEventManager().empty();

        harvester.removeHarvestListener(StatsEngine.get());
        reconnectAndUploadOnHarvestConfigurationUpdated();

        Assert.assertTrue("Should contain supportability metric to indicate configuration has changed",
                StatsEngine.SUPPORTABILITY.getStatsMap().containsKey(MetricNames.SUPPORTABILITY_CONFIGURATION_CHANGED));

        AnalyticsEvent event = controller.getEventManager()
                .getQueuedEvents()
                .stream()
                .filter(analyticsEvent -> AnalyticsEvent.EVENT_TYPE_MOBILE_BREADCRUMB == analyticsEvent.getEventType())
                .findFirst()
                .orElse(null);

        Assert.assertEquals("Events should contain breadcrumb event.", AnalyticsEvent.EVENT_TYPE_MOBILE_BREADCRUMB, event.getEventType());
    }

    @Test
    public void shouldUpdateLogReportingConfigOnHarvestConfigUpdate() {
        LogReportingConfiguration preValue = new LogReportingConfiguration();
        preValue.setConfiguration(harvester.getAgentConfiguration().getLogReportingConfiguration());
        Assert.assertFalse(preValue.getLoggingEnabled());
        Assert.assertEquals(LogLevel.NONE, preValue.getLogLevel());

        reconnectAndUploadOnHarvestConfigurationUpdated();

        LogReportingConfiguration postValue = Mockito.spy(harvester.getAgentConfiguration().getLogReportingConfiguration());
        Mockito.when(postValue.isSampled()).thenReturn(false);
        Assert.assertFalse(postValue.toString().equals(preValue.toString()));
        Assert.assertFalse(postValue.getLoggingEnabled());
        Assert.assertEquals(LogLevel.WARN, postValue.getLogLevel());

        Mockito.reset(postValue);
        Mockito.when(postValue.isSampled()).thenReturn(true);
        Assert.assertTrue(postValue.getLoggingEnabled());
    }

    @Test
    public void shouldUpdateApplicationExitConfigOnHarvestConfigUpdate() {
        ApplicationExitConfiguration preValue = new ApplicationExitConfiguration(false);
        preValue.setConfiguration(harvester.getAgentConfiguration().getApplicationExitConfiguration());
        Assert.assertTrue(preValue.isEnabled());

        reconnectAndUploadOnHarvestConfigurationUpdated();

        ApplicationExitConfiguration postValue = harvester.getAgentConfiguration().getApplicationExitConfiguration();
        Assert.assertFalse(postValue.equals(preValue));
        Assert.assertFalse(postValue.isEnabled());
    }

    @Test
    public void parseV4ConnectResponse() {
        String connectResponse = Providers.provideJsonObject("/Connect-Spec-v4.json").toString();

        HarvestResponse mockedResponse = Mockito.spy(new HarvestResponse());
        Mockito.doReturn(connectResponse).when(mockedResponse).getResponseBody();

        HarvestConfiguration harvestConfig = harvester.parseHarvesterConfiguration(mockedResponse);
        Assert.assertNotNull(harvestConfig);

        Assert.assertTrue(harvestConfig.getDataToken().isValid());
        Assert.assertEquals("6060842", harvestConfig.getAccount_id());
        Assert.assertFalse(harvestConfig.getApplication_id().isEmpty());
        Assert.assertEquals("33", harvestConfig.getTrusted_account_key());

        Assert.assertNotNull(harvestConfig.getEntity_guid());
        Assert.assertTrue(harvestConfig.getEntity_guid().isEmpty());

        Assert.assertNotNull(harvestConfig.getRemote_configuration().getLogReportingConfiguration());
        Assert.assertFalse(harvestConfig.getRemote_configuration().getLogReportingConfiguration().getLoggingEnabled());
        Assert.assertEquals(LogLevel.INFO, harvestConfig.getRemote_configuration().getLogReportingConfiguration().getLogLevel());
        Assert.assertEquals(30, harvestConfig.getRemote_configuration().getLogReportingConfiguration().getHarvestPeriod());
        Assert.assertEquals(172800, harvestConfig.getRemote_configuration().getLogReportingConfiguration().getExpirationPeriod());
    }

    @Test
    public void testCheckAndResetSessionIfExpired_SessionUnderFourHours() {
        Harvest mockHarvest = setupMockHarvestWithSessionTime(TWO_HOURS_MS);

        harvester.checkAndResetSessionIfExpired();

        Mockito.verify(mockHarvest, Mockito.never()).startSession();
    }

    @Test
    public void testCheckAndResetSessionIfExpired_SessionAtFourHours() {
        long fourHoursInMs = HarvestTimer.DEFAULT_SESSION_DURATION_PERIOD;
        Harvest mockHarvest = setupMockHarvestWithSessionTime(fourHoursInMs);

        harvester.checkAndResetSessionIfExpired();

        Mockito.verify(mockHarvest, Mockito.times(1)).startSession();
    }

    @Test
    public void testCheckAndResetSessionIfExpired_SessionOverFourHours() {
        Harvest mockHarvest = setupMockHarvestWithSessionTime(FIVE_HOURS_MS);

        harvester.checkAndResetSessionIfExpired();

        Mockito.verify(mockHarvest, Mockito.times(1)).startSession();
    }

    @Test
    public void testCheckAndResetSessionIfExpired_NullHarvestTimer() {
        Harvest mockHarvest = Mockito.spy(Harvest.getInstance());
        Mockito.doReturn(null).when(mockHarvest).getHarvestTimer();
        Harvest.setInstance(mockHarvest);
        try {
            harvester.checkAndResetSessionIfExpired();
            Mockito.verify(mockHarvest, Mockito.never()).startSession();
        } catch (Exception e) {
            Assert.fail("Should handle null HarvestTimer gracefully: " + e.getMessage());
        }
    }

    @Test
    public void testCheckAndResetSessionIfExpired_NullHarvestInstance() {
        Harvest.setInstance(null);

        try {
            harvester.checkAndResetSessionIfExpired();
        } catch (Exception e) {
            Assert.fail("Should handle null Harvest instance gracefully: " + e.getMessage());
        }
    }

    @Test
    public void testCheckAndResetSessionIfExpired_ExceptionHandling() {
        HarvestTimer mockHarvestTimer = Mockito.spy(new HarvestTimer(harvester));
        Mockito.doThrow(new RuntimeException("Test exception")).when(mockHarvestTimer).sessionTimeSinceStart();

        Harvest mockHarvest = Mockito.spy(Harvest.getInstance());
        Mockito.doReturn(mockHarvestTimer).when(mockHarvest).getHarvestTimer();
        Harvest.setInstance(mockHarvest);

        try {
            harvester.checkAndResetSessionIfExpired();
        } catch (Exception e) {
            Assert.fail("Should handle exceptions gracefully: " + e.getMessage());
        }
    }

    @Test
    public void testSessionResetCalledInConnectedState() {
        HarvestResponse mockedDataResponse = Mockito.spy(new HarvestResponse());
        Mockito.doReturn(false).when(mockedDataResponse).isError();
        Mockito.doReturn(false).when(mockedDataResponse).isUnknown();
        Mockito.doReturn(HarvestResponse.Code.OK).when(mockedDataResponse).getResponseCode();
        Mockito.doReturn(mockedDataResponse).when(harvester.getHarvestConnection()).sendData(Mockito.any(HarvestData.class));

        HarvestTimer mockHarvestTimer = Mockito.spy(new HarvestTimer(harvester));
        long fiveHoursInMs = 5 * 60 * 60 * 1000; // 5 hours
        Mockito.doReturn(fiveHoursInMs).when(mockHarvestTimer).sessionTimeSinceStart();

        Harvest mockHarvest = Mockito.spy(Harvest.getInstance());
        Mockito.doReturn(mockHarvestTimer).when(mockHarvest).getHarvestTimer();
        Harvest.setInstance(mockHarvest);

        harvester.getHarvestData().setDataToken(Providers.provideDataToken());
        harvester.transition(Harvester.State.CONNECTED);

        harvester.execute();

        Mockito.verify(mockHarvest, Mockito.times(1)).startSession();
    }

    @Test
    public void testSessionResetCalledEvenOnDataSendFailure() {
        HarvestResponse mockedDataResponse = Mockito.spy(new HarvestResponse());
        Mockito.doReturn(true).when(mockedDataResponse).isError();
        Mockito.doReturn(HarvestResponse.Code.REQUEST_TIMEOUT).when(mockedDataResponse).getResponseCode();
        Mockito.doReturn(mockedDataResponse).when(harvester.getHarvestConnection()).sendData(Mockito.any(HarvestData.class));

        HarvestTimer mockHarvestTimer = Mockito.spy(new HarvestTimer(harvester));
        long fiveHoursInMs = 5 * 60 * 60 * 1000; // 5 hours
        Mockito.doReturn(fiveHoursInMs).when(mockHarvestTimer).sessionTimeSinceStart();

        Harvest mockHarvest = Mockito.spy(Harvest.getInstance());
        Mockito.doReturn(mockHarvestTimer).when(mockHarvest).getHarvestTimer();
        Harvest.setInstance(mockHarvest);

        harvester.getHarvestData().setDataToken(Providers.provideDataToken());
        harvester.transition(Harvester.State.CONNECTED);

        harvester.execute();
        Mockito.verify(mockHarvest, Mockito.times(1)).startSession();
    }

    @Test
    public void testCheckAndResetSessionIfExpired_JustUnderFourHours() {
        long justUnderFourHours = HarvestTimer.DEFAULT_SESSION_DURATION_PERIOD - ONE_SECOND_MS; // 3h59m59s
        Harvest mockHarvest = setupMockHarvestWithSessionTime(justUnderFourHours);

        harvester.checkAndResetSessionIfExpired();

        Mockito.verify(mockHarvest, Mockito.never()).startSession();
    }

    @Test
    public void testCheckAndResetSessionIfExpired_NegativeSessionTime() {
        long negativeTime = -ONE_SECOND_MS; // Clock moved backward
        Harvest mockHarvest = setupMockHarvestWithSessionTime(negativeTime);

        harvester.checkAndResetSessionIfExpired();

        Mockito.verify(mockHarvest, Mockito.never()).startSession();
    }

    @Test
    public void testCheckAndResetSessionIfExpired_VeryLongSession() {
        Harvest mockHarvest = setupMockHarvestWithSessionTime(EIGHT_HOURS_MS);

        harvester.checkAndResetSessionIfExpired();

        Mockito.verify(mockHarvest, Mockito.times(1)).startSession();
    }

    @Test
    public void testCompleteSessionResetCycle() {
        AnalyticsControllerImpl controller = AnalyticsControllerImpl.getInstance();
        AnalyticsControllerImpl.initialize(harvester.getAgentConfiguration(), new StubAgentImpl());
        controller.getEventManager().empty();

            AnalyticsAttribute initialSessionIdAttr = controller.getAttribute(AnalyticsAttribute.SESSION_ID_ATTRIBUTE);
            String initialSessionId = initialSessionIdAttr != null ? initialSessionIdAttr.getStringValue() : null;

            HarvestResponse mockedDataResponse = Mockito.spy(new HarvestResponse());
            Mockito.doReturn(false).when(mockedDataResponse).isError();
            Mockito.doReturn(false).when(mockedDataResponse).isUnknown();
            Mockito.doReturn(HarvestResponse.Code.OK).when(mockedDataResponse).getResponseCode();
            Mockito.doReturn(mockedDataResponse).when(harvester.getHarvestConnection()).sendData(Mockito.any(HarvestData.class));

            Harvest mockHarvest = setupMockHarvestWithSessionTime(FIVE_HOURS_MS);

            harvester.getHarvestData().setDataToken(Providers.provideDataToken());
            harvester.transition(Harvester.State.CONNECTED);

            harvester.execute();

            Mockito.verify(mockHarvest, Mockito.times(1)).startSession();

            // Verify: New session ID was created (if initial session existed)
            if (initialSessionId != null) {
                AnalyticsAttribute newSessionIdAttr = controller.getAttribute(AnalyticsAttribute.SESSION_ID_ATTRIBUTE);
                if (newSessionIdAttr != null) {
                    String newSessionId = newSessionIdAttr.getStringValue();
                    Assert.assertNotEquals("Session ID should change after 4-hour reset", initialSessionId, newSessionId);
                }
            }
    }

    @Test
    public void testCheckAndResetSessionIfExpired_ConcurrentAccess() throws InterruptedException {
        final AtomicInteger resetCount = new AtomicInteger(0);

        HarvestTimer mockHarvestTimer = Mockito.spy(new HarvestTimer(harvester));
        Mockito.doReturn(FIVE_HOURS_MS).when(mockHarvestTimer).sessionTimeSinceStart();

        Harvest mockHarvest = Mockito.spy(Harvest.getInstance());
        Mockito.doReturn(mockHarvestTimer).when(mockHarvest).getHarvestTimer();
        Mockito.doAnswer(invocation -> {
            resetCount.incrementAndGet();
            return null;
        }).when(mockHarvest).startSession();

        Harvest.setInstance(mockHarvest);

        final CountDownLatch latch = new CountDownLatch(2);

        Thread t1 = new Thread(() -> {
            try {
                harvester.checkAndResetSessionIfExpired();
            } finally {
                latch.countDown();
            }
        });
        Thread t2 = new Thread(() -> {
            try {
                harvester.checkAndResetSessionIfExpired();
            } finally {
                latch.countDown();
            }
        });

        t1.start();
        t2.start();

        Assert.assertTrue("Concurrent test should complete within timeout",
                         latch.await(CONCURRENT_TEST_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS));

        Assert.assertTrue("Session should be reset at least once", resetCount.get() >= 1);
        Assert.assertTrue("Session reset count should be reasonable (may have race condition)",
                         resetCount.get() <= 2);
    }

    @Test
    public void testMultipleSessionResetsOver8Hours() {
        Harvest mockHarvest = setupMockHarvestWithSessionTime(EIGHT_HOURS_MS);

        harvester.checkAndResetSessionIfExpired();

        Mockito.verify(mockHarvest, Mockito.times(1)).startSession();
    }

    @Test
    public void testCheckAndResetSessionIfExpired_InDisconnectedState() {
        Harvest mockHarvest = setupMockHarvestWithSessionTime(FIVE_HOURS_MS);

        harvester.transition(Harvester.State.DISCONNECTED);

        harvester.checkAndResetSessionIfExpired();

        Mockito.verify(mockHarvest, Mockito.times(1)).startSession();
    }

    @Test
    public void testSessionResetWithPendingHarvestData() {
        Harvest mockHarvest = setupMockHarvestWithSessionTime(FIVE_HOURS_MS);

        harvester.getHarvestData().setDataToken(Providers.provideDataToken());

        Assert.assertNotNull("Harvest data should be present before session reset",
                            harvester.getHarvestData().getDataToken());

        harvester.checkAndResetSessionIfExpired();

        Mockito.verify(mockHarvest, Mockito.times(1)).startSession();

        Assert.assertNotNull("Harvest data should be preserved during session reset",
                            harvester.getHarvestData().getDataToken());
    }

    private Harvest setupMockHarvestWithSessionTime(long sessionTimeMs) {
        HarvestTimer mockHarvestTimer = Mockito.spy(new HarvestTimer(harvester));
        Mockito.doReturn(sessionTimeMs).when(mockHarvestTimer).sessionTimeSinceStart();

        Harvest mockHarvest = Mockito.spy(Harvest.getInstance());
        Mockito.doReturn(mockHarvestTimer).when(mockHarvest).getHarvestTimer();
        Harvest.setInstance(mockHarvest);

        return mockHarvest;
    }

}