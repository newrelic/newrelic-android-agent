package com.newrelic.agent.android.sessionReplay.internal;

import android.view.MotionEvent; /**
 * Listener added to Window.touchEventInterceptors.
 */
public interface OnTouchEventListener extends TouchEventInterceptor {
    /**
     * Called when android.view.Window.Callback.dispatchTouchEvent is called.
     */
    void onTouchEvent(MotionEvent motionEvent);

    @Override
    default DispatchState intercept(MotionEvent motionEvent, DispatchFunction<MotionEvent> dispatch) {
        onTouchEvent(motionEvent);
        return dispatch.dispatch(motionEvent);
    }
}
