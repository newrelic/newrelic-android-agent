package com.newrelic.agent.android.sessionReplay.capture;

import android.graphics.Point;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;

import androidx.compose.ui.platform.AndroidComposeView;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.R;
import com.newrelic.agent.android.sessionReplay.compose.ComposeSessionReplayConstants;
import com.newrelic.agent.android.sessionReplay.compose.ComposeTreeCapture;
import com.newrelic.agent.android.sessionReplay.internal.ViewPrivacyUtils;
import com.newrelic.agent.android.sessionReplay.viewMapper.SessionReplayViewThingyInterface;
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
        // Check if this view is blocked — return a black rectangle with no children
        if (ViewPrivacyUtils.isBlocked(rootView)) {
            return recorder.recordBlockedView(rootView);
        }

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

                // Check privacy tags via shared utility
                String effectiveTag = ViewPrivacyUtils.getEffectivePrivacyTag(child);

                boolean shouldAddMask1 = ComposeSessionReplayConstants.PrivacyTags.MASK.equals(effectiveTag);
                boolean shouldAddUnMask1 = ComposeSessionReplayConstants.PrivacyTags.UNMASK.equals(effectiveTag);


                if(shouldAddMask &&  !shouldAddUnMask1 ) {
                    child.setTag(R.id.newrelic_privacy, ComposeSessionReplayConstants.PrivacyTags.MASK);
                }

                if(shouldAddUnMask && !shouldAddMask1) {
                    child.setTag(R.id.newrelic_privacy, ComposeSessionReplayConstants.PrivacyTags.UNMASK);
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


