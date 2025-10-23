
package com.newrelic.agent.android.sessionReplay.compose;

import androidx.compose.ui.node.LayoutNode;
import androidx.compose.ui.platform.AndroidComposeView;
import androidx.compose.ui.semantics.SemanticsNode;
import androidx.compose.ui.semantics.SemanticsOwner;

import com.newrelic.agent.android.sessionReplay.SessionReplayThingyRecorder;
import com.newrelic.agent.android.sessionReplay.SessionReplayViewThingyInterface;
import com.newrelic.agent.android.sessionReplay.internal.ReflectionUtils;

import java.util.ArrayList;

/**
 * Captures Compose UI tree structure for session replay recording.
 * Recursively traverses the semantics tree and converts nodes to replay format.
 */
public class ComposeTreeCapture {

    private final SessionReplayThingyRecorder recorder;

    public ComposeTreeCapture(SessionReplayThingyRecorder recorder) {
        this.recorder = recorder;
    }

    /**
     * Captures the Compose view hierarchy starting from the root.
     *
     * @param view The AndroidComposeView to capture
     * @return The captured view hierarchy, or null if capture fails
     */
    public SessionReplayViewThingyInterface captureComposeView(AndroidComposeView view) {

        if (view == null) {
            return null;
        }

        float density = view.getResources().getDisplayMetrics().density;

        SemanticsOwner semanticsOwner = view.getSemanticsOwner();
        if (semanticsOwner == null) {
            return null;
        }

        SemanticsNode rootNode = semanticsOwner.getUnmergedRootSemanticsNode();
        if (rootNode == null) {
            return null;
        }

        return captureChildren(rootNode, density);
    }

    /**
     * Recursively captures child nodes.
     *
     * @param node Current node to capture
     * @param density Display density for coordinate conversion
     * @return Captured node with children, or null if node should be skipped
     */

    private SessionReplayViewThingyInterface captureChildren(SemanticsNode node, float density) {

        ArrayList<SessionReplayViewThingyInterface> childThingies = new ArrayList<>();
        for (SemanticsNode child : node.getChildren()) {
            childThingies.add(captureChildren(child,density));
        }
        SessionReplayViewThingyInterface replayView = recorder.recordView(node, density);
        replayView.setSubviews(childThingies);
        return replayView;
    }

    /**
     * Determines if a node should be recorded in the session replay.
     * Only placed nodes (visible in the layout) are recorded.
     *
     * @param node The node to check
     * @return true if the node should be recorded, false otherwise
     */
    private boolean shouldRecordView(SemanticsNode node) {
        if (node == null) {
            return false;
        }

        try {
            LayoutNode layoutNode = ReflectionUtils.getLayoutNode(node);
            return layoutNode != null && layoutNode.isPlaced();
        } catch (Exception e) {
            // Log error and skip node if reflection fails
            return false;
        }
    }
}
