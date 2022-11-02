/**
 * Copyright 2021 New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.interactions;

import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.NewRelic;
import com.newrelic.agent.android.tracing.ActivityTrace;
import com.newrelic.agent.android.tracing.TraceLifecycleAware;
import com.newrelic.agent.android.tracing.TraceMachine;
import com.newrelic.agent.android.tracing.TracingInactiveException;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class InteractionsTest {

    @Before
    public void setUpTraceMachine() throws Exception {
        NewRelic.enableFeature(FeatureFlag.InteractionTracing);
        NewRelic.enableFeature(FeatureFlag.DefaultInteractions);
        TraceMachine.haltTracing();
    }

    @Test
    public void testShouldAllowDefaultInteractionsWhenEnabled() throws Exception {
        NewRelic.enableFeature(FeatureFlag.DefaultInteractions);
        String id = provideDefaultInteraction("testActivityTrace");
        Assert.assertTrue("Should start interaction if feature enabled", TraceMachine.isTracingActive());
        NewRelic.endInteraction(id);

        NewRelic.disableFeature(FeatureFlag.InteractionTracing);
        id = provideDefaultInteraction("testActivityTrace");
        Assert.assertFalse("Should not start default interaction if feature disabled", TraceMachine.isTracingActive());
        try {
            TraceMachine.getCurrentTrace();
            Assert.fail("Should not return current trace");
        } catch (TracingInactiveException e) {
        }
    }

    @Test
    public void testShouldNotAllowDefaultInteractionsWhenDisabled() throws Exception {
        NewRelic.disableFeature(FeatureFlag.DefaultInteractions);
        String id = provideDefaultInteraction("testActivityTrace");
        Assert.assertFalse("Should not start default interaction if feature disabled", TraceMachine.isTracingActive());
        NewRelic.endInteraction(id);

        NewRelic.disableFeature(FeatureFlag.InteractionTracing);
        id = provideDefaultInteraction("testActivityTrace");
        Assert.assertFalse("Should not start default interaction if feature disabled", TraceMachine.isTracingActive());
        try {
            TraceMachine.getCurrentTrace();
            Assert.fail("Should not return current trace");
        } catch (TracingInactiveException e) {
        }
    }

    @Test
    public void testShouldNotAllowInteractionsWhenDisabled() throws Exception {
        NewRelic.disableFeature(FeatureFlag.InteractionTracing);

        // should not allow default interactions
        provideDefaultInteraction("testActivityTrace");
        Assert.assertFalse("Should not start interaction if feature disabled", TraceMachine.isTracingActive());
        try {
            TraceMachine.getCurrentTrace();
            Assert.fail("Should not return current trace");
        } catch (TracingInactiveException e) {
        }

        // should not allow custom interactions
        String id = provideCustomInteraction("testActivityTrace");
        Assert.assertFalse("Should not start custom interaction if features disabled", TraceMachine.isTracingActive());

    }

    @Test
    public void testShouldAllowCustomInteractions() throws Exception {
        NewRelic.disableFeature(FeatureFlag.DefaultInteractions);
        provideDefaultInteraction("testActivityTrace");
        Assert.assertFalse("Should not start trace if feature disabled", TraceMachine.isTracingActive());

        String id = provideCustomInteraction("testInteraction");
        Assert.assertTrue("Should start default interaction even if feature disabled", TraceMachine.isTracingActive());
        try {
            Assert.assertNotNull("Should return current trace", TraceMachine.getCurrentTrace());
            NewRelic.endInteraction(id);
            Assert.assertTrue("Should end custom interaction", TraceMachine.isTracingInactive());
        } catch (TracingInactiveException e) {
            Assert.fail("Should return current trace");
        }
    }

    @Test
    public void testShouldAllowCustomMethodTraces() throws Exception {
        NewRelic.disableFeature(FeatureFlag.DefaultInteractions);
        provideDefaultInteraction("testActivityTrace");
        Assert.assertFalse("Should not start trace if feature disabled", TraceMachine.isTracingActive());

        TraceLifecycleListener listener = new TraceLifecycleListener();
        TraceMachine.addTraceListener(listener);

        provideCustomInteraction("testInteraction");
        Assert.assertTrue("Should start default interaction if feature enabled", TraceMachine.isTracingActive());
        NewRelic.startMethodTrace("testMethodInteraction");
        Assert.assertNotNull("Should trace method if custom interaction running", TraceMachine.getCurrentTrace());
        Assert.assertTrue("Method was traced", listener.wasCalled);
        NewRelic.endMethodTrace();
    }

    @Test
    public void testEnablelInteractionsEnableDefaults() throws Exception {
        NewRelic.enableFeature(FeatureFlag.InteractionTracing);
        NewRelic.enableFeature(FeatureFlag.DefaultInteractions);

        String defaultInteraction = provideDefaultInteraction("testActivityTrace");
        Assert.assertNotNull("Should return interaction ID", defaultInteraction);
        Assert.assertTrue("Should start default interaction if feature enabled", TraceMachine.isTracingActive());
        NewRelic.endInteraction(defaultInteraction);
        TraceMachine.haltTracing();

        String customInteraction = provideCustomInteraction("testActivityTrace");
        Assert.assertTrue("Should start custom interaction if feature enabled", TraceMachine.isTracingActive());
        NewRelic.endInteraction(defaultInteraction);
    }

    @Test
    public void testEnablelInteractionsDisableDefaults() throws Exception {
        NewRelic.enableFeature(FeatureFlag.InteractionTracing);
        NewRelic.disableFeature(FeatureFlag.DefaultInteractions);

        String defaultInteraction = provideDefaultInteraction("testActivityTrace");
        Assert.assertNull("Should not return interaction ID", defaultInteraction);
        Assert.assertFalse("Should not start default interaction if feature disabled", TraceMachine.isTracingActive());
        NewRelic.endInteraction(defaultInteraction);
        TraceMachine.haltTracing();

        String customInteraction = provideCustomInteraction("testActivityTrace");
        Assert.assertTrue("Should start custom interaction if feature enabled", TraceMachine.isTracingActive());
        NewRelic.endInteraction(customInteraction);
    }

    @Test
    public void testDisablelInteractionsEnableDefaults() throws Exception {
        NewRelic.disableFeature(FeatureFlag.InteractionTracing);
        NewRelic.enableFeature(FeatureFlag.DefaultInteractions);

        String defaultInteraction = provideDefaultInteraction("testActivityTrace");
        Assert.assertNull("Should not return interaction ID", defaultInteraction);
        Assert.assertFalse("Should not start default interaction if feature disabled", TraceMachine.isTracingActive());
        NewRelic.endInteraction(defaultInteraction);
        TraceMachine.haltTracing();

        String customInteraction = provideCustomInteraction("testActivityTrace");
        Assert.assertFalse("Should not start custom interaction if feature enabled", TraceMachine.isTracingActive());
        NewRelic.endInteraction(customInteraction);
    }

    @Test
    public void testDisablelInteractionsDisableDefaults() throws Exception {
        NewRelic.disableFeature(FeatureFlag.InteractionTracing);
        NewRelic.disableFeature(FeatureFlag.DefaultInteractions);

        String defaultInteraction = provideDefaultInteraction("testActivityTrace");
        Assert.assertNull("Should not return interaction ID", defaultInteraction);
        Assert.assertFalse("Should start default interaction if feature disabled", TraceMachine.isTracingActive());
        NewRelic.endInteraction(defaultInteraction);

        TraceMachine.haltTracing();
        String customInteraction = provideCustomInteraction("testActivityTrace");
        Assert.assertFalse("Should not start custom interaction if feature disabled", TraceMachine.isTracingActive());
        NewRelic.endInteraction(customInteraction);
    }


    protected String provideDefaultInteraction(final String name) {
        try {
            TraceMachine.startTracing(name);
            return TraceMachine.getActivityTrace().getId();
        } catch (TracingInactiveException e) {
        }
        return null;
    }

    protected String provideCustomInteraction(final String name) {
        return NewRelic.startInteraction(name);
    }

    private static class TraceLifecycleListener implements TraceLifecycleAware {
        public boolean wasCalled = false;

        @Override
        public void onEnterMethod() {
            wasCalled = true;
        }

        @Override
        public void onExitMethod() {
        }

        @Override
        public void onTraceStart(ActivityTrace activityTrace) {
        }

        @Override
        public void onTraceComplete(ActivityTrace activityTrace) {
        }

        @Override
        public void onTraceRename(ActivityTrace activityTrace) {
        }
    }

}

