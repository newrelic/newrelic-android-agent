package com.newrelic.agent.android.sessionReplay.internal;

import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Window;

import androidx.annotation.NonNull;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.WeakHashMap;

public class WindowCallbackWrapper extends FixedWindowCallback {
    private WindowListeners listeners = new WindowListeners();
    private Window.Callback delegate;

    WindowCallbackWrapper(@NonNull Window.Callback delegate) {
        super(delegate);
        this.delegate = delegate;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if(event != null) {
            Iterator<KeyEventInterceptor> iterator = listeners.getKeyEventInterceptors().iterator();

            DispatchFunction<KeyEvent> dispatch = new DispatchFunction<KeyEvent>() {
                @Override
                public DispatchState dispatch(KeyEvent event) {
                    if (iterator.hasNext()) {
                        KeyEventInterceptor nextInterceptor = iterator.next();
                        return nextInterceptor.intercept(event, this);
                    } else {
                        return DispatchState.from(delegate.dispatchKeyEvent(event));
                    }
                }
            };

            if(iterator.hasNext()) {
                KeyEventInterceptor firstInterceptor = iterator.next();
                firstInterceptor.intercept(event, dispatch);
            } else {
                return (DispatchState.from(delegate.dispatchKeyEvent(event)) == DispatchState.Consumed);
            }
        }

        return delegate.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if(event != null) {
            Iterator<TouchEventInterceptor> iterator = listeners.getTouchEventInterceptors().iterator();

            DispatchFunction<MotionEvent> dispatchFunction = new DispatchFunction<MotionEvent>() {
                @Override
                public DispatchState dispatch(MotionEvent event) {
                    if(iterator.hasNext()) {
                        TouchEventInterceptor nextInterceptor = iterator.next();
                        return nextInterceptor.intercept(event, this);
                    } else {
                        return DispatchState.from(delegate.dispatchTouchEvent(event));
                    }
                }
            };

            if(iterator.hasNext()) {
                TouchEventInterceptor firstInterceptor = iterator.next();
                firstInterceptor.intercept(event, dispatchFunction);
            } else {
                return (DispatchState.from(delegate.dispatchTouchEvent(event)) == DispatchState.Consumed);
            }
        }

        return delegate.dispatchTouchEvent(event);
    }

    @Override
    public void onContentChanged() {
        for(OnContentChangedListener listener: listeners.getOnContentChangedListeners()) {
            listener.onContentChanged();
        }
        delegate.onContentChanged();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        for(OnWindowFocusChangedListener listener : listeners.getOnWindowFocusChangedListeners()) {
            listener.onWindowFocusChanged(hasFocus);
        }

        delegate.onWindowFocusChanged(hasFocus);
    }

    private static final Object listenerLock = new Object();
    private static final WeakHashMap<Window, WeakReference<WindowCallbackWrapper>> callbackCache = new WeakHashMap<>();

    public static WindowListeners getListeners(Window window) {
        synchronized (listenerLock) {
            WeakReference<WindowCallbackWrapper> existingWrapperRef = callbackCache.get(window);
            WindowCallbackWrapper existingWrapper = existingWrapperRef != null  ? existingWrapperRef.get() : null;
            if(existingWrapper != null) {
                return existingWrapper.listeners;
            }

            Window.Callback currentCallback = window.getCallback();
            if(currentCallback == null) {
                return new WindowListeners();
            }

            WindowCallbackWrapper windowCallbackWrapper = new WindowCallbackWrapper(currentCallback);
            window.setCallback(windowCallbackWrapper);
            callbackCache.put(window, new WeakReference<>(windowCallbackWrapper));
            return windowCallbackWrapper.listeners;
        }
    }

    public static Window.Callback unwrap(Window.Callback callback) {
        if(callback == null) return null;
        if(callback instanceof WindowCallbackWrapper) {
            return unwrap(((WindowCallbackWrapper) callback).delegate);
        }

        return callback;
    }
}
