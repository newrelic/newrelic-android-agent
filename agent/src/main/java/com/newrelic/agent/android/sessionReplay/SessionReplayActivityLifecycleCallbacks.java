package com.newrelic.agent.android.sessionReplay;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.R;
import com.newrelic.agent.android.sessionReplay.internal.Curtains;
import com.newrelic.agent.android.sessionReplay.internal.OnTouchEventListener;
import com.newrelic.agent.android.sessionReplay.internal.WindowCallbackWrapper;
import com.newrelic.agent.android.sessionReplay.models.RecordedTouchData;
import com.newrelic.agent.android.sessionReplay.models.SessionReplayRoot;

import java.util.ArrayList;
import java.util.Set;


public class SessionReplayActivityLifecycleCallbacks implements Application.ActivityLifecycleCallbacks {
    ArrayList<SessionReplayRoot> sessionReplayRoots = new ArrayList<>();

    private final AgentConfiguration agentConfiguration = AgentConfiguration.getInstance();
    private static final String TAG = "SessionReplayActivityLifecycleCallbacks";
    private float density;
    private int currentTouchId = -1;
    private TouchTracker currentTouchTracker = null;
    SessionReplayConfiguration sessionReplayConfiguration;
    private final OnTouchRecordedListener onTouchRecordedListener;

    public SessionReplayActivityLifecycleCallbacks(OnTouchRecordedListener onTouchRecordedListener) {
        this.onTouchRecordedListener = onTouchRecordedListener;
    }


    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle bundle) {

    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {

    }

    @Override
    public void onActivityPrePaused(@NonNull Activity activity) {

        Gson gson = new Gson();
        String json = gson.toJson(sessionReplayRoots);
        Log.d(TAG, "onActivityPrePaused: " + json);
        Application.ActivityLifecycleCallbacks.super.onActivityPrePaused(activity);
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        sessionReplayConfiguration = agentConfiguration.getSessionReplayConfiguration();
        boolean shouldMaskTouches = sessionReplayConfiguration.isMaskAllUserTouches();
        Log.d(TAG, "onActivityResumed: " + activity.getClass().getSimpleName());
        density = activity.getResources().getDisplayMetrics().density;
        Curtains.getOnRootViewsChangedListeners().add((view, added) -> {
            Log.d(TAG, "Root View Changed in Listener");
            Windows.WindowType windowType = Windows.getWindowType(view);
            if (windowType == Windows.WindowType.POPUP_WINDOW) {
                return;
            }
            Window window = Windows.getPhoneWindowForView(view);
            WindowCallbackWrapper.getListeners(window).getTouchEventInterceptors().add((OnTouchEventListener) motionEvent -> {
                long timestamp = System.currentTimeMillis();
                MotionEvent.PointerCoords pointerCoords = new MotionEvent.PointerCoords();
                motionEvent.getPointerCoords(0, pointerCoords);
                RecordedTouchData moveTouch;
                if (motionEvent.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    View containingView = findViewAtCoords(view, (int)pointerCoords.x, (int)pointerCoords.y);
                    View maskView = getMaskedViewIfNeeded(containingView,  shouldMaskTouches);
                    int containingTouchViewId = getStableId(maskView);
                    if (currentTouchTracker == null && containingTouchViewId != -1) {
                        Log.d(TAG, "Adding Start Event");
                        currentTouchId = containingTouchViewId;
                        moveTouch = new RecordedTouchData(0, currentTouchId, getPixel(pointerCoords.x), getPixel(pointerCoords.y), timestamp);
                        currentTouchTracker = new TouchTracker(moveTouch);
                    } else if (containingTouchViewId == -1) {
                        Log.e(TAG, "TOUCH LOST: Unable to find originating View.");
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

            );

        });
    }

    private int getStableId(View child) {
        if(child == null ) {return -1;}
        int keyCode = "NewRelicSessionReplayViewId".hashCode();
        Integer idValue;
        idValue = (Integer) child.getTag(keyCode);
        if(idValue == null) {
            idValue = NewRelicIdGenerator.generateId();
            child.setTag(keyCode, idValue);
        }
        return idValue;
    }

    private View findViewAtCoords(View rootView, int x, int y) {
        if (rootView == null) {
            return null;
        }

        // Check if the touch coordinates are within the bounds of the root view
        if (!isViewContainsPoint(rootView, x, y)) {
            return null;
        }

        if (!(rootView instanceof ViewGroup)) {
            // If it's not a ViewGroup, return the view itself
            return rootView;
        }

        // If it's a ViewGroup, search its children
        ViewGroup viewGroup = (ViewGroup) rootView;
        for (int i = viewGroup.getChildCount() - 1; i >= 0; i--) {
            View child = viewGroup.getChildAt(i);
            View foundView = findViewAtCoords(child, x, y);
            if (foundView != null) {
                return foundView;
            }
        }

        // If no child views contain the point, return the parent
        return rootView;
    }

    private boolean isViewContainsPoint(View view, int x, int y) {
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        int left = location[0];
        int top = location[1];
        int right = left + view.getWidth();
        int bottom = top + view.getHeight();
        return (x >= left && x <= right && y >= top && y <= bottom);
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

    protected View getMaskedViewIfNeeded(View view, boolean shouldMask) {

        if(view != null) {
            // Check if view has tags that prevent masking
            Object viewTag = view.getTag();
            Object privacyTag = view.getTag(R.id.newrelic_privacy);
            boolean hasUnmaskTag = ("nr-unmask".equals(viewTag)) ||
                    ("nr-unmask".equals(privacyTag)) || (view.getTag() != null && sessionReplayConfiguration.shouldUnmaskViewTag(view.getTag().toString())) || checkMaskUnMaskViewClass(sessionReplayConfiguration.getUnmaskedViewClasses(), view);

            // Check if view has tag that forces masking
            boolean hasMaskTag = ("nr-mask".equals(viewTag) || "nr-mask".equals(privacyTag)) || (view.getTag() != null && sessionReplayConfiguration.shouldMaskViewTag(view.getTag().toString())) || checkMaskUnMaskViewClass(sessionReplayConfiguration.getMaskedViewClasses(), view);
            // Apply masking if needed:
            // - If general masking is enabled AND no unmask tag AND not in unmask class list, OR
            // - If has explicit mask tag OR class is explicitly masked
            if ((shouldMask && !hasUnmaskTag) || (!shouldMask && hasMaskTag)) {
                return null;
            }

            // Return original text if no masking needed
            return view;
        }
        return null;
    }
    private boolean checkMaskUnMaskViewClass(Set<String> viewClasses, View view) {

        Class clazz = view.getClass();

        while (clazz!= null) {
            if (viewClasses.contains(clazz.getName())) {
                return true;
            }
            clazz = clazz.getSuperclass();
        }
        return false;
    }
}

