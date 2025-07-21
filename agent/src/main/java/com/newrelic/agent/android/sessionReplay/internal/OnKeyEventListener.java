package com.newrelic.agent.android.sessionReplay.internal;

import android.view.KeyEvent; /**
 * Listener added to Window.keyEventInterceptors.
 */
public interface OnKeyEventListener extends KeyEventInterceptor {
    /**
     * Called when android.view.Window.Callback.dispatchKeyEvent is called.
     */
    void onKeyEvent(KeyEvent keyEvent);

    @Override
    default DispatchState intercept(KeyEvent keyEvent, DispatchFunction<KeyEvent> dispatch) {
        onKeyEvent(keyEvent);
        return dispatch.dispatch(keyEvent);
    }
}
