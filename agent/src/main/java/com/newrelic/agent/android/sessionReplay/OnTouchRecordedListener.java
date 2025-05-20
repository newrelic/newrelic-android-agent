package com.newrelic.agent.android.sessionReplay;

public interface OnTouchRecordedListener {

     default void onTouchRecorded(TouchTracker touchTracker) {
        // Handle touch recording here
        // For example, you can log the touch event or perform any other action
    }
}
