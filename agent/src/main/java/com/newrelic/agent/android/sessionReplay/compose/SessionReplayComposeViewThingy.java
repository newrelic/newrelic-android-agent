
package com.newrelic.agent.android.sessionReplay.compose;

import androidx.compose.ui.semantics.SemanticsNode;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.sessionReplay.SessionReplayViewThingyInterface;
import com.newrelic.agent.android.sessionReplay.models.Attributes;
import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.MutationRecord;
import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.RRWebMutationData;
import com.newrelic.agent.android.sessionReplay.models.RRWebElementNode;
import com.newrelic.agent.android.sessionReplay.models.RRWebNode;

import java.util.ArrayList;
import java.util.List;

public class SessionReplayComposeViewThingy implements SessionReplayViewThingyInterface {
    ComposeViewDetails viewDetails;
    private final AgentConfiguration agentConfiguration;

    private List<? extends SessionReplayViewThingyInterface> subviews = new ArrayList<>();

    public SessionReplayComposeViewThingy(ComposeViewDetails viewDetails, SemanticsNode semanticsNode, AgentConfiguration agentConfiguration) {
        this.viewDetails = viewDetails;
        this.agentConfiguration = agentConfiguration;
    }

    @Override
    public Object getViewDetails() {
        return viewDetails;
    }

    @Override
    public boolean shouldRecordSubviews() {
        return true;
    }

    @Override
    public List<? extends SessionReplayViewThingyInterface> getSubviews() {
        return this.subviews;
    }

    @Override
    public void setSubviews(List<? extends SessionReplayViewThingyInterface> subviews) {
        this.subviews = subviews;
    }

    @Override
    public String getCssSelector() {
        return viewDetails.getCssSelector();
    }

    @Override
    public String generateCssDescription() {
        return viewDetails.generateCssDescription();
    }

    @Override
    public String generateInlineCss() {
        return viewDetails.generateInlineCSS();
    }

    @Override
    public RRWebElementNode generateRRWebNode() {
        Attributes attributes = new Attributes(viewDetails.getCssSelector());
        return new RRWebElementNode(attributes, RRWebElementNode.TAG_TYPE_DIV, viewDetails.viewId,new ArrayList<RRWebNode>()  );
    }

    @Override
    public List<MutationRecord> generateDifferences(SessionReplayViewThingyInterface other) {
        // Make sure this is not null and is of the same type
        if (!(other instanceof SessionReplayComposeViewThingy)) {
            return null;
        }

        SessionReplayComposeViewThingy otherCompose = (SessionReplayComposeViewThingy) other;
        ComposeViewDetails otherDetails = otherCompose.viewDetails;

        // Early return if no changes
        if (viewDetails.equals(otherDetails)) {
            return new ArrayList<>(); // or Collections.emptyList()
        }

        // Use LinkedHashMap for predictable iteration order
        java.util.Map<String, String> styleDifferences = new java.util.LinkedHashMap<>(8); // Size hint

        // Compare frames
        if (!viewDetails.frame.equals(otherDetails.frame)) {
            styleDifferences.put("left", otherDetails.frame.left + "px");
            styleDifferences.put("top", otherDetails.frame.top + "px");
            styleDifferences.put("width", otherDetails.frame.width() + "px");
            styleDifferences.put("height", otherDetails.frame.height() + "px");
        }

        // Compare background colors (null-safe)
        if (!java.util.Objects.equals(viewDetails.backgroundColor, otherDetails.backgroundColor)) {
            if (otherDetails.backgroundColor != null) {
                styleDifferences.put("background-color", otherDetails.backgroundColor);
            }
        }

        // Compare visibility
        if (viewDetails.isHidden() != otherDetails.isHidden()) {
            styleDifferences.put("visibility", otherDetails.isHidden() ? "hidden" : "visible");
        }

        // Early return if no style differences
        if (styleDifferences.isEmpty()) {
            return new ArrayList<>();
        }

        // Create mutation record
        Attributes attributes = new Attributes(viewDetails.getCssSelector());
        attributes.setMetadata(styleDifferences);

        List<MutationRecord> mutations = new ArrayList<>(1); // Capacity hint
        mutations.add(new RRWebMutationData.AttributeRecord(viewDetails.viewId, attributes));

        return mutations;
    }

    @Override
    public List<RRWebMutationData.AddRecord> generateAdditionNodes(int parentId) {
        RRWebElementNode node = generateRRWebNode();
        node.attributes.metadata.put("style", generateInlineCss());
        RRWebMutationData.AddRecord addRecord = new RRWebMutationData.AddRecord(
                parentId,
                null,
                node
        );

        List<RRWebMutationData.AddRecord> adds = new ArrayList<>();
        adds.add(addRecord);
        return adds;
    }

    @Override
    public int getViewId() {
        return viewDetails.viewId;
    }

    @Override
    public int getParentViewId() {
        return 0;
    }


    // Compose-specific methods

    public AgentConfiguration getAgentConfiguration() {
        return agentConfiguration;
    }

    @Override
    public boolean hasChanged(SessionReplayViewThingyInterface other) {
        if (other == null || !(other instanceof SessionReplayComposeViewThingy)) {
            return true;
        }

        SessionReplayComposeViewThingy otherCompose = (SessionReplayComposeViewThingy) other;
        return !viewDetails.equals(otherCompose.viewDetails);
    }
}
