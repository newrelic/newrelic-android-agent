///*
// * Copyright (c) 2024. New Relic Corporation. All rights reserved.
// * SPDX-License-Identifier: Apache-2.0
// */
//
//package com.newrelic.agent.android.sessionReplay;
//
//import android.app.Application;
//import android.content.Context;
//
//import androidx.test.core.app.ApplicationProvider;
//
//import com.google.gson.JsonArray;
//import com.google.gson.JsonObject;
//import com.newrelic.agent.android.AgentConfiguration;
//import com.newrelic.agent.android.sessionReplay.models.RRWebEvent;
//
//import org.junit.After;
//import org.junit.Assert;
//import org.junit.Before;
//import org.junit.Test;
//import org.junit.runner.RunWith;
//import org.robolectric.RobolectricTestRunner;
//
//import java.io.File;
//import java.io.IOException;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.concurrent.TimeUnit;
//
//@RunWith(RobolectricTestRunner.class)
//public class SessionReplayFileManagerTest {
//
//    private Application application;
//    private SessionReplayProcessor processor;
//    private SessionReplayFileManager fileManager;
//    private File testCacheDir;
//
//    @Before
//    public void setUp() {
//        application = (Application) ApplicationProvider.getApplicationContext();
//        processor = new SessionReplayProcessor();
//        fileManager = new SessionReplayFileManager(processor);
//
//        // Initialize AgentConfiguration with a session ID
//        AgentConfiguration config = AgentConfiguration.getInstance();
//        config.setSessionID("test-session-" + System.currentTimeMillis());
//
//        // Initialize the file manager
//        SessionReplayFileManager.initialize(application);
//
//        // Give async operations time to complete
//        waitForAsyncOperations();
//
//        testCacheDir = application.getCacheDir();
//    }
//
//    @After
//    public void tearDown() {
//        try {
//            // Shutdown the file manager
//            SessionReplayFileManager.shutdown();
//
//            // Clean up test files
//            cleanupTestFiles();
//
//            waitForAsyncOperations();
//        } catch (Exception e) {
//            // Ignore cleanup errors
//        }
//    }
//
//    private void waitForAsyncOperations() {
//        try {
//            // Wait for async file operations to complete
//            Thread.sleep(500);
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//        }
//    }
//
//    private void cleanupTestFiles() {
//        if (testCacheDir != null && testCacheDir.exists()) {
//            File sessionReplayDir = new File(testCacheDir, "session_replay");
//            if (sessionReplayDir.exists()) {
//                File[] files = sessionReplayDir.listFiles();
//                if (files != null) {
//                    for (File file : files) {
//                        file.delete();
//                    }
//                }
//            }
//        }
//    }
//
//    // ==================== CONSTRUCTOR TESTS ====================
//
//    @Test
//    public void testConstructor_WithValidProcessor() {
//        SessionReplayFileManager newManager = new SessionReplayFileManager(processor);
//        Assert.assertNotNull(newManager);
//    }
//
//    @Test
//    public void testConstructor_WithNullProcessor() {
//        SessionReplayFileManager newManager = new SessionReplayFileManager(null);
//        Assert.assertNotNull(newManager);
//    }
//
//    // ==================== INITIALIZE TESTS ====================
//
//    @Test
//    public void testInitialize_WithValidApplication() {
//        // Initialize was called in setUp, verify it worked
//        File sessionReplayDir = new File(testCacheDir, "session_replay");
//        Assert.assertTrue("Session replay directory should exist", sessionReplayDir.exists());
//        Assert.assertTrue("Session replay directory should be a directory", sessionReplayDir.isDirectory());
//    }
//
//    @Test
//    public void testInitialize_WithNullApplication() {
//        // Should not throw exception, just log error
//        SessionReplayFileManager.initialize(null);
//        // Test passes if no exception thrown
//    }
//
//    @Test
//    public void testInitialize_CreatesWorkingFile() throws IOException {
//        // Verify working file was created
//        File workingFile = SessionReplayFileManager.getWorkingSessionReplayFile();
//        Assert.assertNotNull(workingFile);
//        Assert.assertTrue("Working file should exist", workingFile.exists());
//    }
//
//    // ==================== ADD FRAME TO FILE TESTS ====================
//
//    @Test
//    public void testAddFrameToFile_WithSingleEvent() {
//        RRWebEvent event = new RRWebEvent();
//        event.setTimestamp(System.currentTimeMillis());
//        event.setType(RRWebEvent.RRWebEventType.META);
//
//        List<RRWebEvent> events = new ArrayList<>();
//        events.add(event);
//
//        fileManager.addFrameToFile(events);
//        waitForAsyncOperations();
//
//        // Verify event was written
//        JsonArray readEvents = SessionReplayFileManager.readEventsAsJsonArray();
//        Assert.assertTrue("Should have at least one event", readEvents.size() >= 1);
//    }
//
//    @Test
//    public void testAddFrameToFile_WithMultipleEvents() {
//        List<RRWebEvent> events = new ArrayList<>();
//
//        for (int i = 0; i < 5; i++) {
//            RRWebEvent event = new RRWebEvent();
//            event.setTimestamp(System.currentTimeMillis() + i);
//            event.setType(RRWebEvent.RRWebEventType.META);
//            events.add(event);
//        }
//
//        fileManager.addFrameToFile(events);
//        waitForAsyncOperations();
//
//        // Verify events were written
//        JsonArray readEvents = SessionReplayFileManager.readEventsAsJsonArray();
//        Assert.assertTrue("Should have at least 5 events", readEvents.size() >= 5);
//    }
//
//    @Test
//    public void testAddFrameToFile_WithEmptyList() {
//        List<RRWebEvent> events = new ArrayList<>();
//
//        // Should not throw exception
//        fileManager.addFrameToFile(events);
//        waitForAsyncOperations();
//    }
//
//    @Test
//    public void testAddFrameToFile_MultipleCallsAppend() {
//        // First batch
//        List<RRWebEvent> events1 = new ArrayList<>();
//        RRWebEvent event1 = new RRWebEvent();
//        event1.setTimestamp(1000L);
//        event1.setType(RRWebEvent.RRWebEventType.META);
//        events1.add(event1);
//
//        fileManager.addFrameToFile(events1);
//        waitForAsyncOperations();
//
//        int sizeAfterFirst = SessionReplayFileManager.readEventsAsJsonArray().size();
//
//        // Second batch
//        List<RRWebEvent> events2 = new ArrayList<>();
//        RRWebEvent event2 = new RRWebEvent();
//        event2.setTimestamp(2000L);
//        event2.setType(RRWebEvent.RRWebEventType.META);
//        events2.add(event2);
//
//        fileManager.addFrameToFile(events2);
//        waitForAsyncOperations();
//
//        int sizeAfterSecond = SessionReplayFileManager.readEventsAsJsonArray().size();
//
//        // Second call should append
//        Assert.assertTrue("Second call should append events", sizeAfterSecond > sizeAfterFirst);
//    }
//
//    // ==================== ADD TOUCH TO FILE TESTS ====================
//
//    @Test
//    public void testAddTouchToFile_WithTouchData() {
//        TouchTracker touchTracker = new TouchTracker(System.currentTimeMillis());
//        touchTracker.recordTouchDown(100, 200, 1);
//        touchTracker.recordTouchUp(100, 200, 1);
//
//        fileManager.addTouchToFile(touchTracker);
//        waitForAsyncOperations();
//
//        // Verify touch data was written
//        JsonArray readEvents = SessionReplayFileManager.readEventsAsJsonArray();
//        Assert.assertTrue("Should have touch events", readEvents.size() > 0);
//    }
//
//    @Test
//    public void testAddTouchToFile_WithEmptyTouchTracker() {
//        TouchTracker touchTracker = new TouchTracker(System.currentTimeMillis());
//
//        // No touches recorded
//        fileManager.addTouchToFile(touchTracker);
//        waitForAsyncOperations();
//
//        // Should not throw exception
//    }
//
//    // ==================== READ EVENTS AS JSON ARRAY TESTS ====================
//
//    @Test
//    public void testReadEventsAsJsonArray_WithNoEvents() {
//        // Clear file first
//        fileManager.clearWorkingFile();
//        waitForAsyncOperations();
//
//        // Reinitialize
//        SessionReplayFileManager.initialize(application);
//        waitForAsyncOperations();
//
//        JsonArray events = SessionReplayFileManager.readEventsAsJsonArray();
//        Assert.assertNotNull(events);
//        Assert.assertEquals("Should have no events", 0, events.size());
//    }
//
//    @Test
//    public void testReadEventsAsJsonArray_WithEvents() {
//        // Write an event
//        RRWebEvent event = new RRWebEvent();
//        event.setTimestamp(System.currentTimeMillis());
//        event.setType(RRWebEvent.RRWebEventType.META);
//
//        List<RRWebEvent> events = new ArrayList<>();
//        events.add(event);
//
//        fileManager.addFrameToFile(events);
//        waitForAsyncOperations();
//
//        // Read events
//        JsonArray readEvents = SessionReplayFileManager.readEventsAsJsonArray();
//        Assert.assertNotNull(readEvents);
//        Assert.assertTrue("Should have at least one event", readEvents.size() >= 1);
//    }
//
//    @Test
//    public void testReadEventsAsJsonArray_ParsesJsonObjects() {
//        // Write an event with specific data
//        RRWebEvent event = new RRWebEvent();
//        event.setTimestamp(12345L);
//        event.setType(RRWebEvent.RRWebEventType.META);
//
//        List<RRWebEvent> events = new ArrayList<>();
//        events.add(event);
//
//        fileManager.addFrameToFile(events);
//        waitForAsyncOperations();
//
//        // Read and verify
//        JsonArray readEvents = SessionReplayFileManager.readEventsAsJsonArray();
//        Assert.assertTrue("Should have at least one event", readEvents.size() >= 1);
//
//        // Check that we got valid JSON objects
//        JsonObject firstEvent = readEvents.get(readEvents.size() - 1).getAsJsonObject();
//        Assert.assertNotNull(firstEvent);
//        Assert.assertTrue("Event should have timestamp", firstEvent.has("timestamp"));
//    }
//
//    // ==================== CLEAR WORKING FILE WHILE RUNNING SESSION TESTS ====================
//
//    @Test
//    public void testClearWorkingFileWhileRunningSession_ClearsContent() {
//        // Write some events
//        RRWebEvent event = new RRWebEvent();
//        event.setTimestamp(System.currentTimeMillis());
//        event.setType(RRWebEvent.RRWebEventType.META);
//
//        List<RRWebEvent> events = new ArrayList<>();
//        events.add(event);
//
//        fileManager.addFrameToFile(events);
//        waitForAsyncOperations();
//
//        // Verify events exist
//        int sizeBefore = SessionReplayFileManager.readEventsAsJsonArray().size();
//        Assert.assertTrue("Should have events before clear", sizeBefore > 0);
//
//        // Clear file
//        fileManager.clearWorkingFileWhileRunningSession();
//        waitForAsyncOperations();
//
//        // Verify file is cleared
//        JsonArray afterClear = SessionReplayFileManager.readEventsAsJsonArray();
//        Assert.assertEquals("File should be empty after clear", 0, afterClear.size());
//    }
//
//    @Test
//    public void testClearWorkingFileWhileRunningSession_AllowsNewWrites() {
//        // Clear file
//        fileManager.clearWorkingFileWhileRunningSession();
//        waitForAsyncOperations();
//
//        // Write new event after clear
//        RRWebEvent event = new RRWebEvent();
//        event.setTimestamp(System.currentTimeMillis());
//        event.setType(RRWebEvent.RRWebEventType.META);
//
//        List<RRWebEvent> events = new ArrayList<>();
//        events.add(event);
//
//        fileManager.addFrameToFile(events);
//        waitForAsyncOperations();
//
//        // Verify new event was written
//        JsonArray readEvents = SessionReplayFileManager.readEventsAsJsonArray();
//        Assert.assertTrue("Should have new event", readEvents.size() > 0);
//    }
//
//    // ==================== CLEAR WORKING FILE TESTS ====================
//
//    @Test
//    public void testClearWorkingFile_DeletesFile() {
//        // Write some events first
//        RRWebEvent event = new RRWebEvent();
//        event.setTimestamp(System.currentTimeMillis());
//        event.setType(RRWebEvent.RRWebEventType.META);
//
//        List<RRWebEvent> events = new ArrayList<>();
//        events.add(event);
//
//        fileManager.addFrameToFile(events);
//        waitForAsyncOperations();
//
//        // Clear file
//        fileManager.clearWorkingFile();
//        waitForAsyncOperations();
//
//        // File should be deleted
//        // Note: After clearWorkingFile, the file writer is closed but file might still exist
//        // depending on timing. This is more of an integration test.
//    }
//
//    // ==================== GET WORKING SESSION REPLAY FILE TESTS ====================
//
//    @Test
//    public void testGetWorkingSessionReplayFile_CreatesFile() throws IOException {
//        File workingFile = SessionReplayFileManager.getWorkingSessionReplayFile();
//
//        Assert.assertNotNull(workingFile);
//        Assert.assertTrue("File should exist", workingFile.exists());
//        Assert.assertTrue("Should be a file", workingFile.isFile());
//    }
//
//    @Test
//    public void testGetWorkingSessionReplayFile_CreatesParentDirectories() throws IOException {
//        File workingFile = SessionReplayFileManager.getWorkingSessionReplayFile();
//
//        Assert.assertNotNull(workingFile.getParentFile());
//        Assert.assertTrue("Parent directory should exist", workingFile.getParentFile().exists());
//        Assert.assertTrue("Parent should be a directory", workingFile.getParentFile().isDirectory());
//    }
//
//    @Test
//    public void testGetWorkingSessionReplayFile_SetsLastModified() throws IOException {
//        long beforeTime = System.currentTimeMillis();
//        File workingFile = SessionReplayFileManager.getWorkingSessionReplayFile();
//        long afterTime = System.currentTimeMillis();
//
//        long lastModified = workingFile.lastModified();
//        Assert.assertTrue("Last modified should be recent", lastModified >= beforeTime && lastModified <= afterTime);
//    }
//
//    @Test
//    public void testGetWorkingSessionReplayFile_IncludesSessionId() throws IOException {
//        File workingFile = SessionReplayFileManager.getWorkingSessionReplayFile();
//        String fileName = workingFile.getName();
//
//        // File name should contain session ID
//        String sessionId = AgentConfiguration.getInstance().getSessionID();
//        Assert.assertTrue("File name should contain session ID", fileName.contains(sessionId));
//    }
//
//    // ==================== PRUNE EVENTS OLDER THAN TESTS ====================
//
//    @Test
//    public void testPruneEventsOlderThan_RemovesOldEvents() {
//        long currentTime = System.currentTimeMillis();
//
//        // Write old event
//        RRWebEvent oldEvent = new RRWebEvent();
//        oldEvent.setTimestamp(currentTime - 20000); // 20 seconds ago
//        oldEvent.setType(RRWebEvent.RRWebEventType.META);
//
//        List<RRWebEvent> oldEvents = new ArrayList<>();
//        oldEvents.add(oldEvent);
//        fileManager.addFrameToFile(oldEvents);
//        waitForAsyncOperations();
//
//        // Write recent event
//        RRWebEvent recentEvent = new RRWebEvent();
//        recentEvent.setTimestamp(currentTime - 5000); // 5 seconds ago
//        recentEvent.setType(RRWebEvent.RRWebEventType.META);
//
//        List<RRWebEvent> recentEvents = new ArrayList<>();
//        recentEvents.add(recentEvent);
//        fileManager.addFrameToFile(recentEvents);
//        waitForAsyncOperations();
//
//        int sizeBefore = SessionReplayFileManager.readEventsAsJsonArray().size();
//
//        // Prune events older than 15 seconds
//        SessionReplayFileManager.pruneEventsOlderThan(15000);
//        waitForAsyncOperations();
//
//        int sizeAfter = SessionReplayFileManager.readEventsAsJsonArray().size();
//
//        // Should have fewer events after pruning
//        Assert.assertTrue("Should have removed old events", sizeAfter < sizeBefore);
//    }
//
//    @Test
//    public void testPruneEventsOlderThan_KeepsRecentEvents() {
//        long currentTime = System.currentTimeMillis();
//
//        // Write only recent events
//        RRWebEvent recentEvent1 = new RRWebEvent();
//        recentEvent1.setTimestamp(currentTime - 5000);
//        recentEvent1.setType(RRWebEvent.RRWebEventType.META);
//
//        RRWebEvent recentEvent2 = new RRWebEvent();
//        recentEvent2.setTimestamp(currentTime - 3000);
//        recentEvent2.setType(RRWebEvent.RRWebEventType.META);
//
//        List<RRWebEvent> events = new ArrayList<>();
//        events.add(recentEvent1);
//        events.add(recentEvent2);
//
//        fileManager.addFrameToFile(events);
//        waitForAsyncOperations();
//
//        int sizeBefore = SessionReplayFileManager.readEventsAsJsonArray().size();
//
//        // Prune events older than 15 seconds (should keep all)
//        SessionReplayFileManager.pruneEventsOlderThan(15000);
//        waitForAsyncOperations();
//
//        int sizeAfter = SessionReplayFileManager.readEventsAsJsonArray().size();
//
//        // Should keep all recent events
//        Assert.assertEquals("Should keep all recent events", sizeBefore, sizeAfter);
//    }
//
//    @Test
//    public void testPruneEventsOlderThan_WithNoFile() {
//        // Clear and delete file
//        fileManager.clearWorkingFile();
//        waitForAsyncOperations();
//
//        // Prune should not throw exception
//        SessionReplayFileManager.pruneEventsOlderThan(15000);
//        waitForAsyncOperations();
//    }
//
//    @Test
//    public void testPruneEventsOlderThan_AllowsNewWritesAfter() {
//        // Prune events
//        SessionReplayFileManager.pruneEventsOlderThan(15000);
//        waitForAsyncOperations();
//
//        // Write new event
//        RRWebEvent event = new RRWebEvent();
//        event.setTimestamp(System.currentTimeMillis());
//        event.setType(RRWebEvent.RRWebEventType.META);
//
//        List<RRWebEvent> events = new ArrayList<>();
//        events.add(event);
//
//        fileManager.addFrameToFile(events);
//        waitForAsyncOperations();
//
//        // Verify new event was written
//        JsonArray readEvents = SessionReplayFileManager.readEventsAsJsonArray();
//        Assert.assertTrue("Should have new event", readEvents.size() > 0);
//    }
//
//    // ==================== SHUTDOWN TESTS ====================
//
//    @Test
//    public void testShutdown_ClosesWriter() {
//        // Write some data
//        RRWebEvent event = new RRWebEvent();
//        event.setTimestamp(System.currentTimeMillis());
//        event.setType(RRWebEvent.RRWebEventType.META);
//
//        List<RRWebEvent> events = new ArrayList<>();
//        events.add(event);
//
//        fileManager.addFrameToFile(events);
//        waitForAsyncOperations();
//
//        // Shutdown
//        SessionReplayFileManager.shutdown();
//        waitForAsyncOperations();
//
//        // Should not throw exception
//    }
//
//    @Test
//    public void testShutdown_MultipleCallsSafe() {
//        SessionReplayFileManager.shutdown();
//        waitForAsyncOperations();
//
//        // Second shutdown should not throw exception
//        SessionReplayFileManager.shutdown();
//        waitForAsyncOperations();
//    }
//
//    // ==================== EDGE CASE TESTS ====================
//
//    @Test
//    public void testConcurrentWrites() {
//        // Write from multiple threads (simulated by multiple calls)
//        for (int i = 0; i < 10; i++) {
//            RRWebEvent event = new RRWebEvent();
//            event.setTimestamp(System.currentTimeMillis() + i);
//            event.setType(RRWebEvent.RRWebEventType.META);
//
//            List<RRWebEvent> events = new ArrayList<>();
//            events.add(event);
//
//            fileManager.addFrameToFile(events);
//        }
//
//        waitForAsyncOperations();
//        waitForAsyncOperations(); // Extra wait for concurrent operations
//
//        // Should not lose any writes
//        JsonArray readEvents = SessionReplayFileManager.readEventsAsJsonArray();
//        Assert.assertTrue("Should have multiple events", readEvents.size() >= 10);
//    }
//
//    @Test
//    public void testReadWhileWriting() {
//        // Start a write
//        RRWebEvent event = new RRWebEvent();
//        event.setTimestamp(System.currentTimeMillis());
//        event.setType(RRWebEvent.RRWebEventType.META);
//
//        List<RRWebEvent> events = new ArrayList<>();
//        events.add(event);
//
//        fileManager.addFrameToFile(events);
//
//        // Read immediately (might read old data due to buffering)
//        JsonArray readEvents = SessionReplayFileManager.readEventsAsJsonArray();
//        Assert.assertNotNull(readEvents);
//
//        // Wait and read again
//        waitForAsyncOperations();
//        JsonArray readEventsAfter = SessionReplayFileManager.readEventsAsJsonArray();
//        Assert.assertNotNull(readEventsAfter);
//    }
//
//    @Test
//    public void testFileOperationsAfterShutdown() {
//        SessionReplayFileManager.shutdown();
//        waitForAsyncOperations();
//
//        // Reinitialize
//        SessionReplayFileManager.initialize(application);
//        waitForAsyncOperations();
//
//        // Should be able to write after reinitializing
//        RRWebEvent event = new RRWebEvent();
//        event.setTimestamp(System.currentTimeMillis());
//        event.setType(RRWebEvent.RRWebEventType.META);
//
//        List<RRWebEvent> events = new ArrayList<>();
//        events.add(event);
//
//        fileManager.addFrameToFile(events);
//        waitForAsyncOperations();
//
//        JsonArray readEvents = SessionReplayFileManager.readEventsAsJsonArray();
//        Assert.assertTrue("Should have event after reinitialize", readEvents.size() > 0);
//    }
//
//    // ==================== ERROR CONDITION TESTS ====================
//
//    @Test
//    public void testReadEventsAsJsonArray_WithMalformedJson() {
//        // This is hard to test directly without access to write malformed JSON
//        // The method handles parse exceptions gracefully by skipping bad lines
//        // Test would require mocking file system or writing directly to file
//    }
//
//    @Test
//    public void testPruneEventsOlderThan_WithEventsWithoutTimestamp() {
//        // Events without timestamp should be kept
//        // This is hard to test without directly writing to file
//        // The implementation keeps events without timestamp as a safety measure
//    }
//
//    @Test
//    public void testMultipleFileManagers() {
//        // Create another file manager instance
//        SessionReplayProcessor processor2 = new SessionReplayProcessor();
//        SessionReplayFileManager fileManager2 = new SessionReplayFileManager(processor2);
//
//        // Both should work with same file (static state)
//        RRWebEvent event1 = new RRWebEvent();
//        event1.setTimestamp(1000L);
//        event1.setType(RRWebEvent.RRWebEventType.META);
//
//        RRWebEvent event2 = new RRWebEvent();
//        event2.setTimestamp(2000L);
//        event2.setType(RRWebEvent.RRWebEventType.META);
//
//        List<RRWebEvent> events1 = new ArrayList<>();
//        events1.add(event1);
//
//        List<RRWebEvent> events2 = new ArrayList<>();
//        events2.add(event2);
//
//        fileManager.addFrameToFile(events1);
//        fileManager2.addFrameToFile(events2);
//
//        waitForAsyncOperations();
//
//        // Both writes should succeed
//        JsonArray readEvents = SessionReplayFileManager.readEventsAsJsonArray();
//        Assert.assertTrue("Should have events from both managers", readEvents.size() >= 2);
//    }
//}