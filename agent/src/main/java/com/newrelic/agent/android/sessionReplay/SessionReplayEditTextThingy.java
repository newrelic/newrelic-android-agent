package com.newrelic.agent.android.sessionReplay;

import android.annotation.SuppressLint;
import android.widget.EditText;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.sessionReplay.models.Attributes;
import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.MutationRecord;
import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.RRWebMutationData;
import com.newrelic.agent.android.sessionReplay.models.RRWebElementNode;
import com.newrelic.agent.android.sessionReplay.models.RRWebNode;
import com.newrelic.agent.android.sessionReplay.models.RRWebTextNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SessionReplayEditTextThingy extends SessionReplayTextViewThingy implements SessionReplayViewThingyInterface {
    private List<? extends SessionReplayViewThingyInterface> subviews = new ArrayList<>();
    private final ViewDetails viewDetails;

    public boolean shouldRecordSubviews = false;
    private final String hint;

    public SessionReplayEditTextThingy(ViewDetails viewDetails, EditText view, AgentConfiguration agentConfiguration) {
        // Call the parent constructor to initialize common properties
        super(viewDetails, view, agentConfiguration);
        this.viewDetails = viewDetails;

        // Get the raw text from the TextView
        String rawText = view.getHint() != null ? view.getHint().toString() : "";
        boolean shouldMaskText =  view.getInputType() != 0 ? sessionReplayConfiguration.isMaskUserInputText(): sessionReplayConfiguration.isMaskApplicationText();

        // Apply masking if needed
        this.hint = getMaskedTextIfNeeded(view, rawText, shouldMaskText);
    }

    @Override
    public List<? extends SessionReplayViewThingyInterface> getSubviews() {
        return subviews;
    }

    @Override
    public void setSubviews(List<? extends SessionReplayViewThingyInterface> subviews) {
        this.subviews = subviews;
    }

    @Override
    public ViewDetails getViewDetails() {
        return viewDetails;
    }

    @Override
    public boolean shouldRecordSubviews() {
        return shouldRecordSubviews;
    }


    @SuppressLint("DefaultLocale")
    @Override
    public String generateCssDescription() {

        StringBuilder cssBuilder = new StringBuilder(super.generateCssDescription());
        return cssBuilder.toString();
    }

    @Override
    public String generateInlineCss() {
        return super.generateInlineCss();
    }

    @Override
    public RRWebElementNode generateRRWebNode() {
        RRWebTextNode textNode;
        if (super.getLabelText().isEmpty() && !hint.isEmpty()) {
            textNode = new RRWebTextNode(hint, false, NewRelicIdGenerator.generateId());
        } else {
            textNode = new RRWebTextNode(super.getLabelText(), false, NewRelicIdGenerator.generateId());
        }

        Attributes attributes = new Attributes(viewDetails.getCssSelector());
        attributes.setType("text"); // Set input type to "text" for EditText

        ArrayList <RRWebNode> children = new ArrayList<>();
        children.add(textNode);
        return new RRWebElementNode(attributes, RRWebElementNode.TAG_TYPE_DIV, viewDetails.viewId,children);
    }

    @Override
    public List<MutationRecord> generateDifferences(SessionReplayViewThingyInterface other) {
        // Make sure this is not null and is of the same type
        if (!(other instanceof SessionReplayEditTextThingy)) {
            return null;
        }

        // Create a map to store style differences
        java.util.Map<String, String> styleDifferences = new java.util.HashMap<>();

        ViewDetails otherViewDetails = (ViewDetails) other.getViewDetails();
        // Compare frames
        if (!viewDetails.frame.equals(otherViewDetails.frame)) {
            styleDifferences.put("left", otherViewDetails.frame.left + "px");
            styleDifferences.put("top", otherViewDetails.frame.top + "px");
            styleDifferences.put("width", otherViewDetails.frame.width() + "px");
            styleDifferences.put("height", otherViewDetails.frame.height() + "px");
            styleDifferences.put("line-height", otherViewDetails.frame.height() + "px");
        }

        // Compare background colors if available
        if (viewDetails.backgroundColor != null && otherViewDetails.backgroundColor != null) {
            if (!viewDetails.backgroundColor.equals(otherViewDetails.backgroundColor)) {
                styleDifferences.put("background-color", otherViewDetails.backgroundColor);
            }
        } else if (otherViewDetails.backgroundColor != null) {
            styleDifferences.put("background-color", otherViewDetails.backgroundColor);
        }

        // compare TextColor if available
        if(super.getTextColor() != null && ((SessionReplayTextViewThingy) other).getTextColor() != null) {
            String otherTextColor = ((SessionReplayTextViewThingy) other).getTextColor();
            if (!super.getTextColor().equals(otherTextColor)) {
                styleDifferences.put("color", otherTextColor);
            }
        }

        // Create and return a MutationRecord with the style differences
        Attributes attributes = new Attributes(viewDetails.getCSSSelector());
        attributes.setMetadata(styleDifferences);    List<MutationRecord> mutations = new ArrayList<>();
        mutations.add(new RRWebMutationData.AttributeRecord(viewDetails.viewId, attributes));

        // Check if label text has changed
        if (!super.getLabelText().equals(((SessionReplayTextViewThingy) other).getLabelText())) {
            mutations.add(new RRWebMutationData.TextRecord(viewDetails.viewId, ((SessionReplayTextViewThingy) other).getLabelText()));
        }

        return mutations;
    }

    @Override
    public int getViewId() {
        return viewDetails.viewId;
    }

    @Override
    public int getParentViewId() {
        return viewDetails.parentId;
    }
}