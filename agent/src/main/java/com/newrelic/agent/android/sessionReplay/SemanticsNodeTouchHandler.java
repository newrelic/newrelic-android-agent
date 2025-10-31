package com.newrelic.agent.android.sessionReplay;

import android.util.Log;
import android.view.View;
import androidx.compose.ui.geometry.Rect;
import androidx.compose.ui.platform.AndroidComposeView;
import androidx.compose.ui.semantics.SemanticsNode;
import androidx.compose.ui.semantics.SemanticsOwner;

/**
 * Handler for touch events on Compose Semantics Nodes with privacy support
 */
public class SemanticsNodeTouchHandler {
    private static final String TAG = "SemanticsNodeTouchHandler";
    private static final int MASKED_TOUCH_ID = 0;

    private final SessionReplayConfiguration sessionReplayConfiguration;

    public SemanticsNodeTouchHandler(SessionReplayConfiguration sessionReplayConfiguration) {
        this.sessionReplayConfiguration = sessionReplayConfiguration;
    }

    /**
     * Gets the Compose SemanticsNode at the given coordinates
     * Uses reflection to access internal AndroidComposeView
     */
    public Object getComposeSemanticsNode(View foundView, int x, int y) {
        if (foundView == null || foundView.getParent() == null) {
            return null;
        }

        if(foundView instanceof AndroidComposeView) {
            SemanticsOwner semanticsOwner = ((AndroidComposeView) foundView).getSemanticsOwner();
            SemanticsNode semanticsNodes = semanticsOwner.getUnmergedRootSemanticsNode();
            return findNodeAtPosition(semanticsNodes, x, y);
        } else {
            // handle other view types here, such as RecyclerView or ListView
            return null;
        }
    }

    public Object findNodeAtPosition(SemanticsNode semanticsNodes, int x, int y) {
        if(semanticsNodes == null) {
            return null;
        }

        // Check if the touch coordinates are within the bounds of the root view
        Rect bounds = semanticsNodes.getBoundsInRoot();
        if (bounds == null || !isNodeContainsPoint(bounds, x, y)) {
            return null;
        }

        java.util.List<SemanticsNode> children = semanticsNodes.getChildren();
        if (children.isEmpty()) {
            // If it has no children, return the node itself
            return semanticsNodes;
        }

        // If it has children, search its children
        for (SemanticsNode child : children) {
            Object foundNode = findNodeAtPosition(child, x, y);
            if (foundNode != null) {
                return foundNode;
            }
        }

        // If no child nodes contain the point, return the parent
        return semanticsNodes;
    }

    /**
     * Gets a stable ID for the SemanticsNode, considering privacy masking
     * If the node or any of its parents are masked, returns a generic masked ID
     */
    public int getSemanticsNodeStableId(SemanticsNode semanticsNode) {
        if (semanticsNode == null) {
            return -1;
        }

        // Check if touches should be masked based on privacy settings
        if (shouldMaskTouch(semanticsNode)) {
            // Return a generic masked ID instead of the actual node ID
            Log.d(TAG, "Touch masked for node: " + semanticsNode.getId());
            return MASKED_TOUCH_ID;
        }

        // Use the ID of the SemanticsNode as a stable ID
        return semanticsNode.getId();
    }

    /**
     * Determines if touches on this node should be masked based on privacy settings
     */
    private boolean shouldMaskTouch(SemanticsNode semanticsNode) {
        // Check global touch masking setting
        if (sessionReplayConfiguration.isMaskAllUserTouches()) {
            return true;
        }

        // Check if this node or any parent has a mask privacy tag
        String privacyTag = com.newrelic.agent.android.sessionReplay.compose.ComposePrivacyUtils.INSTANCE.getEffectivePrivacyTag(semanticsNode);
        boolean isCustomMode = com.newrelic.agent.android.sessionReplay.compose.ComposeSessionReplayConstants.Modes.CUSTOM.equals(sessionReplayConfiguration.getMode());

        // In custom mode, check for explicit mask tags
        if (isCustomMode && com.newrelic.agent.android.sessionReplay.compose.ComposeSessionReplayConstants.PrivacyTags.MASK.equals(privacyTag)) {
            return true;
        }

        // Check if unmask tag is present (overrides masking)
        if (isCustomMode && com.newrelic.agent.android.sessionReplay.compose.ComposeSessionReplayConstants.PrivacyTags.UNMASK.equals(privacyTag)) {
            return false;
        }

        return false;
    }

    private boolean isNodeContainsPoint(Rect rect, int x, int y) {
        return (x >= rect.getLeft() && x <= rect.getRight() && y >= rect.getTop() && y <= rect.getBottom());
    }
}