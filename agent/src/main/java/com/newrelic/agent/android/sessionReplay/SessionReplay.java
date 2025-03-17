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
import com.newrelic.agent.android.sessionReplay.internal.Curtains;
import com.newrelic.agent.android.sessionReplay.internal.OnFrameTakenListener;
import com.newrelic.agent.android.sessionReplay.internal.RootViewsSpy;
import com.newrelic.agent.android.sessionReplay.models.RRWebEvent;
import com.newrelic.agent.android.sessionReplay.models.RRWebMetaEvent;

import java.util.ArrayList;

public class SessionReplay implements OnFrameTakenListener {
    private Application application;
    private Handler uiThreadHandler;
    private SessionReplayActivityLifecycleCallbacks sessionReplayActivityLifecycleCallbacks;
    private SessionReplayProcessor processor = new SessionReplayProcessor();
    private ViewDrawInterceptor viewDrawInterceptor;

    private ArrayList<SessionReplayFrame> rawFrames = new ArrayList<>();

    public SessionReplay(Application application, Handler uiThreadHandler) {
        this.application = application;
        this.uiThreadHandler = uiThreadHandler;

        this.sessionReplayActivityLifecycleCallbacks = new SessionReplayActivityLifecycleCallbacks();
        this.viewDrawInterceptor = new ViewDrawInterceptor(this);
    }

    public void Initialize() {
        registerCallbacks();
    }

    public void deInitialize() {
        unregisterCallbacks();
    }


    private void registerCallbacks() {
        application.registerActivityLifecycleCallbacks(sessionReplayActivityLifecycleCallbacks);
    }

    private void unregisterCallbacks() {
        application.unregisterActivityLifecycleCallbacks(sessionReplayActivityLifecycleCallbacks);
    }

    public void startRecording() {
        uiThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                View[] decorViews = Curtains.getRootViews().toArray(new View[0]);//WindowManagerSpy.windowManagerMViewsArray();
                viewDrawInterceptor.Intercept(decorViews);
            }
        });
    }

    public void stopRecording() {
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

        if(rawFrames.size() > 5) {
            WindowManager wm = (WindowManager) application.getApplicationContext().getSystemService(Context.WINDOW_SERVICE);
            Display display = wm.getDefaultDisplay();
            Point size = new Point();
            display.getSize(size);

            RRWebMetaEvent metaEvent = new RRWebMetaEvent(new RRWebMetaEvent.RRWebMetaEventData("https://newrelic.com", size.x, size.y), System.currentTimeMillis());
            ArrayList<RRWebEvent> rrWebEvents = new ArrayList<>();
            rrWebEvents.add(metaEvent);

            rrWebEvents.addAll(processor.processFrames(rawFrames));
            String json = new Gson().toJson(rrWebEvents);
            Log.d("Session Replay", "jsonPayload in RRWeb: " + json);
        }
    }
}
