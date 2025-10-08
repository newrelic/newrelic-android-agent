
package com.newrelic.agent.android.sessionReplay.compose;

import androidx.compose.ui.node.LayoutNode;
import androidx.compose.ui.platform.AndroidComposeView;
import androidx.compose.ui.semantics.SemanticsNode;
import androidx.compose.ui.semantics.SemanticsOwner;

import com.newrelic.agent.android.sessionReplay.SessionReplayThingyRecorder;
import com.newrelic.agent.android.sessionReplay.SessionReplayViewThingyInterface;
import com.newrelic.agent.android.sessionReplay.internal.ReflectionUtils;

import java.util.ArrayList;

public class ComposeTreeCapture {


    private float density;
    private SessionReplayThingyRecorder recorder;

    public ComposeTreeCapture(SessionReplayThingyRecorder recorder) {
        this.recorder = recorder;
    }
    public SessionReplayViewThingyInterface captureComposeView(AndroidComposeView view) {

        this.density = view.getResources().getDisplayMetrics().density;

        SemanticsOwner semanticsOwner = (SemanticsOwner) view.getSemanticsOwner();
        SemanticsNode semanticsNode = semanticsOwner.getUnmergedRootSemanticsNode();

        SessionReplayViewThingyInterface replayView = captureChildren(semanticsNode);

        return replayView;
    }

    private SessionReplayViewThingyInterface captureChildren(SemanticsNode node) {

        ArrayList<SessionReplayViewThingyInterface> childThingies = new ArrayList<>();



        for (SemanticsNode child : node.getChildren()) {
            childThingies.add(captureChildren(child));
        }



        SessionReplayViewThingyInterface replayView = recorder.recordView(node, density);
        replayView.setSubviews(childThingies);

        return replayView;
    }

    private boolean shouldRecordView(SemanticsNode node) {
        // Note: The original code appears incomplete here
        LayoutNode layoutNode = ReflectionUtils.getLayoutNode(node);
        return layoutNode.isPlaced();
    }
}
