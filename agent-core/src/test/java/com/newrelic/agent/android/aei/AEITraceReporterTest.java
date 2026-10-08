/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.aei;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.harvest.Harvest;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.logging.ConsoleAgentLog;
import com.newrelic.agent.android.util.Streams;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

public class AEITraceReporterTest {
    private static final int NUM_TRACE_FILES = 3;
    protected static File reportsDir;

    private AgentConfiguration agentConfiguration;
    private AEITraceReporter traceReporter;
    private String sysTrace;

    @BeforeClass
    public static void beforeClass() throws Exception {
        reportsDir = Files.createTempDirectory("ApplicationExitInfo-").toFile();
        reportsDir.mkdirs();

        AgentLogManager.setAgentLog(new ConsoleAgentLog());
        AgentLogManager.getAgentLog().setLevel(AgentLog.DEBUG);

        Harvest.getHarvestConfiguration().setData_token(new int[]{2, 3});
    }

    @Before
    public void setUp() throws Exception {
        FeatureFlag.enableFeature(FeatureFlag.ApplicationExitReporting);

        sysTrace = Streams.slurpString(AEITraceTest.class.getResource("/applicationExitInfo/systrace").openStream());

        agentConfiguration = new AgentConfiguration();
        agentConfiguration.setApplicationToken("APP-TOKEN>");
        agentConfiguration.getApplicationExitConfiguration().setConfiguration(new ApplicationExitConfiguration(true));

        traceReporter = Mockito.spy(AEITraceReporter.initialize(reportsDir, agentConfiguration));
        AEITraceReporter.instance.set(traceReporter);

        seedTraceData(NUM_TRACE_FILES);
    }

    @After
    public void tearDown() throws Exception {
        FeatureFlag.disableFeature(FeatureFlag.ApplicationExitReporting);
        Streams.list(traceReporter.traceStore).forEach(file -> file.delete());
        Assert.assertTrue(traceReporter.traceStore.delete());
        AEITraceReporter.instance.set(null);
    }

    @AfterClass
    public static void afterClass() {
        Assert.assertTrue(reportsDir.delete());
    }

    @Test
    public void getInstance() {
        Assert.assertEquals(traceReporter, AEITraceReporter.getInstance());
    }

    @Test
    public void initialize() throws IOException {
        AEITraceReporter.instance.set(null);

        try {
            AEITraceReporter.initialize(new File("/"), AgentConfiguration.getInstance());
            Assert.fail("Should fail with missing or un-writable report directory");
        } catch (Exception e) {
            Assert.assertNull(AEITraceReporter.instance.get());
        }

        try {
            AEITraceReporter.initialize(File.createTempFile("aeiTraceReporting", "tmp"), AgentConfiguration.getInstance());
            Assert.fail("Should fail with a non-directory file");
        } catch (Exception e) {
            Assert.assertNull(AEITraceReporter.instance.get());
        }

        try {
            AEITraceReporter.initialize(reportsDir, null);
            Assert.fail("Should fail with missing agent configuration");
        } catch (Exception e) {
            Assert.assertNull(AEITraceReporter.instance.get());
        }

        AEITraceReporter.initialize(reportsDir, agentConfiguration);
        Assert.assertNotNull(AEITraceReporter.instance.get());
        Assert.assertTrue(reportsDir.exists() && reportsDir.canWrite());
    }

    @Test
    public void isInitialized() {
        traceReporter.start();
        Assert.assertTrue(traceReporter.isEnabled() && traceReporter.isStarted());
    }

    @Test
    public void start() {
        Assert.assertFalse(traceReporter.isStarted());
        traceReporter.start();
        Assert.assertTrue(traceReporter.isStarted());
    }

    @Test
    public void stop() {
        traceReporter.start();
        Assert.assertTrue(traceReporter.isStarted());
        traceReporter.stop();
        Assert.assertFalse(traceReporter.isStarted());
    }

    @Test
    public void shutdown() {
        traceReporter.start();
        Assert.assertTrue(traceReporter.isStarted());

        AEITraceReporter.shutdown();
        Assert.assertFalse(traceReporter.isStarted());
        Assert.assertNull(AEITraceReporter.getInstance());
    }

    @Test
    public void postCachedAgentData() {
        Mockito.doReturn(true).when(traceReporter).postAEITrace(Mockito.any(File.class));

        Set<File> cachedReports = traceReporter.getCachedTraces();
        Assert.assertEquals(NUM_TRACE_FILES, cachedReports.size());

        traceReporter.postCachedAgentData();
        Mockito.verify(traceReporter, Mockito.times(NUM_TRACE_FILES)).postAEITrace(Mockito.any(File.class));
    }

    @Test
    public void reportAEITraceToArtifact() throws Exception {
        File traceFile = File.createTempFile("aeitrace-", ".dat", traceReporter.traceStore);

        Assert.assertTrue(traceFile.exists());
        Assert.assertEquals(0, traceFile.length());

        traceReporter.reportAEITrace(sysTrace, traceFile);
        Assert.assertTrue(traceFile.exists());
        Assert.assertNotEquals(0, traceFile.length());
        Assert.assertFalse(traceFile.canWrite());
    }

    @Test
    public void reportAEITraceToArtifact_nullArtifactDoesNotThrow() {
        // generateUniqueDataFilename() can return null when every candidate name for a pid is
        // still occupied; the caller must not NPE on new FileOutputStream(null, ...).
        traceReporter.reportAEITrace(sysTrace, (File) null);
    }

    @Test
    public void generateUniqueDataFilename_disambiguatesOnCollision() {
        int pid = 424242;

        File primary = traceReporter.generateUniqueDataFilename(pid);
        Assert.assertNotNull(primary);
        traceReporter.reportAEITrace(sysTrace, primary);

        File secondary = traceReporter.generateUniqueDataFilename(pid);
        Assert.assertNotNull("Should disambiguate rather than collide with the occupied primary name", secondary);
        Assert.assertNotEquals(primary.getName(), secondary.getName());
        Assert.assertFalse(secondary.exists());
    }

    @Test
    public void generateUniqueDataFilename_disambiguatedFilesAreStillTracked() {
        int pid = 424243;

        File primary = traceReporter.generateUniqueDataFilename(pid);
        traceReporter.reportAEITrace(sysTrace, primary);

        File secondary = traceReporter.generateUniqueDataFilename(pid);
        traceReporter.reportAEITrace(sysTrace, secondary);

        Set<File> cached = traceReporter.getCachedTraces();
        Assert.assertTrue("Primary trace file should still be picked up for upload", cached.contains(primary));
        Assert.assertTrue("Disambiguated trace file should also be picked up for upload", cached.contains(secondary));
    }

    @Test
    public void generateUniqueDataFilename_returnsNullWhenExhausted() {
        int pid = 424244;

        // Occupy the primary name and every disambiguated slot.
        for (int attempt = 0; attempt <= AEITraceReporter.MAX_FILENAME_ATTEMPTS; attempt++) {
            File traceFile = traceReporter.generateUniqueDataFilename(pid);
            Assert.assertNotNull("Should still have an unused slot at attempt " + attempt, traceFile);
            traceReporter.reportAEITrace(sysTrace, traceFile);
        }

        Assert.assertNull("Every candidate name is occupied", traceReporter.generateUniqueDataFilename(pid));

        // The int-pid overload must not throw even though generateUniqueDataFilename() returns null.
        traceReporter.reportAEITrace(sysTrace, pid);
    }

    @Test
    public void postAEITrace() {
        Mockito.doReturn(true).when(traceReporter).postAEITrace(Mockito.any(File.class));

        Set<File> cachedReports = traceReporter.getCachedTraces();
        cachedReports.forEach(file -> {
            try {
                String traceAsString = Streams.slurpString(file, StandardCharsets.UTF_8.toString());
                traceReporter.postAEITrace(traceAsString);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        Mockito.verify(traceReporter, Mockito.times(cachedReports.size())).postAEITrace(Mockito.anyString());
    }

    @Test
    public void getCachedTraces() {
        Assert.assertEquals(NUM_TRACE_FILES, traceReporter.getCachedTraces().size());
        Set<File> seededTraces = seedTraceData(5);
        Assert.assertEquals(NUM_TRACE_FILES + seededTraces.size(), traceReporter.getCachedTraces().size());
    }

    @Test
    public void expire() throws InterruptedException {
        Assert.assertEquals(NUM_TRACE_FILES, traceReporter.getCachedTraces().size());
        Thread.sleep(5000);
        traceReporter.expire(1000);
        Assert.assertEquals(0, traceReporter.getCachedTraces().size());
        Mockito.verify(traceReporter, Mockito.times(NUM_TRACE_FILES)).safeDelete(Mockito.any(File.class));
    }

    @Test
    public void safeDelete() {
        Assert.assertEquals(NUM_TRACE_FILES, traceReporter.getCachedTraces().size());
        traceReporter.getCachedTraces().forEach(file -> traceReporter.safeDelete(file));
        Assert.assertEquals(0, traceReporter.getCachedTraces().size());
    }

    @Test
    public void onHarvest() {
        Mockito.doReturn(true).when(traceReporter).postAEITrace(Mockito.any(File.class));

        Assert.assertEquals(3, traceReporter.getCachedTraces().size());
        traceReporter.onHarvest();
        Assert.assertEquals(0, traceReporter.getCachedTraces().size());
    }

    Set<File> seedTraceData(int numFiles) {
        final HashSet<File> reportSet = new HashSet<>();

        for (int file = 0; file < numFiles; file++) {
            File traceFile = traceReporter.generateUniqueDataFilename((int) (Math.random() * 10000) + 1);
            traceReporter.reportAEITrace(sysTrace, traceFile);
            reportSet.add(traceFile);
        }

        return reportSet;
    }

}