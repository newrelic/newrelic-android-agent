package com.newrelic.agent.android.sessionReplay;

import android.graphics.Point;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;

public class SessionReplayCapture {
    private SessionReplayThingyRecorder recorder;

    public SessionReplayViewThingyInterface capture(View rootView) {
        recorder = new SessionReplayThingyRecorder(rootView.getResources().getDisplayMetrics().density);
        return recursivelyCapture(rootView);
    }

    private SessionReplayViewThingyInterface recursivelyCapture(View rootView) {
        ArrayList<SessionReplayViewThingyInterface> childThingies = new ArrayList<>();

        if(rootView instanceof ViewGroup) {
            for(int i = 0; i < ((ViewGroup) rootView).getChildCount(); i++) {

                View child = ((ViewGroup) rootView).getChildAt(i);
                if(!shouldRecordView(child)) {
                    continue;
                }
                childThingies.add(recursivelyCapture(((ViewGroup) rootView).getChildAt(i)));
            }
        }

        SessionReplayViewThingyInterface thingy = recorder.recordView(rootView);
        thingy.setSubviews(childThingies);

        return thingy;
    }

    private boolean shouldRecordView(View child) {

        Rect rootView = new Rect();
        Point point = new Point();

        return child.getGlobalVisibleRect(rootView, point) && child.getVisibility() == View.VISIBLE && child.getAlpha() > 0;
    }
}


