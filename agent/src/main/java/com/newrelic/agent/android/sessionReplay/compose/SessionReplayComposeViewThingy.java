
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
import java.util.Collections;
import java.util.List;

public class SessionReplayComposeViewThingy implements SessionReplayViewThingyInterface {
    ComposeViewDetails viewDetails;
    private final SemanticsNode semanticsNode;
    private final AgentConfiguration agentConfiguration;

    private List<? extends SessionReplayViewThingyInterface> subviews = new ArrayList<>();

    public SessionReplayComposeViewThingy(ComposeViewDetails viewDetails, SemanticsNode semanticsNode, AgentConfiguration agentConfiguration) {
        this.viewDetails = viewDetails;
        this.semanticsNode = semanticsNode;
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
        StringBuilder css = new StringBuilder();
        css.append(viewDetails.generateInlineCSS());
        
        // Add Compose-specific styling if needed
        if (semanticsNode != null) {
            // Add any Compose-specific CSS properties here
        }
        
        return css.toString();
    }

    @Override
    public RRWebElementNode generateRRWebNode() {
        Attributes attributes = new Attributes(viewDetails.getCssSelector());
        
        // Add Compose-specific attributes
        if (semanticsNode != null) {
            // You can add semantic information as data attributes

        }
        
        return new RRWebElementNode(attributes, RRWebElementNode.TAG_TYPE_DIV, viewDetails.viewId,new ArrayList<RRWebNode>()  );
//                new ArrayList<RRWebNode>());
    }

    @Override
    public List<MutationRecord> generateDifferences(SessionReplayViewThingyInterface other) {
        // Make sure this is not null and is of the same type
        if (!(other instanceof SessionReplayComposeViewThingy)) {
            return null;
        }

        SessionReplayComposeViewThingy otherCompose = (SessionReplayComposeViewThingy) other;

        // Create a map to store style differences
        java.util.Map<String, String> styleDifferences = new java.util.HashMap<>();

        ComposeViewDetails otherComposeDetails = (ComposeViewDetails) other.getViewDetails();
        // Compare frames
        if (!viewDetails.frame.equals(otherComposeDetails.frame)) {
            styleDifferences.put("left", otherComposeDetails.frame.left + "px");
            styleDifferences.put("top", otherComposeDetails.frame.top + "px");
            styleDifferences.put("width", otherComposeDetails.frame.width() + "px");
            styleDifferences.put("height", otherComposeDetails.frame.height() + "px");
        }

        // Compare background colors if available
        if (!viewDetails.backgroundColor.equals(otherComposeDetails.backgroundColor)) {
            styleDifferences.put("background-color", otherComposeDetails.backgroundColor);
        }

        // Compare Compose-specific properties
        if (semanticsNode != null && otherCompose.semanticsNode != null) {
            // Compare semantic properties and add differences if needed
            // This is where you'd add Compose-specific difference detection
        }

        // Create and return a MutationRecord with the style differences
        Attributes attributes = new Attributes(viewDetails.getCssSelector());
        attributes.setMetadata(styleDifferences);
        List<MutationRecord> mutations = new ArrayList<>();
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

    /**
     * Check if this Compose view has semantic content that should be captured
     */
    public boolean hasSemanticContent() {
        if (semanticsNode == null) {
            return false;
        }

        // Check for text content
        if (semanticsNode.getConfig().contains(androidx.compose.ui.semantics.SemanticsProperties.INSTANCE.getText())) {
            return true;
        }
        
        // Check for content description
        if (semanticsNode.getConfig().contains(androidx.compose.ui.semantics.SemanticsProperties.INSTANCE.getContentDescription())) {
            return true;
        }
        
        // Check for other semantic properties that indicate meaningful content
        return semanticsNode.getConfig().contains(androidx.compose.ui.semantics.SemanticsProperties.INSTANCE.getRole());
    }
}
