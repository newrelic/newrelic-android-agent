/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android;

import com.newrelic.agent.android.analytics.TestEventStore;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class AgentConfigurationTest {

    static final String appToken = "AA284dbe44f6b0be1c0f53cdb97f10624400a01111";

    AgentConfiguration agentConfiguration;

    @Before
    public void setUp() throws Exception {
        agentConfiguration = new AgentConfiguration();
    }

    @Test
    public void shouldParseRegionSpecifierFromApplicationToken() {
        String region;

        region = agentConfiguration.parseRegionFromApplicationToken(appToken);
        Assert.assertNull(region);

        region = agentConfiguration.parseRegionFromApplicationToken("eu01xx" + appToken);
        Assert.assertEquals(region, "eu01");

        region = agentConfiguration.parseRegionFromApplicationToken("xtestx" + appToken);
        Assert.assertEquals(region, "xtest");

        region = agentConfiguration.parseRegionFromApplicationToken("xtest" + appToken);
        Assert.assertNull(region);

        region = agentConfiguration.parseRegionFromApplicationToken("eu01x");
        Assert.assertEquals(region, "eu01");

        region = agentConfiguration.parseRegionFromApplicationToken("gov66xx");
        Assert.assertEquals(region, "gov66");

        // Edge-case: leading spaces
        region = agentConfiguration.parseRegionFromApplicationToken("  x" + appToken);
        Assert.assertEquals(region, "  ");

        region = agentConfiguration.parseRegionFromApplicationToken("  xx" + appToken);
        Assert.assertEquals(region, "  ");

        // More tests: https://source.datanerd.us/agents/cross_agent_tests/blob/master/collector_hostname.json
    }

    @Test
    public void shouldGetRegionalCollectorFromLicenseKey() {
        String collectorHost;

        collectorHost = agentConfiguration.getRegionalCollectorFromLicenseKey(appToken);
        Assert.assertEquals(collectorHost, agentConfiguration.getDefaultCollectorHost());

        collectorHost = agentConfiguration.getRegionalCollectorFromLicenseKey("eu01xx" + appToken);
        Assert.assertEquals(collectorHost, "mobile-collector.eu01.nr-data.net");

        collectorHost = agentConfiguration.getRegionalCollectorFromLicenseKey("xtestx" + appToken);
        Assert.assertEquals(collectorHost, "mobile-collector.xtest.nr-data.net");

        collectorHost = agentConfiguration.getRegionalCollectorFromLicenseKey("xtest" + appToken);
        Assert.assertEquals(collectorHost, agentConfiguration.getDefaultCollectorHost());

        // Return default collector host, event with bad app token
        collectorHost = agentConfiguration.getRegionalCollectorFromLicenseKey("gov66x");
        Assert.assertEquals(collectorHost, "mobile-collector.gov66.nr-data.net");

        // Edge-case: leading spaces
        collectorHost = agentConfiguration.getRegionalCollectorFromLicenseKey("  x" + appToken);
        Assert.assertEquals(collectorHost, "mobile-collector.  .nr-data.net");

        collectorHost = agentConfiguration.getRegionalCollectorFromLicenseKey("  xx" + appToken);
        Assert.assertEquals(collectorHost, "mobile-collector.  .nr-data.net");

        // region aware key with more than one identifier
        collectorHost = agentConfiguration.getRegionalCollectorFromLicenseKey("eu01xeu02x" + appToken);
        Assert.assertEquals(collectorHost, "mobile-collector.eu01.nr-data.net");

        // More tests: https://source.datanerd.us/agents/cross_agent_tests/blob/master/collector_hostname.json
    }

    @Test
    public void shouldGetFedRampCollector() {
        agentConfiguration.setApplicationToken(appToken);
        Assert.assertEquals(agentConfiguration.getCollectorHost(), agentConfiguration.getDefaultCollectorHost());
        Assert.assertEquals(agentConfiguration.getCrashCollectorHost(), agentConfiguration.getDefaultCrashCollectorHost());

        FeatureFlag.enableFeature(FeatureFlag.FedRampEnabled);
        agentConfiguration.setApplicationToken(appToken);
        Assert.assertEquals(agentConfiguration.getCollectorHost(), agentConfiguration.getFedRampCollectorHost());
        Assert.assertEquals(agentConfiguration.getCrashCollectorHost(), agentConfiguration.getFedRampCrashCollectorHost());

        FeatureFlag.resetFeatures();
        agentConfiguration.setApplicationToken(appToken);
        Assert.assertEquals(agentConfiguration.getCollectorHost(), agentConfiguration.getDefaultCollectorHost());
        Assert.assertEquals(agentConfiguration.getCrashCollectorHost(), agentConfiguration.getDefaultCrashCollectorHost());
    }

    @Test
    public void shouldAllowCollectorOverride() {
        agentConfiguration.setApplicationToken("eu01xx" + appToken);
        Assert.assertEquals(agentConfiguration.getCollectorHost(), "mobile-collector.eu01.nr-data.net");
        Assert.assertEquals(agentConfiguration.getCrashCollectorHost(), "mobile-crash.eu01.nr-data.net");

        agentConfiguration.setCollectorHost("staging-mobile-collector");
        Assert.assertEquals(agentConfiguration.getCollectorHost(), "staging-mobile-collector");

        agentConfiguration.setCrashCollectorHost("staging-mobile-crash");
        Assert.assertEquals(agentConfiguration.getCrashCollectorHost(), "staging-mobile-crash");
    }

    @Test
    public void shouldParseApplicationToken() {
        agentConfiguration.setApplicationToken(appToken);
        Assert.assertEquals(agentConfiguration.getApplicationToken(), appToken);
        Assert.assertEquals(agentConfiguration.getCollectorHost(), agentConfiguration.getDefaultCollectorHost());
        Assert.assertEquals(agentConfiguration.getCrashCollectorHost(), agentConfiguration.getDefaultCrashCollectorHost());

        agentConfiguration.setApplicationToken("eu01xx" + appToken);
        Assert.assertEquals(agentConfiguration.getApplicationToken(), "eu01xx" + appToken);
        Assert.assertEquals(agentConfiguration.getCollectorHost(), "mobile-collector.eu01.nr-data.net");
        Assert.assertEquals(agentConfiguration.getCrashCollectorHost(), "mobile-crash.eu01.nr-data.net");
    }

    @Test
    public void shouldParseFlutterApplicationFramework() {
        agentConfiguration.setApplicationFramework(ApplicationFramework.Flutter);
        Assert.assertEquals(agentConfiguration.getApplicationFramework(), ApplicationFramework.Flutter);
    }

    @Test
    public void shouldParseCapacitorApplicationFramework() {
        agentConfiguration.setApplicationFramework(ApplicationFramework.Capacitor);
        Assert.assertEquals(agentConfiguration.getApplicationFramework(), ApplicationFramework.Capacitor);
    }

    @Test
    public void shouldParseXamarinApplicationFramework() {
        agentConfiguration.setApplicationFramework(ApplicationFramework.Xamarin);
        Assert.assertEquals(agentConfiguration.getApplicationFramework(), ApplicationFramework.Xamarin);
    }

    @Test
    public void shouldParseUnityApplicationFramework() {
        agentConfiguration.setApplicationFramework(ApplicationFramework.Unity);
        Assert.assertEquals(agentConfiguration.getApplicationFramework(), ApplicationFramework.Unity);
    }

    @Test
    public void shouldParseUnrealApplicationFramework() {
        agentConfiguration.setApplicationFramework(ApplicationFramework.Unreal);
        Assert.assertEquals(agentConfiguration.getApplicationFramework(), ApplicationFramework.Unreal);
    }

    @Test
    public void testSetEventStore() {
        agentConfiguration.setEventStore(new TestEventStore());
        Assert.assertNotNull(agentConfiguration.getEventStore());
    }
}