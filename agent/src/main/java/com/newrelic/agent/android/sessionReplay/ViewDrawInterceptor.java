package com.newrelic.agent.android.sessionReplay;

import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;

import java.util.Map;
import java.util.WeakHashMap;

public class ViewDrawInterceptor {
    private WeakHashMap<View, ViewTreeObserver.OnDrawListener> decorViewListeners = new WeakHashMap<>();
    SessionReplayCapture capture = new SessionReplayCapture();

    public void Intercept(View[] decorViews) {
        stopInterceptAndRemove(decorViews);
        ViewTreeObserver.OnDrawListener listener = new ViewTreeObserver.OnDrawListener() {
            @Override
            public void onDraw() {
                // Start walking the view tree
                capture.capture(decorViews[0]);
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
