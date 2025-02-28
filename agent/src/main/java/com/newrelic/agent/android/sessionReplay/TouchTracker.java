package com.newrelic.agent.android.sessionReplay;

import com.newrelic.agent.android.sessionReplay.models.Touch;

import java.util.ArrayList;

public class TouchTracker {
    private Touch startTouch;
    private Touch endTouch;
    private ArrayList<Touch> moveTouches;

    public TouchTracker(Touch startTouch) {
        this.startTouch = startTouch;
    }

    public void addMoveTouch(Touch touch) {
        moveTouches.add(touch);
    }

    public void addEndTouch(Touch touch) {
        endTouch = touch;
    }
}
