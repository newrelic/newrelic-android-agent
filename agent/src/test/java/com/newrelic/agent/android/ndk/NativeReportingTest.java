/*
 * Copyright (c) 2021-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.ndk;

import androidx.annotation.Nullable;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.NewRelic;
import com.newrelic.agent.android.SpyContext;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.logging.ConsoleAgentLog;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.util.Streams;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;


@RunWith(RobolectricTestRunner.class)
public class NativeReportingTest {

    private static File reportsDir;

    private AgentConfiguration agentConfig;
    private NativeReporting nativeReporter;
    private AgentNDK agentNDK;
    private AgentNDKListener agentNDKListener;

    HashMap<String, AtomicInteger> counters = new HashMap<String, AtomicInteger>() {{
        put("crash", new AtomicInteger(0));
        put("exception", new AtomicInteger(0));
        put("anr", new AtomicInteger(0));
    }};


    @BeforeClass
    public static void beforeClass() throws Exception {
        reportsDir = Files.createTempDirectory("com.newrelic.nativeReporting.").toFile();
        reportsDir.mkdirs();

        FeatureFlag.disableFeature(FeatureFlag.HandledExceptions);
    }

    @AfterClass
    public static void afterClass() throws Exception {
        Assert.assertTrue(reportsDir.exists() && reportsDir.isDirectory());
        Streams.list(reportsDir).forEach(file -> file.delete());
        Assert.assertTrue(reportsDir.delete());
    }

    @Before
    public void setup() {

        AgentLogManager.setAgentLog(new ConsoleAgentLog());
        AgentLogManager.getAgentLog().setLevel(AgentLog.DEBUG);

        NewRelic.enableFeature(FeatureFlag.NativeReporting);

        SpyContext context = new SpyContext();

        agentNDKListener = Mockito.spy(new AgentNDKListener() {
            @Override
            public boolean onNativeCrash(@Nullable String s) {
                counters.get("crash").incrementAndGet();
                nativeReporter.nativeReportListener.onNativeCrash(s);
                return true;
            }

            @Override
            public boolean onNativeException(@Nullable String s) {
                counters.get("exception").incrementAndGet();
                nativeReporter.nativeReportListener.onNativeException(s);
                return true;
            }

            @Override
            public boolean onApplicationNotResponding(@Nullable String s) {
                counters.get("anr").incrementAndGet();
                nativeReporter.nativeReportListener.onApplicationNotResponding(s);
                return true;
            }
        });

        StatsEngine.reset();

        agentNDK = Mockito.spy(new AgentNDK.Builder(context.getContext())
                .withStorageDir(reportsDir)
                .withANRMonitor(true)
                .withBuildId(UUID.randomUUID().toString())
                .withReportListener(agentNDKListener)
                .withLogger(AgentLogManager.getAgentLog())
                .withSessionId(UUID.randomUUID().toString())
                .build());

        agentConfig = AgentConfiguration.getInstance();
        nativeReporter = Mockito.spy(NativeReporting.initialize(context.getContext(), agentConfig));

        NativeReporting.instance.set(nativeReporter);
    }

    @Before
    public void seedReportData() throws IOException {
        File reportsDir = agentNDK.getManagedContext().getReportsDir();

        Streams.newBufferedFileWriter(new File(reportsDir, "crash-" + UUID.randomUUID().toString())).append("{'crash':{}}").flush();
        Streams.newBufferedFileWriter(new File(reportsDir, "ex-" + UUID.randomUUID().toString())).append("{'exception':{}}").flush();
        Streams.newBufferedFileWriter(new File(reportsDir, "anr-" + UUID.randomUUID().toString())).append("{'anr':{}}").flush();
        Streams.newBufferedFileWriter(new File(reportsDir, "ex-" + UUID.randomUUID().toString())).append("{'exception':{}}").flush();
        Streams.newBufferedFileWriter(new File(reportsDir, "anr-" + UUID.randomUUID().toString())).append("{'anr':{}}").flush();
        Streams.newBufferedFileWriter(new File(reportsDir, "crash-" + UUID.randomUUID().toString())).append("{'crash':{}}").flush();
    }

    @After
    public void tearDown() throws Exception {
        Streams.list(agentNDK.getManagedContext().getReportsDir()).forEach(file -> Assert.assertTrue(file.delete()));
        Assert.assertTrue(agentNDK.getManagedContext().getReportsDir().delete());
        Assert.assertTrue(agentNDK.getManagedContext().getReportsDir().getParentFile().delete());
        NativeReporting.instance.set(null);
    }

    @Test
    public void getInstance() {
        Assert.assertEquals(nativeReporter, NativeReporting.getInstance());
    }

    @Test
    public void initialize() {
        Assert.assertTrue(NativeReporting.isInitialized());
        Assert.assertTrue(statsEngineContains(MetricNames.SUPPORTABILITY_NDK_INIT));
    }

    @Test
    @Ignore("FIXME: java.lang.UnsatisfiedLinkError")
    public void shutdown() {
        NativeReporting.shutdown();
        Assert.assertFalse(NativeReporting.isInitialized());
        Assert.assertNull(NativeReporting.getInstance());
    }

    @Test
    public void isInitialized() {
        Assert.assertTrue(NativeReporting.isInitialized());
    }

    @Test
    public void isRooted() {
        NativeReporting.isRooted();
        Assert.assertTrue(statsEngineContains(MetricNames.SUPPORTABILITY_NDK_ROOTED_DEVICE));
    }

    @Test
    @Ignore("FIXME: java.lang.UnsatisfiedLinkError")
    public void start() {
        nativeReporter.start();
        Assert.assertTrue(statsEngineContains(MetricNames.SUPPORTABILITY_NDK_START));
    }

    @Test
    @Ignore("FIXME: java.lang.UnsatisfiedLinkError")
    public void stop() {
        nativeReporter.stop();
        Assert.assertTrue(statsEngineContains(MetricNames.SUPPORTABILITY_NDK_STOP));
    }

    @Test
    public void onNativeCrash() {
        agentNDK.flushPendingReports();
        Assert.assertTrue(statsEngineContains(MetricNames.SUPPORTABILITY_NDK_REPORTS_CRASH));
        Assert.assertEquals("anr", 2, counters.get("crash").get());
    }

    @Test
    public void onNativeException() {
        agentNDK.flushPendingReports();
        Assert.assertTrue(statsEngineContains(MetricNames.SUPPORTABILITY_NDK_REPORTS_EXCEPTION));
        Assert.assertEquals("anr", 2, counters.get("exception").get());
    }

    @Test
    public void onApplicationNotResponding() {
        agentNDK.flushPendingReports();
        Assert.assertTrue(statsEngineContains(MetricNames.SUPPORTABILITY_NDK_REPORTS_ANR));
        Assert.assertEquals("anr", 2, counters.get("anr").get());
    }

    @Test
    public void onHarvestStart() {
        nativeReporter.onHarvestStart();
        Assert.assertTrue(statsEngineContains(MetricNames.SUPPORTABILITY_NDK_REPORTS_FLUSH));
        counters.entrySet().forEach(c -> Assert.assertEquals(c.getKey(), 0, c.getValue().get()));
    }

    @Test
    public void flushPendingReports() {
        Assert.assertEquals(6, Streams.list(agentNDK.getManagedContext().getReportsDir()).count());
        agentNDK.flushPendingReports();
        counters.forEach((key, value) -> Assert.assertEquals(key, 2, value.get()));
        Assert.assertEquals(0, Streams.list(agentNDK.getManagedContext().getReportsDir()).count());
    }

    @Test
    public void testNativeException() throws IOException {
        String json = Streams.slurpString(NativeReporting.class.getResourceAsStream("/nativeReporting/crash-2024.sample"));
        NativeException e = new NativeException(json);
        Assert.assertNotNull(e.getStackTrace());
        Assert.assertNotNull(e.getNativeStackTrace());
        Assert.assertFalse(e.getNativeStackTrace().getExceptionMessage().isEmpty());
    }

    @Test
    public void testNativeCrashException() throws IOException {
        String json = Streams.slurpString(NativeReporting.class.getResourceAsStream("/nativeReporting/crash-2024.sample"));
        NativeReporting.NativeCrashException e = new NativeReporting.NativeCrashException(json);
        Assert.assertFalse(e.getNativeStackTrace().getExceptionMessage().isEmpty());
        Assert.assertFalse(e.getNativeStackTrace().getThreads().isEmpty());
        Assert.assertNotNull(e.getNativeStackTrace().getCrashedThread());
    }

    @Test
    public void testNativeUnhandledException() throws IOException {
        String json = Streams.slurpString(NativeReporting.class.getResourceAsStream("/nativeReporting/exception-2024.sample"));
        NativeReporting.NativeUnhandledException e = new NativeReporting.NativeUnhandledException(json);
        Assert.assertNotNull(e.getNativeStackTrace());
        Assert.assertFalse(e.getNativeStackTrace().getExceptionMessage().isEmpty());
        Assert.assertFalse(e.getNativeStackTrace().getThreads().isEmpty());
        Assert.assertNotNull(e.getNativeStackTrace().getCrashedThread());
    }

    @Test
    public void testANRException() throws IOException {
        String json = Streams.slurpString(NativeReporting.class.getResourceAsStream("/nativeReporting/anr-2024.sample"));
        NativeReporting.ANRException e = new NativeReporting.ANRException(json);
        Assert.assertNotNull(e.getNativeStackTrace());
        Assert.assertFalse(e.getNativeStackTrace().getExceptionMessage().isEmpty());
        Assert.assertFalse(e.getNativeStackTrace().getThreads().isEmpty());
        Assert.assertNotNull(e.getNativeStackTrace().getCrashedThread());
    }

    private boolean statsEngineContains(String metricName) {
        return StatsEngine.SUPPORTABILITY.getStatsMap().keySet().contains(metricName);
    }

}