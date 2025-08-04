package com.newrelic.agent.android.sessionReplay.internal;

import android.view.View; /**
 * Listener added to Curtains.onRootViewsChangedListeners.
 */
public interface OnRootViewRemovedListener extends OnRootViewsChangedListener {
    /**
     * Called when android.view.WindowManager.removeView is called.
     */
    void onRootViewRemoved(View view);

    @Override
    default void onRootViewsChanged(View view, boolean added) {
        if (!added) {
            onRootViewRemoved(view);
        }
    }
}
