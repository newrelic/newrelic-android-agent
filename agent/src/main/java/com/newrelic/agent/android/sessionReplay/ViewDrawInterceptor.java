package com.newrelic.agent.android.sessionReplay;

import android.graphics.Rect;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;

import com.newrelic.agent.android.sessionReplay.internal.Curtains;
import com.newrelic.agent.android.sessionReplay.internal.OnFrameTakenListener;
import com.newrelic.agent.android.sessionReplay.internal.OnRootViewsChangedListener;
import com.newrelic.agent.android.sessionReplay.internal.OnTouchEventListener;
import com.newrelic.agent.android.sessionReplay.internal.WindowCallbackWrapper;
import com.newrelic.agent.android.sessionReplay.models.RecordedTouchData;

import java.util.Map;
import java.util.WeakHashMap;

public class ViewDrawInterceptor {
    private WeakHashMap<View, ViewTreeObserver.OnDrawListener> decorViewListeners = new WeakHashMap<>();
    SessionReplayCapture capture = new SessionReplayCapture();
    private OnFrameTakenListener listener;
    private OnTouchRecordeListener onTouchRecordeListener;
    private int currentTouchId = -1;
    private TouchTracker currentTouchTracker = null;
    float density;
    public ViewDrawInterceptor(OnFrameTakenListener listener,OnTouchRecordeListener onTouchRecordeListener) {
        this.listener = listener;
        this.onTouchRecordeListener = onTouchRecordeListener;
    }


    public void Intercept(View[] decorViews) {
        stopInterceptAndRemove(decorViews);
        ViewTreeObserver.OnDrawListener listener = new ViewTreeObserver.OnDrawListener() {
            @Override
            public void onDraw() {
                // Start walking the view tree
                SessionReplayFrame frame = new SessionReplayFrame(capture.capture(decorViews[0]), System.currentTimeMillis());

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

    private View findViewAtCoords(View parent, int x, int y) {
        Rect hitRect = new Rect();
        parent.getHitRect(hitRect);

        if(!hitRect.contains(x, y)) {
            return null;
        } else if (parent instanceof ViewGroup) {
            for(int i = 0; i < ((ViewGroup) parent).getChildCount(); i++) {
                View childView = ((ViewGroup) parent).getChildAt(i);
                Rect bounds = new Rect();
                childView.getHitRect(bounds);
                if(bounds.contains(x, y)) {
                    if(childView instanceof ViewGroup) {
                        View foundView = findViewAtCoords(childView, x, y);
                        if(foundView != null && foundView.isShown()) {
                            return foundView;
                        }
                    } else {
                        return childView;
                    }
                }
            }
        }

        return null;
    }

    private int getStableId(View view) {
        int keyCode = "NewRelicSessionReplayViewId".hashCode();
        Integer idValue = null;
        idValue = (Integer) view.getTag(keyCode);
        if(idValue == null) {
            idValue = com.newrelic.agent.android.sessionReplay.NewRelicIdGenerator.generateId();
            view.setTag(keyCode, idValue);
        }
        int id = idValue;
        return id;
    }
    private float getPixel(float pixel){
        return  (pixel /density);
    }
}
