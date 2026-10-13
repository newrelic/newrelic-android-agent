/*
 * Copyright (c) 2026. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.logging;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.analytics.AnalyticsControllerImpl;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.newrelic.agent.android.logging.LogReporting.LOG_PAYLOAD_COMMON_ATTRIBUTE;
import static com.newrelic.agent.android.logging.LogReporting.LOG_PAYLOAD_LOGS_ATTRIBUTE;

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

    @Test
    public void testRecoveredLogKeepsOriginatingSessionIdAfterRotation() throws Exception {
        final String originalSessionId = AgentConfiguration.getInstance().getSessionID();
        final String probe = "NR-616897 rotation probe";

        LogReporting.getLogger().log(LogLevel.INFO, probe);
        LogReporting.getLogger().flush(1_000);
        logReporter.flushWorkingLogfile();

        // Simulate the next launch: the session rotates before the leftover file is rolled up.
        // Mirrors Harvest.startSession(), which is the ONLY production path that rotates the
        // session id: it calls provideSessionId() and, in the same breath, pushes the new id into
        // AnalyticsControllerImpl's cached "sessionId" system attribute. That attribute is a
        // ConcurrentLinkedQueue entry populated once at controller construction and never touched
        // again on its own - getCommonBlockAttributes()'s attrs.putAll(sessionAttributes) overwrites
        // whatever it just read live from AgentConfiguration with whatever is cached there. Skipping
        // this second step (as a bare provideSessionId() call does) leaves the cached attribute
        // stale and would make the common block assertion below fail for a reason that has nothing
        // to do with the sessionId attribution fix under test.
        final String newSessionId = AgentConfiguration.getInstance().provideSessionId();
        Assert.assertNotEquals("rotation must actually change the session id",
                originalSessionId, newSessionId);
        AnalyticsControllerImpl.getInstance()
                .getAttribute(AnalyticsAttribute.SESSION_ID_ATTRIBUTE)
                .setStringValue(newSessionId);

        logReporter.finalizeWorkingLogfile();
        JsonArray jsonArray = LogReporter.logfileToJsonArray(logReporter.workingLogfile);
        JsonObject envelope = jsonArray.get(0).getAsJsonObject();

        // Documents the mechanism: the shared common block carries the NEW session id...
        String commonSessionId = envelope.get(LOG_PAYLOAD_COMMON_ATTRIBUTE).getAsJsonObject()
                .get(LogReporting.LOG_PAYLOAD_ATTRIBUTES_ATTRIBUTE).getAsJsonObject()
                .get(LogReporting.LOG_SESSION_ID).getAsString();
        Assert.assertEquals("common block resolves the session id lazily, so it is the new one",
                newSessionId, commonSessionId);

        // ...and the per-entry stamp overrides it with the session that actually logged the line.
        boolean found = false;
        for (JsonElement element : envelope.get(LOG_PAYLOAD_LOGS_ATTRIBUTE).getAsJsonArray()) {
            JsonObject record = element.getAsJsonObject();
            if (record.has(LogReporting.LOG_MESSAGE_ATTRIBUTE)
                    && record.get(LogReporting.LOG_MESSAGE_ATTRIBUTE).getAsString().contains("rotation probe")) {
                Assert.assertEquals("recovered line must keep its ORIGINATING session id",
                        originalSessionId, record.get(LogReporting.LOG_SESSION_ID).getAsString());
                found = true;
            }
        }
        Assert.assertTrue("probe record must be present", found);
    }
}
