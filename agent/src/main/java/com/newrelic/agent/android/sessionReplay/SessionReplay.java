package com.newrelic.agent.android.sessionReplay;

import android.app.Application;
import android.os.Handler;
import android.view.View;

import androidx.annotation.NonNull;

import com.newrelic.agent.android.sessionReplay.internal.Curtains;
import com.newrelic.agent.android.sessionReplay.internal.OnFrameTakenListener;
import com.newrelic.agent.android.sessionReplay.internal.RootViewsSpy;

import java.util.ArrayList;

public class SessionReplay implements OnFrameTakenListener {
    private Application application;
    private Handler uiThreadHandler;
    private SessionReplayActivityLifecycleCallbacks sessionReplayActivityLifecycleCallbacks;
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
    }
}
