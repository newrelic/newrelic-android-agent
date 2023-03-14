/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.rum;

import org.junit.Assert;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class AppStartUpMetricsTest {
    private AppTracer tracerInstance;
    private AppStartUpMetrics metrics;

    @Before
    public void setUp() {
        tracerInstance = AppTracer.getInstance();
        Assert.assertNotNull(tracerInstance);

        tracerInstance.setContentProviderStartedTime(100L);
        tracerInstance.setAppOnCreateTime(200L);
        tracerInstance.setAppOnCreateEndTime(300L);
        tracerInstance.setFirstDrawTime(400L);
        tracerInstance.setFirstActivityCreatedTime(500L);
        tracerInstance.setFirstActivityStartTime(600L);
        tracerInstance.setFirstActivityResumeTime(700L);
        tracerInstance.setLastAppPauseTime(800L);

        metrics = new AppStartUpMetrics();
    }

    @Test
    public void validateMetrics() {
        Assert.assertEquals((long) metrics.getContentProviderToAppStart(), 100L);
        Assert.assertEquals((long) metrics.getApplicationOnCreateTime(), 100L);
        Assert.assertEquals((long) metrics.getAppOnCreateEndToFirstActivityCreate(), 200L);
        Assert.assertEquals((long) metrics.getFirstActivityCreateToResume(), 200L);
        Assert.assertEquals((long) metrics.getColdStartTime(), 600L);
        Assert.assertEquals((long) metrics.getHotStartTime(), 100L);
        Assert.assertEquals((long) metrics.getWarmStartTime(), 600L);
    }
}
