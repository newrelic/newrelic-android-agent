package com.newrelic.agent.android.sessionReplay;

import com.newrelic.agent.android.sessionReplay.models.RecordedTouchData;

public interface OnTouchRecordeListener {

     default void onTouchRecorde(TouchTracker touchTracker) {
        // Handle touch recording here
        // For example, you can log the touch event or perform any other action
    }
}
