package com.newrelic.agent.android.sessionReplay;

import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.newrelic.agent.android.AgentConfiguration;

public class SessionReplayThingyRecorder {
    private static String SessionReplayKey = "NewRelicSessionReplayKey";
    private float density;
    private AgentConfiguration agentConfiguration;

    public SessionReplayThingyRecorder(float density,AgentConfiguration agentConfiguration) {
        this.density = density;
        this.agentConfiguration = agentConfiguration;
    }

    public SessionReplayViewThingyInterface recordView(View view) {
        ViewDetails viewDetails = new ViewDetails(view);





        if (view instanceof EditText) {
            return new SessionReplayEditTextThingy(viewDetails, (EditText) view,agentConfiguration);
        } if (view instanceof ImageView) {
            return new SessionReplayImageViewThingy(viewDetails, (ImageView) view, agentConfiguration);
        } else  if (view instanceof TextView) {
            return new SessionReplayTextViewThingy(viewDetails, (TextView) view,agentConfiguration);
        }else {
            // This is a plain old view
            return new SessionReplayViewThingy(viewDetails);
        }
    }
}
