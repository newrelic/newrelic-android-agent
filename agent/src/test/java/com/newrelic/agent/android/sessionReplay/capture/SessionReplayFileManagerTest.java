/*
 * Copyright (c) 2025 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay.capture;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;

public class SessionReplayFileManagerTest {

    private File originalDataStore;
    private File originalWorkingFile;

    @Before
    public void setUp() throws Exception {
        originalDataStore = SessionReplayFileManager.sessionReplayDataStore;
        originalWorkingFile = SessionReplayFileManager.workingSessionReplayFile;

        // Point the data store at an isolated directory: clearWorkingFile() deletes and
        // re-creates the working file, which must not touch a real session replay buffer.
        File dataStore = File.createTempFile("sessionReplayStore", "");
        Assert.assertTrue(dataStore.delete());
        Assert.assertTrue(dataStore.mkdirs());
        dataStore.deleteOnExit();
        SessionReplayFileManager.sessionReplayDataStore = dataStore;
    }

    @After
    public void tearDown() throws Exception {
        SessionReplayFileManager.closeWorkingSessionReplayFileWriter();
        SessionReplayFileManager.workingSessionReplayFileWriter.set(null);
        SessionReplayFileManager.sessionReplayDataStore = originalDataStore;
        SessionReplayFileManager.workingSessionReplayFile = originalWorkingFile;
    }

    @Test
    public void closeWorkingSessionReplayFileWriter_closesAndClearsCurrentWriter() throws Exception {
        TrackingBufferedWriter previousWriter = new TrackingBufferedWriter();
        SessionReplayFileManager.workingSessionReplayFileWriter.set(previousWriter);

        SessionReplayFileManager.closeWorkingSessionReplayFileWriter();

        Assert.assertTrue(previousWriter.closed);
        Assert.assertNull(SessionReplayFileManager.workingSessionReplayFileWriter.get());
    }

    @Test
    public void closeWorkingSessionReplayFileWriter_clearsReferenceEvenWhenCloseFails() {
        BufferedWriter failingWriter = new BufferedWriter(new StringWriter()) {
            @Override
            public void close() throws IOException {
                throw new IOException("close failed");
            }
        };
        SessionReplayFileManager.workingSessionReplayFileWriter.set(failingWriter);

        try {
            SessionReplayFileManager.closeWorkingSessionReplayFileWriter();
            Assert.fail("Expected the failing close() to propagate");
        } catch (IOException expected) {
            // expected
        }

        // A writer that failed to close must still be unpublished, otherwise the next
        // session inherits a dead writer.
        Assert.assertNull(SessionReplayFileManager.workingSessionReplayFileWriter.get());
    }

    @Test
    public void replaceWorkingSessionReplayFileWriter_closesPreviousWriterBeforeReplacing() throws Exception {
        TrackingBufferedWriter previousWriter = new TrackingBufferedWriter();
        SessionReplayFileManager.workingSessionReplayFileWriter.set(previousWriter);
        File sessionReplayFile = File.createTempFile("session-replay", ".tmp");
        sessionReplayFile.deleteOnExit();

        SessionReplayFileManager.replaceWorkingSessionReplayFileWriter(sessionReplayFile, true);

        BufferedWriter currentWriter = SessionReplayFileManager.workingSessionReplayFileWriter.get();
        Assert.assertTrue(previousWriter.closed);
        Assert.assertNotNull(currentWriter);
        Assert.assertNotSame(previousWriter, currentWriter);
    }

    /**
     * clearWorkingFile() runs on every application background. Publishing a closed writer
     * there made every subsequent frame/touch write fail with "Stream closed", silently
     * ending capture for the rest of the process lifetime, since nothing re-initializes the
     * writer on foreground.
     */
    @Test
    public void clearWorkingFile_leavesAWritableWriterSoCaptureResumes() throws Exception {
        TrackingBufferedWriter previousWriter = new TrackingBufferedWriter();
        SessionReplayFileManager.workingSessionReplayFile = SessionReplayFileManager.getWorkingSessionReplayFile();
        SessionReplayFileManager.workingSessionReplayFileWriter.set(previousWriter);

        new SessionReplayFileManager(null).clearWorkingFile();

        BufferedWriter currentWriter = awaitReplacementWriter(previousWriter);
        Assert.assertTrue(previousWriter.closed);

        // Would throw IOException("Stream closed") if the closed writer were still published.
        currentWriter.write("{\"type\":4}");
        currentWriter.newLine();
        currentWriter.flush();

        Assert.assertTrue("Post-background write did not reach the working file",
                SessionReplayFileManager.workingSessionReplayFile.length() > 0);
    }

    @Test
    public void shutdown_closesAndClearsCurrentWriter() {
        TrackingBufferedWriter previousWriter = new TrackingBufferedWriter();
        SessionReplayFileManager.workingSessionReplayFileWriter.set(previousWriter);

        SessionReplayFileManager.shutdown();

        Assert.assertTrue(previousWriter.closed);
        Assert.assertNull(SessionReplayFileManager.workingSessionReplayFileWriter.get());
    }

    /**
     * clearWorkingFile() is dispatched onto the file-write executor, so poll for the
     * replacement writer instead of assuming the task has already run.
     */
    private static BufferedWriter awaitReplacementWriter(BufferedWriter previousWriter) throws InterruptedException {
        for (int attempt = 0; attempt < 100; attempt++) {
            BufferedWriter candidate = SessionReplayFileManager.workingSessionReplayFileWriter.get();
            if (candidate != null && candidate != previousWriter) {
                return candidate;
            }
            Thread.sleep(20);
        }
        Assert.fail("clearWorkingFile() never published a replacement writer");
        return null;
    }

    private static final class TrackingBufferedWriter extends BufferedWriter {
        private boolean closed;

        private TrackingBufferedWriter() {
            super(new StringWriter());
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}
