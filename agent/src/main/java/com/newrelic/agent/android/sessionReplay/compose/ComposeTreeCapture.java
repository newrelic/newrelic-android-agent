
package com.newrelic.agent.android.sessionReplay.compose;

import static com.newrelic.agent.android.sessionReplay.compose.NewRelicSemanticsPropertiesKt.NewRelicPrivacyKey;

import androidx.compose.ui.node.LayoutNode;
import androidx.compose.ui.platform.AndroidComposeView;
import androidx.compose.ui.semantics.SemanticsConfiguration;
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

        // Check if root node has MASK or UNMASK tags (both propagate, but MASK overrides UNMASK)
        // Optimization: Check contains() once and reuse the result
        SemanticsConfiguration rootConfig = rootNode.getConfig();
        String rootTag = rootConfig.contains(NewRelicPrivacyKey) ? rootConfig.get(NewRelicPrivacyKey) : "";
        boolean rootHasMask = ComposeSessionReplayConstants.PrivacyTags.MASK.equals(rootTag);
        boolean rootHasUnmask = ComposeSessionReplayConstants.PrivacyTags.UNMASK.equals(rootTag);

        return captureChildren(rootNode, density, rootHasMask, rootHasUnmask);
    }

    /**
     * Recursively captures child nodes with privacy tag propagation.
     *
     * <h3>Privacy Tag Inheritance Rules:</h3>
     * <ol>
     *   <li><b>Explicit Child Tags Take Precedence:</b> If a child has an explicit MASK or UNMASK tag, it wins</li>
     *   <li><b>MASK is Dominant:</b> Parent MASK overrides child UNMASK (security-first approach)</li>
     *   <li><b>Both Tags Propagate:</b> MASK and UNMASK both inherit to descendants</li>
     *   <li><b>Config Mutation for Performance:</b> Tags are written to SemanticsConfiguration during traversal
     *       to eliminate O(depth) parent chain walks in subsequent lookups</li>
     * </ol>
     *
     * <h3>Performance Optimization:</h3>
     * This approach reduces privacy tag lookup from O(n * depth) to O(n) for the entire tree,
     * where n is the number of nodes and depth is the tree depth. By setting tags during capture,
     * downstream code (ComposeImageThingy, ComposeTextViewThingy) can use O(1) lookups via
     * {@code ComposePrivacyUtils.getEffectivePrivacyTag()}.
     *
     * <h3>Example Scenarios:</h3>
     * <pre>
     * Scenario 1: MASK Dominance
     *   Parent: MASK → Child: UNMASK → Result: Child becomes MASK
     *
     * Scenario 2: Explicit Tag Wins
     *   Parent: UNMASK → Child: MASK → Result: Child stays MASK
     *
     * Scenario 3: Inheritance
     *   Parent: MASK → Child: (no tag) → Result: Child inherits MASK
     *
     * Scenario 4: No Propagation Needed
     *   Parent: (no tag) → Child: (no tag) → Result: Both remain untagged
     * </pre>
     *
     * @param node Current node to capture
     * @param density Display density for coordinate conversion (dp to px)
     * @param parentHasMask If true, parent has MASK tag (will force descendants to MASK)
     * @param parentHasUnmask If true, parent has UNMASK tag (will propagate unless overridden)
     * @return Captured node with children, or null if node should be skipped
     */
    private SessionReplayViewThingyInterface captureChildren(SemanticsNode node, float density, boolean parentHasMask, boolean parentHasUnmask) {

        ArrayList<SessionReplayViewThingyInterface> childThingies = new ArrayList<>();
        for (SemanticsNode child : node.getChildren()) {

            SemanticsConfiguration childConfig = child.getConfig();

            // Optimization: Check contains() once and reuse the result
            String childTag = childConfig.contains(NewRelicPrivacyKey) ? childConfig.get(NewRelicPrivacyKey) : "";
            boolean childHasMask = ComposeSessionReplayConstants.PrivacyTags.MASK.equals(childTag);
            boolean childHasUnmask = ComposeSessionReplayConstants.PrivacyTags.UNMASK.equals(childTag);

            // Determine effective tag state using priority rules
            boolean effectiveMask;
            boolean effectiveUnmask;

            if (childHasMask) {
                // Rule 1: Child's explicit MASK takes absolute precedence
                effectiveMask = true;
                effectiveUnmask = false;

            } else if (childHasUnmask && !parentHasMask) {
                // Rule 2: Child's explicit UNMASK applies if parent doesn't have MASK
                effectiveMask = false;
                effectiveUnmask = true;
                // Note: Config already has UNMASK, but we set it for descendants to inherit
                childConfig.set(NewRelicPrivacyKey, ComposeSessionReplayConstants.PrivacyTags.UNMASK);

            } else if (parentHasMask) {
                // Rule 3: Parent MASK is dominant - overrides child UNMASK or propagates to untagged children
                effectiveMask = true;
                effectiveUnmask = false;
                childConfig.set(NewRelicPrivacyKey, ComposeSessionReplayConstants.PrivacyTags.MASK);

            } else if (parentHasUnmask) {
                // Rule 4: Parent UNMASK propagates to untagged children
                effectiveMask = false;
                effectiveUnmask = true;
                childConfig.set(NewRelicPrivacyKey, ComposeSessionReplayConstants.PrivacyTags.UNMASK);

            } else {
                // Rule 5: No tags anywhere in the hierarchy
                effectiveMask = false;
                effectiveUnmask = false;
            }

            // Recurse with effective tag flags to propagate to all descendants
            childThingies.add(captureChildren(child, density, effectiveMask, effectiveUnmask));
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
