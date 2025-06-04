package com.newrelic.agent.android.sessionReplay;

import com.newrelic.agent.android.sessionReplay.models.Attributes;
import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.MutationRecord;
import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.RRWebMutationData;
import com.newrelic.agent.android.sessionReplay.models.RRWebElementNode;
import com.newrelic.agent.android.sessionReplay.models.RRWebNode;

import java.util.ArrayList;
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
    public String getCSSSelector() {
        return viewDetails.getCssSelector();
    }

    @Override
    public String generateCssDescription() {
        return viewDetails.generateCssDescription() + "}";
    }

    @Override
    public RRWebElementNode generateRRWebNode() {
        Attributes attribues = new Attributes(viewDetails.getCSSSelector());
        return new RRWebElementNode(attribues, RRWebElementNode.TAG_TYPE_DIV, viewDetails.getViewId(),
                new ArrayList<RRWebNode>());
    }

    @Override
    public List<MutationRecord> generateDifferences(SessionReplayViewThingyInterface other) {
        // Make sure this is not null and is of the same type
        if (!(other instanceof SessionReplayViewThingy)) {
            return null;
        }

        // Create a map to store style differences
        java.util.Map<String, String> styleDifferences = new java.util.HashMap<>();

        // Compare frames
        if (!viewDetails.getFrame().equals(other.getViewDetails().getFrame())) {
            styleDifferences.put("left", String.format("%.2fpx", other.getViewDetails().getFrame().left));
            styleDifferences.put("top", String.format("%.2fpx", other.getViewDetails().getFrame().top));
            styleDifferences.put("width", String.format("%.2fpx", other.getViewDetails().getFrame().width()));
            styleDifferences.put("height", String.format("%.2fpx", other.getViewDetails().getFrame().height()));
        }

        // Compare background colors if available
        if (viewDetails.getBackgroundColor() != null && other.getViewDetails().getBackgroundColor() != null) {
            if (!viewDetails.getBackgroundColor().equals(other.getViewDetails().getBackgroundColor())) {
                styleDifferences.put("background-color", other.getViewDetails().getBackgroundColor());
            }
        } else if (other.getViewDetails().getBackgroundColor() != null) {
            styleDifferences.put("background-color", other.getViewDetails().getBackgroundColor());
        }

        // Create and return a MutationRecord with the style differences
        Attributes attributes = new Attributes(viewDetails.getCSSSelector());
        attributes.setMetadata(styleDifferences);
        List<MutationRecord> mutations = new ArrayList<>();
        mutations.add(new RRWebMutationData.AttributeRecord(viewDetails.getViewId(), attributes));
        return mutations;
    }

    @Override
    public int getViewId() {
        return viewDetails.getViewId();
    }
}
