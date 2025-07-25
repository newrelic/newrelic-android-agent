package com.newrelic.agent.android.sessionReplay.internal;

/**
 * Listener added to Window.onContentChangedListeners.
 */
public interface OnContentChangedListener {
    /**
     * Called when android.view.Window.Callback.onContentChanged is called.
     */
    void onContentChanged();
}
