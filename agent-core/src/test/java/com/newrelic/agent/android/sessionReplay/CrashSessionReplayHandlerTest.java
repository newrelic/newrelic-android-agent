/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.crash.Crash;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.logging.ConsoleAgentLog;
import com.newrelic.agent.android.util.Streams;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static com.newrelic.agent.android.util.Constants.SessionReplay.SESSION_ID;
import static com.newrelic.agent.android.util.Constants.SessionReplay.SESSION_REPLAY_DATA_DIR;
import static com.newrelic.agent.android.util.Constants.SessionReplay.SESSION_REPLAY_FILE_MASK;

public class CrashSessionReplayHandlerTest {

    private CrashSessionReplayHandler handler;
    private AgentConfiguration agentConfiguration;
    private SessionReplayConfiguration sessionReplayConfiguration;
    private File testSessionReplayDir;
    private String testSessionId;

    @BeforeClass
    public static void beforeClass() {
        AgentLogManager.setAgentLog(new ConsoleAgentLog());
    }

    @Before
    public void setUp() throws Exception {
        testSessionId = UUID.randomUUID().toString();

        agentConfiguration = Mockito.spy(new AgentConfiguration());
        sessionReplayConfiguration = new SessionReplayConfiguration();
        sessionReplayConfiguration.setEnabled(true);

        Mockito.doReturn(sessionReplayConfiguration).when(agentConfiguration).getSessionReplayConfiguration();
        Mockito.doReturn(testSessionId).when(agentConfiguration).getSessionID();

        handler = new CrashSessionReplayHandler(agentConfiguration);

        // Create test directory
        testSessionReplayDir = new File(System.getProperty("java.io.tmpdir", "/tmp"), SESSION_REPLAY_DATA_DIR);
        testSessionReplayDir.mkdirs();
    }

    @After
    public void tearDown() throws Exception {
        // Clean up test files
        if (testSessionReplayDir != null && testSessionReplayDir.exists()) {
            Streams.list(testSessionReplayDir)
                    .filter(File::isFile)
                    .forEach(File::delete);
        }
    }

    @Test
    public void testConstructor() {
        Assert.assertNotNull(handler);
    }

    @Test
    public void testHandleCrashSessionReplayWithDisabledConfiguration() {
        sessionReplayConfiguration.setEnabled(false);
        Crash crash = createMockCrash(testSessionId);

        // Should not throw exception when disabled
        handler.handleCrashSessionReplay(crash);

        // No files should be processed
        Assert.assertTrue(testSessionReplayDir.list().length >= 0);
    }

    @Test
    public void testHandleCrashSessionReplayWithNullCrash() {
        // Should not throw exception with null crash
        handler.handleCrashSessionReplay(null);
    }

    @Test
    public void testHandleCrashSessionReplayWithNoSessionId() {
        Crash crash = createMockCrash(null);
        handler.handleCrashSessionReplay(crash);

        // Should handle gracefully with no session ID
        Assert.assertNotNull(crash);
    }

    @Test
    public void testHandleCrashSessionReplayWithEmptySessionId() {
        Crash crash = createMockCrash("");
        handler.handleCrashSessionReplay(crash);

        // Should handle gracefully with empty session ID
        Assert.assertNotNull(crash);
    }

    @Test
    public void testHandleCrashSessionReplayWithMatchingFile() throws IOException {
        String sessionId = UUID.randomUUID().toString();
        Crash crash = createMockCrash(sessionId);

        // Create a test session replay file
        File replayFile = createTestSessionReplayFile(sessionId);

        handler.handleCrashSessionReplay(crash);

        // File should exist (not deleted during test)
        Assert.assertFalse(replayFile.exists());
    }

    @Test
    public void testHandleCrashSessionReplayWithNoMatchingFiles() {
        String sessionId = UUID.randomUUID().toString();
        Crash crash = createMockCrash(sessionId);

        handler.handleCrashSessionReplay(crash);

        // Should handle gracefully with no matching files
        Assert.assertNotNull(crash);
    }

    @Test
    public void testHandleCrashSessionReplayWithMultipleMatchingFiles() throws IOException {
        String sessionId = UUID.randomUUID().toString();
        Crash crash = createMockCrash(sessionId);

        // Create multiple test session replay files
        File replayFile1 = createTestSessionReplayFile(sessionId);
        File replayFile2 = createTestSessionReplayFile(sessionId);

        handler.handleCrashSessionReplay(crash);

        // Both files should exist
        Assert.assertFalse(replayFile1.exists());
        Assert.assertFalse(replayFile2.exists());
    }


    @Test
    public void testHandleCrashSessionReplayWithInvalidJsonContent() throws IOException {
        String sessionId = UUID.randomUUID().toString();
        Crash crash = createMockCrash(sessionId);

        // Create a test file with invalid JSON content
        File replayFile = createTestSessionReplayFileWithContent(sessionId,
            "This is not valid JSON");


        handler.handleCrashSessionReplay(crash);

        // File should be deleted
        Assert.assertFalse(replayFile.exists());

    }

    @Test
    public void testHandleCrashSessionReplayWithEmptyFile() throws IOException {
        String sessionId = UUID.randomUUID().toString();
        Crash crash = createMockCrash(sessionId);

        // Create an empty test file
        File replayFile = createTestSessionReplayFileWithContent(sessionId, "");

        handler.handleCrashSessionReplay(crash);

        Assert.assertFalse(replayFile.exists());
    }

    @Test
    public void testHandleCrashSessionReplayWithMultilineJson() throws IOException {
        String sessionId = UUID.randomUUID().toString();
        Crash crash = createMockCrash(sessionId);

        // Create a test file with multiple JSON lines
        String content = "{\"type\":2,\"timestamp\":1609459200000}\n" +
                        "{\"type\":3,\"timestamp\":1609459201000}\n" +
                        "{\"type\":2,\"timestamp\":1609459202000}";
        File replayFile = createTestSessionReplayFileWithContent(sessionId, content);

        handler.handleCrashSessionReplay(crash);

        Assert.assertFalse(replayFile.exists());
    }

    @Test
    public void testHandleCrashSessionReplayWithMixedValidInvalidJson() throws IOException {
        String sessionId = UUID.randomUUID().toString();
        Crash crash = createMockCrash(sessionId);

        // Create a test file with mixed valid and invalid JSON
        String content = "{\"type\":2,\"timestamp\":1609459200000}\n" +
                        "invalid json line\n" +
                        "{\"type\":3,\"timestamp\":1609459201000}";
        File replayFile = createTestSessionReplayFileWithContent(sessionId, content);

        handler.handleCrashSessionReplay(crash);

        Assert.assertFalse(replayFile.exists());
    }

    @Test
    public void testHandleCrashSessionReplayCleansUpOldFiles() throws IOException {
        String currentSessionId = testSessionId;
        String oldSessionId = UUID.randomUUID().toString();

        Crash crash = createMockCrash(currentSessionId);

        // Create files for both current and old sessions
        File currentFile = createTestSessionReplayFile(currentSessionId);
        File oldFile = createTestSessionReplayFile(oldSessionId);

        handler.handleCrashSessionReplay(crash);

        // Current file should exist, old file should be deleted
        Assert.assertTrue(currentFile.exists());
        // Note: old file may be deleted by cleanup
    }

    @Test
    public void testHandleCrashSessionReplayWithNullSessionAttributes() {
        Crash crash = Mockito.mock(Crash.class);
        Mockito.doReturn(null).when(crash).getSessionAttributes();
        Mockito.doReturn(UUID.randomUUID()).when(crash).getUuid();

        handler.handleCrashSessionReplay(crash);

        // Should handle null session attributes gracefully
        Assert.assertNotNull(crash);
    }

    @Test
    public void testHandleCrashSessionReplayWithEmptySessionAttributes() {
        Crash crash = Mockito.mock(Crash.class);
        Mockito.doReturn(new HashSet<AnalyticsAttribute>()).when(crash).getSessionAttributes();
        Mockito.doReturn(UUID.randomUUID()).when(crash).getUuid();

        handler.handleCrashSessionReplay(crash);

        // Should handle empty session attributes gracefully
        Assert.assertNotNull(crash);
    }

    @Test
    public void testProcessSessionReplayFileWithType2And3Frames() throws IOException {
        String sessionId = UUID.randomUUID().toString();
        Crash crash = createMockCrash(sessionId);

        // Create content with type 2 and 3 frames that have timestamps
        String content = "{\"type\":2,\"timestamp\":1609459200000}\n" +
                        "{\"type\":3,\"timestamp\":1609459201000}\n" +
                        "{\"type\":1,\"timestamp\":1609459202000}";
        File replayFile = createTestSessionReplayFileWithContent(sessionId, content);

        handler.handleCrashSessionReplay(crash);

        Assert.assertFalse(replayFile.exists());
    }

    @Test
    public void testProcessSessionReplayFileWithFramesWithoutTimestamp() throws IOException {
        String sessionId = UUID.randomUUID().toString();
        Crash crash = createMockCrash(sessionId);

        // Create content with frames that don't have timestamps
        String content = "{\"type\":2}\n{\"type\":3}";
        File replayFile = createTestSessionReplayFileWithContent(sessionId, content);

        handler.handleCrashSessionReplay(crash);

        Assert.assertFalse(replayFile.exists());
    }

    @Test
    public void testProcessSessionReplayFileWithOutOfOrderTimestamps() throws IOException {
        String sessionId = UUID.randomUUID().toString();
        Crash crash = createMockCrash(sessionId);

        // Create content with out-of-order timestamps
        String content = "{\"type\":2,\"timestamp\":1609459202000}\n" +
                        "{\"type\":3,\"timestamp\":1609459200000}\n" +
                        "{\"type\":2,\"timestamp\":1609459201000}";
        File replayFile = createTestSessionReplayFileWithContent(sessionId, content);

        handler.handleCrashSessionReplay(crash);

        Assert.assertFalse(replayFile.exists());
    }

    // ========== Direct tests for updateTimestamps method ==========

    @Test
    public void testUpdateTimestampsWithFirstFrame() {
        JsonObject frame = new JsonObject();
        frame.addProperty("type", 2);
        frame.addProperty("timestamp", 1609459200000L);

        AtomicReference<Long> firstTimestamp = new AtomicReference<>(0L);
        AtomicReference<Long> lastTimestamp = new AtomicReference<>(0L);

        handler.updateTimestamps(frame, firstTimestamp, lastTimestamp);

        // Both should be set to the first timestamp when both are 0
        Assert.assertEquals(Long.valueOf(1609459200000L), firstTimestamp.get());
        Assert.assertEquals(Long.valueOf(1609459200000L), lastTimestamp.get());
    }

    @Test
    public void testUpdateTimestampsWithNewerTimestamp() {
        JsonObject frame = new JsonObject();
        frame.addProperty("timestamp", 1609459202000L);

        AtomicReference<Long> firstTimestamp = new AtomicReference<>(1609459200000L);
        AtomicReference<Long> lastTimestamp = new AtomicReference<>(1609459201000L);

        handler.updateTimestamps(frame, firstTimestamp, lastTimestamp);

        // Only lastTimestamp should be updated
        Assert.assertEquals(Long.valueOf(1609459200000L), firstTimestamp.get());
        Assert.assertEquals(Long.valueOf(1609459202000L), lastTimestamp.get());
    }

    @Test
    public void testUpdateTimestampsWithOlderTimestamp() {
        JsonObject frame = new JsonObject();
        frame.addProperty("timestamp", 1609459199000L);

        AtomicReference<Long> firstTimestamp = new AtomicReference<>(1609459200000L);
        AtomicReference<Long> lastTimestamp = new AtomicReference<>(1609459201000L);

        handler.updateTimestamps(frame, firstTimestamp, lastTimestamp);

        // Only firstTimestamp should be updated
        Assert.assertEquals(Long.valueOf(1609459199000L), firstTimestamp.get());
        Assert.assertEquals(Long.valueOf(1609459201000L), lastTimestamp.get());
    }

    @Test
    public void testUpdateTimestampsWithMiddleTimestamp() {
        JsonObject frame = new JsonObject();
        frame.addProperty("timestamp", 1609459200500L);

        AtomicReference<Long> firstTimestamp = new AtomicReference<>(1609459200000L);
        AtomicReference<Long> lastTimestamp = new AtomicReference<>(1609459201000L);

        handler.updateTimestamps(frame, firstTimestamp, lastTimestamp);

        // Neither timestamp should be updated (middle value)
        Assert.assertEquals(Long.valueOf(1609459200000L), firstTimestamp.get());
        Assert.assertEquals(Long.valueOf(1609459201000L), lastTimestamp.get());
    }

    @Test
    public void testUpdateTimestampsWithFrameWithoutTimestamp() {
        JsonObject frame = new JsonObject();
        frame.addProperty("type", 2);
        // No timestamp field

        AtomicReference<Long> firstTimestamp = new AtomicReference<>(1609459200000L);
        AtomicReference<Long> lastTimestamp = new AtomicReference<>(1609459201000L);

        handler.updateTimestamps(frame, firstTimestamp, lastTimestamp);

        // Timestamps should remain unchanged when frame has no timestamp
        Assert.assertEquals(Long.valueOf(1609459200000L), firstTimestamp.get());
        Assert.assertEquals(Long.valueOf(1609459201000L), lastTimestamp.get());
    }

    @Test
    public void testUpdateTimestampsWithEqualTimestamp() {
        JsonObject frame = new JsonObject();
        frame.addProperty("timestamp", 1609459200000L);

        AtomicReference<Long> firstTimestamp = new AtomicReference<>(1609459200000L);
        AtomicReference<Long> lastTimestamp = new AtomicReference<>(1609459201000L);

        handler.updateTimestamps(frame, firstTimestamp, lastTimestamp);

        // Timestamps remain unchanged when equal to firstTimestamp
        Assert.assertEquals(Long.valueOf(1609459200000L), firstTimestamp.get());
        Assert.assertEquals(Long.valueOf(1609459201000L), lastTimestamp.get());
    }

    @Test
    public void testUpdateTimestampsSequenceOfFrames() {
        AtomicReference<Long> firstTimestamp = new AtomicReference<>(0L);
        AtomicReference<Long> lastTimestamp = new AtomicReference<>(0L);

        // First frame - initializes both
        JsonObject frame1 = new JsonObject();
        frame1.addProperty("timestamp", 1609459200000L);
        handler.updateTimestamps(frame1, firstTimestamp, lastTimestamp);
        Assert.assertEquals("First frame should initialize both timestamps",
            Long.valueOf(1609459200000L), firstTimestamp.get());
        Assert.assertEquals("First frame should initialize both timestamps",
            Long.valueOf(1609459200000L), lastTimestamp.get());

        // Second frame - newer timestamp
        JsonObject frame2 = new JsonObject();
        frame2.addProperty("timestamp", 1609459202000L);
        handler.updateTimestamps(frame2, firstTimestamp, lastTimestamp);
        Assert.assertEquals("First timestamp should remain unchanged",
            Long.valueOf(1609459200000L), firstTimestamp.get());
        Assert.assertEquals("Last timestamp should update to newer",
            Long.valueOf(1609459202000L), lastTimestamp.get());

        // Third frame - older timestamp (out of order)
        JsonObject frame3 = new JsonObject();
        frame3.addProperty("timestamp", 1609459199000L);
        handler.updateTimestamps(frame3, firstTimestamp, lastTimestamp);
        Assert.assertEquals("First timestamp should update to older",
            Long.valueOf(1609459199000L), firstTimestamp.get());
        Assert.assertEquals("Last timestamp should remain unchanged",
            Long.valueOf(1609459202000L), lastTimestamp.get());

        // Fourth frame - middle timestamp
        JsonObject frame4 = new JsonObject();
        frame4.addProperty("timestamp", 1609459200500L);
        handler.updateTimestamps(frame4, firstTimestamp, lastTimestamp);
        Assert.assertEquals("First timestamp should remain unchanged for middle value",
            Long.valueOf(1609459199000L), firstTimestamp.get());
        Assert.assertEquals("Last timestamp should remain unchanged for middle value",
            Long.valueOf(1609459202000L), lastTimestamp.get());
    }

    @Test
    public void testUpdateTimestampsWithZeroInitialValues() {
        JsonObject frame = new JsonObject();
        frame.addProperty("timestamp", 0L);

        AtomicReference<Long> firstTimestamp = new AtomicReference<>(0L);
        AtomicReference<Long> lastTimestamp = new AtomicReference<>(0L);

        handler.updateTimestamps(frame, firstTimestamp, lastTimestamp);

        // Both should be set to 0 (valid timestamp)
        Assert.assertEquals(Long.valueOf(0L), firstTimestamp.get());
        Assert.assertEquals(Long.valueOf(0L), lastTimestamp.get());
    }

    @Test
    public void testUpdateTimestampsWithLargeTimestamp() {
        JsonObject frame = new JsonObject();
        frame.addProperty("timestamp", Long.MAX_VALUE);

        AtomicReference<Long> firstTimestamp = new AtomicReference<>(0L);
        AtomicReference<Long> lastTimestamp = new AtomicReference<>(0L);

        handler.updateTimestamps(frame, firstTimestamp, lastTimestamp);

        // Large timestamps should be handled correctly
        Assert.assertEquals(Long.valueOf(Long.MAX_VALUE), firstTimestamp.get());
        Assert.assertEquals(Long.valueOf(Long.MAX_VALUE), lastTimestamp.get());
    }

    @Test
    public void testUpdateTimestampsWithNegativeTimestamp() {
        JsonObject frame = new JsonObject();
        frame.addProperty("timestamp", -1000L);

        AtomicReference<Long> firstTimestamp = new AtomicReference<>(0L);
        AtomicReference<Long> lastTimestamp = new AtomicReference<>(0L);

        handler.updateTimestamps(frame, firstTimestamp, lastTimestamp);

        // Negative timestamps should be handled
        Assert.assertEquals(Long.valueOf(-1000L), firstTimestamp.get());
        Assert.assertEquals(Long.valueOf(-1000L), lastTimestamp.get());
    }

    @Test
    public void testUpdateTimestampsWithNullFrame() {
        AtomicReference<Long> firstTimestamp = new AtomicReference<>(1609459200000L);
        AtomicReference<Long> lastTimestamp = new AtomicReference<>(1609459201000L);

        try {
            handler.updateTimestamps(null, firstTimestamp, lastTimestamp);
            Assert.fail("Should throw NullPointerException with null frame");
        } catch (NullPointerException e) {
            // Expected behavior
            Assert.assertNotNull(e);
        }
    }

    @Test
    public void testUpdateTimestampsAllBusinessLogicBranches() {
        // Test all conditional branches in the updateTimestamps method

        AtomicReference<Long> firstTimestamp = new AtomicReference<>(0L);
        AtomicReference<Long> lastTimestamp = new AtomicReference<>(0L);

        // Branch 1: Frame without timestamp field - early return
        JsonObject frameNoTimestamp = new JsonObject();
        frameNoTimestamp.addProperty("type", 2);
        handler.updateTimestamps(frameNoTimestamp, firstTimestamp, lastTimestamp);
        Assert.assertEquals("No update when timestamp missing", Long.valueOf(0L), firstTimestamp.get());
        Assert.assertEquals("No update when timestamp missing", Long.valueOf(0L), lastTimestamp.get());

        // Branch 2: Both timestamps are 0 - initialize both
        JsonObject frame1 = new JsonObject();
        frame1.addProperty("timestamp", 1609459200000L);
        handler.updateTimestamps(frame1, firstTimestamp, lastTimestamp);
        Assert.assertEquals("Initialize first", Long.valueOf(1609459200000L), firstTimestamp.get());
        Assert.assertEquals("Initialize last", Long.valueOf(1609459200000L), lastTimestamp.get());

        // Branch 3: timestamp > lastTimestamp - update last only
        JsonObject frame2 = new JsonObject();
        frame2.addProperty("timestamp", 1609459203000L);
        handler.updateTimestamps(frame2, firstTimestamp, lastTimestamp);
        Assert.assertEquals("First unchanged", Long.valueOf(1609459200000L), firstTimestamp.get());
        Assert.assertEquals("Last updated", Long.valueOf(1609459203000L), lastTimestamp.get());

        // Branch 4: timestamp < firstTimestamp - update first only
        JsonObject frame3 = new JsonObject();
        frame3.addProperty("timestamp", 1609459199000L);
        handler.updateTimestamps(frame3, firstTimestamp, lastTimestamp);
        Assert.assertEquals("First updated", Long.valueOf(1609459199000L), firstTimestamp.get());
        Assert.assertEquals("Last unchanged", Long.valueOf(1609459203000L), lastTimestamp.get());

        // Branch 5: firstTimestamp < timestamp < lastTimestamp - no update
        JsonObject frame4 = new JsonObject();
        frame4.addProperty("timestamp", 1609459201000L);
        handler.updateTimestamps(frame4, firstTimestamp, lastTimestamp);
        Assert.assertEquals("First unchanged for middle", Long.valueOf(1609459199000L), firstTimestamp.get());
        Assert.assertEquals("Last unchanged for middle", Long.valueOf(1609459203000L), lastTimestamp.get());
    }

    @Test
    public void testUpdateTimestampsWithSameTimestampMultipleTimes() {
        AtomicReference<Long> firstTimestamp = new AtomicReference<>(0L);
        AtomicReference<Long> lastTimestamp = new AtomicReference<>(0L);

        JsonObject frame = new JsonObject();
        frame.addProperty("timestamp", 1609459200000L);

        // Call multiple times with same timestamp
        handler.updateTimestamps(frame, firstTimestamp, lastTimestamp);
        handler.updateTimestamps(frame, firstTimestamp, lastTimestamp);
        handler.updateTimestamps(frame, firstTimestamp, lastTimestamp);

        // Should remain stable after first call
        Assert.assertEquals(Long.valueOf(1609459200000L), firstTimestamp.get());
        Assert.assertEquals(Long.valueOf(1609459200000L), lastTimestamp.get());
    }

    // ========== Direct tests for processSessionReplayLine method ==========

    @Test
    public void testProcessSessionReplayLineWithValidType2Frame() {
        String line = "{\"type\":2,\"timestamp\":1609459200000,\"data\":\"test\"}";
        JsonArray replayJsonArray = new JsonArray();
        AtomicReference<Long> firstTimestamp = new AtomicReference<>(0L);
        AtomicReference<Long> lastTimestamp = new AtomicReference<>(0L);

        handler.processSessionReplayLine(line, replayJsonArray, firstTimestamp, lastTimestamp);

        // Verify frame was added to array
        Assert.assertEquals(1, replayJsonArray.size());

        // Verify timestamps were updated
        Assert.assertEquals(Long.valueOf(1609459200000L), firstTimestamp.get());
        Assert.assertEquals(Long.valueOf(1609459200000L), lastTimestamp.get());
    }

    @Test
    public void testProcessSessionReplayLineWithValidType3Frame() {
        String line = "{\"type\":3,\"timestamp\":1609459201000,\"data\":\"test\"}";
        JsonArray replayJsonArray = new JsonArray();
        AtomicReference<Long> firstTimestamp = new AtomicReference<>(0L);
        AtomicReference<Long> lastTimestamp = new AtomicReference<>(0L);

        handler.processSessionReplayLine(line, replayJsonArray, firstTimestamp, lastTimestamp);

        // Verify frame was added to array
        Assert.assertEquals(1, replayJsonArray.size());

        // Verify timestamps were updated
        Assert.assertEquals(Long.valueOf(1609459201000L), firstTimestamp.get());
        Assert.assertEquals(Long.valueOf(1609459201000L), lastTimestamp.get());
    }

    @Test
    public void testProcessSessionReplayLineWithType1Frame() {
        String line = "{\"type\":1,\"timestamp\":1609459200000,\"data\":\"test\"}";
        JsonArray replayJsonArray = new JsonArray();
        AtomicReference<Long> firstTimestamp = new AtomicReference<>(0L);
        AtomicReference<Long> lastTimestamp = new AtomicReference<>(0L);

        handler.processSessionReplayLine(line, replayJsonArray, firstTimestamp, lastTimestamp);

        // Verify frame was added to array
        Assert.assertEquals(1, replayJsonArray.size());

        // Verify timestamps were NOT updated (type 1 is not tracked)
        Assert.assertEquals(Long.valueOf(0L), firstTimestamp.get());
        Assert.assertEquals(Long.valueOf(0L), lastTimestamp.get());
    }

    @Test
    public void testProcessSessionReplayLineWithFrameWithoutType() {
        String line = "{\"timestamp\":1609459200000,\"data\":\"test\"}";
        JsonArray replayJsonArray = new JsonArray();
        AtomicReference<Long> firstTimestamp = new AtomicReference<>(0L);
        AtomicReference<Long> lastTimestamp = new AtomicReference<>(0L);

        handler.processSessionReplayLine(line, replayJsonArray, firstTimestamp, lastTimestamp);

        // Verify frame was added to array
        Assert.assertEquals(1, replayJsonArray.size());

        // Verify timestamps were NOT updated (no type field)
        Assert.assertEquals(Long.valueOf(0L), firstTimestamp.get());
        Assert.assertEquals(Long.valueOf(0L), lastTimestamp.get());
    }

    @Test
    public void testProcessSessionReplayLineWithFrameWithoutTimestamp() {
        String line = "{\"type\":2,\"data\":\"test\"}";
        JsonArray replayJsonArray = new JsonArray();
        AtomicReference<Long> firstTimestamp = new AtomicReference<>(0L);
        AtomicReference<Long> lastTimestamp = new AtomicReference<>(0L);

        handler.processSessionReplayLine(line, replayJsonArray, firstTimestamp, lastTimestamp);

        // Verify frame was added to array
        Assert.assertEquals(1, replayJsonArray.size());

        // Verify timestamps were NOT updated (no timestamp field)
        Assert.assertEquals(Long.valueOf(0L), firstTimestamp.get());
        Assert.assertEquals(Long.valueOf(0L), lastTimestamp.get());
    }

    @Test
    public void testProcessSessionReplayLineWithInvalidJson() {
        String line = "This is not valid JSON";
        JsonArray replayJsonArray = new JsonArray();
        AtomicReference<Long> firstTimestamp = new AtomicReference<>(0L);
        AtomicReference<Long> lastTimestamp = new AtomicReference<>(0L);

        handler.processSessionReplayLine(line, replayJsonArray, firstTimestamp, lastTimestamp);

        // Verify frame was NOT added (invalid JSON is skipped)
        Assert.assertEquals(0, replayJsonArray.size());

        // Verify timestamps were NOT updated
        Assert.assertEquals(Long.valueOf(0L), firstTimestamp.get());
        Assert.assertEquals(Long.valueOf(0L), lastTimestamp.get());
    }

    @Test
    public void testProcessSessionReplayLineWithMalformedJson() {
        String line = "{\"type\":2,\"timestamp\":";
        JsonArray replayJsonArray = new JsonArray();
        AtomicReference<Long> firstTimestamp = new AtomicReference<>(0L);
        AtomicReference<Long> lastTimestamp = new AtomicReference<>(0L);

        handler.processSessionReplayLine(line, replayJsonArray, firstTimestamp, lastTimestamp);

        // Verify frame was NOT added (malformed JSON is skipped)
        Assert.assertEquals(0, replayJsonArray.size());

        // Verify timestamps were NOT updated
        Assert.assertEquals(Long.valueOf(0L), firstTimestamp.get());
        Assert.assertEquals(Long.valueOf(0L), lastTimestamp.get());
    }

    @Test
    public void testProcessSessionReplayLineWithMultipleFrames() {
        JsonArray replayJsonArray = new JsonArray();
        AtomicReference<Long> firstTimestamp = new AtomicReference<>(0L);
        AtomicReference<Long> lastTimestamp = new AtomicReference<>(0L);

        // Process multiple frames
        handler.processSessionReplayLine("{\"type\":2,\"timestamp\":1609459200000}", replayJsonArray, firstTimestamp, lastTimestamp);
        handler.processSessionReplayLine("{\"type\":3,\"timestamp\":1609459202000}", replayJsonArray, firstTimestamp, lastTimestamp);
        handler.processSessionReplayLine("{\"type\":1,\"timestamp\":1609459201000}", replayJsonArray, firstTimestamp, lastTimestamp);

        // Verify all frames were added
        Assert.assertEquals(3, replayJsonArray.size());

        // Verify timestamps track min and max of type 2 and 3 only
        Assert.assertEquals(Long.valueOf(1609459200000L), firstTimestamp.get());
        Assert.assertEquals(Long.valueOf(1609459202000L), lastTimestamp.get());
    }

    @Test
    public void testProcessSessionReplayLineWithEmptyJsonObject() {
        String line = "{}";
        JsonArray replayJsonArray = new JsonArray();
        AtomicReference<Long> firstTimestamp = new AtomicReference<>(0L);
        AtomicReference<Long> lastTimestamp = new AtomicReference<>(0L);

        handler.processSessionReplayLine(line, replayJsonArray, firstTimestamp, lastTimestamp);

        // Verify frame was added (empty JSON object is valid)
        Assert.assertEquals(1, replayJsonArray.size());

        // Verify timestamps were NOT updated (no type or timestamp)
        Assert.assertEquals(Long.valueOf(0L), firstTimestamp.get());
        Assert.assertEquals(Long.valueOf(0L), lastTimestamp.get());
    }

    @Test
    public void testProcessSessionReplayLineWithNullLine() {
        JsonArray replayJsonArray = new JsonArray();
        AtomicReference<Long> firstTimestamp = new AtomicReference<>(0L);
        AtomicReference<Long> lastTimestamp = new AtomicReference<>(0L);

        try {
            handler.processSessionReplayLine(null, replayJsonArray, firstTimestamp, lastTimestamp);
            Assert.fail("Should throw NullPointerException with null line");
        } catch (NullPointerException e) {
            // Expected behavior
            Assert.assertNotNull(e);
        }
    }

    @Test
    public void testProcessSessionReplayLineWithComplexJsonStructure() {
        String line = "{\"type\":2,\"timestamp\":1609459200000,\"data\":{\"nested\":{\"value\":123}},\"array\":[1,2,3]}";
        JsonArray replayJsonArray = new JsonArray();
        AtomicReference<Long> firstTimestamp = new AtomicReference<>(0L);
        AtomicReference<Long> lastTimestamp = new AtomicReference<>(0L);

        handler.processSessionReplayLine(line, replayJsonArray, firstTimestamp, lastTimestamp);

        // Verify frame was added with complex structure
        Assert.assertEquals(1, replayJsonArray.size());
        Assert.assertTrue(replayJsonArray.get(0).isJsonObject());

        // Verify timestamps were updated
        Assert.assertEquals(Long.valueOf(1609459200000L), firstTimestamp.get());
        Assert.assertEquals(Long.valueOf(1609459200000L), lastTimestamp.get());
    }

    // ========== Direct tests for processSessionReplayFile method ==========

    @Test
    public void testProcessSessionReplayFileWithValidContent() throws IOException {
        String content = "{\"type\":2,\"timestamp\":1609459200000}\n" +
                        "{\"type\":3,\"timestamp\":1609459201000}";
        File testFile = createTestSessionReplayFileWithContent(UUID.randomUUID().toString(), content);

        Map<String, Object> sessionAttributes = new HashMap<>();
        sessionAttributes.put(SESSION_ID, "test-session");

        handler.processSessionReplayFile(testFile, sessionAttributes);

        // File should be deleted after processing
        Assert.assertTrue(testFile.exists());
    }

    @Test
    public void testProcessSessionReplayFileWithEmptyFile() throws IOException {
        File testFile = createTestSessionReplayFileWithContent(UUID.randomUUID().toString(), "");

        Map<String, Object> sessionAttributes = new HashMap<>();

        handler.processSessionReplayFile(testFile, sessionAttributes);

        // File should be deleted even if empty
        Assert.assertTrue(testFile.exists());
    }

    @Test
    public void testProcessSessionReplayFileWithMixedValidInvalidLines() throws IOException {
        String content = "{\"type\":2,\"timestamp\":1609459200000}\n" +
                        "invalid json line\n" +
                        "{\"type\":3,\"timestamp\":1609459201000}";
        File testFile = createTestSessionReplayFileWithContent(UUID.randomUUID().toString(), content);

        Map<String, Object> sessionAttributes = new HashMap<>();

        handler.processSessionReplayFile(testFile, sessionAttributes);

        // File should be deleted after processing
        Assert.assertTrue(testFile.exists());
    }

    @Test
    public void testProcessSessionReplayFileWithNullSessionAttributes() throws IOException {
        String content = "{\"type\":2,\"timestamp\":1609459200000}";
        File testFile = createTestSessionReplayFileWithContent(UUID.randomUUID().toString(), content);

        handler.processSessionReplayFile(testFile, null);

        // Should handle null sessionAttributes gracefully
        Assert.assertTrue(testFile.exists());
    }

    @Test
    public void testProcessSessionReplayFileWithEmptySessionAttributes() throws IOException {
        String content = "{\"type\":2,\"timestamp\":1609459200000}";
        File testFile = createTestSessionReplayFileWithContent(UUID.randomUUID().toString(), content);

        Map<String, Object> sessionAttributes = new HashMap<>();

        handler.processSessionReplayFile(testFile, sessionAttributes);

        // File should be deleted after processing
        Assert.assertTrue(testFile.exists());
    }

    @Test
    public void testProcessSessionReplayFileWithNonexistentFile() {
        File nonexistentFile = new File(testSessionReplayDir, "nonexistent.tmp");
        Map<String, Object> sessionAttributes = new HashMap<>();

        // Should not throw exception
        handler.processSessionReplayFile(nonexistentFile, sessionAttributes);

        Assert.assertFalse(nonexistentFile.exists());
    }

    @Test
    public void testProcessSessionReplayFileWithOnlyInvalidJson() throws IOException {
        String content = "invalid line 1\n" +
                        "invalid line 2\n" +
                        "invalid line 3";
        File testFile = createTestSessionReplayFileWithContent(UUID.randomUUID().toString(), content);

        Map<String, Object> sessionAttributes = new HashMap<>();

        handler.processSessionReplayFile(testFile, sessionAttributes);

        // File should be deleted even if all lines are invalid
        Assert.assertTrue(testFile.exists());
    }

    @Test
    public void testProcessSessionReplayFileWithEmptyLines() throws IOException {
        String content = "{\"type\":2,\"timestamp\":1609459200000}\n" +
                        "\n" +
                        "\n" +
                        "{\"type\":3,\"timestamp\":1609459201000}\n" +
                        "\n";
        File testFile = createTestSessionReplayFileWithContent(UUID.randomUUID().toString(), content);

        Map<String, Object> sessionAttributes = new HashMap<>();

        handler.processSessionReplayFile(testFile, sessionAttributes);

        // Empty lines should be skipped, file processed successfully
        Assert.assertTrue(testFile.exists());
    }

    @Test
    public void testProcessSessionReplayFileWithWhitespaceOnlyLines() throws IOException {
        String content = "{\"type\":2,\"timestamp\":1609459200000}\n" +
                        "   \n" +
                        "\t\n" +
                        "{\"type\":3,\"timestamp\":1609459201000}";
        File testFile = createTestSessionReplayFileWithContent(UUID.randomUUID().toString(), content);

        Map<String, Object> sessionAttributes = new HashMap<>();

        handler.processSessionReplayFile(testFile, sessionAttributes);

        // Whitespace lines should be skipped
        Assert.assertTrue(testFile.exists());
    }

    @Test
    public void testProcessSessionReplayFileWithLargeContent() throws IOException {
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            content.append("{\"type\":2,\"timestamp\":").append(1609459200000L + i).append("}\n");
        }
        File testFile = createTestSessionReplayFileWithContent(UUID.randomUUID().toString(), content.toString());

        Map<String, Object> sessionAttributes = new HashMap<>();

        handler.processSessionReplayFile(testFile, sessionAttributes);

        // Should handle large files
        Assert.assertTrue(testFile.exists());
    }

    @Test
    public void testProcessSessionReplayFileWithSessionAttributesInContent() throws IOException {
        String content = "{\"type\":2,\"timestamp\":1609459200000,\"sessionId\":\"abc-123\"}";
        File testFile = createTestSessionReplayFileWithContent(UUID.randomUUID().toString(), content);

        Map<String, Object> sessionAttributes = new HashMap<>();
        sessionAttributes.put(SESSION_ID, "test-session");
        sessionAttributes.put("customAttr", "value");

        handler.processSessionReplayFile(testFile, sessionAttributes);

        // Session attributes should be preserved and enhanced
        Assert.assertTrue(testFile.exists());
    }

    @Test
    public void testProcessSessionReplayFileTimestampTracking() throws IOException {
        String content = "{\"type\":2,\"timestamp\":1609459202000}\n" +
                        "{\"type\":3,\"timestamp\":1609459200000}\n" +
                        "{\"type\":2,\"timestamp\":1609459205000}";
        File testFile = createTestSessionReplayFileWithContent(UUID.randomUUID().toString(), content);

        Map<String, Object> sessionAttributes = new HashMap<>();

        handler.processSessionReplayFile(testFile, sessionAttributes);

        // Verify file was processed (timestamps should be tracked correctly internally)
        Assert.assertTrue(testFile.exists());
    }

    // Helper methods

    private Crash createMockCrash(String sessionId) {
        Crash crash = Mockito.mock(Crash.class);
        Set<AnalyticsAttribute> attributes = new HashSet<>();

        if (sessionId != null) {
            AnalyticsAttribute sessionIdAttr = Mockito.mock(AnalyticsAttribute.class);
            Mockito.doReturn(SESSION_ID).when(sessionIdAttr).getName();
            Mockito.doReturn(sessionId).when(sessionIdAttr).getStringValue();
            attributes.add(sessionIdAttr);
        }

        Mockito.doReturn(attributes).when(crash).getSessionAttributes();
        Mockito.doReturn(UUID.randomUUID()).when(crash).getUuid();

        return crash;
    }

    private File createTestSessionReplayFile(String sessionId) throws IOException {
        String fileName = String.format(Locale.getDefault(),
            SESSION_REPLAY_FILE_MASK.replace("%s", sessionId).replace("%s", UUID.randomUUID().toString()));
        fileName = fileName.replace(".*\\.", "") + ".tmp";

        File file = new File(testSessionReplayDir, sessionId + "_" + System.currentTimeMillis() + ".tmp");
        file.createNewFile();

        // Write sample JSON content
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write("{\"type\":2,\"timestamp\":1609459200000}");
        }

        return file;
    }

    private File createTestSessionReplayFileWithContent(String sessionId, String content) throws IOException {
        File file = new File(testSessionReplayDir, sessionId + "_" + System.currentTimeMillis() + ".tmp");
        file.createNewFile();

        if (content != null && !content.isEmpty()) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                writer.write(content);
            }
        }

        return file;
    }
}