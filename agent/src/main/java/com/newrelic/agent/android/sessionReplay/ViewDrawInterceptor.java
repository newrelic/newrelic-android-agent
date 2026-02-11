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

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public class ViewDrawInterceptor  {
    private final Map<View, ViewTreeObserver.OnDrawListener> decorViewListeners =
            Collections.synchronizedMap(new WeakHashMap<>());
    SessionReplayCapture capture;
    private final OnFrameTakenListener listener;
    private static final long CAPTURE_INTERVAL = 1000;
    private long lastCaptureTime = 0;
    private final AgentConfiguration agentConfiguration;
    private final Debouncer captureDebouncer;
    private static final long DEBOUNCE_DELAY = 1000; // ~60 FPS (16ms per frame)

    public ViewDrawInterceptor(OnFrameTakenListener listener, AgentConfiguration agentConfiguration) {
        this.listener = listener;
        this.agentConfiguration = agentConfiguration;
        capture = new SessionReplayCapture(agentConfiguration);
        this.captureDebouncer = new Debouncer(true);
    }


    public void Intercept(View[] decorViews) {
        for(View decorView : decorViews) {
            // Remove existing listener if present
            ViewTreeObserver.OnDrawListener existingListener = decorViewListeners.remove(decorView);
            if (existingListener != null) {
                safeObserverRemoval(decorView, existingListener);
            }

            // Create NEW unique listener for THIS view
            ViewTreeObserver.OnDrawListener listener = () -> {
                long currentTime = System.currentTimeMillis();
                Log.d("ViewDrawInterceptor", "onDraw() called" + " at " + currentTime + " lastCaptureTime: " + lastCaptureTime + " interval: " + CAPTURE_INTERVAL);

                captureDebouncer.debounce(() -> {
                    // Use debouncer to limit capture frequency
                    lastCaptureTime = currentTime;
                    Log.d("ViewDrawInterceptor", "Capturing frame");

                    // Safely get context - the view may have been detached by the time this runs
                    if (decorViews == null || decorViews.length == 0) {
                        Log.w("ViewDrawInterceptor", "decorViews is null or empty, skipping frame capture");
                        return;
                    }

                    View firstView = decorViews[0];
                    if (firstView == null) {
                        Log.w("ViewDrawInterceptor", "First decor view is null, skipping frame capture");
                        return;
                    }

                    Context viewContext = firstView.getContext();
                    if (viewContext == null) {
                        Log.w("ViewDrawInterceptor", "View context is null (view may be detached), skipping frame capture");
                        return;
                    }

                    Context context = viewContext.getApplicationContext();
                    if (context == null) {
                        Log.w("ViewDrawInterceptor", "Application context is null, skipping frame capture");
                        return;
                    }

                    float density = context.getResources().getDisplayMetrics().density;

                    // Get screen dimensions
                    Point screenSize = getScreenDimensions(context);
                    int width = (int) (screenSize.x / density);
                    int height = (int) (screenSize.y / density);

                    // Start timing the frame creation
                    long frameCreationStart = System.currentTimeMillis();


                    // Capture the LAST decorView (most recently added window)
                    //
                    // Why the last element?
                    // - Android maintains decorViews in a stack ordered by z-index (rendering order)
                    // - Index 0: Base activity window (bottom layer)
                    // - Index 1..n-1: Dialogs, popups, toasts (middle layers)
                    // - Index n-1: Most recent window (top layer) - THIS IS WHAT USER SEES
                    //
                    // Examples:
                    // - MainActivity (index 0) + AlertDialog (index 1) → We capture index 1 (the visible dialog)
                    // - Activity + Popup Menu → We capture the popup (most recent interaction)
                    // - Activity only → We capture index 0 (the only window)
                    //
                    // This approach captures what the user is currently interacting with, which is
                    // always the topmost (most recent) window in the decorViews array.
                    //
                    // Alternative considered: Merge all decorViews into a composite snapshot
                    // Reason not implemented: Performance overhead + complexity of merging z-indexed views
                    View topMostView = decorViews[decorViews.length - 1];
                    SessionReplayFrame frame = new SessionReplayFrame(
                        capture.capture(topMostView, agentConfiguration),
                        System.currentTimeMillis(),
                        width,
                        height
                    );

                    // Calculate frame creation time
                    long frameCreationTime = System.currentTimeMillis() - frameCreationStart;
                    Log.d("ViewDrawInterceptor", "Frame creation took: " + frameCreationTime + "ms");
                    // Create a SessionReplayFrame, then add it to a thing to wait for processing
                    ViewDrawInterceptor.this.listener.onFrameTaken(frame);
                });
            };

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
            ViewTreeObserver.OnDrawListener listener = decorViewListeners.remove(view);
            if (listener != null) {
                safeObserverRemoval(view, listener);
            }
        }
    }

    private void safeObserverRemoval(View view, ViewTreeObserver.OnDrawListener listener) {
        if (listener == null) {
            return;
        }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // For API 30 (Android 11) and above
            WindowMetrics windowMetrics = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE))
                    .getCurrentWindowMetrics();
            return new Point(windowMetrics.getBounds().width(), windowMetrics.getBounds().height());
        } else {
            // For API 24-29
            return getScreenDimensionsLegacy(context);
        }
    }

    /**
     * Gets screen dimensions using legacy Display API for API 24-29
     */
    @SuppressWarnings("deprecation")
    private Point getScreenDimensionsLegacy(Context context) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        
        Point size = new Point();
        display.getRealSize(size); // Safe to use since minSdk is 24 (getRealSize available since API 17)
        
        return size;
    }


}