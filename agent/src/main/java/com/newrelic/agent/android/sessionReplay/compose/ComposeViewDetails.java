package com.newrelic.agent.android.sessionReplay.compose;

import android.graphics.Rect;

import androidx.compose.ui.layout.Placeable;
import androidx.compose.ui.node.LayoutNode;
import androidx.compose.ui.platform.AndroidComposeView;
import androidx.compose.ui.platform.ComposeView;
import androidx.compose.ui.semantics.SemanticsActions;
import androidx.compose.ui.semantics.SemanticsNode;
import androidx.compose.ui.semantics.SemanticsProperties;

import com.newrelic.agent.android.sessionReplay.internal.ReflectionUtils;

import java.util.Objects;

public class ComposeViewDetails {
    public final int viewId;
    public final Rect frame;
    public String backgroundColor;
    public final boolean isHidden;
    public final int parentId;
    public final String viewName;
    public final float density;
    public final SemanticsNode semanticsNode;
    public float paddingTop;
    public float paddingBottom;
    public float paddingLeft;
    public float paddingRight;

    public String getCssSelector() {
        return this.viewName + "-" + this.viewId;
    }


    public ComposeViewDetails(SemanticsNode semanticsNode, float density) {
        this.semanticsNode = semanticsNode;
        this.density = density;

        // Extract layout information from semantics node
        LayoutNode layoutNode = ReflectionUtils.getLayoutNode(semanticsNode);
        Placeable placeable = ReflectionUtils.getPlaceable(layoutNode);
        this.backgroundColor = extractBackgroundColor(semanticsNode);
        androidx.compose.ui.geometry.Rect boundsInRoot = semanticsNode.getBoundsInRoot();
        semanticsNode.getLayoutInfo().getModifierInfo().forEach(modifierInfo -> {
           if(modifierInfo.getModifier().toString().contains("androidx.compose.foundation.layout.PaddingElement")){
               // This is a padding modifier
                // We might want to adjust boundsInRoot accordingly if needed
               try {
                   this.paddingTop = ReflectionUtils.getPaddingTop(modifierInfo.getModifier());
                   this.paddingBottom = ReflectionUtils.getPaddingBottom(modifierInfo.getModifier());
                     this.paddingLeft = ReflectionUtils.getPaddingStart(modifierInfo.getModifier());
                        this.paddingRight = ReflectionUtils.getPaddingEnd(modifierInfo.getModifier());
                    } catch (ClassNotFoundException e) {
                        this.paddingTop = 0;
                        this.paddingBottom = 0;
                        this.paddingLeft = 0;
                        this.paddingRight = 0;
                    }

                } else if (modifierInfo.getModifier().toString().contains("androidx.compose.foundation.BackgroundElement")) {
                    // This is a background modifier
                    // We might want to extract background color if needed
                    try {
                        this.backgroundColor = SemanticsNodeUtil.Companion.getBackgroundColor(modifierInfo.getModifier());
                    } catch (ClassNotFoundException e) {

                    }
                }
        });

        // Convert Compose coordinates to Android Rect (scaled by density)
        int left = (int) (boundsInRoot.getLeft() / density);
        int top = (int) (boundsInRoot.getTop() / density);
        int right = (int) (boundsInRoot.getRight() / density);
        int bottom = (int) (boundsInRoot.getBottom() / density);

        if(boundsInRoot != new androidx.compose.ui.geometry.Rect(0, 0, 0,0)) { // In case of null boundsInRoot (e.g., when the view is off-screen)
            if (placeable != null) {
                int width = (int) (placeable.getWidth() / density);
                int height = (int) (placeable.getHeight() / density);
                right = left + width;
                bottom = top + height;
            }
        }


        this.frame = new Rect(left, top, right, bottom);

        // Extract background color if available

        // Extract alpha/transparency
        // Determine if hidden based on semantics
        this.isHidden = extractVisibility(semanticsNode);

        // Generate view name from semantic role or content
        this.viewName = extractViewName(semanticsNode);

        // Generate stable IDs
        this.viewId = semanticsNode.getId();
        this.parentId = generateParentId(semanticsNode);
    }

    private String extractBackgroundColor(SemanticsNode node) {
        // Try to extract background color from semantics properties
        // This is a best effort since Compose doesn't always expose background colors in semantics
        return "transparent"; // Default fallback
    }

    private boolean extractVisibility(SemanticsNode node) {
        try {
            // Try touch bounds first (Compose 1.5+)
            androidx.compose.ui.geometry.Rect bounds = node.getTouchBoundsInRoot();
            return bounds.getWidth() <= 0 || bounds.getHeight() <= 0;
        } catch (NoSuchMethodError e) {
            // Fallback for older Compose versions
            androidx.compose.ui.geometry.Rect bounds = node.getBoundsInRoot();
            return bounds.getWidth() <= 0 || bounds.getHeight() <= 0;
        }
    }

    private String extractViewName(SemanticsNode node) {
        // Try to get semantic role first
        if (node.getConfig().contains(SemanticsProperties.INSTANCE.getRole())) {
            androidx.compose.ui.semantics.Role role = node.getConfig().get(SemanticsProperties.INSTANCE.getRole());
            if (role != null) {
                return "Compose" + role;
            }
        }

        // Check for text content
        if (node.getConfig().contains(SemanticsProperties.INSTANCE.getText())) {
            return "ComposeText";
        }

        // Check for clickable
        if (node.getConfig().contains(SemanticsActions.INSTANCE.getOnClick())) {
            return "ComposeButton";
        }

        // Check for other semantic properties
        if (node.getConfig().contains(SemanticsProperties.INSTANCE.getContentDescription())) {
            return "ComposeView";
        }
        // Default fallback
        return "ComposeNode";
    }

    private int generateStableId(SemanticsNode node) {
        // Generate a stable ID based on the node's position in the tree and its properties
        return node.getId();
    }

    private int generateParentId(SemanticsNode node) {
        SemanticsNode parent = node.getParent();
        if (parent != null) {
            return generateStableId(parent);
        }
        return 0;
    }

    // Getters
    public int getViewId() {
        return viewId;
    }

    public Rect getFrame() {
        return frame;
    }

    public String getBackgroundColor() {
        return backgroundColor;
    }

    public boolean isHidden() {
        return isHidden;
    }

    public String getViewName() {
        return viewName;
    }

    public String generateCssDescription() {
        StringBuilder cssString = new StringBuilder();
        String cssSelector = getCssSelector();

        cssString.append(" #")
                .append(cssSelector)
                .append(" {")
                .append(" ")
                .append(generateInlineCSS());

        return cssString.toString();
    }

    public String generateInlineCSS() {
        StringBuilder cssString = new StringBuilder();
        cssString.append(generatePositionCss())
                .append(" ")
                .append(generateBackgroundColorCss())
                .append(" ")
                .append(generateComposeCss());

        if(isHidden) {
            cssString.append("visibility: hidden;");
        }


        return cssString.toString();
    }

    private String generatePositionCss() {
        StringBuilder positionStringBuilder = new StringBuilder();

        positionStringBuilder.append("position: fixed;")
                .append("left: ")
                .append(frame.left)
                .append("px;")
                .append("top: ")
                .append(frame.top)
                .append("px;")
                .append("width: ")
                .append(frame.width())
                .append("px;")
                .append("height: ")
                .append(frame.height())
                .append("px;")
                .append("padding-top:")
                .append(paddingTop)
                .append("px;");



        return positionStringBuilder.toString();
    }

    private String generateBackgroundColorCss() {
        StringBuilder backgroundColorStringBuilder = new StringBuilder();
        if (!backgroundColor.isEmpty() && !backgroundColor.equals("transparent")) {
            backgroundColorStringBuilder.append("background-color: #")
                    .append(backgroundColor)
                    .append(";");
        }
        return backgroundColorStringBuilder.toString();
    }

    private String generateComposeCss() {
        StringBuilder composeStringBuilder = new StringBuilder();
            // Add text styling if this is a text node
            if (semanticsNode.getConfig().contains(SemanticsProperties.INSTANCE.getText())) {
                composeStringBuilder.append("display: flex;")
                        .append("align-items: center;")
                        .append("justify-content: center;");
            }

        return composeStringBuilder.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ComposeViewDetails that = (ComposeViewDetails) o;
        return viewId == that.viewId &&
                isHidden == that.isHidden &&
                Objects.equals(frame, that.frame) &&
                Objects.equals(backgroundColor, that.backgroundColor) &&
                Objects.equals(viewName, that.viewName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(viewId, frame, backgroundColor, isHidden, viewName);
    }


    public boolean hasText() {
        return semanticsNode != null &&
                semanticsNode.getConfig().contains(SemanticsProperties.INSTANCE.getText());
    }

    public String getText() {
        if (hasText()) {
            return semanticsNode.getConfig().get(SemanticsProperties.INSTANCE.getText()).toString();
        }
        return "";
    }
}