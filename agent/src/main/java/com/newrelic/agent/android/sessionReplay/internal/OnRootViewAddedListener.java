package com.newrelic.agent.android.sessionReplay.internal;

import android.view.View; /**
 * Listener added to Curtains.onRootViewsChangedListeners.
 */
public interface OnRootViewAddedListener extends OnRootViewsChangedListener {
    /**
     * Called when android.view.WindowManager.addView is called.
     */
    void onRootViewAdded(View view);

    @Override
    default void onRootViewsChanged(View view, boolean added) {
        if (added) {
            onRootViewAdded(view);
        }
    }
}
