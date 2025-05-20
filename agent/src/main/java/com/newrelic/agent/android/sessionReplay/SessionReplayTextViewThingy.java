package com.newrelic.agent.android.sessionReplay;

import android.annotation.SuppressLint;
import android.graphics.Typeface;
import android.widget.TextView;

import com.newrelic.agent.android.sessionReplay.models.Attributes;
import com.newrelic.agent.android.sessionReplay.models.RRWebElementNode;
import com.newrelic.agent.android.sessionReplay.models.RRWebNode;
import com.newrelic.agent.android.sessionReplay.models.RRWebTextNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// Assuming SessionReplayViewThingy is an interface or abstract class in Java
// and ViewDetails is a separate class in your project.
public class SessionReplayTextViewThingy implements SessionReplayViewThingyInterface { // Assuming this interface exists
    private List<? extends SessionReplayViewThingyInterface> subviews = new ArrayList<>();
    private ViewDetails viewDetails;

    public boolean shouldRecordSubviews = false;
    private String labelText;
    private float fontSize;
    private String fontName;
    private String fontFamily;
    private String textColor;

    public SessionReplayTextViewThingy(ViewDetails viewDetails, TextView view) {
        this.viewDetails = viewDetails;

        this.labelText = view.getText() != null ? view.getText().toString() : "";
        this.fontSize = view.getTextSize() / view.getContext().getResources().getDisplayMetrics().density;
        Typeface typeface = view.getTypeface();


        this.fontName = "default";
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

    public String getLabelText() {
        return labelText;
    }

    public float getFontSize() {
        return fontSize;
    }

    public String getFontName() {
        return fontName;
    }

    public String getFontFamily() {
        return fontFamily;
    }

    public String getTextColor() {
        return textColor;
    }

    @Override
    public String getCSSSelector() {
        return viewDetails.getCssSelector();
    }

    @SuppressLint("DefaultLocale")
    @Override
    public String generateCssDescription() {
        StringBuilder cssBuilder = new StringBuilder(viewDetails.generateCssDescription());
        cssBuilder.append("");
        cssBuilder.append("white-space: pre-wrap;");
        cssBuilder.append("");
        cssBuilder.append("word-wrap: break-word");
        cssBuilder.append(" ");
        cssBuilder.append("font-size: ");
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
        // Use classes from your project's models package
        RRWebTextNode textNode = new RRWebTextNode(this.labelText, false, NewRelicIdGenerator.generateId());

        // Use classes from your project's models package
        Attributes attributes = new Attributes(viewDetails.getCssSelector());

        // Use classes from your project's models package
        return new RRWebElementNode(attributes, RRWebElementNode.TAG_TYPE_DIV, viewDetails.getViewId(), Collections.singletonList(textNode));
    }

    private String getFontFamily(Typeface typeface) {
        if (typeface == null) {
            return "font-weight: normal; font-style: normal;";
        }

        StringBuilder style = new StringBuilder();

        // Handle font family
        if (typeface.equals(Typeface.MONOSPACE)) {
            style.append("font-family: monospace;");
        } else if (typeface.equals(Typeface.SERIF)) {
            style.append("font-family: serif;");
        } else {
            // Default, SANS_SERIF, and custom typefaces
            style.append("font-family: sans-serif;");
        }

        // Handle font weight and style
        int typefaceStyle = typeface.getStyle();
        if ((typefaceStyle & Typeface.BOLD) != 0) {
            style.append(" font-weight: bold;");
        }
        if ((typefaceStyle & Typeface.ITALIC) != 0) {
            style.append(" font-style: italic;");
        }

        return style.toString();
    }
}