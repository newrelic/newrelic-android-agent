package com.newrelic.agent.android.sessionReplay;

import static com.newrelic.agent.android.util.Constants.SessionReplay.FIRST_TIMESTAMP;
import static com.newrelic.agent.android.util.Constants.SessionReplay.LAST_TIMESTAMP;
import static com.newrelic.agent.android.util.Constants.SessionReplay.SESSION_ID;
import static com.newrelic.agent.android.util.Constants.SessionReplay.InstrumentationDetails;

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
import com.newrelic.agent.android.sessionReplay.internal.Curtains;
import com.newrelic.agent.android.sessionReplay.internal.OnFrameTakenListener;
import com.newrelic.agent.android.sessionReplay.models.RRWebEvent;
import com.newrelic.agent.android.stats.StatsEngine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SessionReplay implements OnFrameTakenListener, HarvestLifecycleAware, OnTouchRecordedListener, ApplicationStateListener {
    private static Application application;
    private static Handler uiThreadHandler;
    private static SessionReplayActivityLifecycleCallbacks sessionReplayActivityLifecycleCallbacks;
    private final SessionReplayProcessor processor = new SessionReplayProcessor();
    private static ViewDrawInterceptor viewDrawInterceptor;
    private final List<TouchTracker> touchTrackers = new ArrayList<>();
    private static final SessionReplay instance = new SessionReplay();
    private SessionReplayFileManager fileManager;
    protected static final AgentLog log = AgentLogManager.getAgentLog();
    private final ArrayList<SessionReplayFrame> rawFrames = new ArrayList<>();
    private final ArrayList<RRWebEvent> rrWebEvents = new ArrayList<>();

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

        sessionReplayActivityLifecycleCallbacks = new SessionReplayActivityLifecycleCallbacks(instance);
        viewDrawInterceptor = new ViewDrawInterceptor(instance,agentConfiguration);

        // Initialize file manager
        instance.fileManager = new SessionReplayFileManager(instance.processor);
        SessionReplayFileManager.initialize(application);
        StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_SESSION_REPLAY_INIT);

        registerCallbacks();
        startRecording();

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

        Log.d("SessionReplay","Processing"+rawFrames.size() +  "frame data");

        // Start timing frame processing
        long frameProcessingStart = System.currentTimeMillis();
        rrWebEvents.addAll(processor.processFrames(rawFrames));
        long frameProcessingTime = System.currentTimeMillis() - frameProcessingStart;

        Log.d("SessionReplay","Frame processing took: " + frameProcessingTime + "ms for " + rawFrames.size() + " frames");

        ArrayList<RRWebEvent> totalTouches = new ArrayList<>();
        for (TouchTracker touchTracker : touchTrackers) {
            totalTouches.addAll(touchTracker.processTouchData());
        }

        rrWebEvents.addAll(totalTouches);

        String json = new Gson().toJson(rrWebEvents);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(FIRST_TIMESTAMP, rawFrames.get(0).timestamp);
        attributes.put(LAST_TIMESTAMP, rawFrames.get(rawFrames.size() - 1).timestamp);
        SessionReplayReporter.reportSessionReplayData(json.getBytes(), attributes);

        rrWebEvents.clear();
        rawFrames.clear();
        touchTrackers.clear();
    }


    private static void registerCallbacks() {
        application.registerActivityLifecycleCallbacks(sessionReplayActivityLifecycleCallbacks);
    }

    private static void unregisterCallbacks() {
        application.unregisterActivityLifecycleCallbacks(sessionReplayActivityLifecycleCallbacks);
    }

    public static void startRecording() {
        Harvest.addHarvestListener(instance);
        uiThreadHandler.post(() -> {
            View[] decorViews = Curtains.getRootViews().toArray(new View[0]);//WindowManagerSpy.windowManagerMViewsArray();
            viewDrawInterceptor.Intercept(decorViews);
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
        if (fileManager != null) {
            fileManager.addFrameToFile(newFrame);
        }
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