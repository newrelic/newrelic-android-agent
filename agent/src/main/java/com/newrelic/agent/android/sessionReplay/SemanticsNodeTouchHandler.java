package com.newrelic.agent.android.sessionReplay;

import android.view.View;
import androidx.compose.ui.geometry.Rect;
import androidx.compose.ui.platform.AndroidComposeView;
import androidx.compose.ui.semantics.SemanticsNode;
import androidx.compose.ui.semantics.SemanticsOwner;

public class SemanticsNodeTouchHandler {

    public Object getComposeSemanticsNode(View foundView, int x, int y) {
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
        if (!isNodeContainsPoint(semanticsNodes.getBoundsInRoot(), x, y)) {
            return null;
        }

        if (semanticsNodes.getChildren().isEmpty()) {
            // If it has no children, return the node itself
            return semanticsNodes;
        }

        // If it has children, search its children
        for (SemanticsNode child : semanticsNodes.getChildren()) {
            Object foundNode = findNodeAtPosition(child, x, y);
            if (foundNode != null) {
                return foundNode;
            }
        }

        // If no child nodes contain the point, return the parent
        return semanticsNodes;
    }

    public int getSemanticsNodeStableId(SemanticsNode semanticsNode) {
        if (semanticsNode == null) {
            return -1;
        }
        // Use the hashcode of the SemanticsNode as a stable ID
        return semanticsNode.getId();
    }

    private boolean isNodeContainsPoint(Rect rect, int x, int y) {
        return (x >= rect.getLeft() && x <= rect.getRight() && y >= rect.getTop() && y <= rect.getBottom());
    }
}