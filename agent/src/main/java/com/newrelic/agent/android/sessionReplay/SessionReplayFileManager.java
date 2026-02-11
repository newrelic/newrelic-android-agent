package com.newrelic.agent.android.sessionReplay;

import static com.newrelic.agent.android.util.Constants.SessionReplay.SESSION_REPLAY_DATA_DIR;
import static com.newrelic.agent.android.util.Constants.SessionReplay.SESSION_REPLAY_FILE_MASK;

import android.app.Application;


import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.sessionReplay.models.RRWebEvent;
import com.newrelic.agent.android.util.NamedThreadFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
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

    // Synchronization object for file write/read operations
    private static final Object fileSyncLock = new Object();

    static int POOL_SIZE = Math.max(2, Runtime.getRuntime().availableProcessors() / 4);

    // Gson for serialization
    private static Gson gson;
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


        gson = new Gson();
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
     * @param rrWebEvents The frame to add
     */
    public void addFrameToFile(List<RRWebEvent> rrWebEvents) {
        Callable<Void> fileWriteTask = new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                synchronized (fileSyncLock) {
                    try {
                        if (workingSessionReplayFileWriter.get() != null) {
                            for (RRWebEvent event : rrWebEvents) {
                                workingSessionReplayFileWriter.get().write(gson.toJson(event));
                                workingSessionReplayFileWriter.get().newLine();
                            }
                        }
                        BufferedWriter currentWriter = workingSessionReplayFileWriter.get();
                        if (currentWriter != null) {
                            currentWriter.flush();
                        }
                    } catch (IOException e) {
                        log.error("Error writing frame to file", e);
                    }
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
                synchronized (fileSyncLock) {
                    try {
                        if (workingSessionReplayFileWriter.get() != null) {

                            touchTracker.processTouchData().forEach(position -> {
                                try {
                                    workingSessionReplayFileWriter.get().write(gson.toJson(position));
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
                }
                return null;
            }
        };

        submitFileWriteTask(fileWriteTask);
    }

    public void clearWorkingFileWhileRunningSession() {
        Callable<Void> clearFileTask = new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                synchronized (fileSyncLock) {
                    try {
                        // Close the current writer if it exists
                        BufferedWriter currentWriter = workingSessionReplayFileWriter.get();
                        if (currentWriter != null) {
                            currentWriter.flush();
                            workingSessionReplayFileWriter.set(null);
                        }

                        // Clear the file contents by creating a new empty file
                        if (workingSessionReplayFile != null && workingSessionReplayFile.exists()) {
                            // Truncate the file by creating a new FileWriter in overwrite mode
                            try (java.io.FileWriter fw = new java.io.FileWriter(workingSessionReplayFile, false)) {
                                // Writing nothing effectively clears the file
                            }
                        }
                        // Reinitialize the writer for new content
                        if (workingSessionReplayFile != null) {
                            workingSessionReplayFileWriter.set(new BufferedWriter(new java.io.FileWriter(workingSessionReplayFile, true)));
                        }
                    } catch (IOException e) {
                        log.error("Error clearing working session replay file", e);
                    }
                }
                return null;
            }
        };

        submitFileWriteTask(clearFileTask);
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
    private static void submitFileWriteTask(Callable<Void> task) {
        try {
            fileWriteExecutor.submit(task);
        } catch (Exception e) {
            log.error("Failed to submit file write task", e);
        }
    }

    /**
     * Reads all events from the working session replay file as a JsonArray.
     * Each line in the file is treated as a separate JSON object.
     * This method synchronizes on fileSyncLock to ensure all pending writes complete before reading.
     *
     * @return JsonArray of events, or empty JsonArray if file doesn't exist or is empty
     */
    public static JsonArray readEventsAsJsonArray() {
        JsonArray jsonArray = new JsonArray();

        if (workingSessionReplayFile == null || !workingSessionReplayFile.exists()) {
            log.debug("Session replay file does not exist or is not initialized");
            return jsonArray;
        }

        // Synchronize on fileSyncLock to ensure all pending writes complete
        synchronized (fileSyncLock) {
            try (BufferedReader reader = new BufferedReader(new FileReader(workingSessionReplayFile))) {
                String line;

                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) {
                        continue; // Skip empty lines
                    }

                    try {
                        // Parse each line as a JSON object and add to array
                        JsonObject jsonObject = JsonParser.parseString(line).getAsJsonObject();
                        jsonArray.add(jsonObject);

                    } catch (Exception e) {
                        log.warn("Failed to parse event JSON from file: " + line);
                        // Continue reading remaining lines
                    }
                }

                log.debug("Successfully read " + jsonArray.size() + " events from session replay file as JsonArray");

            } catch (IOException e) {
                log.error("Error reading session replay file: " + (workingSessionReplayFile != null ? workingSessionReplayFile.getAbsolutePath() : "unknown"), e);
            }
        }

        return jsonArray;
    }

    /**
     * Prunes events older than the specified threshold from the session replay file.
     * Only keeps events with timestamp >= (currentTime - thresholdMs).
     * This method is used for ERROR mode sliding window (15-second buffer).
     *
     * @param thresholdMs The time threshold in milliseconds (e.g., 15000 for 15 seconds)
     */
    public static void pruneEventsOlderThan(long thresholdMs) {
        Callable<Void> pruneTask = new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                synchronized (fileSyncLock) {
                    try {
                        if (workingSessionReplayFile == null || !workingSessionReplayFile.exists()) {
                            log.debug("Session replay file does not exist, nothing to prune");
                            return null;
                        }

                        long currentTimeMs = System.currentTimeMillis();
                        long cutoffTimeMs = currentTimeMs - thresholdMs;

                        // Read all events from file
                        JsonArray allEvents = new JsonArray();
                        try (BufferedReader reader = new BufferedReader(new FileReader(workingSessionReplayFile))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (line.trim().isEmpty()) {
                                    continue;
                                }
                                try {
                                    JsonObject jsonObject = JsonParser.parseString(line).getAsJsonObject();
                                    allEvents.add(jsonObject);
                                } catch (Exception e) {
                                    log.warn("Failed to parse event JSON during pruning: " + line);
                                }
                            }
                        }

                        // Filter events to keep only those within the threshold
                        JsonArray recentEvents = new JsonArray();
                        for (int i = 0; i < allEvents.size(); i++) {
                            JsonObject event = allEvents.get(i).getAsJsonObject();
                            try {
                                if (event.has("timestamp")) {
                                    long eventTimestamp = event.get("timestamp").getAsLong();
                                    if (eventTimestamp >= cutoffTimeMs) {
                                        recentEvents.add(event);
                                    }
                                } else {
                                    // Keep events without timestamp (shouldn't happen but be safe)
                                    recentEvents.add(event);
                                }
                            } catch (Exception e) {
                                log.warn("Error filtering event during pruning, keeping event anyway");
                                recentEvents.add(event);
                            }
                        }

                        log.debug("Pruning: removed " + (allEvents.size() - recentEvents.size()) + " old events, kept " + recentEvents.size() + " recent events");

                        // Close current writer before rewriting file
                        BufferedWriter currentWriter = workingSessionReplayFileWriter.get();
                        if (currentWriter != null) {
                            currentWriter.flush();
                            currentWriter.close();
                            workingSessionReplayFileWriter.set(null);
                        }

                        // Rewrite file with only recent events
                        try (BufferedWriter writer = new BufferedWriter(new java.io.FileWriter(workingSessionReplayFile, false))) {
                            for (int i = 0; i < recentEvents.size(); i++) {
                                writer.write(gson.toJson(recentEvents.get(i)));
                                writer.newLine();
                            }
                            writer.flush();
                        }

                        // Reinitialize the writer for new content
                        workingSessionReplayFileWriter.set(new BufferedWriter(new java.io.FileWriter(workingSessionReplayFile, true)));
                        log.debug("Successfully pruned events older than " + thresholdMs + "ms");

                    } catch (IOException e) {
                        log.error("Error during pruning of session replay file", e);
                    }
                }
                return null;
            }
        };

        submitFileWriteTask(pruneTask);
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