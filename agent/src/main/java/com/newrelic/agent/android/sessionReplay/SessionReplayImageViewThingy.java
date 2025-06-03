package com.newrelic.agent.android.sessionReplay;

import android.annotation.SuppressLint;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import com.newrelic.agent.android.sessionReplay.models.Attributes;
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