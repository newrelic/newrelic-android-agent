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
    }

    @Test
    public void validateMetrics() {
        AppStartUpMetrics metrics = new AppStartUpMetrics();

        Assert.assertEquals(100L, (long) metrics.getApplicationOnCreateTime());
        Assert.assertEquals(200L, (long) metrics.getAppOnCreateEndToFirstActivityCreate());
        Assert.assertEquals(200L, (long) metrics.getFirstActivityCreateToResume());
        Assert.assertEquals(600L, (long) metrics.getColdStartTime());
        Assert.assertEquals(100L, (long) metrics.getHotStartTime());
    }

    @Test
    public void appOnCreateEndTimeUnset_returnsZeroForDependentMetrics() {
        // Simulates the Handler.post Runnable not having drained yet:
        // appOnCreateEndTime stays at its default 0L. The constructor must NOT
        // emit a negative value for applicationOnCreateTime — that's #559.
        tracerInstance.setAppOnCreateEndTime(0L);

        AppStartUpMetrics metrics = new AppStartUpMetrics();

        Assert.assertEquals(0L, (long) metrics.getApplicationOnCreateTime());
        Assert.assertEquals(0L, (long) metrics.getAppOnCreateEndToFirstActivityCreate());
    }

    @Test
    public void firstActivityStartTimeUnset_returnsZeroForHotStart() {
        // Defensive: if firstActivityStartTime never got set, hotStartTime would
        // otherwise resolve to ≈ uptime.
        tracerInstance.setFirstActivityStartTime(0L);

        AppStartUpMetrics metrics = new AppStartUpMetrics();

        Assert.assertEquals(0L, (long) metrics.getHotStartTime());
    }
}