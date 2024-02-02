/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.logging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.util.Streams;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class LogReporterTest {

    private AgentConfiguration agentConfiguration;
    private File reportsDir;
    private LogReporter logReporter;

    @BeforeClass
    public static void beforeClass() {
        AgentLogManager.setAgentLog(new ConsoleAgentLog());
    }

    @Before
    public void setUp() throws Exception {
        FeatureFlag.enableFeature(FeatureFlag.LogReporting);

        agentConfiguration = new AgentConfiguration();
        agentConfiguration.setLogReportingConfiguration(new LogReportingConfiguration(true, LogLevel.DEBUG));

        reportsDir = Files.createTempDirectory("LogReporting-").toFile();
        reportsDir.mkdirs();

        logReporter = Mockito.spy(LogReporter.initialize(reportsDir, agentConfiguration));
        LogReporter.instance.set(logReporter);

        // generate at least 4, and up to 11, logdata files, each with 4 log records
        seedLogData((int) (Math.random() * 7) + 4);
    }

    @After
    public void tearDown() throws Exception {
        // Streams.list(LogReporter.logDataStore).forEach(file -> file.delete());
        FeatureFlag.disableFeature(FeatureFlag.LogReporting);
    }

    @Test
    public void initialize() throws IOException {
        LogReporter.instance.set(null);

        try {
            LogReporter.initialize(new File("/"), AgentConfiguration.getInstance());
            Assert.fail("Should fail with missing or un-writable report directory");
        } catch (Exception e) {
            Assert.assertNull(LogReporter.instance.get());
        }

        try {
            LogReporter.initialize(File.createTempFile("logReporting", "tmp"), AgentConfiguration.getInstance());
            Assert.fail("Should fail with a non-directory file");
        } catch (Exception e) {
            Assert.assertNull(LogReporter.instance.get());
        }

        try {
            LogReporter.initialize(reportsDir, null);
            Assert.fail("Should fail with missing agent configuration");
        } catch (Exception e) {
            Assert.assertNull(LogReporter.instance.get());
        }

        LogReporter.initialize(reportsDir, agentConfiguration);
        Assert.assertNotNull(LogReporter.instance.get());
        Assert.assertTrue(reportsDir.exists() && reportsDir.canWrite());
    }

    @Test
    public void start() {
        logReporter.start();
        verify(logReporter, times(1)).onHarvestStart();
    }

    @Test
    public void stop() {
        logReporter.stop();
        verify(logReporter, times(1)).onHarvestStop();
    }

    @Test
    public void startWhenDisabled() {
        logReporter.setEnabled(false);
        logReporter.start();
        verify(logReporter, never()).onHarvestStart();
    }

    @Test
    public void stopWhenDisabled() {
        logReporter.setEnabled(false);
        logReporter.stop();
        verify(logReporter, never()).onHarvestStop();
    }

    @Test
    public void onHarvestStart() {
        logReporter.onHarvestStart();
        verify(logReporter, times(1)).onHarvest();
    }

    @Test
    public void onHarvestStop() {
        logReporter.onHarvestStop();
        verify(logReporter, never()).onHarvest();
    }

    @Test
    public void onHarvest() throws Exception {
        logReporter.rollupLogDataFiles();
        logReporter.onHarvest();
        verify(logReporter, times(1)).getCachedLogReports(LogReporter.LogReportState.ROLLUP);
        verify(logReporter, times(1)).postLogReport(any(File.class));
    }

    @Test
    public void onHarvestConfigurationChanged() {
        agentConfiguration.getLogReportingConfiguration().setLoggingEnabled(false);
        logReporter.onHarvestConfigurationChanged();
        verify(logReporter, times(1)).setEnabled(false);

        agentConfiguration.getLogReportingConfiguration().setLoggingEnabled(true);
        logReporter.onHarvestConfigurationChanged();
        verify(logReporter, times(1)).setEnabled(true);
    }

    @Test
    public void getCachedLogReports() throws Exception {
        Set<File> completedLogFiles = logReporter.getCachedLogReports(LogReporter.LogReportState.CLOSED);
        Assert.assertEquals(4, completedLogFiles.size(), 7);
    }

    @Test
    public void mergeLogDataToArchive() throws Exception {
        File archivedLogFile = logReporter.rollupLogDataFiles();
        Assert.assertTrue((archivedLogFile.exists() && archivedLogFile.isFile() && archivedLogFile.length() > 0));
        Assert.assertFalse(archivedLogFile.canWrite());
        JsonArray jsonArray = new Gson().fromJson(Streams.newBufferedFileReader(archivedLogFile), JsonArray.class);
        Assert.assertEquals(4*4, jsonArray.size(), 7*4);
    }

    @Test
    public void postLogReport() throws Exception {
        File archivedLogFile = logReporter.rollupLogDataFiles();
        // logReporter.postLogReport(archivedLogFile)
    }

    @Test
    public void getWorkingLogfile() throws IOException {
        File workingLogFile = logReporter.getWorkingLogfile();
        Assert.assertTrue(workingLogFile.exists());
        Assert.assertEquals(workingLogFile, new File(LogReporter.logDataStore,
                String.format(Locale.getDefault(),
                        LogReporter.LOG_FILE_MASK,
                        "",
                        LogReporter.LogReportState.WORKING.value)));
    }

    @Test
    public void rollLogfile() throws IOException {
        long tStart = System.currentTimeMillis();
        File workingLogFile = logReporter.getWorkingLogfile();
        Assert.assertEquals(tStart, workingLogFile.lastModified(), 100);
        Assert.assertTrue(workingLogFile.exists());

        File closeLogFile = logReporter.rollLogfile(workingLogFile);
        Assert.assertTrue(closeLogFile.exists());
        Assert.assertFalse(workingLogFile.exists());
        Assert.assertTrue(closeLogFile.lastModified() >= tStart);
    }

    @Test
    public void safeDelete() throws IOException {
        File closedLogFile = logReporter.rollLogfile(logReporter.getWorkingLogfile());
        Assert.assertTrue(closedLogFile.exists());
        LogReporter.safeDelete(closedLogFile);
        Assert.assertFalse(closedLogFile.exists());
    }

    @Test
    public void expire() {
        Set<File> allFiles = logReporter.getCachedLogReports(LogReporter.LogReportState.CLOSED);
        allFiles.forEach(file -> file.setLastModified(System.currentTimeMillis() - TimeUnit.MILLISECONDS.convert(4, TimeUnit.DAYS)));
        logReporter.expire(logReporter.reportTTL);
        allFiles.forEach(file -> {
            Assert.assertFalse(file.exists());
            Assert.assertTrue(new File(file.getAbsolutePath() + "." + LogReporter.LogReportState.EXPIRED.value).exists());
        });
    }

    @Test
    public void cleanup() {
        Set<File> closedFiles = logReporter.getCachedLogReports(LogReporter.LogReportState.CLOSED);
        closedFiles.forEach(file -> LogReporter.safeDelete(file));
        Set<File> expiredFiles = logReporter.getCachedLogReports(LogReporter.LogReportState.EXPIRED);
        logReporter.cleanup();
        expiredFiles.forEach(file -> {
            Assert.assertFalse(file.exists());
        });
    }

    private Set<File> seedLogData(int numFiles) throws Exception {
        HashSet<File> reportSet = new HashSet<File>();
        for (int file = 0; file < numFiles; file++) {
            RemoteLogger remoteLogger = new RemoteLogger();
            remoteLogger.log(LogLevel.INFO, RemoteLoggerTest.getRandomMsg((int) (Math.random() * 30) + 12));
            remoteLogger.log(LogLevel.INFO, RemoteLoggerTest.getRandomMsg((int) (Math.random() * 30) + 12));
            remoteLogger.log(LogLevel.INFO, RemoteLoggerTest.getRandomMsg((int) (Math.random() * 30) + 12));
            remoteLogger.log(LogLevel.INFO, RemoteLoggerTest.getRandomMsg((int) (Math.random() * 30) + 12));
            remoteLogger.finalizeWorkingLogFile();
            reportSet.add(remoteLogger.rollWorkingLogFile());
        }

        return reportSet;
    }
}

