/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sample;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Debug;

import com.newrelic.agent.android.harvest.AgentHealth;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.metric.Metric;
import com.newrelic.agent.android.stats.TicToc;
import com.newrelic.agent.android.tracing.ActivityTrace;
import com.newrelic.agent.android.tracing.Sample;
import com.newrelic.agent.android.tracing.TraceLifecycleAware;
import com.newrelic.agent.android.tracing.TraceMachine;
import com.newrelic.agent.android.util.NamedThreadFactory;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

public class Sampler implements TraceLifecycleAware, Runnable {
    protected static final long SAMPLE_FREQ_MS = 100;
    protected static final long SAMPLE_FREQ_MS_MAX = 250;     // upper limit on sampling frequency
    private static final int[] PID = {android.os.Process.myPid()};
    private static final int KB_IN_MB = 1024;
    private static final AgentLog log = AgentLogManager.getAgentLog();
    private static final ReentrantLock samplerLock = new ReentrantLock();
    protected static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("Sampler"));


    protected static Sampler sampler;
    protected static boolean cpuSamplingDisabled = false;

    private final ActivityManager activityManager;
    private final EnumMap<Sample.SampleType, Collection<Sample>> samples = new EnumMap<Sample.SampleType, Collection<Sample>>(Sample.SampleType.class);
    protected final AtomicBoolean isRunning = new AtomicBoolean(false);
    protected long sampleFreqMs = SAMPLE_FREQ_MS;

    protected ScheduledFuture sampleFuture;

    private Long lastCpuTime;
    private Long lastAppCpuTime;
    private RandomAccessFile procStatFile;
    private RandomAccessFile appStatFile;
    private Metric samplerServiceMetric;

    protected Sampler(Context context) {
        activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

        samples.put(Sample.SampleType.MEMORY, new ArrayList<Sample>());
        samples.put(Sample.SampleType.CPU, new ArrayList<Sample>());
    }

    public static void init(Context context) {
        samplerLock.lock();
        try {
            if (sampler == null) {
                sampler = provideSampler(context);
                sampler.sampleFreqMs = SAMPLE_FREQ_MS;
                sampler.samplerServiceMetric = new Metric("samplerServiceTime");

                TraceMachine.addTraceListener(sampler);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    cpuSamplingDisabled = true;
                    log.debug("CPU sampling not supported in Android 8 and above.");
                }

                log.debug("Sampler initialized");
            }
        } catch (Exception e) {
            log.error("Sampler init failed: " + e.getMessage());
            // cleanup any acquired state
            shutdown();
        } finally {
            samplerLock.unlock();
        }
    }

    protected static Sampler provideSampler(Context context) {
        return new Sampler(context);
    }

    public static void start() {
        samplerLock.lock();
        try {
            if (sampler != null) {
                sampler.schedule();
                log.debug("Sampler started");
            }
        } finally {
            samplerLock.unlock();
        }
    }

    public static void stop() {
        samplerLock.lock();
        try {
            if (sampler != null) {
                sampler.stop(false);
                log.debug("Sampler stopped");
            }
        } finally {
            samplerLock.unlock();
        }
    }

    public static void stopNow() {
        samplerLock.lock();
        try {
            if (sampler != null) {
                sampler.stop(true);
                log.debug("Sampler hard stopped");
            }
        } finally {
            samplerLock.unlock();
        }
    }

    public static void shutdown() {
        samplerLock.lock();
        try {
            if (sampler != null) {
                TraceMachine.removeTraceListener(sampler);
                stopNow();
                sampler = null;
                log.debug("Sampler shutdown");
            }
        } finally {
            samplerLock.unlock();
        }
    }

    public void run() {
        try {
            if (isRunning.get()) {
                sample();
            }
        } catch (Exception e) {
            log.error("Caught exception while running the sampler", e);
            AgentHealth.noticeException(e);
        }
    }

    protected void schedule() {
        samplerLock.lock();
        try {
            if (!isRunning.get()) {
                clear();
                sampleFuture = scheduler.scheduleWithFixedDelay(this, 0, sampleFreqMs, TimeUnit.MILLISECONDS);
                isRunning.set(true);
                log.debug(String.format("Sampler scheduler started; sampling will occur every %d ms.", sampleFreqMs));
            }
        } catch (Exception e) {
            log.error("Sampler scheduling failed: " + e.getMessage());
            AgentHealth.noticeException(e);
        } finally {
            samplerLock.unlock();
        }
    }

    protected void stop(boolean immediate) {
        samplerLock.lock();
        try {
            if (isRunning.get()) {
                isRunning.set(false);
                if (sampleFuture != null) {
                    sampleFuture.cancel(immediate);
                }
                resetCpuSampler();
                log.debug("Sampler canceled");
            }
        } catch (Exception e) {
            log.error("Sampler stop failed: " + e.getMessage());
            AgentHealth.noticeException(e);
        } finally {
            samplerLock.unlock();
        }
    }

    protected static boolean isRunning() {
        if (sampler == null || sampler.sampleFuture == null) {
            return false;
        }

        return !sampler.sampleFuture.isDone();
    }

    /**
     * Check that the avg sampler service time does not exceed the scheduling period. If does occur,
     * increase the schedule period by 10%.
     *
     * @param serviceTime Duration of last sampler run in ms
     */
    protected void monitorSamplerServiceTime(double serviceTime) {
        samplerServiceMetric.sample(serviceTime);
        Double serviceTimeAvg = samplerServiceMetric.getTotal() / samplerServiceMetric.getCount();
        // log.debug(String.format("Sampler: Service time[%.3f] Avg[%.3f] Max[%.3f]", serviceTime, serviceTimeAvg, samplerServiceMetric.getMax()));
        if (serviceTimeAvg > sampleFreqMs) {
            log.debug("Sampler: sample service time has been exceeded. Increase by 10%");
            sampleFreqMs = Math.min((long) (sampleFreqMs * 1.10f), SAMPLE_FREQ_MS_MAX);
            if (sampleFuture != null) {
                sampleFuture.cancel(true);
            }
            sampleFuture = scheduler.scheduleWithFixedDelay(this, 0, sampleFreqMs, TimeUnit.MILLISECONDS);
            log.debug(String.format("Sampler scheduler restarted; sampling will now occur every %d ms.", sampleFreqMs));
            samplerServiceMetric.clear();
        }
    }

    protected void sample() {
        TicToc timer = new TicToc();

        samplerLock.lock();
        try {
            timer.tic();
            final Sample memorySample = sampleMemory();

            if (memorySample != null) {
                getSampleCollection(Sample.SampleType.MEMORY).add(memorySample);
            }

            final Sample cpuSample = sampleCpu();
            if (cpuSample != null) {
                getSampleCollection(Sample.SampleType.CPU).add(cpuSample);
            }
        } catch (Exception e) {
            log.error("Sampling failed: " + e.getMessage());
            AgentHealth.noticeException(e);
        } finally {
            samplerLock.unlock();
        }

        // keep an eye on our sample service time
        monitorSamplerServiceTime(timer.toc());
    }

    protected void clear() {
        for (Collection<Sample> sampleCollection : samples.values()) {
            sampleCollection.clear();
        }
    }

    public static Sample sampleMemory() {
        if (sampler == null) {
            return null;
        }

        return sampleMemory(sampler.activityManager);
    }

    public static Sample sampleMemory(ActivityManager activityManager) {
        try {
            final Debug.MemoryInfo[] memInfo = activityManager.getProcessMemoryInfo(PID);

            if (memInfo.length > 0) {
                final int totalPss = memInfo[0].getTotalPss();

                if (totalPss >= 0) {
                    final Sample sample = new Sample(Sample.SampleType.MEMORY);
                    sample.setSampleValue((double) totalPss / KB_IN_MB);
                    return sample;
                }
            }
        } catch (Exception e) {
            log.error("Sample memory failed: " + e.getMessage());
            AgentHealth.noticeException(e);
        }

        return null;
    }

    protected static Sample sampleCpuInstance() {
        if (sampler == null) {
            return null;
        }

        return sampler.sampleCpu();
    }

    public Sample sampleCpu() {

        if (cpuSamplingDisabled) {
            return null;
        }

        try {

            if (procStatFile == null || appStatFile == null) {
                // On our first cycle, open up the proc files.
                // Starting with Android N (8), runtime apps no longer have access to teh /proc fs
                // There is no workaround at the moment
                appStatFile = new RandomAccessFile("/proc/" + PID[0] + "/stat", "r");
                procStatFile = new RandomAccessFile("/proc/stat", "r");
            } else {
                // Now that the files are open, just seek back to the beginning to save a few cycles.
                procStatFile.seek(0);
                appStatFile.seek(0);
            }

            final String procStatString = procStatFile.readLine();
            final String appStatString = appStatFile.readLine();

            // String.split can be an expensive operation. Consider parsing manually, since token are delimited by ' '
            final String[] procStats = procStatString.split(" ");
            final String[] appStats = appStatString.split(" ");

            // Same for Long.parseLong. This adds a second string parse. A better solution would be to
            // combine the split/parse passes into a single pass that tokenizes to long as it encounters each field
            final long cpuTime = Long.parseLong(procStats[2]) +   // user
                    Long.parseLong(procStats[3]) +   // nice
                    Long.parseLong(procStats[4]) +   // system
                    Long.parseLong(procStats[5]) +   // idle
                    Long.parseLong(procStats[6]) +   // iowait
                    Long.parseLong(procStats[7]) +   // irq
                    Long.parseLong(procStats[8]);    // softirq

            final long appTime = Long.parseLong(appStats[13]) +   // user time
                    Long.parseLong(appStats[14]);    // kernel time

            if (lastCpuTime == null && lastAppCpuTime == null) {
                // First time through, just record the first values and bail
                lastCpuTime = cpuTime;
                lastAppCpuTime = appTime;

                return null;
            } else {
                // All subsequent runs, compare this sample with the last
                final Sample sample = new Sample(Sample.SampleType.CPU);

                sample.setSampleValue((double) (appTime - lastAppCpuTime) / (cpuTime - lastCpuTime) * 100);

                lastCpuTime = cpuTime;
                lastAppCpuTime = appTime;

                return sample;
            }
        } catch (Exception e) {
            cpuSamplingDisabled = true;
            log.debug("Exception hit while CPU sampling: " + e.getMessage());
            AgentHealth.noticeException(e);
        }

        return null;
    }

    private void resetCpuSampler() {
        lastCpuTime = null;
        lastAppCpuTime = null;

        if (appStatFile != null && procStatFile != null) {
            try {
                appStatFile.close();
                procStatFile.close();
                appStatFile = null;
                procStatFile = null;
            } catch (IOException e) {
                log.debug("Exception hit while resetting CPU sampler: " + e.getMessage());
                AgentHealth.noticeException(e);
            }
        }
    }

    public static Map<Sample.SampleType, Collection<Sample>> copySamples() {
        EnumMap<Sample.SampleType, Collection<Sample>> copy;

        samplerLock.lock();
        try {
            if (sampler == null) {
                samplerLock.unlock();
                return new HashMap<Sample.SampleType, Collection<Sample>>();
            }

            copy = new EnumMap<Sample.SampleType, Collection<Sample>>(sampler.samples);

            for (Sample.SampleType key : sampler.samples.keySet()) {
                copy.put(key, new ArrayList<Sample>(sampler.samples.get(key)));
            }
        } finally {
            samplerLock.unlock();
        }

        return Collections.unmodifiableMap(copy);
    }

    private Collection<Sample> getSampleCollection(Sample.SampleType type) {
        return samples.get(type);
    }

    /**
     * We hook onEnterMethod here since the TraceMachine may be already running before the Agent initializes us.  We won't
     * sample the method from the very beginning, but we'll start as soon as we are initialized.
     */
    @Override
    public void onEnterMethod() {
        if (isRunning.get())
            return;

        start();
    }

    @Override
    public void onExitMethod() {
    }

    @Override
    public void onTraceStart(ActivityTrace activityTrace) {
        start();
    }

    @Override
    public void onTraceComplete(final ActivityTrace activityTrace) {
        // put this work on the b/g to return asap
        scheduler.execute(new Runnable() {
            @Override
            public void run() {
                // stop the sample task immediately so as not to lag the interaction involved
                // if stopped while processing a crash, the sampleLock may throw a sync exception.
                try {
                    stop(true);
                    activityTrace.setVitals(copySamples());
                    clear();
                } catch (RuntimeException e) {
                    log.error(e.toString());
                }
            }
        });

    }

    @Override
    public void onTraceRename(ActivityTrace activityTrace) {
    }
}
