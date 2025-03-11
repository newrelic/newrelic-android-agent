package com.newrelic.agent.android.sessionReplay;

import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;

import com.newrelic.agent.android.sessionReplay.internal.OnFrameTakenListener;

import java.util.Map;
import java.util.WeakHashMap;

public class ViewDrawInterceptor {
    private WeakHashMap<View, ViewTreeObserver.OnDrawListener> decorViewListeners = new WeakHashMap<>();
    SessionReplayCapture capture = new SessionReplayCapture();
    private OnFrameTakenListener listener;

    public ViewDrawInterceptor(OnFrameTakenListener listener) {
        this.listener = listener;
    }

    public void Intercept(View[] decorViews) {
        stopInterceptAndRemove(decorViews);
        ViewTreeObserver.OnDrawListener listener = new ViewTreeObserver.OnDrawListener() {
            @Override
            public void onDraw() {
                // Start walking the view tree
                SessionReplayFrame frame = new SessionReplayFrame(capture.capture(decorViews[0]), System.currentTimeMillis() * 1000);

                // Create a SessionReplayFrame, then add it to a thing to wait for processing
                ViewDrawInterceptor.this.listener.onFrameTaken(frame);
            }
        };

        for(View decorView : decorViews) {
            ViewTreeObserver viewTreeObserver = decorView.getViewTreeObserver();
            if(viewTreeObserver != null && viewTreeObserver.isAlive()) {
                try{
                    viewTreeObserver.addOnDrawListener(listener);
                    decorViewListeners.put(decorView, listener);
                } catch (IllegalStateException e) {
                    Log.e("ViewDrawInterceptor", "Unable to add onDrawListener!");
                }
            }
        }
    }

    public void stopIntercept() {
        for(Map.Entry<View, ViewTreeObserver.OnDrawListener> entry : decorViewListeners.entrySet()) {
            safeObserverRemoval(entry.getKey(), entry.getValue());
        }
    }

    private void stopInterceptAndRemove(View[] decorViews) {
        for(View view : decorViews) {
            ViewTreeObserver.OnDrawListener listener = decorViewListeners.get(view);
            safeObserverRemoval(view, listener);
        }
        decorViewListeners.clear();
    }

    private void safeObserverRemoval(View view, ViewTreeObserver.OnDrawListener listener) {
        if(view.getViewTreeObserver().isAlive()) {
            try {
                view.getViewTreeObserver().removeOnDrawListener(listener);
            } catch(IllegalStateException e) {
                Log.e("ViewDrawInterceptor", "Unable to remove onDrawListener!");
            }
        }
    }
}
