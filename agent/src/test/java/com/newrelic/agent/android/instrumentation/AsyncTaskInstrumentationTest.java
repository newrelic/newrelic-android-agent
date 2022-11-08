/**
 * Copyright 2021 New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation;

import android.annotation.TargetApi;
import android.os.AsyncTask;
import android.os.Build;

import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.NewRelic;
import com.newrelic.agent.android.api.v2.TraceFieldInterface;
import com.newrelic.agent.android.tracing.ActivityTrace;
import com.newrelic.agent.android.tracing.TraceLifecycleAware;
import com.newrelic.agent.android.tracing.TraceMachine;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Spy;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

@RunWith(RobolectricTestRunner.class)
public class AsyncTaskInstrumentationTest {

    private final static String TAG = "AsyncTaskInstrumentationTest";

    @Spy
    private TraceLifecycleListener traceLifecycleListener;
    @Spy
    private TestAsyncTask testAsyncTask;
    @Spy
    private TestTraceFieldInterface testTraceFieldInterface;

    @Before
    public void setUpClass() throws Exception {
        TraceMachine.clearActivityHistory();
    }

    @Before
    public void setUp() throws Exception {
        traceLifecycleListener = spy(new TraceLifecycleListener());
        testAsyncTask = spy(new TestAsyncTask());
        testTraceFieldInterface = spy(new TestTraceFieldInterface());
        TraceMachine.addTraceListener(traceLifecycleListener);

        NewRelic.enableFeature(FeatureFlag.HttpResponseBodyCapture);
        NewRelic.enableFeature(FeatureFlag.CrashReporting);
        NewRelic.enableFeature(FeatureFlag.AnalyticsEvents);
        NewRelic.enableFeature(FeatureFlag.InteractionTracing);
        NewRelic.enableFeature(FeatureFlag.DefaultInteractions);
    }

    @BeforeClass
    public static void adjustTraceMachineTimeouts() throws Exception {
        /**
         * TraceMachine timing is very tight, and tests can be slow. So adjust the
         * HEALTHY_TRACE_TIMEOUT to a more reasonable value.
         */
        TraceMachine.HEALTHY_TRACE_TIMEOUT = 10000;
    }

    @After
    public void tearDown() throws Exception {
        TraceMachine.removeTraceListener(traceLifecycleListener);
        TraceMachine.haltTracing();
        Assert.assertTrue(TraceMachine.isTracingInactive());
    }

    @Test
    public void testTraceLifecycle() throws Exception {
        try {
            TraceMachine.startTracing("testTraceLifecycle");

            ActivityTrace activityTrace = TraceMachine.getActivityTrace();
            verify(traceLifecycleListener).onTraceStart(activityTrace);

            TraceMachine.enterMethod("execute");
            verify(traceLifecycleListener).onEnterMethod();

            AsyncTaskInstrumentation.execute(testAsyncTask, TAG);

            TraceMachine.exitMethod();
            verify(traceLifecycleListener).onEnterMethod();

            TraceMachine.endTrace();
            verify(traceLifecycleListener).onTraceComplete(activityTrace);

        } catch (Exception e) {
            fail("Should not throw exception: " + e.getMessage());
        }
    }

    @Test
    public void testExecute() throws Exception {
        try {
            AsyncTaskInstrumentation.execute(testAsyncTask, TAG);
            verify(testAsyncTask).execute(TAG);
        } catch (Exception e) {
            fail("Should not throw exception: " + e.getMessage());
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Test
    public void testExecuteOnExecutor() throws Exception {
        try {
            AsyncTaskInstrumentation.executeOnExecutor(testAsyncTask, AsyncTask.THREAD_POOL_EXECUTOR, TAG);
            verify(testAsyncTask).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, TAG);
        } catch (Exception e) {
            fail("Should not throw exception: " + e.getMessage());
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Test
    public void testShouldInjectTraceWhenInstrumented() throws Exception {
        TestTraceFieldInterface testTraceFieldInterface = spy(new TestTraceFieldInterface());

        TraceMachine.startTracing("testShouldInjectTraceWhenInstrumented");
        TraceMachine.enterMethod("execute");
        AsyncTaskInstrumentation.execute(testTraceFieldInterface, TAG);
        TraceMachine.exitMethod();
        verify(testTraceFieldInterface)._nr_setTrace(any(com.newrelic.agent.android.tracing.Trace.class));
        verify(testTraceFieldInterface).execute(TAG);

        TraceMachine.clearActivityHistory();
        TraceMachine.startTracing("testShouldInjectTraceWhenInstrumented");
        testTraceFieldInterface = spy(new TestTraceFieldInterface());
        TraceMachine.enterMethod("executeOnExecutor");
        AsyncTaskInstrumentation.executeOnExecutor(testTraceFieldInterface, AsyncTask.THREAD_POOL_EXECUTOR, TAG);
        TraceMachine.exitMethod();
        verify(testTraceFieldInterface)._nr_setTrace(any(com.newrelic.agent.android.tracing.Trace.class));
        verify(testTraceFieldInterface).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, TAG);

        TraceMachine.endTrace();

        Assert.assertEquals(1, TraceMachine.getActivityHistory().size());
    }

    private static class TestAsyncTask extends AsyncTask<String, String, String>
            implements TraceFieldInterface {

        @Override
        protected void onProgressUpdate(String... values) {
        }

        @Trace   // probably a no-op, but would be useful one day
        @Override
        protected void onPostExecute(String s) {
        }

        @Trace
        @Override
        protected String doInBackground(String... strings) {
            return strings[0];
        }

        @Override
        public void _nr_setTrace(com.newrelic.agent.android.tracing.Trace trace) {

        }
    }

    private static class TestTraceFieldInterface extends TestAsyncTask
            implements TraceFieldInterface {

        @Override
        public void _nr_setTrace(com.newrelic.agent.android.tracing.Trace trace) {

        }
    }


    private static class TraceLifecycleListener implements TraceLifecycleAware {

        @Override
        public void onEnterMethod() {
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