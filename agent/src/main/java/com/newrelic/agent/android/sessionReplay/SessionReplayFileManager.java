package com.newrelic.agent.android.sessionReplay;

import static com.newrelic.agent.android.util.Constants.SessionReplay.SESSION_REPLAY_DATA_DIR;
import static com.newrelic.agent.android.util.Constants.SessionReplay.SESSION_REPLAY_FILE_MASK;

import android.app.Application;


import com.google.gson.Gson;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.util.NamedThreadFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages file operations for session replay data.
 * Handles creation, writing, and cleanup of session replay files.
 */
public class SessionReplayFileManager {
    protected static final AgentLog log = AgentLogManager.getAgentLog();
    private static final String TAG = "SessionReplayFileManager";
    static File sessionReplayDataStore = new File(System.getProperty("java.io.tmpdir", "/tmp"), SESSION_REPLAY_DATA_DIR).getAbsoluteFile();
    protected static File workingSessionReplayFile;
    protected static AtomicReference<BufferedWriter> workingSessionReplayFileWriter = new AtomicReference<>(null);

    static int POOL_SIZE = Math.max(2, Runtime.getRuntime().availableProcessors() / 4);

    // ThreadPoolExecutor for file operations
    private static final ThreadPoolExecutor fileWriteExecutor = new ThreadPoolExecutor(
            2, // Core pool size
            POOL_SIZE, // Maximum pool size
            30, TimeUnit.SECONDS, // Keep alive time
            new LinkedBlockingQueue<>(), // Work queue
            new NamedThreadFactory("SessionReplayFileWriter") // Thread factory
    );

    private final SessionReplayProcessor processor;

    public SessionReplayFileManager(SessionReplayProcessor processor) {
        this.processor = processor;
    }

    /**
     * Initializes the file storage system for session replay.
     * Creates the necessary directories and sets up the data store.
     *
     * @param application The application instance
     */
    public static void initialize(Application application) {
        if (application == null) {
            log.error("Cannot initialize with null application");
            return;
        }

        File rootDir = application.getCacheDir();
        if (!rootDir.isDirectory() || !rootDir.exists() || !rootDir.canWrite()) {
            log.error("Cache directory is not available or writable");
            return;
        }

        sessionReplayDataStore = new File(rootDir, SESSION_REPLAY_DATA_DIR);
        sessionReplayDataStore.mkdirs();

        // Initialize the file writer
        initializeFileWriter();
    }

    /**
     * Initializes the file writer for session replay data.
     * Creates a new file and sets up the BufferedWriter.
     */
    private static void initializeFileWriter() {
        Callable<Void> initTask = () -> {
            try {
                workingSessionReplayFile = getWorkingSessionReplayFile();
                workingSessionReplayFileWriter.set(new BufferedWriter(new java.io.FileWriter(workingSessionReplayFile)));
                log.debug("Initialized session replay file: " + workingSessionReplayFile.getAbsolutePath());
            } catch (IOException e) {
                log.error("Error initializing session replay file", e);
            }
            return null;
        };

        // Execute synchronously during initialization
        try {
            initTask.call();
        } catch (Exception e) {
            log.error( "Failed to initialize file writer", e);
        }
    }

    /**
     * Adds a frame to the working session replay file.
     *
     * @param newFrame The frame to add
     */
    public void addFrameToFile(final SessionReplayFrame newFrame) {
        Callable<Void> fileWriteTask = new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                try {
                    if (workingSessionReplayFileWriter.get() != null) {
                        workingSessionReplayFileWriter.get().write(new Gson().toJson(processor.createMetaEvent(newFrame)));
                        workingSessionReplayFileWriter.get().newLine();
                        workingSessionReplayFileWriter.get().write(new Gson().toJson(processor.processFullFrame(newFrame)));
                        workingSessionReplayFileWriter.get().newLine();
                    }
                    BufferedWriter currentWriter = workingSessionReplayFileWriter.get();
                    if (currentWriter != null) {
                        currentWriter.flush();
                    }
                } catch (IOException e) {
                    log.error("Error writing frame to file", e);
                }
                return null;
            }
        };

        submitFileWriteTask(fileWriteTask);
    }

    /**
     * Adds touch data to the working session replay file.
     *
     * @param touchTracker The touch tracker containing touch data
     */
    public void  addTouchToFile(final TouchTracker touchTracker) {
        Callable<Void> fileWriteTask = new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                try {
                    if (workingSessionReplayFileWriter.get() != null) {

                        touchTracker.processTouchData().forEach(position -> {
                            try {
                                workingSessionReplayFileWriter.get().write(new Gson().toJson(position));
                                workingSessionReplayFileWriter.get().newLine();
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });

                    }
                    BufferedWriter currentWriter = workingSessionReplayFileWriter.get();
                    if (currentWriter != null) {
                        currentWriter.flush();
                    }
                } catch (IOException e) {
                    log.error("Error writing touch data to file", e);
                }
                return null;
            }
        };

        submitFileWriteTask(fileWriteTask);
    }

    /**
     * Clears the current working session replay file and creates a new one.
     * This is called after a successful harvest to ensure we start with a fresh file.
     */
    public void clearWorkingFile() {
        Callable<Void> clearFileTask = new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                try {
                    // Close the current writer if it exists
                    BufferedWriter currentWriter = workingSessionReplayFileWriter.get();
                    if (currentWriter != null) {
                        currentWriter.flush();
                        currentWriter.close();
                    }

                    // Delete the current file
                    if (workingSessionReplayFile != null && workingSessionReplayFile.exists()) {
                        boolean deleted = workingSessionReplayFile.delete();
                        if (!deleted) {
                            log.warn("Failed to delete working session replay file");
                        }
                    }

                    log.debug("Created new session replay file: " + workingSessionReplayFile.getAbsolutePath());
                } catch (IOException e) {
                    log.error("Error clearing working session replay file", e);
                }
                return null;
            }
        };

        submitFileWriteTask(clearFileTask);
    }

    /**
     * Gets the working session replay file.
     *
     * @return The working session replay file
     * @throws IOException If file creation fails
     */
    static File getWorkingSessionReplayFile() throws IOException {
        File sessionReplayFile = new File(sessionReplayDataStore,
                String.format(Locale.getDefault(),
                        SESSION_REPLAY_FILE_MASK,
                        AgentConfiguration.getInstance().getSessionID(),
                        "tmp"));

        sessionReplayFile.getParentFile().mkdirs();
        if (!sessionReplayFile.exists()) {
            sessionReplayFile.createNewFile();
        }

        sessionReplayFile.setLastModified(System.currentTimeMillis());

        return sessionReplayFile;
    }

    /**
     * Submits a file write task to be executed asynchronously.
     *
     * @param task The task to submit
     */
    private void submitFileWriteTask(Callable<Void> task) {
        try {
            fileWriteExecutor.submit(task);
        } catch (Exception e) {
            log.error("Failed to submit file write task", e);
        }
    }

    /**
     * Shuts down the file write executor.
     * Should be called during cleanup.
     */
    public static void shutdown() {
        try {
            // Close the current writer if it exists
            BufferedWriter currentWriter = workingSessionReplayFileWriter.get();
            if (currentWriter != null) {
                currentWriter.flush();
                currentWriter.close();
                workingSessionReplayFileWriter.set(null);
            }
        } catch (Exception e) {
            log.error("Error during shutdown", e);
        }
    }
}