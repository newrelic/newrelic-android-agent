/*
 * Copyright (c) 2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.harvest;

import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.ApplicationExitConfiguration;
import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.analytics.AnalyticsControllerImpl;
import com.newrelic.agent.android.analytics.AnalyticsEvent;
import com.newrelic.agent.android.logging.LogLevel;
import com.newrelic.agent.android.logging.LogReportingConfiguration;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.test.mock.Providers;
import com.newrelic.agent.android.test.stub.StubAgentImpl;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Collection;
import java.util.Iterator;

public class HarvesterTest {
    Harvester harvester;
    HarvestTests.TestHarvestAdapter testAdapter;

    @Before
    public void setUp() throws Exception {
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

        // agent should by now have reconnected and sent pending harvest payload
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

        // call again in disconnected state
        testAdapter.reset();
        harvester.execute();

        // agent should by now have reconnected and sent pending harvest payload
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

        Collection<AnalyticsEvent> events = controller.getEventManager().getQueuedEvents();
        Iterator<AnalyticsEvent> iter = events.iterator();
        AnalyticsEvent event = iter.next();
        Assert.assertEquals("Events should contain breadcrumb event.", AnalyticsEvent.EVENT_TYPE_MOBILE_BREADCRUMB, event.getEventType());
    }

    @Test
    public void shouldUpdateLogReportingConfigOnHarvestConfigUpdate() {
        LogReportingConfiguration preValue = new LogReportingConfiguration();
        preValue.setConfiguration(harvester.getAgentConfiguration().getLogReportingConfiguration());
        Assert.assertFalse(preValue.getLoggingEnabled());
        Assert.assertEquals(LogLevel.NONE, preValue.getLogLevel());

        reconnectAndUploadOnHarvestConfigurationUpdated();

        LogReportingConfiguration postValue = harvester.getAgentConfiguration().getLogReportingConfiguration();
        Assert.assertFalse(postValue.toString().equals(preValue.toString()));
        Assert.assertTrue(postValue.getLoggingEnabled());
        Assert.assertEquals(LogLevel.WARN, postValue.getLogLevel());
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
        Assert.assertTrue(harvestConfig.getRemote_configuration().getLogReportingConfiguration().getLoggingEnabled());
        Assert.assertEquals(LogLevel.INFO, harvestConfig.getRemote_configuration().getLogReportingConfiguration().getLogLevel());
        Assert.assertEquals(30, harvestConfig.getRemote_configuration().getLogReportingConfiguration().getHarvestPeriod());
        Assert.assertEquals(172800, harvestConfig.getRemote_configuration().getLogReportingConfiguration().getExpirationPeriod());
    }

}