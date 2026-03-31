package com.newrelic.agent.android.sessionReplay;

import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.util.List;

import io.socket.client.IO;
import io.socket.client.Socket;

/**
 * Debug utility for streaming session replay events to a local Socket.IO server in real-time.
 * This allows developers to view session replay data as it's captured, without waiting for harvest cycles.
 *
 * <p>This is intended for development/debugging purposes only.
 * <p><b>ENABLED BY DEFAULT</b> - Streams to http://10.0.2.2:3000 (Android emulator's host localhost).
 *
 * <p>Usage:
 * <pre>
 * // Disable streaming (for production builds)
 * SocketIOStreamer.getInstance().disable();
 *
 * // Change server URL
 * SocketIOStreamer.getInstance().setServerURL("http://192.168.1.100:3000");
 *
 * // Re-enable with default emulator URL
 * SocketIOStreamer.getInstance().enableLocalhost();
 * </pre>
 *
 * <p>The streamer emits:
 * <ul>
 *   <li>"recorder-start" - emitted once when first event is sent</li>
 *   <li>"rrweb-event" - emitted for each session replay event (frames and touches)</li>
 * </ul>
 */
public class SocketIOStreamer {
    private static final AgentLog log = AgentLogManager.getAgentLog();
    private static final String TAG = "SocketIOStreamer";

    // Default server URL for Android emulator (10.0.2.2 is host machine's localhost)
    private static final String DEFAULT_SERVER_URL = "http://10.0.2.2:3000";

    // Configurable server URL - set to default for emulator debugging
    private URI socketIOURL = URI.create(DEFAULT_SERVER_URL);

    private Socket socket = null;
    private volatile boolean recorderStarted = false;
    private volatile boolean isConnecting = false;

    // Capture timing passed from ViewDrawInterceptor to avoid modifying SessionReplayFrame
    private volatile long lastCaptureTimeMs = 0;

    public void setLastCaptureTimeMs(long ms) { this.lastCaptureTimeMs = ms; }
    public long getLastCaptureTimeMs()        { return this.lastCaptureTimeMs; }

    // Singleton instance
    private static volatile SocketIOStreamer instance = null;
    private static final Object instanceLock = new Object();

    private SocketIOStreamer() {}

    /**
     * Gets the singleton instance of SocketIOStreamer.
     *
     * @return The singleton instance
     */
    public static SocketIOStreamer getInstance() {
        if (instance == null) {
            synchronized (instanceLock) {
                if (instance == null) {
                    instance = new SocketIOStreamer();
                }
            }
        }
        return instance;
    }

    /**
     * Sets the Socket.IO server URL.
     * Pass null to disable streaming (default).
     *
     * @param url The server URL (e.g., "http://localhost:3000" or "http://10.0.2.2:3000" for emulator)
     */
    public void setServerURL(String url) {
        // Disconnect existing connection if URL is changing
        if (socket != null && socket.connected()) {
            disconnect();
        }

        try {
            this.socketIOURL = url != null ? URI.create(url) : null;
            if (url != null) {
                log.debug(TAG + ": Server URL set to " + url);
            } else {
                log.debug(TAG + ": Streaming disabled");
            }
        } catch (Exception e) {
            log.error(TAG + ": Invalid URL: " + url, e);
            this.socketIOURL = null;
        }
    }

    /**
     * Enables streaming to the default emulator-compatible server (http://10.0.2.2:3000).
     * 10.0.2.2 is the special alias for the host machine's localhost from Android emulator.
     */
    public void enableLocalhost() {
        setServerURL(DEFAULT_SERVER_URL);
    }

    /**
     * Disables Socket.IO streaming.
     * Call this for production builds or when debugging is not needed.
     */
    public void disable() {
        setServerURL(null);
    }

    /**
     * Checks if Socket.IO streaming is enabled.
     *
     * @return true if streaming is enabled, false otherwise
     */
    public boolean isEnabled() {
        return socketIOURL != null;
    }

    /**
     * Connects to the Socket.IO server if not already connected.
     * This is called automatically when sending events.
     */
    private synchronized void connectIfNeeded() {
        if (socketIOURL == null) {
            return;
        }

        if (socket != null && socket.connected()) {
            return;
        }

        if (isConnecting) {
            return;
        }

        isConnecting = true;

        try {
            IO.Options options = IO.Options.builder()
                    .setQuery("type=recorder")
                    .build();

            socket = IO.socket(socketIOURL, options);

            socket.on(Socket.EVENT_CONNECT, args -> {
                log.debug(TAG + ": Connected to " + socketIOURL);
                isConnecting = false;
            });

            socket.on(Socket.EVENT_CONNECT_ERROR, args -> {
                log.error(TAG + ": Connection error: " + (args.length > 0 ? args[0] : "unknown"));
                isConnecting = false;
            });

            socket.on(Socket.EVENT_DISCONNECT, args -> {
                log.debug(TAG + ": Disconnected from server");
            });

            socket.connect();
            log.debug(TAG + ": Connecting to " + socketIOURL);

        } catch (Exception e) {
            log.error(TAG + ": Failed to connect: " + e.getMessage(), e);
            socket = null;
            isConnecting = false;
        }
    }

    /**
     * Sends a single session replay event to the Socket.IO server.
     * This is called immediately when a frame or touch event is captured.
     *
     * @param jsonString The JSON string representation of the event
     */
    public void sendEvent(String jsonString) {
        if (socketIOURL == null) {
            return;
        }

        connectIfNeeded();

        try {
            JSONObject event = new JSONObject(jsonString);
            emitEvent(event);
        } catch (Exception e) {
            log.error(TAG + ": Failed to parse event JSON: " + e.getMessage());
        }
    }

    /**
     * Sends multiple session replay events to the Socket.IO server.
     *
     * @param jsonArrayString JSON string containing an array of events
     */
    public void sendEvents(String jsonArrayString) {
        if (socketIOURL == null) {
            return;
        }

        connectIfNeeded();

        try {
            JSONArray events = new JSONArray(jsonArrayString);
            for (int i = 0; i < events.length(); i++) {
                JSONObject event = events.getJSONObject(i);
                emitEvent(event);
            }
            log.debug(TAG + ": Streamed " + events.length() + " rrweb events");
        } catch (Exception e) {
            log.error(TAG + ": Failed to parse events JSON: " + e.getMessage());
        }
    }

    /**
     * Emits a single event to the Socket.IO server.
     * Handles the "recorder-start" event on first emission.
     *
     * @param event The JSON event object to emit
     */
    private void emitEvent(JSONObject event) {
        if (socket == null) {
            return;
        }

        // If not connected yet, queue the event to be sent when connected
        if (!socket.connected()) {
            socket.once(Socket.EVENT_CONNECT, args -> {
                emitEventInternal(event);
            });
            return;
        }

        emitEventInternal(event);
    }

    /**
     * Internal method to emit an event (assumes socket is connected).
     *
     * @param event The JSON event object to emit
     */
    private void emitEventInternal(JSONObject event) {
        try {
            // Emit recorder-start once with build metadata
            if (!recorderStarted) {
                try {
                    JSONObject metadata = new JSONObject();
                    metadata.put("agentVersion", com.newrelic.agent.android.Agent.getVersion());
                    metadata.put("timestamp", System.currentTimeMillis());
                    socket.emit("recorder-start", metadata);
                } catch (Exception e) {
                    socket.emit("recorder-start");
                }
                recorderStarted = true;
                log.debug(TAG + ": Sent recorder-start");
            }

            // Emit the rrweb event
            socket.emit("rrweb-event", event);
        } catch (Exception e) {
            log.error(TAG + ": Error emitting event: " + e.getMessage());
        }
    }

    /**
     * Sends a performance metric to the Socket.IO server.
     * Used for A/B performance testing — metrics are displayed in the
     * live viewer's perf panel alongside the replay.
     *
     * @param metric JSON object with perf data (captureTimeMs, nodeCount, etc.)
     */
    public void emitMetric(JSONObject metric) {
        if (socketIOURL == null || socket == null || !socket.connected()) {
            return;
        }
        try {
            socket.emit("perf-metric", metric);
        } catch (Exception e) {
            log.error(TAG + ": Error emitting perf metric: " + e.getMessage());
        }
    }

    /**
     * Disconnects from the Socket.IO server and resets state.
     * Call this when session replay is stopped or the app is terminating.
     */
    public void disconnect() {
        if (socket != null) {
            try {
                socket.disconnect();
                socket.close();
            } catch (Exception e) {
                log.error(TAG + ": Error during disconnect: " + e.getMessage());
            }
            socket = null;
        }
        recorderStarted = false;
        isConnecting = false;
        log.debug(TAG + ": Disconnected and reset");
    }

    /**
     * Resets the recorder state without disconnecting.
     * Call this when a new session starts to ensure "recorder-start" is sent again.
     */
    public void resetRecorderState() {
        recorderStarted = false;
        log.debug(TAG + ": Recorder state reset");
    }
}