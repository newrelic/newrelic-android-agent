/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.rum;

import android.content.Intent;

import org.junit.Assert;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class AppTracerTest {

    private AppTracer tracerInstance;
    private Intent tmpIntent = new Intent();

    @Before
    public void setUp() {
        tracerInstance = AppTracer.getInstance();
        Assert.assertNotNull(tracerInstance);

        tracerInstance.setContentProviderStartedTime(100L);
        tracerInstance.setAppOnCreateTime(200L);
        tracerInstance.setAppOnCreateEndTime(300L);
        tracerInstance.setFirstDrawTime(400L);
        tracerInstance.setFirstActivityCreatedTime(500L);
        tracerInstance.setFirstActivityResumeTime(600L);
        tracerInstance.setLastAppPauseTime(700L);

        tracerInstance.setFirstActivityName("First Activity");
        tracerInstance.setFirstActivityReferrer("First Referrer");
        tracerInstance.setFirstActivityIntent(tmpIntent);

        tracerInstance.setCurrentAppLaunchProcessed(false);
        tracerInstance.setFirstPostExecuted(true);
    }

    @Test
    public void validateNewRelicAppTracer() {
        Assert.assertEquals((long) tracerInstance.getContentProviderStartedTime(), 100L);
        Assert.assertEquals((long) tracerInstance.getAppOnCreateTime(), 200L);
        Assert.assertEquals((long) tracerInstance.getAppOnCreateEndTime(), 300L);
        Assert.assertEquals((long) tracerInstance.getFirstDrawTime(), 400L);
        Assert.assertEquals((long) tracerInstance.getFirstActivityCreatedTime(), 500L);
        Assert.assertEquals((long) tracerInstance.getFirstActivityResumeTime(), 600L);
        Assert.assertEquals((long) tracerInstance.getLastAppPauseTime(), 700L);

        Assert.assertEquals(tracerInstance.getFirstActivityName(), "First Activity");
        Assert.assertEquals(tracerInstance.getFirstActivityReferrer(), "First Referrer");
        Assert.assertEquals(tracerInstance.getFirstActivityIntent(), tmpIntent);

        Assert.assertFalse(tracerInstance.getCurrentAppLaunchProcessed());
        Assert.assertTrue(tracerInstance.getFirstPostExecuted());
    }
}
