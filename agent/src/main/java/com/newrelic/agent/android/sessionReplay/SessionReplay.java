package com.newrelic.agent.android.sessionReplay;

import android.app.Application;
import android.os.Handler;
import android.view.View;

import com.newrelic.agent.android.sessionReplay.internal.Curtains;
import com.newrelic.agent.android.sessionReplay.internal.RootViewsSpy;
import com.newrelic.agent.android.sessionReplay.internal.WindowManagerSpy;

public class SessionReplay {
    private Application application;
    private Handler uiThreadHandler;
    private SessionReplayActivityLifecycleCallbacks sessionReplayActivityLifecycleCallbacks;
    private ViewDrawInterceptor viewDrawInterceptor = new ViewDrawInterceptor();
    private RootViewsSpy spy;

    public SessionReplay(Application application, Handler uiThreadHandler) {
        this.application = application;
        this.uiThreadHandler = uiThreadHandler;

        this.spy = RootViewsSpy.install();

        this.sessionReplayActivityLifecycleCallbacks = new SessionReplayActivityLifecycleCallbacks();
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
}
