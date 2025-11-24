package com.newrelic.agent.android.sessionReplay;

import static com.newrelic.agent.android.util.Constants.SessionReplay.FIRST_TIMESTAMP;
import static com.newrelic.agent.android.util.Constants.SessionReplay.LAST_TIMESTAMP;

import android.app.Application;
import android.os.Handler;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.analytics.AnalyticsControllerImpl;
import com.newrelic.agent.android.analytics.AnalyticsEvent;
import com.newrelic.agent.android.analytics.EventListener;
import com.newrelic.agent.android.analytics.EventManager;
import com.newrelic.agent.android.analytics.EventManagerImpl;
import com.newrelic.agent.android.analytics.NetworkRequestErrorEvent;
import com.newrelic.agent.android.background.ApplicationStateEvent;
import com.newrelic.agent.android.background.ApplicationStateListener;
import com.newrelic.agent.android.harvest.Harvest;
import com.newrelic.agent.android.harvest.HarvestLifecycleAware;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.sessionReplay.internal.OnFrameTakenListener;
import com.newrelic.agent.android.sessionReplay.models.RRWebEvent;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.util.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import curtains.Curtains;

public class SessionReplay implements OnFrameTakenListener, HarvestLifecycleAware, OnTouchRecordedListener, ApplicationStateListener {
    private static Application application;
    private static Handler uiThreadHandler;
    private static SessionReplayActivityLifecycleCallbacks sessionReplayActivityLifecycleCallbacks;
    private static SessionReplayProcessor processor;
    private static ViewDrawInterceptor viewDrawInterceptor;
    private final List<TouchTracker> touchTrackers = new ArrayList<>();
    private static final SessionReplay instance = new SessionReplay();
    private SessionReplayFileManager fileManager;
    protected static final AgentLog log = AgentLogManager.getAgentLog();
    private static boolean isFirstChunk = true;
    private final List<SessionReplayFrame> rawFrames =
            Collections.synchronizedList(new ArrayList<>());
    private final List<RRWebEvent> rrWebEvents =
            Collections.synchronizedList(new ArrayList<>());
    private static final AtomicBoolean takeFullSnapshot = new AtomicBoolean(true);
    private static SessionReplayModeManager modeManager;

    // Buffer for queuing frames and touch data that arrive during harvest
    private static final AtomicBoolean isHarvesting = new AtomicBoolean(false);
    private final List<List<RRWebEvent>> frameBufferDuringHarvest =
            Collections.synchronizedList(new ArrayList<>());
    private final List<TouchTracker> touchBufferDuringHarvest =
            Collections.synchronizedList(new ArrayList<>());

    // Track if replay was explicitly paused via API
    private static final AtomicBoolean isPaused = new AtomicBoolean(false);

    // Sliding window for ERROR mode
    private static final long SLIDING_WINDOW_MS = 15000L; // 15 seconds
    private static ScheduledExecutorService slidingWindowExecutor;
    private static ScheduledFuture<?> slidingWindowTask;

    /**
     * Sets whether the next snapshot should be a full snapshot or incremental.
     * This can be called from any class to force a full snapshot on the next capture.
     *
     * @param shouldTakeFullSnapshot true to take a full snapshot, false for incremental
     */
    public static void setTakeFullSnapshot(boolean shouldTakeFullSnapshot) {
        takeFullSnapshot.set(shouldTakeFullSnapshot);
        log.debug("SessionReplay: takeFullSnapshot set to " + shouldTakeFullSnapshot);
    }

    /**
     * Gets the current takeFullSnapshot value.
     *
     * @return true if the next snapshot will be a full snapshot, false otherwise
     */
    public static boolean shouldTakeFullSnapshot() {
        return takeFullSnapshot.get();
    }

    /**
     * Initializes the SessionReplay system.
     * This method sets up the necessary callbacks, handlers, and starts recording.
     * Should be called from the application's onCreate method.
     *
     * @param application     The application instance
     * @param uiThreadHandler Handler for the UI thread
     * @param agentConfiguration The agent configuration
     * @param mode The recording mode (FULL, ERROR, or OFF)
     */
    public static void initialize(Application application, Handler uiThreadHandler, AgentConfiguration agentConfiguration, SessionReplayMode mode) {
        if (application == null) {
            Log.e("SessionReplay", "Cannot initialize with null application");
            return;
        }

        if (uiThreadHandler == null) {
            Log.e("SessionReplay", "Cannot initialize with null UI thread handler");
            return;
        }

        if (mode == null) {
            Log.e("SessionReplay", "Cannot initialize with null mode");
            return;
        }

        SessionReplay.application = application;
        SessionReplay.uiThreadHandler = uiThreadHandler;

        sessionReplayActivityLifecycleCallbacks = new SessionReplayActivityLifecycleCallbacks(instance,application);
        viewDrawInterceptor = new ViewDrawInterceptor(instance,agentConfiguration);
        processor = new SessionReplayProcessor();
        // Initialize file manager
        instance.fileManager = new SessionReplayFileManager(processor);
        SessionReplayFileManager.initialize(application);

        // Initialize the mode manager with the provided mode
        modeManager = new SessionReplayModeManager(agentConfiguration.getSessionReplayConfiguration());
        // Transition to the provided mode if not already in it
        modeManager.transitionTo(mode, "Initialization");

        // Register SessionReplay as event listener using composite pattern
        // This allows SessionReplay to always listen for NetworkRequestErrorEvent while also supporting user-provided listeners
        EventManager eventManager = AnalyticsControllerImpl.getInstance().getEventManager();
        EventListener currentListener = ((EventManagerImpl)eventManager).getListener();

        // Wrap current listener (or create new composite) to ensure SessionReplay is always included
        if (currentListener instanceof CompositeEventListener) {
            // If already a composite, set SessionReplay as the session replay listener
            ((CompositeEventListener) currentListener).setSessionReplayListener(instance);
        } else {
            // Create new composite with SessionReplay and current listener
            CompositeEventListener composite = new CompositeEventListener(instance);
            if (currentListener != eventManager) {
                // Only preserve current listener if it's not the default EventManager itself
                composite.setUserListener(currentListener);
            }
            eventManager.setEventListener(composite);
        }

        StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_SESSION_REPLAY_INIT);

        registerCallbacks();
        startRecording(mode);
        isFirstChunk = true;

        Log.d("SessionReplay", "Session replay initialized successfully with mode: " + mode);
    }

    /**
     * Deinitializes the SessionReplay system.
     * This method cleans up resources, unregisters callbacks, and stops recording.
     * Should be called when the application is being terminated or when session replay
     * functionality needs to be disabled.
     */
    public static void deInitialize() {
        if(application == null) {
            return;
        }
        unregisterCallbacks();
        stopRecording();

        // Clear any pending data
        instance.rawFrames.clear();
        instance.rrWebEvents.clear();
        instance.touchTrackers.clear();

        // Shutdown file manager
        SessionReplayFileManager.shutdown();

        log.debug("Session replay deinitialized");
    }

    @Override
    public void onHarvestStart() {
        // Only prepare for harvest when in FULL mode
        if (modeManager != null && modeManager.getCurrentMode() == SessionReplayMode.FULL) {
            // Mark that harvest is starting - pause frame writes to prevent race condition
            isHarvesting.set(true);
            log.debug("SessionReplay: Harvest started, pausing frame writes");
        }
    }

    @Override
    public void onHarvest() {
        // Only harvest when in FULL mode
        if (modeManager == null || modeManager.getCurrentMode() != SessionReplayMode.FULL) {
            log.debug("SessionReplay: Skipping harvest - not in FULL mode");
            return;
        }

        log.debug("Harvest started, reading frames and touch data from file as JSON array");

        // Read all events from the file as JsonArray
        JsonArray jsonArray = SessionReplayFileManager.readEventsAsJsonArray();

        if (jsonArray.isEmpty()) {
            Log.d("SessionReplay", "No events found in file to process.");
            return;
        }

        Map<String, Object> attributes = new HashMap<>();

        // Extract first timestamp from the first event in the JsonArray
        long firstTimestamp = System.currentTimeMillis();
        try {
            if (!jsonArray.isEmpty()) {
                JsonObject firstEvent = jsonArray.get(0).getAsJsonObject();
                if (firstEvent.has("timestamp")) {
                    long eventTimestamp = firstEvent.get("timestamp").getAsLong();
                    if (eventTimestamp > 0) {
                        firstTimestamp = eventTimestamp;
                    }
                }
            }
            log.debug("Using first event timestamp from file: " + firstTimestamp);
        } catch (Exception e) {
            log.warn("Failed to extract first event timestamp");
        }

        attributes.put(FIRST_TIMESTAMP, firstTimestamp);
        // Use current time as last timestamp to match with Mobile Session Event
        attributes.put(LAST_TIMESTAMP, System.currentTimeMillis());
        attributes.put(Constants.SessionReplay.IS_FIRST_CHUNK, isFirstChunk);
        SessionReplayReporter.reportSessionReplayData(json.getBytes(), attributes);

        // Convert JsonArray to JSON string and report
        String jsonArrayString = new Gson().toJson(jsonArray);
        SessionReplayReporter.reportSessionReplayData(jsonArrayString.getBytes(), attributes);

        // Clear file after successful harvest
        fileManager.clearWorkingFileWhileRunningSession();
        isFirstChunk = false;
        takeFullSnapshot.set(true);
    }


    /**
     * Extracts timestamp from different RRWebEvent types
     */
    private long getEventTimestamp(RRWebEvent event) {
        if (event instanceof com.newrelic.agent.android.sessionReplay.models.RRWebFullSnapshotEvent) {
            return ((com.newrelic.agent.android.sessionReplay.models.RRWebFullSnapshotEvent) event).timestamp;
        } else if (event instanceof com.newrelic.agent.android.sessionReplay.models.RRWebMetaEvent) {
            return ((com.newrelic.agent.android.sessionReplay.models.RRWebMetaEvent) event).timestamp;
        } else if (event instanceof com.newrelic.agent.android.sessionReplay.models.RRWebTouch) {
            return ((com.newrelic.agent.android.sessionReplay.models.RRWebTouch) event).timestamp;
        } else if (event instanceof com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.RRWebIncrementalEvent) {
            return ((com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.RRWebIncrementalEvent) event).timestamp;
        }
        // Default fallback - should not happen if all event types have timestamp
        return 0;
    }


    private static void registerCallbacks() {
        application.registerActivityLifecycleCallbacks(sessionReplayActivityLifecycleCallbacks);
    }

    private static void unregisterCallbacks() {
        application.unregisterActivityLifecycleCallbacks(sessionReplayActivityLifecycleCallbacks);
    }

    /**
     * Starts recording with the specified mode.
     *
     * @param mode The SessionReplayMode to use for recording (FULL or ERROR)
     */
    public static void startRecording(SessionReplayMode mode) {
        log.debug("Starting SessionReplay recording with mode: " + mode);
        Harvest.addHarvestListener(instance);
        takeFullSnapshot.set(true); // Force full snapshot when starting recording

        // Start sliding window timer if in ERROR mode
        if (mode == SessionReplayMode.ERROR) {
            startSlidingWindowTimer();
        }

        uiThreadHandler.post(() -> {
            View[] decorViews = Curtains.getRootViews().toArray(new View[0]);//WindowManagerSpy.windowManagerMViewsArray();

            // Check if decorViews is not empty before accessing
            if (decorViews == null || decorViews.length == 0) {
                Log.w("SessionReplay", "No root views available, skipping initial recording setup");
                return;
            }

            viewDrawInterceptor.Intercept(decorViews);
            sessionReplayActivityLifecycleCallbacks.setupTouchInterceptorForWindow(decorViews[0]);
        });

        Curtains.getOnRootViewsChangedListeners().add((view, added) -> {
            if (added) {
                viewDrawInterceptor.Intercept(new View[]{view});
            } else {
                viewDrawInterceptor.removeIntercept(new View[]{view});
            }
        });
    }


    public static void stopRecording() {
        Harvest.removeHarvestListener(instance);
        uiThreadHandler.post(() -> viewDrawInterceptor.stopIntercept());
    }

    @Override
    public void onFrameTaken(@NonNull SessionReplayFrame newFrame) {
        List<RRWebEvent> events = processor.processFrames(new ArrayList<>(List.of(newFrame)),takeFullSnapshot.get());

        // If harvest is in progress, buffer frames for later writing
        if (isHarvesting.get()) {
            log.debug("Frame received during harvest, buffering for later write");
            frameBufferDuringHarvest.add(events);
        } else {
            // Otherwise, write to file immediately
            if (fileManager != null) {
                fileManager.addFrameToFile(events);
            }
        }
        takeFullSnapshot.set(false);
    }

    @Override
    public void onTouchRecorded(TouchTracker touchTracker) {
        // If harvest is in progress, buffer touch data for later writing
        if (isHarvesting.get()) {
            log.debug("Touch data received during harvest, buffering for later write");
            touchBufferDuringHarvest.add(touchTracker);
        } else {
            // Otherwise, write to file immediately
            if (fileManager != null) {
                fileManager.addTouchToFile(touchTracker);
            }
        }
    }

    @Override
    public void applicationForegrounded(ApplicationStateEvent e) {

    }

    @Override
    public void applicationBackgrounded(ApplicationStateEvent e) {
        // delete the file if it exists
        log.debug("Deleting session replay working file on application backgrounded");
        if (fileManager != null) {
            fileManager.clearWorkingFile();
        }
    }

    /**
     * Called when an event is about to be added to the analytics buffer.
     * Detects MobileRequestError events and switches SESSION REPLAY mode from ERROR to FULL.
     *
     * @param analyticsEvent The event about to be added
     * @return true to add the event, false to ignore
     */
    @Override
    public boolean onEventAdded(AnalyticsEvent analyticsEvent) {
        // Check if this is a mobile request error event
        if (analyticsEvent instanceof NetworkRequestErrorEvent) {
            log.debug("SessionReplay: Mobile request error detected");
            switchModeOnError();
        }
        return true; // Always allow the event to be added
    }

    @Override
    public boolean onEventOverflow(AnalyticsEvent analyticsEvent) {
        return true;
    }

    @Override
    public boolean onEventEvicted(AnalyticsEvent analyticsEvent) {
        return true;
    }

    @Override
    public void onEventQueueSizeExceeded(int currentQueueSize) {
        // No-op
    }

    @Override
    public void onEventQueueTimeExceeded(int maxBufferTimeInSec) {
        // No-op
    }

    @Override
    public void onEventFlush() {
        // No-op
    }

    @Override
    public void onStart(EventManager eventManager) {
        // Register as a listener with the event manager
    }

    @Override
    public void onShutdown() {
        // No-op
    }

    @Override
    public void onHarvestComplete() {
        // Resume frame/touch writes - allow buffered data to be written to file
        isHarvesting.set(false);
        log.debug("SessionReplay: Harvest completed, resuming frame and touch writes");

        // Flush any frames that were buffered during harvest
        if (!frameBufferDuringHarvest.isEmpty()) {
            log.debug("Flushing " + frameBufferDuringHarvest.size() + " buffered frames to file after harvest");
            for (List<RRWebEvent> bufferedEvents : frameBufferDuringHarvest) {
                if (fileManager != null) {
                    fileManager.addFrameToFile(bufferedEvents);
                }
            }
            frameBufferDuringHarvest.clear();
        }

        // Flush any touch data that was buffered during harvest
        if (!touchBufferDuringHarvest.isEmpty()) {
            log.debug("Flushing " + touchBufferDuringHarvest.size() + " buffered touch data to file after harvest");
            for (TouchTracker bufferedTouch : touchBufferDuringHarvest) {
                if (fileManager != null) {
                    fileManager.addTouchToFile(bufferedTouch);
                }
            }
            touchBufferDuringHarvest.clear();
        }
    }

    /**
     * Starts the sliding window timer for ERROR mode.
     * Every 15 seconds, triggers a full snapshot and prunes old data.
     */
    private static void startSlidingWindowTimer() {
        if (slidingWindowExecutor == null) {
            slidingWindowExecutor = Executors.newScheduledThreadPool(1, r -> {
                Thread t = new Thread(r, "SessionReplaySlidingWindow");
                t.setDaemon(true);
                return t;
            });
        }

        if (slidingWindowTask != null) {
            slidingWindowTask.cancel(false);
        }

        slidingWindowTask = slidingWindowExecutor.scheduleWithFixedDelay(
                SessionReplay::handleSlidingWindowTick,
                SLIDING_WINDOW_MS,
                SLIDING_WINDOW_MS,
                TimeUnit.MILLISECONDS
        );
        log.debug("SessionReplay: Sliding window timer started (15 second intervals)");
    }

    /**
     * Stops the sliding window timer for ERROR mode.
     * Called when transitioning from ERROR to FULL mode.
     */
    private static void stopSlidingWindowTimer() {
        if (slidingWindowTask != null) {
            slidingWindowTask.cancel(false);
            slidingWindowTask = null;
            log.debug("SessionReplay: Sliding window timer stopped");
        }
    }

    /**
     * Handles the 15-second sliding window tick.
     * Forces a full snapshot and prunes old data.
     */
    private static void handleSlidingWindowTick() {
        try {
            log.debug("SessionReplay: Sliding window tick - forcing full snapshot and pruning old data");
            // Force full snapshot for next frame
            setTakeFullSnapshot(true);
            // Prune events older than 15 seconds
            SessionReplayFileManager.pruneEventsOlderThan(SLIDING_WINDOW_MS);
        } catch (Exception e) {
            log.error("Error during sliding window tick", e);
        }
    }

    /**
     * Switches session replay mode from ERROR to FULL when an error is detected.
     * This ensures that when an error occurs during buffering, we capture full session context.
     */
    private static void switchModeOnError() {
        if (modeManager != null) {
            boolean modeChanged = modeManager.transitionTo(SessionReplayMode.FULL, "ErrorDetected");
            if (modeChanged) {
                // Stop the sliding window timer when switching to FULL mode
                stopSlidingWindowTimer();
                // Force a full snapshot to ensure we have complete data from this point forward
                setTakeFullSnapshot(true);
            }
        }
    }

    /**
     * Called when a handled exception is about to be reported.
     * Public method that can be called from AgentDataController to notify about handled exceptions.
     * If session replay is in ERROR mode, switches to FULL mode.
     *
     * @param throwable The exception that is being handled
     */
    public static void onHandledExceptionDetected(Throwable throwable) {
        log.debug("SessionReplay: Handled exception detected - " + (throwable != null ? throwable.getClass().getSimpleName() : "null"));
        switchModeOnError();
    }

    /**
            log.debug("SessionReplay: Triggering immediate harvest on pauseReplay()");
}