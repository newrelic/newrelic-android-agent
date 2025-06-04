package com.newrelic.agent.android.sessionReplay;

import android.annotation.SuppressLint;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

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

    public SessionReplayImageViewThingy(ViewDetails viewDetails, ImageView view, MobileSessionReplayConfiguration sessionReplayConfiguration) {
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
    public String getCSSSelector() {
        return viewDetails.getCssSelector();
    }

    @SuppressLint("DefaultLocale")
    @Override
    public String generateCssDescription() {
        StringBuilder cssBuilder = new StringBuilder(viewDetails.generateCssDescription());
        cssBuilder.append("background-color: ");
        cssBuilder.append(this.backgroundColor);
        cssBuilder.append("; ");
        cssBuilder.append("background-size: ");
        cssBuilder.append(getBackgroundSizeFromScaleType());
        cssBuilder.append("; ");
        cssBuilder.append("background-repeat: no-repeat; ");
        cssBuilder.append("background-position: center; ");
        cssBuilder.append("}");

        return cssBuilder.toString();
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

    private String getBackgroundColor(ImageView view) {
        Drawable background = view.getBackground();
        if (background != null) {
            return "#FF474C"; // Placeholder color, you might want to implement a method to extract actual color
        }
        return " #FFC0CB";
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