///*
// * Copyright (c) 2024. New Relic Corporation. All rights reserved.
// * SPDX-License-Identifier: Apache-2.0
// */
//
//package com.newrelic.agent.android.sessionReplay;
//
//import android.content.Context;
//import android.view.View;
//import android.widget.FrameLayout;
//import android.widget.ImageView;
//import android.widget.LinearLayout;
//import android.widget.TextView;
//
//import androidx.test.core.app.ApplicationProvider;
//
//import com.newrelic.agent.android.AgentConfiguration;
//import com.newrelic.agent.android.R;
//
//import org.junit.Assert;
//import org.junit.Before;
//import org.junit.Test;
//import org.junit.runner.RunWith;
//import org.robolectric.RobolectricTestRunner;
//
//@RunWith(RobolectricTestRunner.class)
//public class SessionReplayCaptureTest {
//
//    private Context context;
//    private AgentConfiguration agentConfiguration;
//    private SessionReplayConfiguration sessionReplayConfiguration;
//    private SessionReplayLocalConfiguration sessionReplayLocalConfiguration;
//
//    @Before
//    public void setUp() {
//        context = ApplicationProvider.getApplicationContext();
//
//        agentConfiguration = new AgentConfiguration();
//
//        sessionReplayConfiguration = new SessionReplayConfiguration();
//        sessionReplayConfiguration.setEnabled(true);
//        sessionReplayConfiguration.setMode("full");
//        sessionReplayConfiguration.setMaskUserInputText(false);
//        sessionReplayConfiguration.setMaskApplicationText(false);
//        sessionReplayConfiguration.setMaskAllImages(false);
//
//        sessionReplayLocalConfiguration = new SessionReplayLocalConfiguration();
//
//        agentConfiguration.setSessionReplayConfiguration(sessionReplayConfiguration);
//        agentConfiguration.setSessionReplayLocalConfiguration(sessionReplayLocalConfiguration);
//    }
//
//    // ==================== CONSTRUCTOR TESTS ====================
//
//    @Test
//    public void testConstructor() {
//        SessionReplayCapture capture = new SessionReplayCapture(agentConfiguration);
//        Assert.assertNotNull(capture);
//    }
//
//    @Test
//    public void testConstructorWithNullConfig() {
//        try {
//            SessionReplayCapture capture = new SessionReplayCapture(null);
//            // May or may not throw - depends on implementation
//        } catch (NullPointerException e) {
//            // Expected if null config is not allowed
//        }
//    }
//
//    // ==================== SINGLE VIEW CAPTURE TESTS ====================
//
//    @Test
//    public void testCapture_SingleView() {
//        SessionReplayCapture capture = new SessionReplayCapture(agentConfiguration);
//        View view = new View(context);
//        view.layout(0, 0, 100, 100);
//
//        SessionReplayViewThingyInterface thingy = capture.capture(view, agentConfiguration);
//
//        Assert.assertNotNull(thingy);
//        Assert.assertNotNull(thingy.getViewDetails());
//    }
//
//    @Test
//    public void testCapture_TextView() {
//        SessionReplayCapture capture = new SessionReplayCapture(agentConfiguration);
//        TextView textView = new TextView(context);
//        textView.setText("Test");
//        textView.layout(0, 0, 100, 100);
//
//        SessionReplayViewThingyInterface thingy = capture.capture(textView, agentConfiguration);
//
//        Assert.assertNotNull(thingy);
//        Assert.assertTrue(thingy instanceof SessionReplayTextViewThingy);
//    }
//
//    @Test
//    public void testCapture_ImageView() {
//        SessionReplayCapture capture = new SessionReplayCapture(agentConfiguration);
//        ImageView imageView = new ImageView(context);
//        imageView.layout(0, 0, 100, 100);
//
//        SessionReplayViewThingyInterface thingy = capture.capture(imageView, agentConfiguration);
//
//        Assert.assertNotNull(thingy);
//        Assert.assertTrue(thingy instanceof SessionReplayImageViewThingy);
//    }
//
//    // ==================== VIEW GROUP CAPTURE TESTS ====================
//
//    @Test
//    public void testCapture_EmptyViewGroup() {
//        SessionReplayCapture capture = new SessionReplayCapture(agentConfiguration);
//        FrameLayout frameLayout = new FrameLayout(context);
//        frameLayout.layout(0, 0, 200, 200);
//
//        SessionReplayViewThingyInterface thingy = capture.capture(frameLayout, agentConfiguration);
//
//        Assert.assertNotNull(thingy);
//        Assert.assertNotNull(thingy.getSubviews());
//        Assert.assertTrue(thingy.getSubviews().isEmpty());
//    }
//
//    @Test
//    public void testCapture_ViewGroupWithOneChild() {
//        SessionReplayCapture capture = new SessionReplayCapture(agentConfiguration);
//        FrameLayout parent = new FrameLayout(context);
//        parent.layout(0, 0, 200, 200);
//
//        View child = new View(context);
//        child.layout(0, 0, 100, 100);
//        parent.addView(child);
//
//        SessionReplayViewThingyInterface thingy = capture.capture(parent, agentConfiguration);
//
//        Assert.assertNotNull(thingy);
//        Assert.assertNotNull(thingy.getSubviews());
//        Assert.assertEquals(1, thingy.getSubviews().size());
//    }
//
//    @Test
//    public void testCapture_ViewGroupWithMultipleChildren() {
//        SessionReplayCapture capture = new SessionReplayCapture(agentConfiguration);
//        LinearLayout parent = new LinearLayout(context);
//        parent.layout(0, 0, 300, 300);
//
//        for (int i = 0; i < 5; i++) {
//            View child = new View(context);
//            child.layout(0, i * 50, 100, (i + 1) * 50);
//            parent.addView(child);
//        }
//
//        SessionReplayViewThingyInterface thingy = capture.capture(parent, agentConfiguration);
//
//        Assert.assertNotNull(thingy);
//        Assert.assertEquals(5, thingy.getSubviews().size());
//    }
//
//    @Test
//    public void testCapture_NestedViewGroups() {
//        SessionReplayCapture capture = new SessionReplayCapture(agentConfiguration);
//
//        FrameLayout level1 = new FrameLayout(context);
//        level1.layout(0, 0, 300, 300);
//
//        LinearLayout level2 = new LinearLayout(context);
//        level2.layout(0, 0, 200, 200);
//        level1.addView(level2);
//
//        View level3 = new View(context);
//        level3.layout(0, 0, 100, 100);
//        level2.addView(level3);
//
//        SessionReplayViewThingyInterface thingy = capture.capture(level1, agentConfiguration);
//
//        Assert.assertNotNull(thingy);
//        Assert.assertEquals(1, thingy.getSubviews().size());
//
//        SessionReplayViewThingyInterface level2Thingy = thingy.getSubviews().get(0);
//        Assert.assertEquals(1, level2Thingy.getSubviews().size());
//    }
//
//    @Test
//    public void testCapture_DeeplyNestedHierarchy() {
//        SessionReplayCapture capture = new SessionReplayCapture(agentConfiguration);
//
//        FrameLayout root = new FrameLayout(context);
//        root.layout(0, 0, 400, 400);
//
//        ViewGroup current = root;
//        for (int i = 0; i < 10; i++) {
//            FrameLayout child = new FrameLayout(context);
//            child.layout(0, 0, 300 - i * 20, 300 - i * 20);
//            current.addView(child);
//            current = child;
//        }
//
//        SessionReplayViewThingyInterface thingy = capture.capture(root, agentConfiguration);
//
//        Assert.assertNotNull(thingy);
//
//        // Verify depth
//        SessionReplayViewThingyInterface currentThingy = thingy;
//        int depth = 0;
//        while (!currentThingy.getSubviews().isEmpty()) {
//            currentThingy = currentThingy.getSubviews().get(0);
//            depth++;
//        }
//        Assert.assertEquals(10, depth);
//    }
//
//    // ==================== VISIBILITY FILTERING TESTS ====================
//
//    @Test
//    public void testCapture_InvisibleViewNotCaptured() {
//        SessionReplayCapture capture = new SessionReplayCapture(agentConfiguration);
//        FrameLayout parent = new FrameLayout(context);
//        parent.layout(0, 0, 200, 200);
//
//        View visibleChild = new View(context);
//        visibleChild.layout(0, 0, 100, 100);
//        visibleChild.setVisibility(View.VISIBLE);
//        parent.addView(visibleChild);
//
//        View invisibleChild = new View(context);
//        invisibleChild.layout(0, 0, 100, 100);
//        invisibleChild.setVisibility(View.INVISIBLE);
//        parent.addView(invisibleChild);
//
//        SessionReplayViewThingyInterface thingy = capture.capture(parent, agentConfiguration);
//
//        // Only visible child should be captured
//        Assert.assertEquals(1, thingy.getSubviews().size());
//    }
//
//    @Test
//    public void testCapture_GoneViewNotCaptured() {
//        SessionReplayCapture capture = new SessionReplayCapture(agentConfiguration);
//        FrameLayout parent = new FrameLayout(context);
//        parent.layout(0, 0, 200, 200);
//
//        View visibleChild = new View(context);
//        visibleChild.layout(0, 0, 100, 100);
//        visibleChild.setVisibility(View.VISIBLE);
//        parent.addView(visibleChild);
//
//        View goneChild = new View(context);
//        goneChild.layout(0, 0, 100, 100);
//        goneChild.setVisibility(View.GONE);
//        parent.addView(goneChild);
//
//        SessionReplayViewThingyInterface thingy = capture.capture(parent, agentConfiguration);
//
//        // Only visible child should be captured
//        Assert.assertEquals(1, thingy.getSubviews().size());
//    }
//
//    @Test
//    public void testCapture_ZeroAlphaViewNotCaptured() {
//        SessionReplayCapture capture = new SessionReplayCapture(agentConfiguration);
//        FrameLayout parent = new FrameLayout(context);
//        parent.layout(0, 0, 200, 200);
//
//        View visibleChild = new View(context);
//        visibleChild.layout(0, 0, 100, 100);
//        visibleChild.setAlpha(1.0f);
//        parent.addView(visibleChild);
//
//        View transparentChild = new View(context);
//        transparentChild.layout(0, 0, 100, 100);
//        transparentChild.setAlpha(0.0f);
//        parent.addView(transparentChild);
//
//        SessionReplayViewThingyInterface thingy = capture.capture(parent, agentConfiguration);
//
//        // Only visible child should be captured
//        Assert.assertEquals(1, thingy.getSubviews().size());
//    }
//
//    @Test
//    public void testCapture_PartiallyTransparentViewIsCaptured() {
//        SessionReplayCapture capture = new SessionReplayCapture(agentConfiguration);
//        FrameLayout parent = new FrameLayout(context);
//        parent.layout(0, 0, 200, 200);
//
//        View partiallyTransparent = new View(context);
//        partiallyTransparent.layout(0, 0, 100, 100);
//        partiallyTransparent.setAlpha(0.5f);
//        parent.addView(partiallyTransparent);
//
//        SessionReplayViewThingyInterface thingy = capture.capture(parent, agentConfiguration);
//
//        // Partially transparent view should still be captured
//        Assert.assertEquals(1, thingy.getSubviews().size());
//    }
//
//    // ==================== PRIVACY TAG TESTS ====================
//
//    @Test
//    public void testCapture_PrivacyTag_NrMask() {
//        SessionReplayCapture capture = new SessionReplayCapture(agentConfiguration);
//        FrameLayout parent = new FrameLayout(context);
//        parent.layout(0, 0, 200, 200);
//
//        TextView textView = new TextView(context);
//        textView.setText("Sensitive Data");
//        textView.layout(0, 0, 100, 100);
//        textView.setTag(R.id.newrelic_privacy, "nr-mask");
//        parent.addView(textView);
//
//        SessionReplayViewThingyInterface thingy = capture.capture(parent, agentConfiguration);
//
//        Assert.assertEquals(1, thingy.getSubviews().size());
//        // Tag should be propagated to child
//    }
//
//    @Test
//    public void testCapture_PrivacyTag_NrUnmask() {
//        SessionReplayCapture capture = new SessionReplayCapture(agentConfiguration);
//        FrameLayout parent = new FrameLayout(context);
//        parent.layout(0, 0, 200, 200);
//
//        TextView textView = new TextView(context);
//        textView.setText("Public Data");
//        textView.layout(0, 0, 100, 100);
//        textView.setTag(R.id.newrelic_privacy, "nr-unmask");
//        parent.addView(textView);
//
//        SessionReplayViewThingyInterface thingy = capture.capture(parent, agentConfiguration);
//
//        Assert.assertEquals(1, thingy.getSubviews().size());
//    }
//
//    @Test
//    public void testCapture_PrivacyTag_MaskPropagation() {
//        SessionReplayCapture capture = new SessionReplayCapture(agentConfiguration);
//        FrameLayout parent = new FrameLayout(context);
//        parent.layout(0, 0, 300, 300);
//        parent.setTag(R.id.newrelic_privacy, "nr-mask");
//
//        LinearLayout child = new LinearLayout(context);
//        child.layout(0, 0, 200, 200);
//        parent.addView(child);
//
//        View grandChild = new View(context);
//        grandChild.layout(0, 0, 100, 100);
//        child.addView(grandChild);
//
//        SessionReplayViewThingyInterface thingy = capture.capture(parent, agentConfiguration);
//
//        Assert.assertNotNull(thingy);
//        // Mask should propagate to descendants
//    }
//
//    @Test
//    public void testCapture_PrivacyTag_UnmaskOverridesMaskInParent() {
//        SessionReplayCapture capture = new SessionReplayCapture(agentConfiguration);
//        FrameLayout parent = new FrameLayout(context);
//        parent.layout(0, 0, 300, 300);
//        parent.setTag(R.id.newrelic_privacy, "nr-mask");
//
//        View child = new View(context);
//        child.layout(0, 0, 200, 200);
//        child.setTag(R.id.newrelic_privacy, "nr-unmask");
//        parent.addView(child);
//
//        SessionReplayViewThingyInterface thingy = capture.capture(parent, agentConfiguration);
//
//        Assert.assertEquals(1, thingy.getSubviews().size());
//        // Child should have nr-unmask tag
//    }
//
//    @Test
//    public void testCapture_GeneralTag_NrMask() {
//        SessionReplayCapture capture = new SessionReplayCapture(agentConfiguration);
//        FrameLayout parent = new FrameLayout(context);
//        parent.layout(0, 0, 200, 200);
//
//        View child = new View(context);
//        child.layout(0, 0, 100, 100);
//        child.setTag("nr-mask");
//        parent.addView(child);
//
//        SessionReplayViewThingyInterface thingy = capture.capture(parent, agentConfiguration);
//
//        Assert.assertEquals(1, thingy.getSubviews().size());
//    }
//
//    @Test
//    public void testCapture_GeneralTag_NrUnmask() {
//        SessionReplayCapture capture = new SessionReplayCapture(agentConfiguration);
//        FrameLayout parent = new FrameLayout(context);
//        parent.layout(0, 0, 200, 200);
//
//        View child = new View(context);
//        child.layout(0, 0, 100, 100);
//        child.setTag("nr-unmask");
//        parent.addView(child);
//
//        SessionReplayViewThingyInterface thingy = capture.capture(parent, agentConfiguration);
//
//        Assert.assertEquals(1, thingy.getSubviews().size());
//    }
//
//    // ==================== MIXED VIEW TYPE TESTS ====================
//
//    @Test
//    public void testCapture_MixedViewTypes() {
//        SessionReplayCapture capture = new SessionReplayCapture(agentConfiguration);
//        LinearLayout parent = new LinearLayout(context);
//        parent.layout(0, 0, 400, 400);
//
//        View view = new View(context);
//        view.layout(0, 0, 100, 100);
//        parent.addView(view);
//
//        TextView textView = new TextView(context);
//        textView.setText("Text");
//        textView.layout(0, 100, 100, 200);
//        parent.addView(textView);
//
//        ImageView imageView = new ImageView(context);
//        imageView.layout(0, 200, 100, 300);
//        parent.addView(imageView);
//
//        SessionReplayViewThingyInterface thingy = capture.capture(parent, agentConfiguration);
//
//        Assert.assertEquals(3, thingy.getSubviews().size());
//        Assert.assertTrue(thingy.getSubviews().get(0) instanceof SessionReplayViewThingy);
//        Assert.assertTrue(thingy.getSubviews().get(1) instanceof SessionReplayTextViewThingy);
//        Assert.assertTrue(thingy.getSubviews().get(2) instanceof SessionReplayImageViewThingy);
//    }
//
//    // ==================== EDGE CASE TESTS ====================
//
//    @Test
//    public void testCapture_NullChild() {
//        SessionReplayCapture capture = new SessionReplayCapture(agentConfiguration);
//        FrameLayout parent = new FrameLayout(context);
//        parent.layout(0, 0, 200, 200);
//
//        View child = new View(context);
//        child.layout(0, 0, 100, 100);
//        parent.addView(child);
//
//        // Capture should handle null children gracefully
//        SessionReplayViewThingyInterface thingy = capture.capture(parent, agentConfiguration);
//
//        Assert.assertNotNull(thingy);
//    }
//
//    @Test
//    public void testCapture_ViewWithZeroSize() {
//        SessionReplayCapture capture = new SessionReplayCapture(agentConfiguration);
//        View view = new View(context);
//        // No layout - view has zero size
//
//        SessionReplayViewThingyInterface thingy = capture.capture(view, agentConfiguration);
//
//        Assert.assertNotNull(thingy);
//    }
//
//    @Test
//    public void testCapture_ComplexRealWorldHierarchy() {
//        SessionReplayCapture capture = new SessionReplayCapture(agentConfiguration);
//
//        // Simulate a real app layout
//        FrameLayout root = new FrameLayout(context);
//        root.layout(0, 0, 1080, 1920);
//
//        LinearLayout header = new LinearLayout(context);
//        header.layout(0, 0, 1080, 200);
//        root.addView(header);
//
//        TextView title = new TextView(context);
//        title.setText("App Title");
//        title.layout(0, 0, 540, 200);
//        header.addView(title);
//
//        ImageView logo = new ImageView(context);
//        logo.layout(540, 0, 1080, 200);
//        header.addView(logo);
//
//        LinearLayout content = new LinearLayout(context);
//        content.layout(0, 200, 1080, 1720);
//        root.addView(content);
//
//        for (int i = 0; i < 10; i++) {
//            TextView item = new TextView(context);
//            item.setText("Item " + i);
//            item.layout(0, i * 152, 1080, (i + 1) * 152);
//            content.addView(item);
//        }
//
//        SessionReplayViewThingyInterface thingy = capture.capture(root, agentConfiguration);
//
//        Assert.assertNotNull(thingy);
//        Assert.assertEquals(2, thingy.getSubviews().size()); // header + content
//
//        SessionReplayViewThingyInterface headerThingy = thingy.getSubviews().get(0);
//        Assert.assertEquals(2, headerThingy.getSubviews().size()); // title + logo
//
//        SessionReplayViewThingyInterface contentThingy = thingy.getSubviews().get(1);
//        Assert.assertEquals(10, contentThingy.getSubviews().size()); // 10 items
//    }
//
//    @Test
//    public void testCapture_AllChildrenInvisible() {
//        SessionReplayCapture capture = new SessionReplayCapture(agentConfiguration);
//        FrameLayout parent = new FrameLayout(context);
//        parent.layout(0, 0, 200, 200);
//
//        for (int i = 0; i < 5; i++) {
//            View child = new View(context);
//            child.layout(0, 0, 100, 100);
//            child.setVisibility(View.GONE);
//            parent.addView(child);
//        }
//
//        SessionReplayViewThingyInterface thingy = capture.capture(parent, agentConfiguration);
//
//        // No visible children should be captured
//        Assert.assertTrue(thingy.getSubviews().isEmpty());
//    }
//
//    @Test
//    public void testCapture_MixedVisibilityChildren() {
//        SessionReplayCapture capture = new SessionReplayCapture(agentConfiguration);
//        FrameLayout parent = new FrameLayout(context);
//        parent.layout(0, 0, 300, 300);
//
//        View visible1 = new View(context);
//        visible1.layout(0, 0, 100, 100);
//        visible1.setVisibility(View.VISIBLE);
//        parent.addView(visible1);
//
//        View invisible = new View(context);
//        invisible.layout(0, 0, 100, 100);
//        invisible.setVisibility(View.INVISIBLE);
//        parent.addView(invisible);
//
//        View visible2 = new View(context);
//        visible2.layout(0, 0, 100, 100);
//        visible2.setVisibility(View.VISIBLE);
//        parent.addView(visible2);
//
//        View gone = new View(context);
//        gone.layout(0, 0, 100, 100);
//        gone.setVisibility(View.GONE);
//        parent.addView(gone);
//
//        SessionReplayViewThingyInterface thingy = capture.capture(parent, agentConfiguration);
//
//        // Only 2 visible children should be captured
//        Assert.assertEquals(2, thingy.getSubviews().size());
//    }
//}
