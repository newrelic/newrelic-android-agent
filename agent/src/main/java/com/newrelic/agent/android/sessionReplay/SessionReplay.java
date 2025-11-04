package com.newrelic.agent.android.sessionReplay;

import static com.newrelic.agent.android.util.Constants.SessionReplay.FIRST_TIMESTAMP;
import static com.newrelic.agent.android.util.Constants.SessionReplay.LAST_TIMESTAMP;

import android.app.Application;
import android.os.Handler;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.newrelic.agent.android.AgentConfiguration;
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
     */
    public static void initialize(Application application, Handler uiThreadHandler, AgentConfiguration agentConfiguration) {
        if (application == null) {
            Log.e("SessionReplay", "Cannot initialize with null application");
            return;
        }

        if (uiThreadHandler == null) {
            Log.e("SessionReplay", "Cannot initialize with null UI thread handler");
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
        StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_SESSION_REPLAY_INIT);

        registerCallbacks();
        startRecording();
        isFirstChunk = true;

        Log.d("SessionReplay", "Session replay initialized successfully");
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
        // No-op
    }

    @Override
    public void onHarvest() {
        log.debug("Harvest started, processing frames and touch data");
        if (rawFrames.isEmpty() && touchTrackers.isEmpty()) {
            Log.d("SessionReplay", "No frames or touch data to process.");
            return;
        }

        ArrayList<RRWebEvent> totalTouches = new ArrayList<>();
        for (TouchTracker touchTracker : touchTrackers) {
            totalTouches.addAll(touchTracker.processTouchData());
        }

        rrWebEvents.addAll(totalTouches);

        rrWebEvents.sort((event1, event2) -> Long.compare(getEventTimestamp(event1), getEventTimestamp(event2)));

        String json = new Gson().toJson(rrWebEvents);
        Map<String, Object> attributes = new HashMap<>();

        // Safely get first timestamp - use first frame timestamp if available, otherwise use current time
        if (!rawFrames.isEmpty()) {
            attributes.put(FIRST_TIMESTAMP, rawFrames.get(0).timestamp);
        } else {
            // If we only have touch data without frames, use current time as first timestamp
            attributes.put(FIRST_TIMESTAMP, System.currentTimeMillis());
            Log.w("SessionReplay", "No frames available, using current time as FIRST_TIMESTAMP");
        }

        // Use current time as last timestamp instead of last frame time to match with Mobile Session Event
        attributes.put(LAST_TIMESTAMP, System.currentTimeMillis());
        attributes.put(Constants.SessionReplay.IS_FIRST_CHUNK, isFirstChunk);
        SessionReplayReporter.reportSessionReplayData(json.getBytes(), attributes);

        rrWebEvents.clear();
        rawFrames.clear();
        touchTrackers.clear();
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

    public static void startRecording() {
        Harvest.addHarvestListener(instance);
        takeFullSnapshot.set(true); // Force full snapshot when starting recording
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
        rawFrames.add(newFrame);
        List<RRWebEvent> events = processor.processFrames(new ArrayList<>(List.of(newFrame)),takeFullSnapshot.get());
        rrWebEvents.addAll(events);
        if (fileManager != null) {
            fileManager.addFrameToFile(events);
        }
        takeFullSnapshot.set(false);
    }

    @Override
    public void onTouchRecorded(TouchTracker touchTracker) {
        touchTrackers.add(touchTracker);
        if (fileManager != null) {
            fileManager.addTouchToFile(touchTracker);
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
}