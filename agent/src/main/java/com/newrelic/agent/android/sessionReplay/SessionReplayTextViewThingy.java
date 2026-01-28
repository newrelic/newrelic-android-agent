package com.newrelic.agent.android.sessionReplay;

import android.annotation.SuppressLint;
import android.graphics.Typeface;
import android.widget.TextView;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.R;
import com.newrelic.agent.android.sessionReplay.models.Attributes;
import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.MutationRecord;
import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.RRWebMutationData;
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
        // Note: InputType uses bit fields structured as: [flags][variation][class]
        // - Bits 0-3: TYPE_CLASS (text=1, number=2, phone=3, datetime=4)
        // - Bits 4-11: TYPE_VARIATION (password=0x80, email=0x20, etc.)
        // - Bits 12+: TYPE_FLAGS (caps, multiline, etc.)
        int inputType = view.getInputType();

        // Extract the variation bits (bits 4-11) by masking with 0xFF0 (0b111111110000)
        // This isolates just the variation field, ignoring class and flags
        int TYPE_MASK_CLASS = 0x0000000f;  // Mask for class (bits 0-3)
        int TYPE_MASK_VARIATION = 0x00000ff0;  // Mask for variation (bits 4-11)

        int variation = inputType & TYPE_MASK_VARIATION;

        // Check for password variations - CRITICAL: Always mask passwords
        // We compare ONLY the variation bits (bits 4-11), not the full inputType
        // Password variation values: 0x80=password, 0x90=visible_password, 0xe0=web_password, 0x10=number_password
        boolean isPassword =
            variation == 0x80 ||  // TYPE_TEXT_VARIATION_PASSWORD
            variation == 0x90 ||  // TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            variation == 0xe0 ||  // TYPE_TEXT_VARIATION_WEB_PASSWORD
            variation == 0x10;    // TYPE_NUMBER_VARIATION_PASSWORD

        if (isPassword) {
            // Always mask passwords regardless of configuration
            shouldMaskText = true;
        } else {
            // For non-password fields, check if it's actually an editable input field
            // Use instanceof EditText to determine if it's user input vs static text
            boolean isEditableInput = view instanceof android.widget.EditText;
            shouldMaskText = isEditableInput
                ? sessionReplayConfiguration.isMaskUserInputText()
                : sessionReplayConfiguration.isMaskApplicationText();
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

        // Extract RGB color safely, masking off alpha channel
        this.textColor = String.format("%06x", view.getCurrentTextColor() & 0xFFFFFF);
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
    public String getCssSelector() {
        return viewDetails.getCssSelector();
    }

    @SuppressLint("DefaultLocale")
    @Override
    public String generateCssDescription() {
        StringBuilder cssBuilder = new StringBuilder(viewDetails.generateCssDescription());
        cssBuilder.append("");
        generateTextCss(cssBuilder);

        return cssBuilder.toString();
    }

    @Override
    public String generateInlineCss() {
        StringBuilder cssBuilder = new StringBuilder(viewDetails.generateInlineCSS());
        cssBuilder.append(" ");
        generateTextCss(cssBuilder);
        return cssBuilder.toString();
    }

    private void generateTextCss(StringBuilder cssBuilder) {
        cssBuilder.append("display: flex; ");
        cssBuilder.append("align-items: center; ");

        cssBuilder.append("justify-content: ");
        switch (this.textAlign != null ? this.textAlign : "left") {
            case "center":
                cssBuilder.append("center; ");
                break;
            case "right":
                cssBuilder.append("flex-end; ");
                break;
            case "left":
            default:
                cssBuilder.append("flex-start; ");
                break;
        }

        cssBuilder.append("white-space: pre-wrap;");
        cssBuilder.append(" ");
        cssBuilder.append("word-wrap: break-word;");
        cssBuilder.append(" ");
        cssBuilder.append("font-size: ");
        cssBuilder.append(String.format("%.2f", this.fontSize));
        cssBuilder.append("px; ");

        // Null-safe append for fontFamily
        if (this.fontFamily != null) {
            cssBuilder.append(this.fontFamily);
            cssBuilder.append("; ");
        }

        // Null-safe append for textColor
        cssBuilder.append("color: #");
        cssBuilder.append(this.textColor != null ? this.textColor : "000000");
        cssBuilder.append("; ");
    }

    @Override
    public RRWebElementNode generateRRWebNode() {
        RRWebTextNode textNode = new RRWebTextNode(this.labelText, false, NewRelicIdGenerator.generateId());

        Attributes attributes = new Attributes(viewDetails.getCssSelector());

        return new RRWebElementNode(attributes, RRWebElementNode.TAG_TYPE_DIV, viewDetails.viewId, new ArrayList<>(Collections.singletonList(textNode)));
    }

    @Override
    public List<MutationRecord> generateDifferences(SessionReplayViewThingyInterface other) {
        // Make sure this is not null and is of the same type
        if (!(other instanceof SessionReplayTextViewThingy)) {
            return Collections.emptyList();
        }

        // Create a map to store style differences
        java.util.Map<String, String> styleDifferences = new java.util.HashMap<>();
        // Compare text alignment
        if(this.textAlign != null) {
            String otherTextAlign = ((SessionReplayTextViewThingy) other).textAlign;
            if (!this.textAlign.equals(otherTextAlign)) {
                // Update justify-content instead of text-align
                switch (otherTextAlign != null ? otherTextAlign : "left") {
                    case "center":
                        styleDifferences.put("justify-content", "center");
                        break;
                    case "right":
                        styleDifferences.put("justify-content", "flex-end");
                        break;
                    default:
                        styleDifferences.put("justify-content", "flex-start");
                        break;
                }
            }
        }

        ViewDetails otherViewDetails = (ViewDetails) other.getViewDetails();
        // Compare frames
        if (!viewDetails.frame.equals(otherViewDetails.frame)) {
            styleDifferences.put("left", otherViewDetails.frame.left + "px");
            styleDifferences.put("top", otherViewDetails.frame.top + "px");
            styleDifferences.put("width", otherViewDetails.frame.width() + "px");
            styleDifferences.put("height", otherViewDetails.frame.height() + "px");
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
        if(this.textColor != null) {
            String otherTextColor = ((SessionReplayTextViewThingy) other).getTextColor();
            if (!this.textColor.equals(otherTextColor)) {
                styleDifferences.put("color", "#" + otherTextColor);
            }
        }

        if(this.fontFamily!= null) {
            String otherFontFamily = ((SessionReplayTextViewThingy) other).getFontFamily();
            if (!this.fontFamily.equals(otherFontFamily)) {
                styleDifferences.put("font-family", otherFontFamily);
            }
        }

        if (this.fontSize != ((SessionReplayTextViewThingy) other).getFontSize()) {
            styleDifferences.put("font-size", String.format("%.2fpx", ((SessionReplayTextViewThingy) other).getFontSize()));
        }

        // Create and return a MutationRecord with the style differences
        Attributes attributes = new Attributes(viewDetails.getCSSSelector());
        attributes.setMetadata(styleDifferences);
        List<MutationRecord> mutations = new ArrayList<>();
        mutations.add(new RRWebMutationData.AttributeRecord(viewDetails.viewId, attributes));

        // Check if label text has changed
        if (!this.labelText.equals(((SessionReplayTextViewThingy) other).getLabelText())) {
            mutations.add(new RRWebMutationData.TextRecord(viewDetails.viewId, ((SessionReplayTextViewThingy) other).getLabelText()));
        }

        return mutations;
    }

    @Override
    public List<RRWebMutationData.AddRecord> generateAdditionNodes(int parentId) {
        // We have to recreate the RRWebElementNode instead of calling the
        // method because that method automatically adds the text node as a
        // child. For adds, the text node should be it's own node.

        Attributes attributes = new Attributes(viewDetails.getCssSelector());

        RRWebElementNode viewNode =  new RRWebElementNode(
                attributes,
                RRWebElementNode.TAG_TYPE_DIV,
                viewDetails.viewId,
                new ArrayList<>());

        viewNode.attributes.metadata.put("style", generateInlineCss());

        RRWebTextNode textNode = new RRWebTextNode(this.labelText, false, NewRelicIdGenerator.generateId());

        RRWebMutationData.AddRecord viewAddRecord = new RRWebMutationData.AddRecord(
                parentId,
                null,
                viewNode);

        RRWebMutationData.AddRecord textAddRecord = new RRWebMutationData.AddRecord(
                viewDetails.viewId,
                null,
                textNode);

        List<RRWebMutationData.AddRecord> adds = new ArrayList<>();
        adds.add(viewAddRecord);
        adds.add(textAddRecord);
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

    @Override
    public boolean hasChanged(SessionReplayViewThingyInterface other) {
        // Quick check: if it's not the same type, it has changed
        if (other == null || !(other instanceof SessionReplayTextViewThingy)) {
            return true;
        }

        // Compare using hashCode (which should reflect the content)
        return this.hashCode() != other.hashCode();
    }

}