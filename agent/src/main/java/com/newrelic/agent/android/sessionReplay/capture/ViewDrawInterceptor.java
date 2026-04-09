package com.newrelic.agent.android.sessionReplay.capture;

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

import curtains.Curtains;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

                    // Get ALL current root views (windows) at capture time.
                    // Using Curtains.getRootViews() instead of the captured decorViews
                    // parameter ensures we see every visible window, not just the ones
                    // that were present when this listener was registered.
                    List<View> allRootViews = Curtains.getRootViews();
                    if (allRootViews == null || allRootViews.isEmpty()) {
                        Log.w("ViewDrawInterceptor", "No root views available, skipping frame capture");
                        return;
                    }

                    View firstView = allRootViews.get(0);
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

                    // Capture ALL visible windows, layered by z-order.
                    // Curtains maintains root views in rendering order:
                    // - Index 0: Base activity window (bottom layer)
                    // - Index 1..n: Dialogs, popups, toasts (higher layers)
                    //
                    // We capture all windows so that when a dialog is open,
                    // both the background activity and the dialog are included
                    // in a single replay snapshot.
                    List<SessionReplayViewThingyInterface> windowRoots = new ArrayList<>();
                    boolean hasWindowWithDimBehind = false;
                    for (View dv : allRootViews) {
                        if (dv != null && dv.getVisibility() == View.VISIBLE) {
                            SessionReplayViewThingyInterface windowRoot = capture.capture(dv, agentConfiguration);
                            if (windowRoot != null) {
                                windowRoots.add(windowRoot);
                                // Check if this secondary window dims the background
                                if (windowRoots.size() > 1
                                        && dv.getLayoutParams() instanceof WindowManager.LayoutParams) {
                                    int flags = ((WindowManager.LayoutParams) dv.getLayoutParams()).flags;
                                    if ((flags & WindowManager.LayoutParams.FLAG_DIM_BEHIND) != 0) {
                                        hasWindowWithDimBehind = true;
                                    }
                                }
                            }
                        }
                    }

                    if (windowRoots.isEmpty()) {
                        Log.w("ViewDrawInterceptor", "No visible windows to capture, skipping frame");
                        return;
                    }

                    SessionReplayFrame frame = new SessionReplayFrame(
                        windowRoots,
                        hasWindowWithDimBehind,
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