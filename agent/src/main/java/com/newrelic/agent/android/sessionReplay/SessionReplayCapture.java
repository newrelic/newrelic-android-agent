package com.newrelic.agent.android.sessionReplay;

import android.graphics.Point;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;

import com.newrelic.agent.android.R;

import java.util.ArrayList;

public class SessionReplayCapture {
    private SessionReplayThingyRecorder recorder;

    public SessionReplayViewThingyInterface capture(View rootView) {
        recorder = new SessionReplayThingyRecorder(rootView.getResources().getDisplayMetrics().density);
        return recursivelyCapture(rootView,false);
    }

    private SessionReplayViewThingyInterface recursivelyCapture(View rootView,boolean shouldAddMask) {
        ArrayList<SessionReplayViewThingyInterface> childThingies = new ArrayList<>();

        if(rootView instanceof ViewGroup) {
            for(int i = 0; i < ((ViewGroup) rootView).getChildCount(); i++) {

                View child = ((ViewGroup) rootView).getChildAt(i);

                if(!shouldRecordView(child)) {
                    continue;
                }

                if(shouldAddMask) {
                    child.setTag(R.id.newrelic_privacy,"nr-mask");
                }

                boolean shouldAddMask1;
                shouldAddMask1 = child.getTag(R.id.newrelic_privacy) != null && child.getTag(R.id.newrelic_privacy).equals("nr-mask");


                childThingies.add(recursivelyCapture(((ViewGroup) rootView).getChildAt(i), shouldAddMask1));
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


