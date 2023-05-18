/*
 * Copyright (c) 2021-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.ndk;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.SpyContext;

import org.junit.Assert;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class NativeReportingTest {

    private SpyContext context;
    private AgentConfiguration agentConfig;
    private NativeReporting module;

    @Before
    public void setup() {
        context = new SpyContext();
        agentConfig = new AgentConfiguration();
        module = NativeReporting.initialize(context.getContext(), agentConfig);
    }

    @Test
    public void getInstance() {
        Assert.assertEquals(module, NativeReporting.getInstance());
    }

    @Test
    public void initialize() {
        NativeReporting module = NativeReporting.initialize(context.getContext(), agentConfig);
        Assert.assertTrue(NativeReporting.isInitialized());
    }

    @Test
    public void shutdown() {
    }

    @Test
    public void isInitialized() {
        Assert.assertTrue(NativeReporting.isInitialized());
    }

    @Test
    public void start() {
    }

    @Test
    public void stop() {
    }

    @Test
    public void onNativeCrash() {
    }

    @Test
    public void onNativeException() {
    }

    @Test
    public void onApplicationNotResponding() {
    }

    @Test
    public void onHarvestStart() {
    }
}