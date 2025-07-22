package com.newrelic.agent.android.sessionReplay;

import android.annotation.SuppressLint;
import android.graphics.Typeface;
import android.widget.TextView;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.R;
import com.newrelic.agent.android.sessionReplay.models.Attributes;
import com.newrelic.agent.android.sessionReplay.models.RRWebElementNode;
import com.newrelic.agent.android.sessionReplay.models.RRWebTextNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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
    private String textAlign;
    protected SessionReplayLocalConfiguration sessionReplayLocalConfiguration;
    protected SessionReplayConfiguration sessionReplayConfiguration;

    public SessionReplayTextViewThingy(ViewDetails viewDetails, TextView view, AgentConfiguration agentConfiguration) {
        this.sessionReplayLocalConfiguration = agentConfiguration.getSessionReplayLocalConfiguration();
        this.sessionReplayConfiguration = agentConfiguration.getSessionReplayConfiguration();
        this.viewDetails = viewDetails;

        // Get the raw text from the TextView
        String rawText = view.getText() != null ? view.getText().toString() : "";

        // Determine if text should be masked based on configuration
        boolean shouldMaskText;

        // Check if input type is for password - always mask password fields
        int inputType = view.getInputType();
        if ((inputType & android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD) != 0 ||
                (inputType & android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) != 0 ||
                (inputType & android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD) != 0 ||
                (inputType & android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD) != 0) {
            shouldMaskText = true;
        } else {
            // For non-password fields, use configuration-based logic
            shouldMaskText = inputType != 0 ? sessionReplayConfiguration.isMaskUserInputText() : sessionReplayConfiguration.isMaskApplicationText();
        }

        // Apply masking if needed
        this.labelText = getMaskedTextIfNeeded(view, rawText, shouldMaskText);

        this.fontSize = view.getTextSize() / view.getContext().getResources().getDisplayMetrics().density;
        Typeface typeface = view.getTypeface();

        this.fontName = "default";
        this.fontFamily = getFontFamily(typeface);

        // First check if gravity is set to something that would affect alignment
        this.textAlign = resolveAlignmentFromGravity(view);

        // If textAlignment is explicitly set, it takes precedence over gravity
        switch (view.getTextAlignment()) {
            case TextView.TEXT_ALIGNMENT_CENTER:
                this.textAlign = "center";
                break;
            case TextView.TEXT_ALIGNMENT_TEXT_END:
            case TextView.TEXT_ALIGNMENT_VIEW_END:
                this.textAlign = "right";
                break;
            case TextView.TEXT_ALIGNMENT_TEXT_START:
            case TextView.TEXT_ALIGNMENT_VIEW_START:
                this.textAlign = "left";
                break;
            case TextView.TEXT_ALIGNMENT_INHERIT:
            default:
                // Keep the alignment determined by gravity
                break;
        }

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
        cssBuilder.append("word-wrap: break-word;");
        cssBuilder.append(" ");
        cssBuilder.append("font-size: ");
        cssBuilder.append(String.format("%.2f", this.fontSize));
        cssBuilder.append("px; ");
        cssBuilder.append(this.fontFamily);
        cssBuilder.append("; ");
        cssBuilder.append("color: #");
        cssBuilder.append(this.textColor);
        cssBuilder.append("; ");
        cssBuilder.append("text-align: ");
        cssBuilder.append(this.textAlign);
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

    /**
     * Resolves text alignment from the TextView's gravity property.
     * This is needed because gravity can also affect text alignment in addition to textAlignment.
     *
     * @param view The TextView to extract gravity alignment from
     * @return The CSS text-align value corresponding to the gravity
     */
    private String resolveAlignmentFromGravity(TextView view) {
        int gravity = view.getGravity();

        // Check horizontal gravity
        int horizontalGravity = gravity & android.view.Gravity.HORIZONTAL_GRAVITY_MASK;

        switch (horizontalGravity) {
            case android.view.Gravity.START:
            case android.view.Gravity.LEFT:
                return "left";
            case android.view.Gravity.END:
            case android.view.Gravity.RIGHT:
                return "right";
            case android.view.Gravity.CENTER:
            case android.view.Gravity.CENTER_HORIZONTAL:
                return "center";
            default:
                return "left"; // Default to left alignment
        }
    }

    /**
     * Helper method to mask text with asterisks if needed based on tags and configuration
     *
     * @param view The TextView containing the text
     * @param text The text to potentially mask
     * @param shouldMask Whether masking should be applied based on configuration
     * @return The original text or masked text depending on conditions
     */
    protected String getMaskedTextIfNeeded(TextView view, String text, boolean shouldMask) {
        // If text is empty, no need to mask
        if (text.isEmpty()) {
            return text;
        }

        // Check if view has tags that prevent masking
        Object viewTag = view.getTag();
        Object privacyTag = view.getTag(R.id.newrelic_privacy);
        boolean hasUnmaskTag = false;
        if(Objects.equals(sessionReplayConfiguration.getMode(), "custom")) {
            hasUnmaskTag = ("nr-unmask".equals(viewTag)) ||
                    ("nr-unmask".equals(privacyTag)) || (view.getTag() != null && (sessionReplayConfiguration.shouldUnmaskViewTag(view.getTag().toString()) || sessionReplayLocalConfiguration.shouldUnmaskViewTag(view.getTag().toString()))) || checkMaskUnMaskViewClass(sessionReplayConfiguration.getUnmaskedViewClasses(), view) || checkMaskUnMaskViewClass(sessionReplayLocalConfiguration.getUnmaskedViewClasses(), view);
        }
        // Check if view has tag that forces masking
        boolean hasMaskTag = ("nr-mask".equals(viewTag) || "nr-mask".equals(privacyTag)) || (view.getTag() != null && (sessionReplayConfiguration.shouldMaskViewTag(view.getTag().toString()) || sessionReplayLocalConfiguration.shouldMaskViewTag(view.getTag().toString()))) || checkMaskUnMaskViewClass(sessionReplayConfiguration.getMaskedViewClasses(),view) || checkMaskUnMaskViewClass(sessionReplayLocalConfiguration.getMaskedViewClasses(),view);
        // Apply masking if needed:
        // - If general masking is enabled AND no unmask tag AND not in unmask class list, OR
        // - If has explicit mask tag OR class is explicitly masked
        if ((shouldMask && !hasUnmaskTag) || (!shouldMask && hasMaskTag)) {
            StringBuilder maskedText = new StringBuilder();
            for (int i = 0; i < text.length(); i++) {
                maskedText.append('*');
            }
            return maskedText.toString();
        }

        // Return original text if no masking needed
        return text;
    }

    private boolean checkMaskUnMaskViewClass(Set<String> viewClasses, TextView view) {

        Class clazz = view.getClass();

        while (clazz!= null) {
            if (viewClasses != null && viewClasses.contains(clazz.getName())) {
                return true;
            }
            clazz = clazz.getSuperclass();
        }
        return false;
    }

}