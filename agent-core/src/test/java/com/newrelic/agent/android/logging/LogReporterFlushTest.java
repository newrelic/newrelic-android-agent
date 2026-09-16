/*
 * Copyright (c) 2026. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.logging;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.FeatureFlag;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * NR-616897: log lines must reach disk without waiting for a harvest, and lines
 * recovered on a later launch must keep the session id that produced them.
 */
public class LogReporterFlushTest extends LoggingTests {

    private LogReporter logReporter;

    @BeforeClass
    public static void beforeClass() throws Exception {
        LoggingTests.beforeClass();
    }

    @Before
    public void setUp() throws Exception {
        FeatureFlag.enableFeature(FeatureFlag.LogReporting);
        LogReporting.setLogLevel(LogLevel.INFO);
        LogReporter.MIN_PAYLOAD_THRESHOLD = 0;      // Disable for testing
        logReporter = LogReporter.initialize(reportsDir, new AgentConfiguration());
    }

    private Map<String, Object> entry(String message) {
        Map<String, Object> logDataMap = new HashMap<>();
        logDataMap.put(LogReporting.LOG_TIMESTAMP_ATTRIBUTE, String.valueOf(System.currentTimeMillis()));
        logDataMap.put(LogReporting.LOG_LEVEL_ATTRIBUTE, "INFO");
        logDataMap.put(LogReporting.LOG_MESSAGE_ATTRIBUTE, message);
        return logDataMap;
    }

    @Test
    public void testFlushLandsLineOnDiskWithoutHarvestOrFinalize() throws Exception {
        final String probe = "NR-616897 flush probe";
        logReporter.appendToWorkingLogfile(entry(probe));

        // The 8KB BufferedWriter has not been flushed, so nothing is on disk yet.
        List<String> beforeFlush = Files.readAllLines(logReporter.workingLogfile.toPath());
        Assert.assertFalse("line must NOT be on disk before flushing (otherwise this test proves nothing)",
                beforeFlush.stream().anyMatch(l -> l.contains(probe)));

        logReporter.flushWorkingLogfile();

        List<String> afterFlush = Files.readAllLines(logReporter.workingLogfile.toPath());
        Assert.assertTrue("line must be on disk after flushing, with no harvest and no finalize",
                afterFlush.stream().anyMatch(l -> l.contains(probe)));
    }

    @Test
    public void testFlushIsSafeWhenWriterIsAlreadyClosed() throws Exception {
        logReporter.appendToWorkingLogfile(entry("NR-616897 closed-writer probe"));
        logReporter.finalizeWorkingLogfile();   // sets workingLogfileWriter to null

        logReporter.flushWorkingLogfile();      // must not throw
    }
}
