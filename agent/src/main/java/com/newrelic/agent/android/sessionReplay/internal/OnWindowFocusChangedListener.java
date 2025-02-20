package com.newrelic.agent.android.sessionReplay.internal;

/**
 * Listener added to Window.onWindowFocusChangedListeners.
 */
public interface OnWindowFocusChangedListener {
    /**
     * Called when android.view.Window.Callback.onWindowFocusChanged is called.
     */
    void onWindowFocusChanged(boolean hasFocus);
}
