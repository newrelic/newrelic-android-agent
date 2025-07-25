package com.newrelic.agent.android.sessionReplay.internal;

@FunctionalInterface
public interface DispatchFunction<T> {
    DispatchState dispatch(T event);
}
