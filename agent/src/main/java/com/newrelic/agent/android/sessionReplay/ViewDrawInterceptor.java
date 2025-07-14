package com.newrelic.agent.android.sessionReplay;

import android.content.Context;
import android.graphics.Point;
import android.os.Build;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.WindowMetrics;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.sessionReplay.internal.OnFrameTakenListener;

import java.util.Map;
import java.util.WeakHashMap;

public class ViewDrawInterceptor  {
    private final WeakHashMap<View, ViewTreeObserver.OnDrawListener> decorViewListeners = new WeakHashMap<>();
    SessionReplayCapture capture = new SessionReplayCapture();
    private final OnFrameTakenListener listener;
    private static final long CAPTURE_INTERVAL = 1000;
    private long lastCaptureTime = 0;
    private AgentConfiguration agentConfiguration;
    private Debouncer captureDebouncer;
    private static final long DEBOUNCE_DELAY = 1000; // ~60 FPS (16ms per frame)

    public ViewDrawInterceptor(OnFrameTakenListener listener, AgentConfiguration agentConfiguration) {
        this.listener = listener;
        this.agentConfiguration = agentConfiguration;
        this.captureDebouncer = new Debouncer(DEBOUNCE_DELAY);
    }


    public void Intercept(View[] decorViews) {
        stopInterceptAndRemove(decorViews);
        ViewTreeObserver.OnDrawListener listener = () -> {
            long currentTime = System.currentTimeMillis();
            Log.d("ViewDrawInterceptor", "onDraw() called" + " at " + currentTime + " lastCaptureTime: " + lastCaptureTime + " interval: " + CAPTURE_INTERVAL);

            // Use debouncer to limit capture frequency
                if (currentTime - lastCaptureTime >= CAPTURE_INTERVAL) {
                    Log.d("ViewDrawInterceptor", "Capturing frame");
                    Context context = decorViews[0].getContext().getApplicationContext();
                    float density = context.getResources().getDisplayMetrics().density;

                    // Get screen dimensions
                    Point screenSize = getScreenDimensions(context);
                    int width = (int) (screenSize.x/density);
                    int height = (int) (screenSize.y/density);

                    // Start walking the view tree
                    SessionReplayFrame frame = new SessionReplayFrame(capture.capture(decorViews[decorViews.length -1],agentConfiguration), System.currentTimeMillis(), width, height);

                    // Create a SessionReplayFrame, then add it to a thing to wait for processing
                    ViewDrawInterceptor.this.listener.onFrameTaken(frame);
                    // Update the last capture time
                    lastCaptureTime = System.currentTimeMillis(); // Use current time when actually executing
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
        captureDebouncer.cancel(); // Cancel any pending captures
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

    public void removeIntercept(View[] views) {
        stopInterceptAndRemove(views);
    }

    /**
     * Gets the screen dimensions using API level appropriate methods
     * @param context The application context
     * @return Point containing screen width (x) and height (y)
     */
    private Point getScreenDimensions(Context context) {
        int width, height;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // For API 30 (Android 11) and above
            WindowMetrics windowMetrics = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE))
                    .getCurrentWindowMetrics();
            width = windowMetrics.getBounds().width();
            height = windowMetrics.getBounds().height();
        } else {
            // For API 29 and below
            WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            @SuppressWarnings("deprecation")
            Display display = wm.getDefaultDisplay();

            // Use getRealSize instead of getSize to get the actual full screen size including system decorations
            Point size = new Point();
            display.getRealSize(size);

            width = size.x;
            height = size.y;
        }
        return new Point(width, height);
    }


}