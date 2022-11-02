/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.tracing;

import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.api.v2.TraceFieldInterface;
import com.newrelic.agent.android.api.v2.TraceMachineInterface;
import com.newrelic.agent.android.stats.StatsEngine;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(JUnit4.class)
public class TraceMachineTests {

    @BeforeClass
    public static void classSetUp() throws Exception {
        TraceMachine.HEALTHY_TRACE_TIMEOUT = 10000;
    }

    @Before
    public void setUp() throws Exception {
        TraceMachine.setTraceMachineInterface(null);
    }

    @Before
    public void setUpFeatureFlags() throws Exception {
        FeatureFlag.enableFeature(FeatureFlag.InteractionTracing);
    }

    @Test
    public void testTraceListener() {
        TraceListener traceListener = new TraceListener();
        TraceMachine.addTraceListener(traceListener);

        TraceMachine.startTracing("testActivityTrace");
        Assert.assertTrue(traceListener.onTraceStartCalled);

        TraceMachine.enterMethod("testMethod");
        Assert.assertTrue(traceListener.onEnterMethodCalled);

        TraceMachine.exitMethod();
        Assert.assertTrue(traceListener.onExitMethodCalled);

        // Starting another AT will cause the current AT to complete.
        TraceMachine.startTracing("anotherActivityTrace");
        Assert.assertTrue(traceListener.onTraceCompleteCalled);

        TraceMachine.setCurrentDisplayName("renamedTrace");
        Assert.assertTrue(traceListener.onTraceRenameCalled);

        TraceMachine.removeTraceListener(traceListener);

        // reset the listener
        traceListener = new TraceListener();

        TraceMachine.startTracing("yetAnotherActivityTrace");
        Assert.assertFalse(traceListener.onTraceCompleteCalled);

        TraceMachine.enterMethod("testMethod");
        Assert.assertFalse(traceListener.onEnterMethodCalled);

        TraceMachine.exitMethod();
        Assert.assertFalse(traceListener.onExitMethodCalled);

        TraceMachine.haltTracing();
        Assert.assertTrue(TraceMachine.isTracingInactive());

    }

    @Test
    public void testTraceMachineInterface() throws Exception {
        TestTraceMachineInterface traceMachineInterface = new TestTraceMachineInterface();
        TraceMachine.setTraceMachineInterface(traceMachineInterface);

        TraceMachine.startTracing("testActivityTrace");
        TraceMachine.enterMethod("testMethod");
        Trace trace = TraceMachine.getCurrentTrace();
        TraceMachine.exitMethod();

        Assert.assertEquals(traceMachineInterface.getCurrentThreadId(), trace.threadId);
        Assert.assertEquals(traceMachineInterface.getCurrentThreadName(), trace.threadName);

        TraceMachine.haltTracing();
        Assert.assertTrue(TraceMachine.isTracingInactive());
    }

    @Test
    public void testStartTracing() {
        TraceMachine.startTracing("testActivityTrace");

        Assert.assertTrue(TraceMachine.isTracingActive());

        TraceMachine.haltTracing();
        Assert.assertTrue(TraceMachine.isTracingInactive());
    }

    @Test
    public void testHaltTracing() {
        TraceMachine.startTracing("testActivityTrace");
        TraceMachine.haltTracing();

        Assert.assertTrue(TraceMachine.isTracingInactive());
    }

    @Test
    public void testEnterNetworkSegment() throws Exception {
        TraceMachine.startTracing("testActivityTrace");

        TraceMachine.enterNetworkSegment("testNetwork");

        Trace trace = TraceMachine.getCurrentTrace();
        Assert.assertEquals(TraceType.NETWORK, trace.getType());

        TraceMachine.haltTracing();
        Assert.assertTrue(TraceMachine.isTracingInactive());
    }

    @Test
    public void testEnterMethodSync() throws Exception {
        TraceMachine.startTracing("testActivityTrace");

        Trace rootTrace = TraceMachine.getCurrentTrace();

        TraceMachine.enterMethod("testMethod");
        Trace childTrace = TraceMachine.getCurrentTrace();

        Assert.assertNotEquals(rootTrace.myUUID, childTrace.myUUID);
        Assert.assertEquals(rootTrace.myUUID, childTrace.parentUUID);
        Assert.assertEquals(rootTrace.getChildren().size(), 1);

        Assert.assertEquals(childTrace.scope, TraceMachine.getCurrentScope());
        Assert.assertNotEquals(childTrace.entryTimestamp, 0);

        TraceMachine.haltTracing();
        Assert.assertTrue(TraceMachine.isTracingInactive());
    }

    @Test
    public void testExitMethod() throws Exception {
        TraceMachine.startTracing("testActivityTrace");

        Trace rootTrace = TraceMachine.getCurrentTrace();

        TraceMachine.enterMethod("testMethod");
        Trace childTrace = TraceMachine.getCurrentTrace();

        // Gotta sleep so the durations aren't 0
        Thread.sleep(10);

        TraceMachine.exitMethod();

        Assert.assertNotEquals(rootTrace.childExclusiveTime, 0);
        Assert.assertEquals(rootTrace.myUUID, TraceMachine.getCurrentTrace().myUUID);

        Assert.assertTrue(childTrace.isComplete());
        Assert.assertNotEquals(childTrace.exitTimestamp, 0);
        Assert.assertNotEquals(childTrace.exclusiveTime, 0);

        TraceMachine.haltTracing();
        Assert.assertTrue(TraceMachine.isTracingInactive());
    }

    @Test
    public void testTraceParams() throws Exception {
        TraceMachine.startTracing("testActivityTrace");
        TraceMachine.enterMethod("testMethod");
        TraceMachine.setCurrentTraceParam("test", "me");

        Map<String, Object> map = TraceMachine.getCurrentTraceParams();
        Assert.assertEquals(map.get("test"), "me");

        TraceMachine.haltTracing();
        Assert.assertTrue(TraceMachine.isTracingInactive());
    }

    @Test
    public void testSetDisplayName() throws Exception {
        TraceMachine.startTracing("testActivityTrace");
        TraceMachine.enterMethod("testMethod");
        TraceMachine.setCurrentDisplayName("testing");

        Trace trace = TraceMachine.getCurrentTrace();
        Assert.assertEquals(trace.displayName, "testing");

        TraceMachine.haltTracing();
        Assert.assertTrue(TraceMachine.isTracingInactive());
    }

    @Test
    public void testGetCurrentScope() {
        TraceMachine.startTracing("testActivityTrace");
        TraceMachine.enterMethod("testMethod");

        Assert.assertEquals(TraceMachine.ACTIVITY_METRIC_PREFIX + "Display testActivityTrace", TraceMachine.getCurrentScope());

        TestTraceMachineInterface traceMachineInterface = new TestTraceMachineInterface();
        TraceMachine.setTraceMachineInterface(traceMachineInterface);

        Assert.assertEquals(TraceMachine.ACTIVITY_BACKGROUND_METRIC_PREFIX + "Display testActivityTrace", TraceMachine.getCurrentScope());

        TraceMachine.haltTracing();
        Assert.assertTrue(TraceMachine.isTracingInactive());
    }

    @Test
    public void testHealthyHarvest() throws Exception {
        TraceMachine.startTracing("testActivityTrace");
        TraceMachine.enterMethod("testMethod");
        TraceMachine.exitMethod();

        TraceMachine traceMachine = TraceMachine.getTraceMachine();
        if (traceMachine == null) {
            Assert.fail("Null trace machine");
        }

        ActivityTrace activityTrace = getActivityTraceFromTraceMachine(traceMachine);
        activityTrace.lastUpdatedAt = System.currentTimeMillis() - (TraceMachine.HEALTHY_TRACE_TIMEOUT + 1000);
        traceMachine.onHarvestBefore();

        Assert.assertTrue(activityTrace.isComplete());
        Assert.assertTrue(TraceMachine.isTracingInactive());
    }

    @Test
    public void testUnhealthyHarvest() throws Exception {
        TraceMachine.startTracing("testActivityTrace");
        TraceMachine.enterMethod("testMethod");

        TraceMachine traceMachine = TraceMachine.getTraceMachine();
        if (traceMachine == null) {
            Assert.fail("Null trace machine");
        }

        ActivityTrace activityTrace = getActivityTraceFromTraceMachine(traceMachine);

        activityTrace.startedAt = System.currentTimeMillis() - (TraceMachine.UNHEALTHY_TRACE_TIMEOUT + 1000);

        traceMachine.onHarvestBefore();

        Assert.assertTrue(activityTrace.isComplete());
        Assert.assertTrue(TraceMachine.isTracingInactive());
    }

    @Test
    public void testShouldUnloadTraceFieldInterface() throws Exception {
        TestTraceFieldInterface testTraceFieldInterface = new TestTraceFieldInterface();
        Assert.assertNotNull(testTraceFieldInterface.trace);

        TestTraceMachineInterface traceMachineInterface = new TestTraceMachineInterface();
        TraceMachine.setTraceMachineInterface(traceMachineInterface);
        TraceMachine.startTracing("testActivityTrace");
        TraceMachine.unloadTraceContext(testTraceFieldInterface);

        // Test trace object was cast to TFI and removed form instance
        Assert.assertNull(testTraceFieldInterface.trace);

        // Test with non injected trace
        final Trace testNonTraceFieldInterface = new Trace();
        TraceMachine.unloadTraceContext(testNonTraceFieldInterface);

        final String supportabilityKey = "Supportability/AgentHealth/TraceFieldInterface/com.newrelic.agent.android.tracing.TraceMachine/unloadTraceContext/java.lang.ClassCastException";
        Assert.assertTrue(StatsEngine.get().getStatsMap().containsKey(supportabilityKey));
    }

    @Test
    public void testShouldDisableViaFeatureFlag() throws Exception {
        Assert.assertTrue("Should enable traces by default", TraceMachine.isEnabled());
        FeatureFlag.disableFeature(FeatureFlag.InteractionTracing);
        Assert.assertFalse("Should disable traces via feature flag", TraceMachine.isEnabled());
        TraceMachine.startTracing("testActivityTrace");
        Assert.assertFalse("Should not start trace if feature disabled", TraceMachine.isTracingActive());
        try {
            TraceMachine.getCurrentTrace();
            Assert.fail("Should not return current trace");
        } catch (TracingInactiveException e) {
        }
    }

    @Test
    public void testTraceMachineSynchronization() throws Exception {
        /*
         * We want to ensure in the process of normal lifecycle operations, another thread starting,
         * renaming, or completing an interaction doesn't nullify the existing TraceMachine.
         */
        int iterations = 1000;
        final CountDownLatch latch = new CountDownLatch(iterations);
        final AtomicBoolean gotNpe = new AtomicBoolean(false);

        for (int i = 0; i < iterations; i++) {
            final int finalI = i;
            Thread thread = new Thread() {
                @Override
                public void run() {
                    try {
                        TraceMachine.startTracing("interaction" + finalI);
                        TraceMachine.setCurrentDisplayName("renamedInteraction" + finalI);
                    } catch (NullPointerException e) {
                        gotNpe.set(true);
                        Assert.fail("TraceMachine should not be null: " + e.toString());
                    } finally {
                        latch.countDown();
                    }
                }
            };
            thread.start();
        }
        if (gotNpe.get()) {
            Assert.fail();
        }
        latch.await();
    }

    // Utility stubs
    private class TraceListener implements TraceLifecycleAware {
        public boolean onEnterMethodCalled = false;
        public boolean onExitMethodCalled = false;
        public boolean onTraceCompleteCalled = false;
        public boolean onTraceStartCalled = false;
        public boolean onTraceRenameCalled = false;

        @Override
        public void onEnterMethod() {
            onEnterMethodCalled = true;
        }

        @Override
        public void onExitMethod() {
            onExitMethodCalled = true;
        }

        @Override
        public void onTraceStart(ActivityTrace activityTrace) {
            onTraceStartCalled = true;
        }

        @Override
        public void onTraceComplete(ActivityTrace activityTrace) {
            onTraceCompleteCalled = true;
        }

        @Override
        public void onTraceRename(ActivityTrace activityTrace) {
            onTraceRenameCalled = true;
        }
    }

    private class TestTraceMachineInterface implements TraceMachineInterface {
        @Override
        public long getCurrentThreadId() {
            return 42;
        }

        @Override
        public String getCurrentThreadName() {
            return "testThread";
        }

        @Override
        public boolean isUIThread() {
            return false;
        }
    }

    private static class TestTraceFieldInterface extends Trace implements TraceFieldInterface {
        public Trace trace = new Trace();

        @Override
        public void _nr_setTrace(com.newrelic.agent.android.tracing.Trace trace) {
            this.trace = trace;
        }
    }

    private ActivityTrace getActivityTraceFromTraceMachine(TraceMachine traceMachine) throws Exception {
        // This is all necessary to set fields on the activityTrace
        Class<?> clazz = traceMachine.getClass();
        Field activityTraceField = clazz.getDeclaredField("activityTrace");
        activityTraceField.setAccessible(true);
        return (ActivityTrace) activityTraceField.get(traceMachine);
    }


}
