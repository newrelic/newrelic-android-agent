package com.newrelic.agent.android.sessionReplay;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.compose.ui.platform.AndroidComposeView;
import androidx.compose.ui.platform.ComposeView;
import androidx.compose.ui.semantics.SemanticsNode;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.sessionReplay.models.RecordedTouchData;

import curtains.Curtains;
import curtains.DispatchState;
import curtains.OnTouchEventListener;
import curtains.internal.WindowCallbackWrapper;
import kotlin.jvm.functions.Function1;
import com.newrelic.agent.android.util.ComposeChecker;

public class SessionReplayActivityLifecycleCallbacks implements Application.ActivityLifecycleCallbacks {
    private static final String TAG = "SessionReplayActivityLifecycleCallbacks";
    private final float density;
    private int currentTouchId = -1;
    private TouchTracker currentTouchTracker = null;
    SessionReplayConfiguration sessionReplayConfiguration;
    private final OnTouchRecordedListener onTouchRecordedListener;
    private final ViewTouchHandler viewTouchHandler;
    private final SemanticsNodeTouchHandler semanticsNodeTouchHandler;

    public SessionReplayActivityLifecycleCallbacks(OnTouchRecordedListener onTouchRecordedListener,Application application) {
        this.onTouchRecordedListener = onTouchRecordedListener;
        AgentConfiguration agentConfiguration = AgentConfiguration.getInstance();
        sessionReplayConfiguration = agentConfiguration.getSessionReplayConfiguration();
        density = application.getApplicationContext().getResources().getDisplayMetrics().density;
        this.viewTouchHandler = new ViewTouchHandler(sessionReplayConfiguration);
        this.semanticsNodeTouchHandler = new SemanticsNodeTouchHandler(sessionReplayConfiguration);
    }


    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle bundle) {

    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {

    }

    @Override
    public void onActivityPrePaused(@NonNull Activity activity) {
        Application.ActivityLifecycleCallbacks.super.onActivityPrePaused(activity);
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        Log.d(TAG, "onActivityResumed: " + activity.getClass().getSimpleName());

        Curtains.getOnRootViewsChangedListeners().add((view, added) -> setupTouchInterceptorForWindow(view));
    }

    public void setupTouchInterceptorForWindow(View view) {
        boolean shouldMaskTouches = sessionReplayConfiguration.isMaskAllUserTouches();
        Log.d(TAG, "Root View Changed in Listener");
        Windows.WindowType windowType = Windows.getWindowType(view);
        if (windowType == Windows.WindowType.POPUP_WINDOW) {
            return;
        }
        Window window = Windows.getPhoneWindowForView(view);
        if (window == null) {
            Log.d(TAG, "Window is null for view: " + view.getClass().getSimpleName());
            return;
        }

        OnTouchEventListener touchEventInterceptor = new OnTouchEventListener() {

            @NonNull
            @Override
            public DispatchState intercept(@NonNull MotionEvent motionEvent, @NonNull Function1<? super MotionEvent, ? extends DispatchState> function1) {

                onTouchEvent(motionEvent);
                // Call the dispatch function and return its result
                return function1.invoke(motionEvent);
            }

            @Override
            public void onTouchEvent(@NonNull MotionEvent motionEvent) {
            long timestamp = System.currentTimeMillis();
            MotionEvent.PointerCoords pointerCoords = new MotionEvent.PointerCoords();
            motionEvent.getPointerCoords(0, pointerCoords);
            RecordedTouchData moveTouch;
            if (motionEvent.getActionMasked() == MotionEvent.ACTION_DOWN) {
                Object containingView = viewTouchHandler.findViewAtCoords(view, (int)pointerCoords.x, (int)pointerCoords.y);
                int containingTouchViewId = -1;

                if(containingView instanceof View){
                    View foundView = (View) containingView;
                    ViewParent parent = foundView.getParent();

                    // Check if this is a Compose view that needs SemanticsNode handling
                    if(parent != null && ComposeChecker.isComposeUsed(foundView.getContext()) &&
                       (parent instanceof AndroidComposeView || parent instanceof ComposeView)) {
                        Object semanticsNode = semanticsNodeTouchHandler.getComposeSemanticsNode(foundView, (int)pointerCoords.x, (int)pointerCoords.y);
                        if (semanticsNode instanceof SemanticsNode) {
                            containingTouchViewId = semanticsNodeTouchHandler.getSemanticsNodeStableId((SemanticsNode) semanticsNode);
                        }
                    } else {
                        View maskView = viewTouchHandler.getMaskedViewIfNeeded(foundView, shouldMaskTouches);
                        containingTouchViewId = viewTouchHandler.getViewStableId(maskView);
                    }
                }
                if (currentTouchTracker == null && containingTouchViewId != -1) {
                    Log.d(TAG, "Adding Start Event");
                    currentTouchId = containingTouchViewId;
                    moveTouch = new RecordedTouchData(0, currentTouchId, getPixel(pointerCoords.x), getPixel(pointerCoords.y), timestamp);
                    currentTouchTracker = new TouchTracker(moveTouch);
                } else if (containingTouchViewId == -1) {
                    Log.d(TAG, "TOUCH LOST: Unable to find originating View.");
                }
            } else if (motionEvent.getActionMasked() == MotionEvent.ACTION_MOVE) {
                if (SessionReplayActivityLifecycleCallbacks.this.currentTouchTracker != null) {
                    Log.d(TAG, "Adding Move Event");
                    moveTouch = new RecordedTouchData(2, currentTouchId, getPixel(pointerCoords.x), SessionReplayActivityLifecycleCallbacks.this.getPixel(pointerCoords.y), timestamp);
                    SessionReplayActivityLifecycleCallbacks.this.currentTouchTracker.addMoveTouch(moveTouch);
                }
            } else if (motionEvent.getActionMasked() == MotionEvent.ACTION_UP && currentTouchTracker != null) {
                Log.d(TAG, "Adding End Event");
                moveTouch = new RecordedTouchData(1, currentTouchId, getPixel(pointerCoords.x), getPixel(pointerCoords.y), timestamp);
                currentTouchTracker.addEndTouch(moveTouch);
                SessionReplayActivityLifecycleCallbacks.this.onTouchRecordedListener.onTouchRecorded(currentTouchTracker);
                currentTouchTracker = null;
                currentTouchId = -1;
            }
            }
        };

        WindowCallbackWrapper.Companion.getListeners(window).getTouchEventInterceptors().add(touchEventInterceptor);
    }


    private float getPixel(float pixel){
        return  (pixel /density);
    }


    @Override
    public void onActivityPaused(@NonNull Activity activity) {

    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {

    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle bundle) {

    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {

    }

}

