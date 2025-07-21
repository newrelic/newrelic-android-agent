package com.newrelic.agent.android.sessionReplay.internal;

import android.view.MotionEvent; /**
 * Interceptor added to Window.touchEventInterceptors.
 *
 * If you only care about logging touch events without intercepting, consider implementing
 * OnTouchEventListener instead.
 */
public interface TouchEventInterceptor {
    /**
     * Called when android.view.Window.Callback.dispatchTouchEvent is called.
     *
     * Implementations should either return DispatchState.Consumed (which intercepts the touch
     * event) or return the result of calling dispatch. Implementations can also pass through
     * a copy of motionEvent (for example to fix bugs where the OS sends broken events).
     */
    DispatchState intercept(MotionEvent motionEvent, DispatchFunction<MotionEvent> dispatch);
}
