package com.newrelic.agent.android.sessionReplay;

import android.view.View;
import android.widget.AbsSeekBar;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.compose.ui.node.LayoutNode;
import androidx.compose.ui.semantics.Role;
import androidx.compose.ui.semantics.SemanticsNode;
import androidx.compose.ui.semantics.SemanticsProperties;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.sessionReplay.compose.ComposeBlockedViewThingy;
import com.newrelic.agent.android.sessionReplay.compose.ComposeEditTextThingy;
import com.newrelic.agent.android.sessionReplay.compose.ComposeImageThingy;
import com.newrelic.agent.android.sessionReplay.compose.ComposeRadioButtonThingy;
import com.newrelic.agent.android.sessionReplay.compose.ComposeSliderThingy;
import com.newrelic.agent.android.sessionReplay.compose.ComposeViewDetails;
import com.newrelic.agent.android.sessionReplay.compose.SessionReplayComposeViewThingy;
import com.newrelic.agent.android.sessionReplay.compose.ComposeTextViewThingy;
import com.newrelic.agent.android.sessionReplay.internal.ReflectionUtils;

public class SessionReplayThingyRecorder {
    private final AgentConfiguration agentConfiguration;

    public SessionReplayThingyRecorder(AgentConfiguration agentConfiguration) {
        this.agentConfiguration = agentConfiguration;
    }

    public SessionReplayViewThingyInterface recordBlockedView(View view) {
        ViewDetails viewDetails = new ViewDetails(view);
        return new SessionReplayBlockedViewThingy(viewDetails);
    }

    public SessionReplayViewThingyInterface recordView(View view) {
        ViewDetails viewDetails = new ViewDetails(view);

        if (view instanceof EditText) {
            return new SessionReplayEditTextThingy(viewDetails, (EditText) view,agentConfiguration);
        }else if (view instanceof CompoundButton) {
            return new SessionReplayCompoundButtonThingy(viewDetails, (CompoundButton) view, agentConfiguration);
        }else if (view instanceof AbsSeekBar) {
            return new SessionReplaySeekBarThingy(viewDetails, (AbsSeekBar) view);
        }else if (view instanceof ProgressBar) {
            return new SessionReplayProgressBarThingy(viewDetails, (ProgressBar) view);
        }else if (SessionReplaySliderThingy.isSlider(view)) {
            return new SessionReplaySliderThingy(viewDetails, view);
        }else if (view instanceof ImageView) {
            return new SessionReplayImageViewThingy(viewDetails, (ImageView) view, agentConfiguration);
        } else  if (view instanceof TextView) {
            return new SessionReplayTextViewThingy(viewDetails, (TextView) view,agentConfiguration);
        }else {
            // This is a plain old view
            return new SessionReplayViewThingy(viewDetails);
        }
    }
    @androidx.compose.ui.InternalComposeUiApi
    public  SessionReplayViewThingyInterface recordView(SemanticsNode node,float density) {

        LayoutNode layoutNode = ReflectionUtils.getLayoutNode(node);

        if(layoutNode.getInteropView() != null) {
            // It's a view inside compose
            return recordView(layoutNode.getInteropView());
        }

        ComposeViewDetails composeViewDetails = new ComposeViewDetails(node, density);
         if(node.getConfig().contains(SemanticsProperties.INSTANCE.getEditableText())) {
             return new ComposeEditTextThingy(composeViewDetails, node, agentConfiguration);
        } else if(node.getConfig().contains(SemanticsProperties.INSTANCE.getText())) {
            return new ComposeTextViewThingy(composeViewDetails, node, agentConfiguration);
        } else if(node.getConfig().contains(SemanticsProperties.INSTANCE.getProgressBarRangeInfo())) {
            return new ComposeSliderThingy(composeViewDetails, node, agentConfiguration);
        } else if (layoutNode.getMeasurePolicy().toString().contains("Image")) {
             // There is no reliable way to identify image role for compose nodes, so we are checking measure policy for now
             return new ComposeImageThingy(composeViewDetails, node, agentConfiguration);
         } else if(node.getConfig().contains(SemanticsProperties.INSTANCE.getRole())) {
            Role role = node.getConfig().get(SemanticsProperties.INSTANCE.getRole());
            switch (role.toString()) {
                case "Button":

                    break;
                case "Image":
                    return new ComposeImageThingy(composeViewDetails, node, agentConfiguration);
                case "RadioButton":
                    // otherwise we are not showing text underlying the radio button for time picker
                    if(node.getChildren().isEmpty()) {
                         return new ComposeRadioButtonThingy(composeViewDetails, node, agentConfiguration);
                    }
                default:
                    // Handle other roles or default case
                    break;
            }

        }
        return new SessionReplayComposeViewThingy(composeViewDetails,node,agentConfiguration);

    }

    @androidx.compose.ui.InternalComposeUiApi
    public SessionReplayViewThingyInterface recordBlockedComposeView(SemanticsNode node, float density) {
        ComposeViewDetails details = new ComposeViewDetails(node, density);
        return new ComposeBlockedViewThingy(details, node, agentConfiguration);
    }
}
