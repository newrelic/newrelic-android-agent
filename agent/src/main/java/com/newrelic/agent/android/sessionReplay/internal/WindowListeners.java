package com.newrelic.agent.android.sessionReplay.internal;

import java.util.concurrent.CopyOnWriteArrayList;

public class WindowListeners {

    private CopyOnWriteArrayList<TouchEventInterceptor> touchEventInterceptors = new CopyOnWriteArrayList<>();
    private CopyOnWriteArrayList<KeyEventInterceptor> keyEventInterceptors = new CopyOnWriteArrayList<>();
    private CopyOnWriteArrayList<OnContentChangedListener> onContentChangedListeners = new CopyOnWriteArrayList<>();
    private CopyOnWriteArrayList<OnWindowFocusChangedListener> onWindowFocusChangedListeners = new CopyOnWriteArrayList<>();


    public CopyOnWriteArrayList<TouchEventInterceptor> getTouchEventInterceptors() {
        return touchEventInterceptors;
    }

    public CopyOnWriteArrayList<KeyEventInterceptor> getKeyEventInterceptors() {
        return keyEventInterceptors;
    }

    public CopyOnWriteArrayList<OnContentChangedListener> getOnContentChangedListeners() {
        return onContentChangedListeners;
    }

    public CopyOnWriteArrayList<OnWindowFocusChangedListener> getOnWindowFocusChangedListeners() {
        return onWindowFocusChangedListeners;
    }
}
