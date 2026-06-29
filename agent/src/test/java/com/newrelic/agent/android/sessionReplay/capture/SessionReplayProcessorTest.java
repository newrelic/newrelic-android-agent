/*
 * Copyright (c) 2025 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay.capture;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import androidx.test.core.app.ApplicationProvider;

import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.RRWebIncrementalEvent;
import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.RRWebMutationData;
import com.newrelic.agent.android.sessionReplay.models.RRWebElementNode;
import com.newrelic.agent.android.sessionReplay.models.RRWebEvent;
import com.newrelic.agent.android.sessionReplay.viewMapper.SessionReplayViewThingy;
import com.newrelic.agent.android.sessionReplay.viewMapper.SessionReplayViewThingyInterface;
import com.newrelic.agent.android.sessionReplay.viewMapper.ViewDetails;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Tests for {@link SessionReplayProcessor} incremental-frame diffing, focused on the order
 * in which newly-added views are emitted as rrweb mutation ADD records (NR-576418).
 *
 * <p>rrweb appends added nodes with {@code nextId == null} (append as last child), so the
 * emission order of ADD records becomes the final DOM sibling order. The view-hierarchy
 * flatten must therefore be a forward pre-order DFS: parents before children, and siblings
 * in their natural front-to-back order. A LIFO flatten reverses sibling order, which made
 * dynamically-added views (e.g. nested RecyclerView/ViewPager2 sections) render in the wrong
 * order and visually clobber/hide each other.
 */
@RunWith(RobolectricTestRunner.class)
public class SessionReplayProcessorTest {

    private static final int WIDTH = 1080;
    private static final int HEIGHT = 1920;

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
    }

    /**
     * Core regression for NR-576418: when two new siblings B and C are added to an existing
     * parent, they must be emitted in natural order [B, C] — not reversed to [C, B].
     */
    @Test
    public void testIncrementalAdds_preserveSiblingOrder() {
        // Shared views (same instances => stable, matching viewIds across both frames)
        View rootView = new View(context);
        View viewA = new View(context);
        View viewB = new View(context);
        View viewC = new View(context);

        // Old frame: root -> [A]
        SessionReplayViewThingy oldRoot = thingy(rootView, thingy(viewA));

        // New frame: root -> [A, B, C]  (B and C are new)
        SessionReplayViewThingy newB = thingy(viewB);
        SessionReplayViewThingy newC = thingy(viewC);
        SessionReplayViewThingy newRoot = thingy(rootView, thingy(viewA), newB, newC);

        List<RRWebMutationData.AddRecord> adds = incrementalAdds(oldRoot, newRoot);

        Assert.assertEquals("Expected exactly the two new siblings to be added", 2, adds.size());
        Assert.assertEquals("First add must be B (natural sibling order)",
                newB.getViewId(), nodeId(adds.get(0)));
        Assert.assertEquals("Second add must be C (natural sibling order)",
                newC.getViewId(), nodeId(adds.get(1)));
    }

    /**
     * Stronger ordering check: four siblings added at once to a previously-empty parent must
     * be emitted in the exact order they appear in the new hierarchy.
     */
    @Test
    public void testIncrementalAdds_multipleSiblingsNaturalOrder() {
        View rootView = new View(context);
        View viewA = new View(context);
        View viewB = new View(context);
        View viewC = new View(context);
        View viewD = new View(context);

        // Old frame: root with no children
        SessionReplayViewThingy oldRoot = thingy(rootView);

        // New frame: root -> [A, B, C, D]
        SessionReplayViewThingy a = thingy(viewA);
        SessionReplayViewThingy b = thingy(viewB);
        SessionReplayViewThingy c = thingy(viewC);
        SessionReplayViewThingy d = thingy(viewD);
        SessionReplayViewThingy newRoot = thingy(rootView, a, b, c, d);

        List<RRWebMutationData.AddRecord> adds = incrementalAdds(oldRoot, newRoot);

        List<Integer> actualOrder = new ArrayList<>();
        for (RRWebMutationData.AddRecord add : adds) {
            actualOrder.add(nodeId(add));
        }
        Assert.assertEquals(
                Arrays.asList(a.getViewId(), b.getViewId(), c.getViewId(), d.getViewId()),
                actualOrder);
    }

    /**
     * A newly-added subtree must emit the parent before its child, so rrweb can attach the
     * child to a parent that already exists in the DOM.
     */
    @Test
    public void testIncrementalAdds_parentBeforeChild() {
        FrameLayout rootView = new FrameLayout(context);
        View viewA = new View(context);
        FrameLayout viewP = new FrameLayout(context);
        View viewQ = new View(context);

        // Old frame: root -> [A]
        rootView.addView(viewA);
        SessionReplayViewThingy oldRoot = thingy(rootView, thingy(viewA));

        // New frame: root -> [A, P -> [Q]]  (P and Q are new)
        rootView.addView(viewP);
        viewP.addView(viewQ);

        SessionReplayViewThingy newP = thingy(viewP, thingy(viewQ));
        SessionReplayViewThingy newRoot = thingy(rootView, thingy(viewA), newP);

        List<RRWebMutationData.AddRecord> adds = incrementalAdds(oldRoot, newRoot);

        int parentIndex = indexOfNode(adds, viewId(viewP));
        int childIndex = indexOfNode(adds, viewId(viewQ));

        Assert.assertTrue("Parent P should be added", parentIndex >= 0);
        Assert.assertTrue("Child Q should be added", childIndex >= 0);
        Assert.assertTrue("Parent must be emitted before its child",
                parentIndex < childIndex);

        RRWebMutationData.AddRecord parentAdd = addForNodeId(adds, viewId(viewP));
        RRWebMutationData.AddRecord childAdd = addForNodeId(adds, viewId(viewQ));
        Assert.assertNotNull("Parent add record should exist", parentAdd);
        Assert.assertNotNull("Child add record should exist", childAdd);
        Assert.assertEquals("Parent P should be attached to root",
                viewId(rootView), parentAdd.parentId);
        Assert.assertEquals("Child Q should be attached to parent P",
                viewId(viewP), childAdd.parentId);
    }

    // ==================== HELPERS ====================

    /** Builds a thingy for the given view with the supplied children wired as subviews. */
    private SessionReplayViewThingy thingy(View view, SessionReplayViewThingyInterface... children) {
        SessionReplayViewThingy t = new SessionReplayViewThingy(new ViewDetails(view));
        t.setSubviews(Arrays.asList(children));
        return t;
    }

    /** The stable viewId the agent assigns to a given view (cached on the view's tag). */
    private int viewId(View view) {
        return new ViewDetails(view).viewId;
    }

    private int nodeId(RRWebMutationData.AddRecord add) {
        return ((RRWebElementNode) add.node).id;
    }

    private int indexOfNode(List<RRWebMutationData.AddRecord> adds, int viewId) {
        for (int i = 0; i < adds.size(); i++) {
            if (nodeId(adds.get(i)) == viewId) {
                return i;
            }
        }
        return -1;
    }

    private RRWebMutationData.AddRecord addForNodeId(List<RRWebMutationData.AddRecord> adds, int viewId) {
        for (RRWebMutationData.AddRecord add : adds) {
            if (nodeId(add) == viewId) {
                return add;
            }
        }
        return null;
    }

    /**
     * Runs an old frame (full snapshot) followed by a new frame (incremental) through the
     * processor and returns the ADD records from the resulting mutation event.
     */
    private List<RRWebMutationData.AddRecord> incrementalAdds(
            SessionReplayViewThingy oldRoot, SessionReplayViewThingy newRoot) {
        SessionReplayProcessor processor = new SessionReplayProcessor();
        SessionReplayFrame oldFrame = new SessionReplayFrame(oldRoot, 1000L, WIDTH, HEIGHT);
        SessionReplayFrame newFrame = new SessionReplayFrame(newRoot, 2000L, WIDTH, HEIGHT);

        List<RRWebEvent> events = processor.processFrames(Arrays.asList(oldFrame, newFrame), false);

        for (RRWebEvent event : events) {
            if (event instanceof RRWebIncrementalEvent) {
                RRWebIncrementalEvent incremental = (RRWebIncrementalEvent) event;
                if (incremental.data instanceof RRWebMutationData) {
                    return ((RRWebMutationData) incremental.data).adds;
                }
            }
        }
        throw new AssertionError("No mutation incremental event was produced");
    }
}