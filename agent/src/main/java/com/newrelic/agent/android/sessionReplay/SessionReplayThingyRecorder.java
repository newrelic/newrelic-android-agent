package com.newrelic.agent.android.sessionReplay;

import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.compose.ui.semantics.Role;
import androidx.compose.ui.semantics.SemanticsNode;
import androidx.compose.ui.semantics.SemanticsProperties;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.sessionReplay.compose.ComposeEditTextThingy;
import com.newrelic.agent.android.sessionReplay.compose.ComposeImageThingy;
import com.newrelic.agent.android.sessionReplay.compose.ComposeViewDetails;
import com.newrelic.agent.android.sessionReplay.compose.SessionReplayComposeViewThingy;
import com.newrelic.agent.android.sessionReplay.compose.ComposeTextViewThingy;

public class SessionReplayThingyRecorder {
    private final AgentConfiguration agentConfiguration;

    public SessionReplayThingyRecorder(AgentConfiguration agentConfiguration) {
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

    public  SessionReplayViewThingyInterface recordView(SemanticsNode node,float density) {


        ComposeViewDetails composeViewDetails = new ComposeViewDetails(node, density);
         if(node.getConfig().contains(SemanticsProperties.INSTANCE.getEditableText())) {
             return new ComposeEditTextThingy(composeViewDetails, node, agentConfiguration);
        } else if(node.getConfig().contains(SemanticsProperties.INSTANCE.getText())) {
            return new ComposeTextViewThingy(composeViewDetails, node, agentConfiguration);
        } else if(node.getConfig().contains(SemanticsProperties.INSTANCE.getRole())) {

            Role role = node.getConfig().get(SemanticsProperties.INSTANCE.getRole());
            switch (role.toString()) {
                case "Button":

                    break;
                case "Image":
                    return new ComposeImageThingy(composeViewDetails, node, agentConfiguration);
                default:
                    // Handle other roles or default case
                    break;
            }

        }
        return new SessionReplayComposeViewThingy(composeViewDetails,node,agentConfiguration);

    }
}
