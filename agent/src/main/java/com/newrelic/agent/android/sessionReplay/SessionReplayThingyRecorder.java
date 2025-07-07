package com.newrelic.agent.android.sessionReplay;

import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.newrelic.agent.android.AgentConfiguration;

public class SessionReplayThingyRecorder {
    private static String SessionReplayKey = "NewRelicSessionReplayKey";
    private float density;

    public SessionReplayThingyRecorder(float density) {
        this.density = density;
    }

    public SessionReplayViewThingyInterface recordView(View view) {
        ViewDetails viewDetails = new ViewDetails(view);


        AgentConfiguration agentConfiguration = AgentConfiguration.getInstance();
        SessionReplayConfiguration sessionReplayConfiguration = agentConfiguration.getMobileSessionReplayConfiguration();

        if (view instanceof EditText) {
            return new SessionReplayEditTextThingy(viewDetails, (EditText) view,sessionReplayConfiguration);
        } if (view instanceof ImageView) {
            return new SessionReplayImageViewThingy(viewDetails, (ImageView) view, sessionReplayConfiguration);
        } else  if (view instanceof TextView) {
            return new SessionReplayTextViewThingy(viewDetails, (TextView) view,sessionReplayConfiguration);
        }else {
            // This is a plain old view
            return new SessionReplayViewThingy(viewDetails);
        }
    }
}
