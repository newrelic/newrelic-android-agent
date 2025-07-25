package com.newrelic.agent.android.sessionReplay.internal;

public interface OnWindowFocusLostListener extends OnWindowFocusChangedListener {

    void onWindowFocusGained();

    @Override
    default void onWindowFocusChanged(boolean hasFocus) {
        if (!hasFocus) {
            onWindowFocusGained();
        }
    }
}
