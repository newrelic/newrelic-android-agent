package com.newrelic.agent.android.sessionReplay;

import static com.newrelic.agent.android.util.Constants.SessionReplay.FIRST_TIMESTAMP;
import static com.newrelic.agent.android.util.Constants.SessionReplay.LAST_TIMESTAMP;
import static com.newrelic.agent.android.util.Constants.SessionReplay.SESSION_ID;
import static com.newrelic.agent.android.util.Constants.SessionReplay.SESSION_REPLAY_DATA_DIR;
import static com.newrelic.agent.android.util.Constants.SessionReplay.SESSION_REPLAY_FILE_MASK;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.crash.Crash;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.util.Streams;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Handles session replay data associated with crashes.
 * This class is responsible for finding, processing, and uploading session replay data
 * when a crash occurs.
 */
public class CrashSessionReplayHandler {
    private static final AgentLog log = AgentLogManager.getAgentLog();


    private final AgentConfiguration agentConfiguration;
    
    public CrashSessionReplayHandler(AgentConfiguration agentConfiguration) {
        this.agentConfiguration = agentConfiguration;
    }
    
    /**
     * Processes and uploads session replay data associated with a crash.
     * 
     * @param crash The crash object containing session information
     */
    public void handleCrashSessionReplay(Crash crash) {
        if (!agentConfiguration.getSessionReplayConfiguration().isEnabled()) {
            log.debug("Session replay is disabled, skipping crash session replay handling");
            return;
        }
        
        if (crash == null) {
            log.warn("Cannot handle session replay for null crash");
            return;
        }
        
        String sessionId = extractSessionId(crash);
        if (sessionId == null || sessionId.isEmpty()) {
            log.debug("No session ID found in crash, skipping session replay handling");
            return;
        }
        
        Map<String, Object> sessionAttributes = buildSessionAttributes(crash);
        processSessionReplayFiles(sessionId, sessionAttributes, crash);
        cleanupOldSessionReplayFiles();
    }
    
    /**
     * Extracts the session ID from crash attributes.
     * 
     * @param crash The crash object
     * @return The session ID or null if not found
     */
    private String extractSessionId(Crash crash) {
        Set<AnalyticsAttribute> attributes = crash.getSessionAttributes();
        if (attributes == null) {
            return null;
        }
        
        for (AnalyticsAttribute attribute : attributes) {
            if (SESSION_ID.equals(attribute.getName())) {
                return attribute.getStringValue();
            }
        }
        
        return null;
    }
    
    /**
     * Builds session attributes map from crash data.
     * 
     * @param crash The crash object
     * @return Map of session attributes
     */
    private Map<String, Object> buildSessionAttributes(Crash crash) {
        Map<String, Object> sessionAttributes = new HashMap<>();
        Set<AnalyticsAttribute> attributes = crash.getSessionAttributes();
        
        if (attributes != null) {
            for (AnalyticsAttribute attribute : attributes) {
                if (SESSION_ID.equals(attribute.getName())) {
                    sessionAttributes.put(attribute.getName(), attribute.getStringValue());
                }
            }
        }
        
        return sessionAttributes;
    }
    
    /**
     * Processes session replay files for the given session ID.
     * 
     * @param sessionId The session ID to look for
     * @param sessionAttributes Base session attributes
     * @param crash The crash object for logging purposes
     */
    private void processSessionReplayFiles(String sessionId, Map<String, Object> sessionAttributes, Crash crash) {
        File sessionReplayDataStore = getSessionReplayDataStore();
        String logFileMask = String.format(Locale.getDefault(), SESSION_REPLAY_FILE_MASK, sessionId, "tmp");
        
        Set<File> matchingFiles = Streams.list(sessionReplayDataStore)
                .filter(file -> file.isFile() && file.getName().matches(logFileMask))
                .collect(Collectors.toSet());
        
        for (File file : matchingFiles) {
            log.info("CrashSessionReplayHandler: Found Session Replay Data for Crash [" + crash.getUuid().toString() + "]");
            processSessionReplayFile(file, sessionAttributes);
        }
    }
    
    /**
     * Processes a single session replay file.
     * 
     * @param file The file to process
     * @param sessionAttributes Base session attributes
     */
    private void processSessionReplayFile(File file, Map<String, Object> sessionAttributes) {
        JsonArray logsJsonArray = new JsonArray();
        AtomicReference<Long> firstTimestamp = new AtomicReference<>(0L);
        AtomicReference<Long> lastTimestamp = new AtomicReference<>(0L);
        
        try (BufferedReader reader = Streams.newBufferedFileReader(file)) {
            reader.lines().forEach(line -> {
                if (line != null && !line.isEmpty()) {
                    processSessionReplayLine(line, logsJsonArray, firstTimestamp, lastTimestamp);
                }
            });
        } catch (IOException e) {
            log.error("Error reading session replay file: " + file.getName(), e);
            return;
        }
        
        if (!logsJsonArray.isEmpty()) {
            // Add timestamp attributes
            Map<String, Object> enhancedAttributes = new HashMap<>(sessionAttributes);
            enhancedAttributes.put(FIRST_TIMESTAMP, firstTimestamp.get());
            enhancedAttributes.put(LAST_TIMESTAMP, lastTimestamp.get());
            
            // Upload the session replay data
            SessionReplayReporter.reportSessionReplayData(logsJsonArray.toString().getBytes(), enhancedAttributes);
        }
    }
    
    /**
     * Processes a single line from a session replay file.
     * 
     * @param line The line to process
     * @param logsJsonArray The array to add processed data to
     * @param firstTimestamp Reference to track first timestamp
     * @param lastTimestamp Reference to track last timestamp
     */
    private void processSessionReplayLine(String line, JsonArray logsJsonArray, 
                                        AtomicReference<Long> firstTimestamp, 
                                        AtomicReference<Long> lastTimestamp) {
        try {
            JsonArray messageAsJson = new Gson().fromJson(line, JsonArray.class);
            for (int i = 0; i < messageAsJson.size(); i++) {
                JsonObject frame = messageAsJson.get(i).getAsJsonObject();
                
                if (frame.has("type") && frame.get("type").getAsInt() == 2) {
                    updateTimestamps(frame, firstTimestamp, lastTimestamp);
                }
                
                logsJsonArray.add(messageAsJson.get(i));
            }
        } catch (JsonSyntaxException e) {
            log.error("Invalid JSON entry skipped [" + line + "]", e);
        }
    }
    
    /**
     * Updates the first and last timestamp references based on frame data.
     * 
     * @param frame The frame object
     * @param firstTimestamp Reference to first timestamp
     * @param lastTimestamp Reference to last timestamp
     */
    private void updateTimestamps(JsonObject frame, AtomicReference<Long> firstTimestamp, 
                                AtomicReference<Long> lastTimestamp) {
        if (!frame.has("timestamp")) {
            return;
        }
        
        long timestamp = frame.get("timestamp").getAsLong();
        
        if (firstTimestamp.get() == 0 && lastTimestamp.get() == 0) {
            firstTimestamp.set(timestamp);
            lastTimestamp.set(timestamp);
        } else if (timestamp > lastTimestamp.get()) {
            lastTimestamp.set(timestamp);
        }
    }
    
    /**
     * Cleans up old session replay files that don't belong to the current session.
     */
    private void cleanupOldSessionReplayFiles() {
        File sessionReplayDataStore = getSessionReplayDataStore();
        String currentSessionId = agentConfiguration.getSessionID();
        
        Set<File> filesToDelete = Streams.list(sessionReplayDataStore)
                .filter(file -> file.isFile() && !file.getName().contains(currentSessionId))
                .collect(Collectors.toSet());
        
        for (File file : filesToDelete) {
            boolean deleted = file.delete();
            if (!deleted) {
                log.warn("Failed to delete old session replay file: " + file.getName());
            }
        }
    }
    
    /**
     * Gets the session replay data store directory.
     * 
     * @return The session replay data store directory
     */
    private File getSessionReplayDataStore() {
        return new File(System.getProperty("java.io.tmpdir", "/tmp"), SESSION_REPLAY_DATA_DIR).getAbsoluteFile();
    }
}