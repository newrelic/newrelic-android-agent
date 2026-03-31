package com.newrelic.agent.android.sessionReplay.compose;

import androidx.compose.ui.semantics.SemanticsNode;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.sessionReplay.SessionReplayViewThingyInterface;
import com.newrelic.agent.android.sessionReplay.models.Attributes;
import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.MutationRecord;
import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.RRWebMutationData;
import com.newrelic.agent.android.sessionReplay.models.RRWebElementNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ComposeBlockedViewThingy extends SessionReplayComposeViewThingy {

    public ComposeBlockedViewThingy(ComposeViewDetails viewDetails, SemanticsNode semanticsNode, AgentConfiguration agentConfiguration) {
        super(viewDetails, semanticsNode, agentConfiguration);
    }

    @Override
    public boolean shouldRecordSubviews() {
        return false;
    }

    @Override
    public List<? extends SessionReplayViewThingyInterface> getSubviews() {
        return Collections.emptyList();
    }

    @Override
    public void setSubviews(List<? extends SessionReplayViewThingyInterface> subviews) {
        // no-op — blocked views never have children
    }

    @Override
    public String generateCssDescription() {
        return viewDetails.generateCssDescription() + " background-color: #000000;";
    }

    @Override
    public String generateInlineCss() {
        StringBuilder cssBuilder = new StringBuilder(this.viewDetails.generateInlineCSS());
        cssBuilder.append(" ");
        cssBuilder.append("background-color: #000000;");
        return cssBuilder.toString();
    }

    @Override
    public RRWebElementNode generateRRWebNode() {
        Attributes attributes = new Attributes(viewDetails.getCssSelector());
        attributes.dataNrMasked = "block";
        return new RRWebElementNode(attributes, RRWebElementNode.TAG_TYPE_DIV, viewDetails.viewId,
                new ArrayList<>());
    }

    @Override
    public List<MutationRecord> generateDifferences(SessionReplayViewThingyInterface other) {
        if (!(other instanceof ComposeBlockedViewThingy)) {
            return Collections.emptyList();
        }

        ComposeBlockedViewThingy otherBlocked = (ComposeBlockedViewThingy) other;
        ComposeViewDetails otherDetails = otherBlocked.viewDetails;

        java.util.Map<String, String> styleDifferences = new java.util.HashMap<>();

        if (!viewDetails.frame.equals(otherDetails.frame)) {
            styleDifferences.put("left", otherDetails.frame.left + "px");
            styleDifferences.put("top", otherDetails.frame.top + "px");
            styleDifferences.put("width", otherDetails.frame.width() + "px");
            styleDifferences.put("height", otherDetails.frame.height() + "px");
        }

        Attributes attributes = new Attributes(viewDetails.getCssSelector());
        attributes.dataNrMasked = "block";
        attributes.setMetadata(styleDifferences);
        List<MutationRecord> mutations = new ArrayList<>();
        mutations.add(new RRWebMutationData.AttributeRecord(viewDetails.viewId, attributes));
        return mutations;
    }

    @Override
    public boolean hasChanged(SessionReplayViewThingyInterface other) {
        if (other == null || !(other instanceof ComposeBlockedViewThingy)) {
            return true;
        }
        ComposeViewDetails otherDetails = ((ComposeBlockedViewThingy) other).viewDetails;
        return !viewDetails.frame.equals(otherDetails.frame);
    }
}
