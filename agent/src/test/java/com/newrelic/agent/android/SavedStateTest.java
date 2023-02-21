/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android;

import com.newrelic.agent.android.harvest.ApplicationInformation;
import com.newrelic.agent.android.harvest.ConnectInformation;
import com.newrelic.agent.android.harvest.DeviceInformation;
import com.newrelic.agent.android.harvest.HarvestConfiguration;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.test.mock.Providers;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Config.OLDEST_SDK)
public class SavedStateTest {

    private final static String ENABLED_APP_TOKEN_PROD = "AA9a2d52a0ed09d8ca54e6317d9c92074f2e9b307b";

    private SpyContext spyContext;
    private SavedState savedState;
    private AndroidAgentImpl agentImpl;
    private AgentConfiguration agentConfig;

    @Before
    public void setUp() throws Exception {
        spyContext = new SpyContext();

        savedState = new SavedState(spyContext.getContext());

        agentConfig = new AgentConfiguration();
        agentConfig.setApplicationToken(ENABLED_APP_TOKEN_PROD);

        ApplicationInformation appInfo = Providers.provideApplicationInformation();
        DeviceInformation devInfo = Providers.provideDeviceInformation();

        agentImpl = new AndroidAgentImpl(spyContext.getContext(), agentConfig);
        Agent.setImpl(agentImpl);

        // By default, SavedState is created as clone's of the app's ConnectInformation
        savedState = spy(agentImpl.getSavedState());
        ConnectInformation savedConnInfo = new ConnectInformation(appInfo, devInfo);

        when(savedState.getConnectionToken()).thenReturn(String.valueOf(agentConfig.getApplicationToken().hashCode()));
        when(savedState.getConnectInformation()).thenReturn(savedConnInfo);

        agentImpl.setSavedState(savedState);
        agentImpl.getSavedState().clear();
    }

    @Test
    public void testSaveHarvestConfiguration() throws Exception {
        HarvestConfiguration harvestConfig = new HarvestConfiguration();
        harvestConfig.setCollect_network_errors(false);
        savedState.saveHarvestConfiguration(harvestConfig);
        Assert.assertTrue("Should set harvest config", savedState.getHarvestConfiguration().equals(harvestConfig));
        Assert.assertFalse("Should not collect network errors", savedState.getHarvestConfiguration().isCollect_network_errors());
    }

    @Test
    public void testLoadHarvestConfiguration() throws Exception {
        HarvestConfiguration harvestConfig = new HarvestConfiguration();
        savedState.loadHarvestConfiguration();
        Assert.assertEquals(savedState.getHarvestConfiguration(), harvestConfig);
        Assert.assertNull(savedState.getDataToken());
    }

    @Test
    public void testSaveConnectInformation() throws Exception {
        ConnectInformation connectionInfo = new ConnectInformation(Providers.provideApplicationInformation(), Providers.provideDeviceInformation());
        connectionInfo.getApplicationInformation().setAppVersion("9.9");
        connectionInfo.getApplicationInformation().setAppName("crunchy");
        connectionInfo.getDeviceInformation().setDeviceId("dummy_id");

        SavedState savedState = new SavedState(spyContext.getContext());
        savedState.saveConnectInformation(connectionInfo);
        Assert.assertEquals("9.9", savedState.getConnectInformation().getApplicationInformation().getAppVersion());
        Assert.assertEquals("crunchy", savedState.getConnectInformation().getApplicationInformation().getAppName());
        Assert.assertEquals("dummy_id", savedState.getDeviceId());
    }

    @Test
    public void testSaveDeviceId() throws Exception {
        savedState.saveDeviceId("device_id");
        Assert.assertEquals("Should set device ID", savedState.getDeviceId(), "device_id");
    }

    @Test
    public void testConnectionToken() throws Exception {
        savedState.saveConnectionToken(ENABLED_APP_TOKEN_PROD);
        Assert.assertNotNull("Should return app token", savedState.getConnectionToken());
    }

    @Test
    public void testCachedConnectionToken() throws Exception {
        savedState.saveConnectionToken(ENABLED_APP_TOKEN_PROD);
        Assert.assertTrue("Should return app token", savedState.hasConnectionToken(ENABLED_APP_TOKEN_PROD));
        Assert.assertFalse("Should not return other app token", savedState.hasConnectionToken(ENABLED_APP_TOKEN_PROD + "bogus"));
    }

    @Test
    public void testSaveAppToken() throws Exception {
        savedState.saveConnectionToken(ENABLED_APP_TOKEN_PROD);
        Assert.assertEquals(savedState.getConnectionToken(), String.valueOf(ENABLED_APP_TOKEN_PROD.hashCode()));
    }

    @Test
    public void testLoadConnectInformation() throws Exception {
        ApplicationInformation applicationInformation = Providers.provideApplicationInformation();
        DeviceInformation deviceInformation = Providers.provideDeviceInformation();
        applicationInformation.setAppName("Changed app name");
        applicationInformation.setAppVersion("new version");

        SavedState savedState = new SavedState(spyContext.getContext());
        savedState.saveConnectInformation(new ConnectInformation(applicationInformation, deviceInformation));
        // saveDeviceInformation already reloads data, but...
        savedState.loadConnectInformation();
        ConnectInformation connInfo = savedState.getConnectInformation();
        Assert.assertEquals("Should contain changed app name", "Changed app name", connInfo.getApplicationInformation().getAppName());
    }

    @Test
    public void testGetHarvestConfiguration() throws Exception {
        Assert.assertNotNull("Should return harvest config", savedState.getHarvestConfiguration());
    }

    @Test
    public void testGetConnectInformation() throws Exception {
        Assert.assertNotNull("Should return connection info", savedState.getConnectInformation());
    }

    @Test
    public void testSave() throws Exception {
        savedState.save("string", "string");
        savedState.save("boolean", true);
        savedState.save("int", 9999);
        savedState.save("float", Float.NaN);
        savedState.save("long", Long.valueOf(1));
        Assert.assertEquals("Should contain string", "string", savedState.getString("string"));
        Assert.assertEquals("Should contain boolean", true, savedState.getBoolean("boolean"));
        Assert.assertEquals("Should contain int", 9999, savedState.getInt("int"));
        Assert.assertEquals("Should contain float", 0.0f, savedState.getFloat("float"));
        Assert.assertEquals("Should contain long", Long.valueOf(1).longValue(), savedState.getLong("long"));
    }

    @Test
    public void testGetString() throws Exception {
        savedState.save("string", "string");
        Assert.assertEquals("Should save string", "string", savedState.getString("string"));
    }

    @Test
    public void testGetBoolean() throws Exception {
        savedState.save("boolean", true);
        Assert.assertEquals("Should save boolean", true, savedState.getBoolean("boolean"));
    }

    @Test
    public void testGetLong() throws Exception {
        savedState.save("long", (long) 999);
        Assert.assertEquals("Should save boolean", 999, savedState.getLong("long"));
    }

    @Test
    public void testGetInt() throws Exception {
        savedState.save("int", 999);
        Assert.assertEquals("Should save int", 999, savedState.getInt("int"));
    }

    @Test
    public void testGetFloat() throws Exception {
        savedState.save("float", Float.NaN);
        Assert.assertEquals("Should save float", 0.0f, savedState.getFloat("float"));
    }

    @Test
    public void testDisabledVersion() throws Exception {
        savedState.saveDisabledVersion("1.2.3");
        Assert.assertEquals("Should save disabled version", "1.2.3", savedState.getDisabledVersion());
    }

    @Test
    public void testGetDataToken() throws Exception {
        savedState.saveHarvestConfiguration(Providers.provideHarvestConfiguration());
        int[] dataToken = savedState.getDataToken();
        Assert.assertEquals("Should return 2 elements", 2, dataToken.length);
        Assert.assertEquals(111, dataToken[0]);
        Assert.assertEquals(111, dataToken[1]);
    }

    @Test
    public void testInvalidDataToken() throws Exception {
        HarvestConfiguration harvestConfig = Providers.provideHarvestConfiguration();

        harvestConfig.setData_token(new int[]{0, 0});
        savedState.clear();
        savedState.saveHarvestConfiguration(harvestConfig);
        int[] dataToken = savedState.getDataToken();
        Assert.assertNull("Should return invalid datatoken", dataToken);
        Assert.assertTrue(StatsEngine.SUPPORTABILITY.getStatsMap().keySet().contains(MetricNames.SUPPORTABILITY_INVALID_DATA_TOKEN));

        StatsEngine.SUPPORTABILITY.getStatsMap().clear();

        harvestConfig.setData_token(null);
        savedState.clear();
        savedState.saveHarvestConfiguration(harvestConfig);
        Assert.assertNull("Should return invalid datatoken", dataToken);
        Assert.assertTrue(StatsEngine.SUPPORTABILITY.getStatsMap().keySet().contains(MetricNames.SUPPORTABILITY_INVALID_DATA_TOKEN));
    }

    @Test
    public void testInvalidDataTokenWithValidConfig() throws Exception {
        HarvestConfiguration harvestConfig = Providers.provideHarvestConfiguration();

        harvestConfig.setData_token(null);
        savedState.getHarvestConfiguration().setData_token(new int[]{1,2});
        savedState.saveHarvestConfiguration(harvestConfig);
        int[] dataToken = savedState.getDataToken();
        Assert.assertEquals(1, dataToken[0]);
        Assert.assertEquals(2, dataToken[1]);
    }

    @Test
    public void testGetCrossProcessId() throws Exception {
        savedState.saveHarvestConfiguration(Providers.provideHarvestConfiguration());
        Assert.assertEquals("Should contain cross-process id", "x-process-id", savedState.getCrossProcessId());
    }

    @Test
    public void testIsCollectingNetworkErrors() throws Exception {
        savedState.saveHarvestConfiguration(Providers.provideHarvestConfiguration());
        Assert.assertEquals("Should collect network errors", true, savedState.isCollectingNetworkErrors());
    }

    @Test
    public void testGetServerTimestamp() throws Exception {
        savedState.saveHarvestConfiguration(Providers.provideHarvestConfiguration());
        Assert.assertEquals("Should return server time stamp", 9876543210L, savedState.getServerTimestamp());
    }

    @Test
    public void testGetHarvestInterval() throws Exception {
        savedState.saveHarvestConfiguration(Providers.provideHarvestConfiguration());
        Assert.assertEquals("Should return harvest interval", 444, savedState.getHarvestInterval());
    }

    @Test
    public void testGetMaxTransactionAge() throws Exception {
        savedState.saveHarvestConfiguration(Providers.provideHarvestConfiguration());
        Assert.assertEquals("Should return max transaction age", 666, savedState.getMaxTransactionAge());
    }

    @Test
    public void testGetMaxTransactionCount() throws Exception {
        savedState.saveHarvestConfiguration(Providers.provideHarvestConfiguration());
        Assert.assertEquals("Should return max transaction count", 555, savedState.getMaxTransactionCount());
    }

    @Test
    public void testGetStackTraceLimit() throws Exception {
        savedState.saveHarvestConfiguration(Providers.provideHarvestConfiguration());
        Assert.assertEquals("Should return stack trace limit", 999, savedState.getStackTraceLimit());
    }

    @Test
    public void testGetResponseBodyLimit() throws Exception {
        savedState.saveHarvestConfiguration(Providers.provideHarvestConfiguration());
        Assert.assertEquals("Should return harvest interval", 1111, savedState.getResponseBodyLimit());
    }

    @Test
    public void testGetErrorLimit() throws Exception {
        savedState.saveHarvestConfiguration(Providers.provideHarvestConfiguration());
        Assert.assertEquals("Should return harvest interval", 111, savedState.getErrorLimit());
    }

    @Test
    public void testSaveActivityTraceMinUtilization() throws Exception {
        savedState.saveHarvestConfiguration(Providers.provideHarvestConfiguration());
        Assert.assertEquals("Should return harvest interval", 0.3333, savedState.getActivityTraceMinUtilization(), 0.1);
    }

    @Test
    public void testGetActivityTraceMinUtilization() throws Exception {
        savedState.saveHarvestConfiguration(Providers.provideHarvestConfiguration());
        Assert.assertEquals("Should return harvest interval", 0.3333, savedState.getActivityTraceMinUtilization(), 0.1);
    }

    @Test
    public void testGetHarvestIntervalInSeconds() throws Exception {
        savedState.saveHarvestConfiguration(Providers.provideHarvestConfiguration());
        Assert.assertEquals("Should return harvest interval", 444, savedState.getHarvestIntervalInSeconds());
    }

    @Test
    public void testGetMaxTransactionAgeInSeconds() throws Exception {
        savedState.saveHarvestConfiguration(Providers.provideHarvestConfiguration());
        Assert.assertEquals("Should return harvest interval", 666, savedState.getMaxTransactionAgeInSeconds());
    }

    @Test
    public void testGetAppName() throws Exception {
        savedState.saveConnectInformation(new ConnectInformation(Providers.provideApplicationInformation(), Providers.provideDeviceInformation()));
        Assert.assertEquals("Should get app name", "SpyContext", savedState.getAppName());
    }

    @Test
    public void testGetAppVersion() throws Exception {
        savedState.saveConnectInformation(new ConnectInformation(Providers.provideApplicationInformation(), Providers.provideDeviceInformation()));
        Assert.assertEquals("Should get app version", "1.1", savedState.getAppVersion());
    }

    @Test
    public void testGetAppBuild() throws Exception {
        savedState.saveConnectInformation(new ConnectInformation(Providers.provideApplicationInformation(), Providers.provideDeviceInformation()));
        Assert.assertEquals("Should get app build", "99", savedState.getAppBuild());
    }

    @Test
    public void testGetPackageId() throws Exception {
        savedState.saveConnectInformation(new ConnectInformation(Providers.provideApplicationInformation(), Providers.provideDeviceInformation()));
        Assert.assertEquals("Should get package ID", "com.newrelic.agent.android.test", savedState.getPackageId());
    }

    @Test
    public void testGetAgentName() throws Exception {
        savedState.saveConnectInformation(new ConnectInformation(Providers.provideApplicationInformation(), Providers.provideDeviceInformation()));
        Assert.assertEquals("Should get agent name", "AndroidAgent", savedState.getAgentName());
    }

    @Test
    public void testGetAgentVersion() throws Exception {
        savedState.saveConnectInformation(new ConnectInformation(Providers.provideApplicationInformation(), Providers.provideDeviceInformation()));
        Assert.assertEquals("Should get agent version", Agent.getVersion(), savedState.getAgentVersion());
    }

    @Test
    public void testGetDeviceArchitecture() throws Exception {
        savedState.saveConnectInformation(new ConnectInformation(Providers.provideApplicationInformation(), Providers.provideDeviceInformation()));
        Assert.assertEquals("Should get device architecture", "x86_64", savedState.getDeviceArchitecture());
    }

    @Test
    public void testGetDeviceId() throws Exception {
        savedState.saveConnectInformation(new ConnectInformation(Providers.provideApplicationInformation(), Providers.provideDeviceInformation()));
        Assert.assertEquals("Should ", Providers.FIXED_DEVICE_ID, savedState.getDeviceId());
    }

    @Test
    public void testGetDeviceModel() throws Exception {
        savedState.saveConnectInformation(new ConnectInformation(Providers.provideApplicationInformation(), Providers.provideDeviceInformation()));
        Assert.assertEquals("Should get device model", "6000sux", savedState.getDeviceModel());
    }

    @Test
    public void testGetDeviceManufacturer() throws Exception {
        savedState.saveConnectInformation(new ConnectInformation(Providers.provideApplicationInformation(), Providers.provideDeviceInformation()));
        Assert.assertEquals("Should get device manufacturer", "spacely sprockets", savedState.getDeviceManufacturer());
    }

    @Test
    public void testGetDeviceRunTime() throws Exception {
        savedState.saveConnectInformation(new ConnectInformation(Providers.provideApplicationInformation(), Providers.provideDeviceInformation()));
        Assert.assertEquals("Should get device runtime", "1.7.0", savedState.getDeviceRunTime());
    }

    @Test
    public void testGetDeviceSize() throws Exception {
        savedState.saveConnectInformation(new ConnectInformation(Providers.provideApplicationInformation(), Providers.provideDeviceInformation()));
        Assert.assertEquals("Should get device size", "normal", savedState.getDeviceSize());
    }

    @Test
    public void testGetOsName() throws Exception {
        savedState.saveConnectInformation(new ConnectInformation(Providers.provideApplicationInformation(), Providers.provideDeviceInformation()));
        Assert.assertEquals("Should get o/s name", "Android", savedState.getOsName());
    }

    @Test
    public void testGetOsBuild() throws Exception {
        savedState.saveConnectInformation(new ConnectInformation(Providers.provideApplicationInformation(), Providers.provideDeviceInformation()));
        Assert.assertEquals("Should get o/s build", "4", savedState.getOsBuild());
    }

    @Test
    public void testGetOsVersion() throws Exception {
        savedState.saveConnectInformation(new ConnectInformation(Providers.provideApplicationInformation(), Providers.provideDeviceInformation()));
        Assert.assertEquals("Should get o/s version", "1.2.3", savedState.getOsVersion());
    }

    @Test
    public void testApplicationFramework() throws Exception {
        DeviceInformation thisDeviceInfo = Providers.provideDeviceInformation();
        savedState.saveConnectInformation(new ConnectInformation(Providers.provideApplicationInformation(), thisDeviceInfo));
        Assert.assertEquals("Should save application platform", ApplicationFramework.Cordova.toString(), savedState.getApplicationFramework());
        Assert.assertEquals("Should save application platform version", "1.2.3.4", savedState.getApplicationFrameworkVersion());

        DeviceInformation thatDeviceInfo = Providers.provideDeviceInformation();
        thatDeviceInfo.setApplicationFramework(ApplicationFramework.ReactNative);
        thatDeviceInfo.setApplicationFrameworkVersion("5.6.7.8");
        Assert.assertTrue("Should not consider platform in equality", thisDeviceInfo.equals(thatDeviceInfo));
    }

    @Test
    public void testClear() throws Exception {
        savedState.save("saved", "string");
        savedState.getHarvestConfiguration().setError_limit(999999999);
        Assert.assertEquals("Should contain 1 string", "string", savedState.getString("saved"));

        savedState.clear();
        Assert.assertNull("Should not contain 1 string", savedState.getString("saved"));
        Assert.assertTrue("Should reset error limit", savedState.getHarvestConfiguration().getError_limit() != 999999999);
    }

    @Test
    public void testOnHarvestConnected() throws Exception {
        SavedState spy = spy(savedState);
        spy.onHarvestConnected();
        verify(spy).saveHarvestConfiguration(any(HarvestConfiguration.class));
    }

    @Test
    public void testOnHarvestDisconnected() throws Exception {
        SavedState spy = spy(savedState);
        spy.onHarvestDisconnected();
        verify(spy).clear();
    }

    @Test
    public void testOnHarvestDisabled() throws Exception {
        SavedState spy = spy(savedState);
        spy.onHarvestDisabled();
        verify(spy).saveDisabledVersion(anyString());
    }

    @Test
    public void testOnHarvestComplete() throws InterruptedException {
        when(savedState.getDataTokenTTL()).thenReturn(2L);

        savedState.saveHarvestConfiguration(Providers.provideHarvestConfiguration());
        savedState.loadHarvestConfiguration();
        Assert.assertTrue(savedState.has("dataToken"));
        Assert.assertTrue(savedState.has("dataTokenExpiration"));

        Thread.sleep(10);

        savedState.onHarvestComplete();
        Assert.assertFalse(savedState.has("dataToken"));
        Assert.assertFalse(savedState.has("dataTokenExpiration"));
    }

    @Test
    public void testDataTokenExpiration() {
        Assert.assertTrue(savedState.getDataTokenTTL() > 0);

        Assert.assertFalse(savedState.has("dataToken"));
        Assert.assertFalse(savedState.has("dataTokenExpiration"));
        savedState.saveHarvestConfiguration(Providers.provideHarvestConfiguration());
        savedState.loadHarvestConfiguration();
        Assert.assertTrue(savedState.has("dataToken"));
        Assert.assertTrue(savedState.has("dataTokenExpiration"));
    }
}