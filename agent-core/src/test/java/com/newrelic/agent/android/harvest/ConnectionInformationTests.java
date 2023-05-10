/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.harvest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.ApplicationFramework;
import com.newrelic.agent.android.harvest.type.Harvestable;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.logging.ConsoleAgentLog;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.test.stub.StubAgentImpl;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ConnectionInformationTests {

    final AgentLog log = AgentLogManager.getAgentLog();

    private String appName = "test";
    private String appVersion = "1.0";
    private String packageId = "com.test";
    private String build = "SNAPSHOT-1";
    private ApplicationInformation appInfo;
    private DeviceInformation devInfo;
    private ConnectInformation connectionInformation;
    private TestStubAgentImpl agent;

    @BeforeClass
    public static void setLogging() {
        AgentLogManager.setAgentLog(new ConsoleAgentLog());
    }

    @Before
    public void setUp() throws Exception {
        agent = new TestStubAgentImpl();
        Agent.setImpl(agent);

        connectionInformation = agent.provideConnectInformation();
        appInfo = connectionInformation.getApplicationInformation();
        devInfo = connectionInformation.getDeviceInformation();
    }

    @Test
    public void testApplicationInformation() {
        ApplicationInformation clonedAppInfo = new ApplicationInformation();
        clonedAppInfo.setAppName(appInfo.getAppName());
        clonedAppInfo.setAppVersion(appInfo.getAppVersion());
        clonedAppInfo.setPackageId(appInfo.getPackageId());
        clonedAppInfo.setAppBuild(appInfo.getAppBuild());

        Assert.assertEquals("Cloned appName should match", clonedAppInfo.getAppName(), appInfo.getAppName());
        Assert.assertEquals("Cloned appVersion should match", clonedAppInfo.getAppVersion(), appInfo.getAppVersion());
        Assert.assertEquals("Cloned appBuild should match", clonedAppInfo.getAppBuild(), appInfo.getAppBuild());
        Assert.assertEquals("Cloned packageId should match", clonedAppInfo.getPackageId(), appInfo.getPackageId());

        Assert.assertTrue(clonedAppInfo.equals(appInfo));
        Assert.assertEquals(appInfo.asJsonArray().toString(), clonedAppInfo.asJsonArray().toString());

        ApplicationInformation applicationInformation = Agent.getApplicationInformation();

        Assert.assertEquals("Should return ARRAY Json type", Harvestable.Type.ARRAY, applicationInformation.getType());
        JsonObject jsonObject = applicationInformation.asJsonObject();
        Assert.assertNull("Should not return JsonObject", jsonObject);

        JsonElement jsonElement = applicationInformation.asJson();
        Assert.assertNotNull("Should return JsonArray as JsonElement", jsonElement instanceof JsonArray);

        /**
         * Per the spec https://newrelic.atlassian.net/wiki/display/eng/Mobile+Agent-Collector+Protocol#MobileAgent-CollectorProtocol-2.1ConnectAPIJSONFormat:
         * ApplicationInformation should return only 3 elements as JSON;
         */
        JsonArray jsonArray = applicationInformation.asJsonArray();
        Assert.assertEquals("ApplicationInformation json array should return 3 elements", 3, jsonArray.size());
    }

    @Test
    public void testDeviceInfo() {
        Assert.assertEquals(Harvestable.Type.ARRAY, devInfo.getType());
        Assert.assertNull(appInfo.asJsonObject());

        JsonArray array = devInfo.asJsonArray();
        Assert.assertNotNull(array);
        Assert.assertEquals("Should contain 10 elements", 10, array.size());

        // misc attributes
        devInfo.addMisc("size", "123");
        array = devInfo.asJsonArray();
        Assert.assertNotNull(array);
        Assert.assertEquals("Should still contain 10 elements", 10, array.size());
        JsonObject misc = (JsonObject) array.get(9);
        Assert.assertEquals("Should contain three new misc attributes", 3, misc.entrySet().size());

        DeviceInformation clonedDeviceInformation = new DeviceInformation();
        clonedDeviceInformation.setOsName(devInfo.getOsName());
        clonedDeviceInformation.setOsVersion(devInfo.getOsVersion());
        clonedDeviceInformation.setOsBuild(devInfo.getOsBuild());
        clonedDeviceInformation.setManufacturer(devInfo.getManufacturer());
        clonedDeviceInformation.setModel(devInfo.getModel());
        clonedDeviceInformation.setCountryCode(devInfo.getCountryCode());
        clonedDeviceInformation.setRegionCode(devInfo.getRegionCode());
        clonedDeviceInformation.setAgentName(devInfo.getAgentName());
        clonedDeviceInformation.setAgentVersion(devInfo.getAgentVersion());
        clonedDeviceInformation.setDeviceId(devInfo.getDeviceId());
        clonedDeviceInformation.setArchitecture(devInfo.getArchitecture());
        clonedDeviceInformation.setRunTime(devInfo.getRunTime());
        clonedDeviceInformation.setSize(devInfo.getSize());
        clonedDeviceInformation.setApplicationFramework(devInfo.getApplicationFramework());
        clonedDeviceInformation.setApplicationFrameworkVersion(devInfo.getApplicationFrameworkVersion());

        Assert.assertTrue(clonedDeviceInformation.equals(devInfo));

        // check fields that are not considered in equality
        clonedDeviceInformation.setRegionCode("BC");
        clonedDeviceInformation.setCountryCode("CA");
        clonedDeviceInformation.setApplicationFramework(ApplicationFramework.Native);
        clonedDeviceInformation.setApplicationFrameworkVersion("9.8.7.6");
        Assert.assertTrue(clonedDeviceInformation.equals(devInfo));

        // but check some that are
        clonedDeviceInformation.setArchitecture("bauhaus");
        Assert.assertFalse(clonedDeviceInformation.equals(devInfo));
    }

    @Test
    public void testConnectInformation() {
        JsonArray array = connectionInformation.asJsonArray();
        Assert.assertNotNull(array);
    }

    @Test
    public void testApplicationUpgrade() {
        ConnectInformation oldConnectionInformation = agent.provideConnectInformation();
        ConnectInformation newConnectionInformation = agent.provideConnectInformation();

        // that.versionCode == -1 && that.appName != null : upgrade from older version that didn't track versionCode
        // that.versionCode == -1 && this.versionCode >= 0 : newly installed app (no upgrade)
        // this.versionCode > that.versionCode : upgrade!

        newConnectionInformation.getApplicationInformation().setVersionCode(1);
        Assert.assertTrue("Older untracked versions are upgrades", newConnectionInformation.getApplicationInformation().isAppUpgrade(oldConnectionInformation.getApplicationInformation()));

        oldConnectionInformation.getApplicationInformation().setVersionCode(1);
        newConnectionInformation.getApplicationInformation().setVersionCode(0);
        Assert.assertFalse("Lesser versionCodes are not upgrades", newConnectionInformation.getApplicationInformation().isAppUpgrade(oldConnectionInformation.getApplicationInformation()));

        oldConnectionInformation.getApplicationInformation().setVersionCode(1);
        newConnectionInformation.getApplicationInformation().setVersionCode(1);
        Assert.assertFalse("Equal versionCodes are not upgrades", newConnectionInformation.getApplicationInformation().isAppUpgrade(oldConnectionInformation.getApplicationInformation()));

        oldConnectionInformation.getApplicationInformation().setVersionCode(1);
        newConnectionInformation.getApplicationInformation().setVersionCode(2);
        Assert.assertTrue("Higher versionCodes are upgrades", newConnectionInformation.getApplicationInformation().isAppUpgrade(oldConnectionInformation.getApplicationInformation()));

        oldConnectionInformation = agent.provideConnectInformation();
        oldConnectionInformation.getApplicationInformation().setAppVersion(null);
        newConnectionInformation.getApplicationInformation().setVersionCode(1);
        Assert.assertFalse("New installs are not upgrades", newConnectionInformation.getApplicationInformation().isAppUpgrade(oldConnectionInformation.getApplicationInformation()));
    }


    private class TestStubAgentImpl extends StubAgentImpl {
        @Override
        public boolean updateSavedConnectInformation() {
            return true;
        }

        public ConnectInformation provideConnectInformation() {
            return new ConnectInformation(super.getApplicationInformation(), super.getDeviceInformation());
        }

    }
}
