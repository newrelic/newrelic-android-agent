/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.aei;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.stats.StatsEngine;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ApplicationExitConfigurationTest {

    private ApplicationExitConfiguration applicationExitConfiguration;
    private Gson gson = new GsonBuilder().create();

    @Before
    public void setUp() throws Exception {
        applicationExitConfiguration = new ApplicationExitConfiguration(false);
        FeatureFlag.disableFeature(FeatureFlag.ApplicationExitReporting);
    }

    @Test
    public void isEnabled() {
        Assert.assertFalse(applicationExitConfiguration.isEnabled());

        applicationExitConfiguration.enabled = true;
        Assert.assertFalse("Requires feature flag", applicationExitConfiguration.isEnabled());

        FeatureFlag.enableFeature(FeatureFlag.ApplicationExitReporting);
        Assert.assertTrue(applicationExitConfiguration.isEnabled());
    }

    @Test
    public void setEnabled() {
        Assert.assertFalse(applicationExitConfiguration.isEnabled());

        applicationExitConfiguration.setEnabled(true);
        Assert.assertFalse("Requires feature flag", applicationExitConfiguration.isEnabled());

        FeatureFlag.enableFeature(FeatureFlag.ApplicationExitReporting);
        Assert.assertTrue(applicationExitConfiguration.isEnabled());

        applicationExitConfiguration.setEnabled(true);
    }

    @Test
    public void setConfiguration() {
        applicationExitConfiguration = new ApplicationExitConfiguration(true);

        Assert.assertFalse(applicationExitConfiguration.isEnabled());
        FeatureFlag.enableFeature(FeatureFlag.ApplicationExitReporting);
        Assert.assertTrue(applicationExitConfiguration.isEnabled());

        ApplicationExitConfiguration aeiConfig = new ApplicationExitConfiguration(false);

        applicationExitConfiguration.setConfiguration(aeiConfig);
        Assert.assertFalse(applicationExitConfiguration.isEnabled());
        Assert.assertTrue(StatsEngine.SUPPORTABILITY.getStatsMap().keySet().contains(MetricNames.SUPPORTABILITY_AEI_REMOTE_CONFIG + "disabled"));

        aeiConfig = new ApplicationExitConfiguration(true);
        applicationExitConfiguration.setConfiguration(aeiConfig);
        Assert.assertTrue(applicationExitConfiguration.isEnabled());
        Assert.assertTrue(StatsEngine.SUPPORTABILITY.getStatsMap().keySet().contains(MetricNames.SUPPORTABILITY_AEI_REMOTE_CONFIG + "enabled"));
    }

    @Test
    public void testEquals() {
        ApplicationExitConfiguration lhs = new ApplicationExitConfiguration(true);
        ApplicationExitConfiguration rhs = new ApplicationExitConfiguration(true);

        Assert.assertFalse(lhs == rhs);
        Assert.assertTrue(lhs.equals(rhs));
        Assert.assertEquals(lhs, rhs);

        rhs = new ApplicationExitConfiguration(false);
        Assert.assertFalse(lhs.equals(rhs));
        Assert.assertNotEquals(lhs, rhs);
    }

    @Test
    public void testToString() {
        Assert.assertEquals("{\"enabled\"=false}", applicationExitConfiguration.toString());

        applicationExitConfiguration.enabled = true;
        Assert.assertEquals("{\"enabled\"=true}", applicationExitConfiguration.toString());
    }

    @Test
    public void testToJson() {
        Assert.assertEquals("{\"enabled\":false}", gson.toJson(applicationExitConfiguration));

        applicationExitConfiguration.enabled = true;
        Assert.assertEquals("{\"enabled\":true}", gson.toJson(applicationExitConfiguration));
    }

}