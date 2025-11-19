package com.newrelic.agent.android.sessionReplay;

import android.graphics.Point;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;

import androidx.compose.ui.platform.AndroidComposeView;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.R;
import com.newrelic.agent.android.sessionReplay.compose.ComposeTreeCapture;
import com.newrelic.agent.android.util.ComposeChecker;

import java.util.ArrayList;

public class SessionReplayCapture {
    private SessionReplayThingyRecorder recorder;
    private ComposeTreeCapture composeTreeCapture;

    public SessionReplayCapture(AgentConfiguration config) {
        this.recorder = new SessionReplayThingyRecorder(config);
        this.composeTreeCapture = new ComposeTreeCapture(recorder);
    }

    public SessionReplayViewThingyInterface capture(View rootView, AgentConfiguration agentConfiguration) {
        return recursivelyCapture(rootView,false,false);
    }

    private SessionReplayViewThingyInterface recursivelyCapture(View rootView,boolean shouldAddMask,boolean shouldAddUnMask) {
        ArrayList<SessionReplayViewThingyInterface> childThingies = new ArrayList<>();

        if(rootView instanceof ViewGroup) {
            if(ComposeChecker.isComposeUsed(rootView.getContext()) && rootView instanceof AndroidComposeView) {
                SessionReplayViewThingyInterface thingy = composeTreeCapture.captureComposeView((AndroidComposeView) rootView);
                return thingy;
            }
            for(int i = 0; i < ((ViewGroup) rootView).getChildCount(); i++) {
                View child = ((ViewGroup) rootView).getChildAt(i);

                // Defensive null check - getChildAt can return null in rare concurrent modification cases
                if (child == null || !shouldRecordView(child)) {
                    continue;
                }

                // Check privacy tags - use safe null-checking pattern
                Object privacyTag = child.getTag(R.id.newrelic_privacy);
                Object generalTag = child.getTag();

                boolean shouldAddMask1 = (privacyTag != null && "nr-mask".equals(privacyTag)) ||
                                         (generalTag != null && "nr-mask".equals(generalTag));
                boolean shouldAddUnMask1 = (privacyTag != null && "nr-unmask".equals(privacyTag)) ||
                                           (generalTag != null && "nr-unmask".equals(generalTag));


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


