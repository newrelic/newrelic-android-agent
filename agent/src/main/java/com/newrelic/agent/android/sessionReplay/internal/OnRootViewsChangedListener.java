package com.newrelic.agent.android.sessionReplay.internal;

import android.view.View;


/**
 * Listener added to Curtains.onRootViewsChangedListeners.
 * If you only care about either attached or detached, consider implementing OnRootViewAddedListener
 * or OnRootViewRemovedListener instead.
 */
public interface OnRootViewsChangedListener {
    /**
     * Called when android.view.WindowManager.addView and android.view.WindowManager.removeView
     * are called.
     */
    void onRootViewsChanged(View view, boolean added);
}

@FunctionalInterface
interface DispatchFunction<T> {
    DispatchState dispatch(T event);
}

enum DispatchState {
    Consumed,
    NotConsumed
}