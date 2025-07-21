package com.newrelic.agent.android.sessionReplay.internal;

public enum DispatchState {
    Consumed,
    NotConsumed;

    public static DispatchState from(boolean result) {
        if(result) {
            return Consumed;
        } else {
            return NotConsumed;
        }
    }
}
