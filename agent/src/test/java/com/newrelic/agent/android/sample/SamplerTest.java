/**
 * Copyright 2021 New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sample;

import android.content.Context;

import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.AgentInitializationException;
import com.newrelic.agent.android.AndroidAgentImpl;
import com.newrelic.agent.android.SpyContext;
import com.newrelic.agent.android.background.ApplicationStateEvent;
import com.newrelic.agent.android.tracing.ActivityTrace;
import com.newrelic.agent.android.tracing.Sample;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Config.OLDEST_SDK)
public class SamplerTest {
    private final SpyContext context = new SpyContext();
    private long testStarted;
    private Sampler samplerSpy;
    private String NRSamplerName = "NR_Sampler-";

    @Before
    public void setUp() throws Exception {
        TestSampler.init(context.getContext());
        samplerSpy = spy(TestSampler.sampler);
        testStarted = System.currentTimeMillis();
    }

    @After
    public void tearDown() throws Exception {
        TestSampler.shutdown();
    }

    @Test
    public void testInit() throws Exception {
        Assert.assertTrue("Sampler is initialized", TestSampler.isInitialized());
    }

    @Test
    public void testInitFailed() throws Exception {
        TestSampler.shutdown();
        TestSampler.init(null);
        Assert.assertNull("Sampler init should fail (instance is null)", Sampler.sampler);
        Assert.assertFalse("Sampler should not be running stopped", TestSampler.isRunning());
    }

    @Test
    public void testStart() throws Exception {
        TestSampler.start();
        Assert.assertTrue("Sampler is running", TestSampler.isRunning());
    }

    @Test
    public void testStop() throws Exception {
        TestSampler.stop();
        Assert.assertFalse("Sampler is stopped", TestSampler.isRunning());
    }

    @Test
    public void testStopNow() throws Exception {
        TestSampler.stop();
        Assert.assertFalse("Sampler is hard stopped", TestSampler.isRunning());
    }

    @Test
    public void testShutdown() throws Exception {
        TestSampler.shutdown();
        Assert.assertFalse("Sampler is stopped", TestSampler.isRunning());
        Assert.assertFalse("Sampler is no longer initialized", TestSampler.isInitialized());
        Assert.assertNull("Sampler instance is null", TestSampler.sampler);
    }

    @Test
    public void testRun() throws Exception {
        TestSampler.start();
        Thread.sleep(TestSampler.getSamplerFrequency() * 2);
        Map<Sample.SampleType, Collection<Sample>> samples = TestSampler.copySamples();
        Assert.assertTrue("Should contain samples", samples.get(Sample.SampleType.MEMORY).size() > 0);
        TestSampler.shutdown();
    }

    @Test
    public void testSampleMemory() throws Exception {
        Sample sample = TestSampler.sampleMemory();
        Assert.assertTrue("Should return memory sample", sample.getSampleType().equals(Sample.SampleType.MEMORY));
        Assert.assertTrue("Should return valid sample time", testStarted <= sample.getTimestamp());
        Assert.assertEquals("Should return valid sample value", Double.valueOf(SpyContext.APP_MEMORY / 1024), sample.getSampleValue().asDouble(), 0.1f);
    }

    @Test
    public void testSampleCpu() throws Exception {
        Sample sample = TestSampler.sampleCpuInstance();
        Assert.assertTrue("FIXME: cpu sampling disabled in unit tests", TestSampler.cpuSamplingDisabled());
    }

    @Test
    public void testCopySamples() throws Exception {
        TestSampler.start();
        Thread.sleep(2000);
        Map<Sample.SampleType, Collection<Sample>> samples = TestSampler.copySamples();
        Assert.assertEquals("Should contain 2 entries", 2, samples.keySet().size());

        Assert.assertTrue("Should contain memory samples", samples.containsKey(Sample.SampleType.MEMORY));
        Collection<Sample> memorySamples = samples.get(Sample.SampleType.MEMORY);
        Assert.assertFalse("Should contain memory sample", memorySamples.isEmpty());

        Collection<Sample> cpuSamples = samples.get(Sample.SampleType.CPU);
        Assert.assertTrue("Should contain CPU samples", samples.containsKey(Sample.SampleType.CPU));
        Assert.assertTrue("Should contain 0 CPU memory sample", cpuSamples.isEmpty());
    }

    @Test
    public void testOnEnterMethod() throws Exception {
        samplerSpy.onEnterMethod();
        Assert.assertTrue("Sampler is running", TestSampler.isRunning());
    }

    @Test
    public void testOnTraceStart() throws Exception {
        ActivityTrace activityTrace = provideActivityTrace();
        samplerSpy.onTraceStart(activityTrace);
        Assert.assertTrue("Sampler is running", TestSampler.isRunning());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testOnTraceComplete() throws Exception {
        ActivityTrace activityTrace = provideActivityTrace();
        samplerSpy.onTraceComplete(activityTrace);
        // onTraceComplete is run in worker, so sleep a bit before testing
        Thread.sleep(3000);
        verify(activityTrace).setVitals(ArgumentMatchers.<Sample.SampleType, Collection<Sample>>anyMap());
        verify(samplerSpy).clear();
        Assert.assertFalse("Sampler should be stopped", TestSampler.isRunning());
    }

    @Test
    public void testInstanceSchedule() throws Exception {
        Assert.assertFalse("Should not be running prior to schedule", samplerSpy.isRunning.get());
        samplerSpy.schedule();
        verify(samplerSpy).clear();
        Assert.assertNotNull("Scheduler future should be non-null", samplerSpy.sampleFuture);
        Assert.assertTrue("Should be running after schedule", samplerSpy.isRunning.get());
    }

    @Test
    public void testInstanceRun() throws Exception {
        samplerSpy.isRunning.set(true);
        samplerSpy.run();
        verify(samplerSpy, atLeastOnce()).sample();
    }

    @Test
    public void testInstanceStop() throws Exception {
        samplerSpy.schedule();
        Assert.assertTrue("Should be running prior to stop", samplerSpy.isRunning.get());
        samplerSpy.stop(true);
        Assert.assertTrue("Schedule future should be canceled", samplerSpy.sampleFuture.isCancelled());
        Assert.assertFalse("Should not be running after stop", samplerSpy.isRunning.get());
    }

    @Test
    public void testMonitorSamplerServiceTime() throws Exception {
        long startingFreq = TestSampler.sampler.sampleFreqMs;
        ScheduledFuture scheduledFuture = TestSampler.getScheduledFuture();

        TestSampler.sampler.monitorSamplerServiceTime(10000);
        Assert.assertEquals("Should increase sampling period by 10%", (long) (startingFreq * 1.10f), TestSampler.sampler.sampleFreqMs);
        Assert.assertNotSame("Should create a new scheduled future", scheduledFuture, TestSampler.getScheduledFuture());

        for (int i = 0; i < 10; i++) {
            TestSampler.sampler.monitorSamplerServiceTime(10000);
        }
        Assert.assertEquals("Should cap sampling period", TestSampler.SAMPLE_FREQ_MS_MAX, TestSampler.sampler.sampleFreqMs);
    }

    @Test
    public void testShutdownOnInteractionEnd() throws Exception {
        ActivityTrace activityTrace = provideActivityTrace();
        samplerSpy.onTraceComplete(activityTrace);
        // onTraceComplete is run in worker, so sleep a bit before testing
        Thread.sleep(3000);
        verify(samplerSpy).stop(true);
        Assert.assertFalse("Sampler should be stopped", TestSampler.isRunning());
    }

    @Test
    public void testShouldNotSampleInBackground() throws AgentInitializationException {
        Assert.assertFalse("Sampler singleton should be running prior to agent start",
                Sampler.sampler.scheduler.isShutdown());

        AndroidAgentImpl agent = new AndroidAgentImpl(context.getContext(), new AgentConfiguration());
        Agent.setImpl(agent);

        for (int i = 0; i < 10; i++) {
            agent.applicationForegrounded(new ApplicationStateEvent(this));
            samplerSpy.onTraceStart(provideActivityTrace());
            Assert.assertTrue("Should be running in foreground", Sampler.isRunning());
            Assert.assertEquals(1, getRunningThreadCnt(NRSamplerName));

            agent.applicationBackgrounded(new ApplicationStateEvent(this));
            samplerSpy.onTraceStart(provideActivityTrace());
            Assert.assertFalse("Should not be running in background", Sampler.isRunning());
            Assert.assertEquals(1, getRunningThreadCnt(NRSamplerName));
        }
    }


    private int getRunningThreadCnt(final String threadName) {
        Thread[] remainingThreads = {};
        int returnedThreadCnt = 0;
        int matchingThreadCnt = 0;
        int retries = 10;

        while (retries > 0) {
            remainingThreads = new Thread[Thread.activeCount()];
            returnedThreadCnt = Thread.enumerate(remainingThreads);
            if (returnedThreadCnt == Thread.activeCount()) {
                break;
            }
            Assert.assertTrue(--retries > 0);
        }

        for (int i = 0; i < returnedThreadCnt; i++) {
            if (remainingThreads[i].getName().toLowerCase().startsWith(threadName.toLowerCase())) {
                matchingThreadCnt++;
            }
        }
        return matchingThreadCnt;
    }

    private ActivityTrace provideActivityTrace() {
        ActivityTrace activityTrace = spy(new ActivityTrace());
        return activityTrace;
    }

    private static class TestSampler extends Sampler {
        protected TestSampler(Context context) {
            super(context);
        }

        @Override
        public void run() {
            super.run();
            try {
                Thread.sleep(Sampler.SAMPLE_FREQ_MS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onTraceComplete(ActivityTrace activityTrace) {
            super.onTraceComplete(activityTrace);
            verify(sampler).stop(false);
        }

        public static boolean isInitialized() {
            return sampler != null;
        }

        public static long getSamplerFrequency() {
            return sampler.sampleFreqMs;
        }

        public static boolean cpuSamplingDisabled() {
            return cpuSamplingDisabled;
        }

        public static ScheduledFuture getScheduledFuture() {
            return sampler.sampleFuture;
        }
    }
}