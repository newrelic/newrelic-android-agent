/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.rum;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.app.Activity;

import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.stats.StatsEngine;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Field;

@RunWith(RobolectricTestRunner.class)
public class AppApplicationLifeCycleTest {

    private AppApplicationLifeCycle lifecycle;

    @Before
    public void setUp() throws Exception {
        resetStatic("firstDrawInvoked", false);
        resetStatic("firstActivityCreated", false);
        resetStatic("firstActivityResumed", false);
        resetStatic("isActivityChangingConfig", false);
        resetStatic("isForegrounded", false);
        resetStatic("isBackgrounded", false);
        resetStatic("backgroundedBeforeFirstDraw", false);
        resetStatic("activityReferences", 0);

        StatsEngine.get().getStatsMap().clear();

        AppTracer tracer = AppTracer.getInstance();
        tracer.setContentProviderStartedTime(0L);
        tracer.setProcessStartTime(0L);
        tracer.setFirstActivityStartTime(0L);
        tracer.setFirstActivityResumeTime(0L);
        tracer.setFirstDrawTime(0L);
        tracer.setIsColdStart(true);

        lifecycle = new AppApplicationLifeCycle();
    }

    private static void resetStatic(String fieldName, Object value) throws Exception {
        Field field = AppApplicationLifeCycle.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(null, value);
    }

    private static boolean getStaticBoolean(String fieldName) throws Exception {
        Field field = AppApplicationLifeCycle.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getBoolean(null);
    }

    private static Activity activityNamed(String localClassName) {
        Activity activity = mock(Activity.class);
        when(activity.getLocalClassName()).thenReturn(localClassName);
        when(activity.isChangingConfigurations()).thenReturn(false);
        return activity;
    }

    // --- cold-start recording decision (pure) ---

    @Test
    public void shouldRecordColdStart_forGenuineColdStartNotBackgrounded() {
        Assert.assertTrue(AppApplicationLifeCycle.shouldRecordColdStart(true, false));
    }

    @Test
    public void shouldNotRecordColdStart_whenBackgroundedBeforeFirstFrame() {
        Assert.assertFalse(AppApplicationLifeCycle.shouldRecordColdStart(true, true));
    }

    @Test
    public void shouldNotRecordColdStart_whenNotAColdStart() {
        Assert.assertFalse(AppApplicationLifeCycle.shouldRecordColdStart(false, false));
    }

    // --- cold-start sampling (integration through StatsEngine) ---

    @Test
    public void sampleColdStart_recordsColdMetric_forNormalColdStart() {
        AppTracer tracer = AppTracer.getInstance();
        tracer.setIsColdStart(true);
        tracer.setProcessStartTime(100L);
        tracer.setFirstDrawTime(1600L);

        AppApplicationLifeCycle.sampleColdStart();

        Assert.assertTrue("AppLaunch/Cold should be recorded to first frame on a normal cold start",
                StatsEngine.get().getStatsMap().containsKey(MetricNames.APP_LAUNCH_COLD));
    }

    @Test
    public void sampleColdStart_skipsColdMetric_whenBackgroundedBeforeFirstFrame() throws Exception {
        AppTracer tracer = AppTracer.getInstance();
        tracer.setIsColdStart(true);
        tracer.setProcessStartTime(100L);
        tracer.setFirstDrawTime(1600L);
        resetStatic("backgroundedBeforeFirstDraw", true);

        AppApplicationLifeCycle.sampleColdStart();

        Assert.assertFalse("AppLaunch/Cold must not be recorded when backgrounded before the first frame",
                StatsEngine.get().getStatsMap().containsKey(MetricNames.APP_LAUNCH_COLD));
    }

    @Test
    public void sampleColdStart_skipsColdMetric_whenNotColdStart() {
        AppTracer tracer = AppTracer.getInstance();
        tracer.setIsColdStart(false);
        tracer.setProcessStartTime(100L);
        tracer.setFirstDrawTime(1600L);

        AppApplicationLifeCycle.sampleColdStart();

        Assert.assertFalse("AppLaunch/Cold must not be recorded for a warm/hot start",
                StatsEngine.get().getStatsMap().containsKey(MetricNames.APP_LAUNCH_COLD));
    }

    // --- backgrounded-before-first-frame flag ---

    @Test
    public void backgroundedBeforeFirstDraw_setWhenAppStopsBeforeFirstFrame() throws Exception {
        Activity activity = activityNamed("SplashActivity");

        lifecycle.onActivityStarted(activity);
        lifecycle.onActivityStopped(activity); // activityReferences -> 0 while firstDrawInvoked is false

        Assert.assertTrue("backgrounding before the first frame must invalidate the cold sample",
                getStaticBoolean("backgroundedBeforeFirstDraw"));
    }

    @Test
    public void backgroundedBeforeFirstDraw_notSetWhenAppStopsAfterFirstFrame() throws Exception {
        resetStatic("firstDrawInvoked", true); // first frame already drawn
        Activity activity = activityNamed("MainActivity");

        lifecycle.onActivityStarted(activity);
        lifecycle.onActivityStopped(activity);

        Assert.assertFalse("stopping after the first frame is a normal background, not a cold-start invalidation",
                getStaticBoolean("backgroundedBeforeFirstDraw"));
    }

    // --- hot start behavior (unchanged) ---

    @Test
    public void hotStartMetricRecorded_onReturnToForeground() {
        Activity activity = activityNamed("MainActivity");

        // Foreground, then background, then return to foreground.
        lifecycle.onActivityStarted(activity);
        lifecycle.onActivityStopped(activity);   // activityReferences -> 0, isBackgrounded = true
        lifecycle.onActivityStarted(activity);   // background -> foreground: isForegrounded = true
        lifecycle.onActivityResumed(activity);

        Assert.assertTrue("AppLaunch/Hot should be recorded on return to foreground",
                StatsEngine.get().getStatsMap().containsKey(MetricNames.APP_LAUNCH_HOT));
    }
}
