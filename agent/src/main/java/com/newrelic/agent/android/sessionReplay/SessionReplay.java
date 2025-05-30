package com.newrelic.agent.android.sessionReplay;

import android.app.Application;
import android.content.Context;
import android.graphics.Point;
import android.os.Handler;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.newrelic.agent.android.harvest.Harvest;
import com.newrelic.agent.android.harvest.HarvestLifecycleAware;
import com.newrelic.agent.android.logging.LogReporter;
import com.newrelic.agent.android.sessionReplay.internal.Curtains;
import com.newrelic.agent.android.sessionReplay.internal.OnFrameTakenListener;
import com.newrelic.agent.android.sessionReplay.internal.OnRootViewsChangedListener;
import com.newrelic.agent.android.sessionReplay.models.RRWebEvent;
import com.newrelic.agent.android.sessionReplay.models.RRWebMetaEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class SessionReplay implements OnFrameTakenListener, HarvestLifecycleAware, OnTouchRecordedListener {
    private Application application;
    private Handler uiThreadHandler;
    private SessionReplayActivityLifecycleCallbacks sessionReplayActivityLifecycleCallbacks;
    private SessionReplayProcessor processor = new SessionReplayProcessor();
    private ViewDrawInterceptor viewDrawInterceptor;
    private List<TouchTracker> touchTrackers = new ArrayList<>();
    static final AtomicReference<LogReporter> instance = new AtomicReference<>(null);

    private ArrayList<SessionReplayFrame> rawFrames = new ArrayList<>();

    public SessionReplay(Application application, Handler uiThreadHandler) {
        this.application = application;
        this.uiThreadHandler = uiThreadHandler;

        this.sessionReplayActivityLifecycleCallbacks = new SessionReplayActivityLifecycleCallbacks(this);
        this.viewDrawInterceptor = new ViewDrawInterceptor(this,this);

    }

    public void Initialize() {
        registerCallbacks();
    }

    public void deInitialize() {
        unregisterCallbacks();
    }

    @Override
    public void onHarvestStart() {
        // No-op
    }

    @Override
    public void onHarvestStop() {

    }

    @Override
    public void onHarvest() {

        if (rawFrames.isEmpty() && touchTrackers.isEmpty()) {
            Log.d("SessionReplay", "No frames or touch data to process.");
            return;
        }
        // No-op
        WindowManager wm = (WindowManager) application.getApplicationContext().getSystemService(Context.WINDOW_SERVICE);
        float density = application.getApplicationContext().getResources().getDisplayMetrics().density;

        Display display = wm.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);

        RRWebMetaEvent metaEvent = new RRWebMetaEvent(new RRWebMetaEvent.RRWebMetaEventData("https://newrelic.com", (int) (size.x/density), (int) (size.y/density)), System.currentTimeMillis());        ArrayList<RRWebEvent> rrWebEvents = new ArrayList<>();
        rrWebEvents.add(metaEvent);


        // Create a copy of rawFrames
        List<SessionReplayFrame> rawFramesCopy;
        synchronized (rawFrames) {
            rawFramesCopy = new ArrayList<>(rawFrames);
            rawFrames.clear();
        }

        rrWebEvents.addAll(processor.processFrames(rawFramesCopy));

        // Create a copy of touchTrackers and clear the original
        List<TouchTracker> touchTrackersCopy;
        synchronized (touchTrackers) {
            touchTrackersCopy = new ArrayList<>(touchTrackers);
            touchTrackers.clear();
        }

        ArrayList<RRWebEvent> totalTouches = new ArrayList<>();
        for(TouchTracker touchTracker : touchTrackersCopy) {
            totalTouches.addAll(touchTracker.processTouchData());
        }

        rrWebEvents.addAll(totalTouches);

        String json = new Gson().toJson(rrWebEvents);
        SessionReplayReporter.reportSessionReplayData(json.getBytes());


        touchTrackersCopy.clear();
        rawFramesCopy.clear();
    }


    private void registerCallbacks() {
        application.registerActivityLifecycleCallbacks(sessionReplayActivityLifecycleCallbacks);
    }

    private void unregisterCallbacks() {
        application.unregisterActivityLifecycleCallbacks(sessionReplayActivityLifecycleCallbacks);
    }

    public void startRecording() {
        Harvest.addHarvestListener(this);
        uiThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                View[] decorViews = Curtains.getRootViews().toArray(new View[0]);//WindowManagerSpy.windowManagerMViewsArray();
                viewDrawInterceptor.Intercept(decorViews);
            }
        });

        Curtains.getOnRootViewsChangedListeners().add(new OnRootViewsChangedListener() {
            @Override
            public void onRootViewsChanged(View view, boolean added) {
                if (added) {
                    viewDrawInterceptor.Intercept(new View[]{view});
                } else {
                    viewDrawInterceptor.removeIntercept(new View[]{view});
                }
            }
        });
    }



    public void stopRecording() {
        Harvest.removeHarvestListener(this);
        uiThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                viewDrawInterceptor.stopIntercept();
            }
        });
    }

    @Override
    public void onFrameTaken(@NonNull SessionReplayFrame newFrame) {
        rawFrames.add(newFrame);
    }

    @Override
    public void onTouchRecorded(TouchTracker touchTracker) {
        touchTrackers.add(touchTracker);
    }
}
