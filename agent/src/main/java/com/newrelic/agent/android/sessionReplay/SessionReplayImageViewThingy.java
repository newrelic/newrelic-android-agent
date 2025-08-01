package com.newrelic.agent.android.sessionReplay;

import android.annotation.SuppressLint;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.sessionReplay.models.Attributes;
import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.MutationRecord;
import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.RRWebMutationData;
import com.newrelic.agent.android.sessionReplay.models.RRWebElementNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SessionReplayImageViewThingy implements SessionReplayViewThingyInterface {
    private List<? extends SessionReplayViewThingyInterface> subviews = new ArrayList<>();
    private ViewDetails viewDetails;

    public boolean shouldRecordSubviews = false;
    private String contentDescription;
    private ImageView.ScaleType scaleType;
    private String backgroundColor;

    public SessionReplayImageViewThingy(ViewDetails viewDetails, ImageView view, AgentConfiguration agentConfiguration) {
        this.viewDetails = viewDetails;

        this.contentDescription = view.getContentDescription() != null ? view.getContentDescription().toString() : "";
        this.scaleType = view.getScaleType();
        this.backgroundColor = getBackgroundColor(view);
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

    @Override
    public String getCssSelector() {
        return viewDetails.getCssSelector();
    }

    @SuppressLint("DefaultLocale")
    @Override
    public String generateCssDescription() {
        StringBuilder cssBuilder = new StringBuilder(viewDetails.generateCssDescription());
        generateImageCss(cssBuilder);
        cssBuilder.append("}");

        return cssBuilder.toString();
    }

    @Override
    public String generateInlineCss() {
        StringBuilder cssBuilder = new StringBuilder(viewDetails.generateInlineCSS());
        cssBuilder.append(" ");
        generateImageCss(cssBuilder);
        return cssBuilder.toString();
    }

    private void generateImageCss(StringBuilder cssBuilder) {
        cssBuilder.append("background-color: ");
        cssBuilder.append(this.backgroundColor);
        cssBuilder.append("; ");
        cssBuilder.append("background-size: ");
        cssBuilder.append(getBackgroundSizeFromScaleType());
        cssBuilder.append("; ");
        cssBuilder.append("background-repeat: no-repeat; ");
        cssBuilder.append("background-position: center; ");
    }


    @Override
    public RRWebElementNode generateRRWebNode() {
        Attributes attributes = new Attributes(viewDetails.getCssSelector());
        return new RRWebElementNode(attributes, RRWebElementNode.TAG_TYPE_DIV, viewDetails.getViewId(), Collections.emptyList());
    }

    @Override
    public List<MutationRecord> generateDifferences(SessionReplayViewThingyInterface other) {
        // Make sure this is not null and is of the same type
        if (!(other instanceof SessionReplayImageViewThingy)) {
            return null;
        }

        // Create a map to store style differences
        java.util.Map<String, String> styleDifferences = new java.util.HashMap<>();

        // Compare frames
        if (!viewDetails.frame.equals(other.getViewDetails().frame)) {
            styleDifferences.put("left", other.getViewDetails().frame.left + "px");
            styleDifferences.put("top", other.getViewDetails().frame.top + "px");
            styleDifferences.put("width", other.getViewDetails().frame.width() + "px");
            styleDifferences.put("height", other.getViewDetails().frame.height() + "px");
        }

        // Compare background colors if available
        if (viewDetails.backgroundColor != null && other.getViewDetails().backgroundColor != null) {
            if (!viewDetails.backgroundColor.equals(other.getViewDetails().backgroundColor)) {
                styleDifferences.put("background-color", other.getViewDetails().backgroundColor);
            }
        } else if (other.getViewDetails().backgroundColor != null) {
            styleDifferences.put("background-color", other.getViewDetails().backgroundColor);
        }

        // Create and return a MutationRecord with the style differences
        Attributes attributes = new Attributes(viewDetails.getCSSSelector());
        attributes.setMetadata(styleDifferences);
        List<MutationRecord> mutations = new ArrayList<>();
        mutations.add(new RRWebMutationData.AttributeRecord(viewDetails.getViewId(), attributes));
        return mutations;
    }

    @Override
    public List<RRWebMutationData.AddRecord> generateAdditionNodes(int parentId) {
        RRWebElementNode node = generateRRWebNode();
        node.attributes.metadata.put("style", generateInlineCss());
        RRWebMutationData.AddRecord addRecord = new RRWebMutationData.AddRecord(
                parentId,
                null,
                node);

        List<RRWebMutationData.AddRecord> adds = new ArrayList<>();
        adds.add(addRecord);
        return adds;
    }

    @Override
    public int getViewId() {
        return viewDetails.viewId;
    }

    private String getBackgroundColor(ImageView view) {
        Drawable background = view.getBackground();
        if (background != null) {
            return "#FF474C"; // Placeholder color, you might want to implement a method to extract actual color
        }
        return "#CCCCCC";
    }

    private String getBackgroundSizeFromScaleType() {
        switch (scaleType) {
            case FIT_XY:
                return "100% 100%";
            case CENTER_CROP:
                return "cover";
            case FIT_CENTER:
            case CENTER_INSIDE:
                return "contain";
            default:
                return "auto";
        }
    }
}