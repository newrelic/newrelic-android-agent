/*
 * Copyright (c) 2025 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay.capture;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;

public class SessionReplayFileManagerTest {

    @After
    public void tearDown() throws Exception {
        SessionReplayFileManager.closeWorkingSessionReplayFileWriter();
        SessionReplayFileManager.workingSessionReplayFileWriter.set(null);
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
