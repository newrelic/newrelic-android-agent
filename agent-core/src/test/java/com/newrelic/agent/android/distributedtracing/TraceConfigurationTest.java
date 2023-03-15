/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.distributedtracing;

import com.newrelic.agent.android.harvest.HarvestConfiguration;

import junit.framework.TestCase;

import org.junit.Assert;

public class TraceConfigurationTest extends TestCase {

    private TraceConfiguration traceConfiguration;

    @Override
    public void setUp() throws Exception {
        traceConfiguration = new TraceConfiguration("1", "1", "");
        traceConfiguration = TraceConfiguration.setInstance(traceConfiguration);
    }

    public void testIsSampled() {
        Assert.assertFalse(traceConfiguration.isSampled());
    }

    public void testOnHarvestConnected() {
        HarvestConfiguration.getDefaultHarvestConfiguration().setAccount_id("2");
        HarvestConfiguration.getDefaultHarvestConfiguration().setApplication_id(("22"));
        traceConfiguration.onHarvestConnected();
        Assert.assertEquals("2", traceConfiguration.accountId);
        Assert.assertEquals("22", traceConfiguration.applicationId);
    }

    public void testSetConfiguration() {
        HarvestConfiguration.getDefaultHarvestConfiguration().setAccount_id("2");
        HarvestConfiguration.getDefaultHarvestConfiguration().setApplication_id(("22"));
        traceConfiguration.setConfiguration(HarvestConfiguration.getDefaultHarvestConfiguration());
        Assert.assertEquals("2", traceConfiguration.accountId);
        Assert.assertEquals("22", traceConfiguration.applicationId);
    }

    public void testGetInstance() {
        TraceConfiguration instance = TraceConfiguration.getInstance();
        Assert.assertNotNull(instance);
        Assert.assertEquals(traceConfiguration, instance);
    }

    public void testSetInstance() {
        TraceConfiguration instance = new TraceConfiguration("3", "33", "333");
        TraceConfiguration.setInstance(instance);
        Assert.assertEquals(instance, TraceConfiguration.getInstance());
    }
}