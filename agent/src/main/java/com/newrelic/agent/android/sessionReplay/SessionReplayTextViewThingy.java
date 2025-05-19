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
        // Use classes from your project's models package
        RRWebTextNode textNode = new RRWebTextNode(this.labelText, false, NewRelicIdGenerator.generateId());

        // Use classes from your project's models package
        Attributes attributes = new Attributes(viewDetails.getCssSelector());

        // Use classes from your project's models package
        return new RRWebElementNode(attributes, RRWebElementNode.TAG_TYPE_DIV, viewDetails.getViewId(), Collections.singletonList(textNode));
    }

//    // This method corresponds to generateDifference in Swift
//    // You will need a MutationRecord class and RRWebMutationData in your Java code.
//    // This implementation is a simplified version based on the Swift code.
//    // Assuming MutationRecord and RRWebMutationData are classes in your project
//    public List<MutationRecord> generateDifferences(UILabelThingy other) {
//        List<MutationRecord> mutations = new ArrayList<>();
//
//        // Assuming generateBaseDifferences in the Java interface returns a map of CSS attribute differences
//        java.util.Map<String, String> frameDifferences = generateBaseDifferences(other);
//
//        // Get text color difference
//        if (this.textColor != other.textColor) {
//            String otherTextColorHex = String.format("#%06X", (0xFFFFFF & other.textColor));
//            frameDifferences.put("color", otherTextColorHex);
//        }
//
//        // Assuming you have a MutationRecord.AttributeRecord class in your project
//        // This part needs to be adapted to your Java MutationRecord structure
//        // mutations.add(new MutationRecord.AttributeRecord(viewDetails.getViewId(), frameDifferences));
//
//        if (!this.labelText.equals(other.labelText)) {
//            // Assuming you have a MutationRecord.TextRecord class in your project
//            // mutations.add(new MutationRecord.TextRecord(viewDetails.getViewId(), other.labelText));
//        }
//
//        return mutations;
//    }
//
//    // Equivalent of Swift's Equatable
//    @Override
//    public boolean equals(Object o) {
//        if (this == o) return true;
//        if (o == null || getClass() != o.getClass()) return false;
//        UILabelThingy that = (UILabelThingy) o;
//        return Float.compare(that.fontSize, fontSize) == 0 &&
//                textColor == that.textColor &&
//                Objects.equals(viewDetails, that.viewDetails) &&
//                Objects.equals(labelText, that.labelText) &&
//                Objects.equals(fontName, that.fontName) &&
//                Objects.equals(fontFamily, that.fontFamily);
//    }
//
//    // Equivalent of Swift's Hashable
//    @Override
//    public int hashCode() {
//        return Objects.hash(viewDetails, labelText, fontSize, fontName, fontFamily, textColor);
//    }
//
//    // Assuming generateBaseCSSStyle and generateBaseDifferences are part of SessionReplayViewThingy
//    // You will need to implement these methods in your SessionReplayViewThingy interface/abstract class.
//    // Example placeholder methods:
//    private String generateBaseCSSStyle() {
//        // Implement logic to generate base CSS styles (e.g., position, size)
//        return "";
//    }
//
//    private java.util.Map<String, String> generateBaseDifferences(SessionReplayViewThingy other) {
//        // Implement logic to generate differences for base view properties
//        return new java.util.HashMap<>();
//    }

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