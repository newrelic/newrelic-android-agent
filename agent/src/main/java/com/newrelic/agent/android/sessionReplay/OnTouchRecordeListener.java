package com.newrelic.agent.android.sessionReplay;


public interface OnTouchRecordeListener {

     default void onTouchRecorde(TouchTracker touchTracker) {
        // Handle touch recording here
        // For example, you can log the touch event or perform any other action
    }
}
