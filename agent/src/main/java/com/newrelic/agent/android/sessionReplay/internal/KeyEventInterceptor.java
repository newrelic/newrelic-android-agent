package com.newrelic.agent.android.sessionReplay.internal;

import android.view.KeyEvent; /**
 * Interceptor added to Window.keyEventInterceptors.
 *
 * If you only care about logging key events without intercepting, consider implementing
 * OnKeyEventListener instead.
 */
public interface KeyEventInterceptor {
    /**
     * Called when android.view.Window.Callback.dispatchKeyEvent is called.
     *
     * Implementations should either return DispatchState.Consumed (which intercepts the touch
     * event) or return the result of calling dispatch. Implementations can also pass through
     * a copy of keyEvent.
     */
    DispatchState intercept(KeyEvent keyEvent, DispatchFunction<KeyEvent> dispatch);
}
