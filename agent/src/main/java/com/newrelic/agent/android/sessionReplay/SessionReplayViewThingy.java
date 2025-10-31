package com.newrelic.agent.android.sessionReplay;

import com.newrelic.agent.android.sessionReplay.models.Attributes;
import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.MutationRecord;
import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.RRWebMutationData;
import com.newrelic.agent.android.sessionReplay.models.RRWebElementNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SessionReplayViewThingy implements SessionReplayViewThingyInterface {
    ViewDetails viewDetails;

    private List<? extends SessionReplayViewThingyInterface> subviews = new ArrayList<>();

    public SessionReplayViewThingy(ViewDetails viewDetails) {
        this.viewDetails = viewDetails;
    }

    @Override
    public ViewDetails getViewDetails() {
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
        Attributes attributes = new Attributes(viewDetails.getCSSSelector());
        return new RRWebElementNode(attributes, RRWebElementNode.TAG_TYPE_DIV, viewDetails.viewId,
                new ArrayList<>());
    }

    @Override
    public List<MutationRecord> generateDifferences(SessionReplayViewThingyInterface other) {
        // Make sure this is not null and is of the same type
        if (!(other instanceof SessionReplayViewThingy)) {
            return Collections.emptyList();
        }

        // Create a map to store style differences
        java.util.Map<String, String> styleDifferences = new java.util.HashMap<>();

        ViewDetails otherDetails = (ViewDetails) other.getViewDetails();
        // Compare frames
        if (!viewDetails.frame.equals(otherDetails.frame)) {
            styleDifferences.put("left", otherDetails.frame.left + "px");
            styleDifferences.put("top", otherDetails.frame.top + "px");
            styleDifferences.put("width", otherDetails.frame.width() + "px");
            styleDifferences.put("height", otherDetails.frame.height() + "px");
        }

        // Compare background colors if available
        if (!viewDetails.backgroundColor.equals(otherDetails.backgroundColor)) {
            styleDifferences.put("background-color", otherDetails.backgroundColor);
        }

        // Create and return a MutationRecord with the style differences
        Attributes attributes = new Attributes(viewDetails.getCSSSelector());
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
        return viewDetails.parentId;
    }

    @Override
    public boolean hasChanged(SessionReplayViewThingyInterface other) {
        // Quick check: if it's not the same type, it has changed
        if (other == null || !(other instanceof SessionReplayViewThingy)) {
            return true;
        }

        // Compare using hashCode (which should reflect the content)
        return this.hashCode() != other.hashCode();
    }
}
