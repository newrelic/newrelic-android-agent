package com.newrelic.agent.android.sessionReplay;

import android.graphics.Point;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.R;

import java.util.ArrayList;

public class SessionReplayCapture {
    private SessionReplayThingyRecorder recorder;

    public SessionReplayViewThingyInterface capture(View rootView, AgentConfiguration agentConfiguration) {
        recorder = new SessionReplayThingyRecorder(rootView.getResources().getDisplayMetrics().density,agentConfiguration);
        return recursivelyCapture(rootView,false,false);
    }

    private SessionReplayViewThingyInterface recursivelyCapture(View rootView,boolean shouldAddMask,boolean shouldAddUnMask) {
        ArrayList<SessionReplayViewThingyInterface> childThingies = new ArrayList<>();

        if(rootView instanceof ViewGroup) {
            for(int i = 0; i < ((ViewGroup) rootView).getChildCount(); i++) {

                View child = ((ViewGroup) rootView).getChildAt(i);

                if(!shouldRecordView(child)) {
                    continue;
                }

                boolean shouldAddMask1 = (child.getTag(R.id.newrelic_privacy) != null && child.getTag(R.id.newrelic_privacy).equals("nr-mask")) || (child.getTag() != null && child.getTag().equals("nr-mask"));
                boolean shouldAddUnMask1 = child.getTag(R.id.newrelic_privacy) != null && child.getTag(R.id.newrelic_privacy).equals("nr-unmask") || (child.getTag() != null && child.getTag().equals("nr-unmask"));


                if(shouldAddMask &&  !shouldAddUnMask1 ) {
                    child.setTag(R.id.newrelic_privacy,"nr-mask");
                }

                if(shouldAddUnMask && !shouldAddMask1) {
                    child.setTag(R.id.newrelic_privacy,"nr-unmask");
                }

                childThingies.add(recursivelyCapture(((ViewGroup) rootView).getChildAt(i), shouldAddMask1, shouldAddUnMask1));
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


