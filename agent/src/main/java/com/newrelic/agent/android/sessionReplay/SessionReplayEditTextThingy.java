package com.newrelic.agent.android.sessionReplay;

import android.annotation.SuppressLint;
import android.graphics.Typeface;
import android.widget.EditText;

import com.newrelic.agent.android.sessionReplay.models.Attributes;
import com.newrelic.agent.android.sessionReplay.models.RRWebElementNode;
import com.newrelic.agent.android.sessionReplay.models.RRWebTextNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SessionReplayEditTextThingy implements SessionReplayViewThingyInterface {
    private List<? extends SessionReplayViewThingyInterface> subviews = new ArrayList<>();
    private ViewDetails viewDetails;

    public boolean shouldRecordSubviews = false;
    private String text;
    private String hint;
    private float fontSize;
    private String fontFamily;
    private String textColor;

    public SessionReplayEditTextThingy(ViewDetails viewDetails, EditText view) {
        this.viewDetails = viewDetails;

        this.text = view.getText() != null ? view.getText().toString() : "";
        this.hint = view.getHint() != null ? view.getHint().toString() : "";
        this.fontSize = view.getTextSize() / view.getContext().getResources().getDisplayMetrics().density;
        Typeface typeface = view.getTypeface();

        this.fontFamily = getFontFamily(typeface);
        this.textColor = Integer.toHexString(view.getCurrentTextColor()).substring(2);
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
        cssBuilder.append("white-space: pre-wrap;");
        cssBuilder.append("font: ");
        cssBuilder.append(String.format("%.2f", this.fontSize));
        cssBuilder.append("px ");
        cssBuilder.append(this.fontFamily);
        cssBuilder.append("; ");
        cssBuilder.append("color: #");
        cssBuilder.append(this.textColor);
        cssBuilder.append("; ");
        cssBuilder.append("}");

        return cssBuilder.toString();
    }

    @Override
    public RRWebElementNode generateRRWebNode() {
        RRWebTextNode textNode;
        if (text.isEmpty() && !hint.isEmpty()) {
            textNode = new RRWebTextNode(hint, false, NewRelicIdGenerator.generateId());
        } else {
            textNode = new RRWebTextNode(text, false, NewRelicIdGenerator.generateId());
        }

        Attributes attributes = new Attributes(viewDetails.getCssSelector());
        attributes.setType("text"); // Set input type to "text" for EditText

        return new RRWebElementNode(attributes, RRWebElementNode.TAG_TYPE_DIV, viewDetails.getViewId(), Collections.singletonList(textNode));
    }

    @Override
    public int getViewId() {
        return viewDetails.getViewId();
    }

    private String getFontFamily(Typeface typeface) {
        if(typeface.equals(Typeface.DEFAULT)){
            return "roboto, sans-serif";
        }
        if(typeface.equals(Typeface.DEFAULT_BOLD)){
            return "sans-serif-bold";
        }
        if(typeface.equals(Typeface.MONOSPACE)){
            return "monospace";
        }
        if(typeface.equals(Typeface.SANS_SERIF)){
            return "sans-serif";
        }
        if(typeface.equals(Typeface.SERIF)){
            return "serif";
        }
        return "roboto, sans-serif";
    }
}