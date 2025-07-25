package com.newrelic.agent.android.sessionReplay.internal;

public interface OnWindowFocusGainedListener extends OnWindowFocusChangedListener {

    void onWindowFocusGained();

    @Override
    default void onWindowFocusChanged(boolean hasFocus) {
        if (hasFocus) {
            onWindowFocusGained();
        }
    }
}
