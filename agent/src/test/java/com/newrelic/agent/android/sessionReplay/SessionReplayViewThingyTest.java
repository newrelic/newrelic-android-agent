///*
// * Copyright (c) 2024. New Relic Corporation. All rights reserved.
// * SPDX-License-Identifier: Apache-2.0
// */
//
//package com.newrelic.agent.android.sessionReplay;
//
//import android.content.Context;
//import android.graphics.Color;
//import android.view.View;
//import android.widget.FrameLayout;
//import android.widget.LinearLayout;
//
//import androidx.test.core.app.ApplicationProvider;
//
//import com.newrelic.agent.android.sessionReplay.models.Attributes;
//import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.MutationRecord;
//import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.RRWebMutationData;
//import com.newrelic.agent.android.sessionReplay.models.RRWebElementNode;
//
//import org.junit.Assert;
//import org.junit.Before;
//import org.junit.Test;
//import org.junit.runner.RunWith;
//import org.robolectric.RobolectricTestRunner;
//
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.List;
//
//@RunWith(RobolectricTestRunner.class)
//public class SessionReplayViewThingyTest {
//
//    private Context context;
//
//    @Before
//    public void setUp() {
//        context = ApplicationProvider.getApplicationContext();
//    }
//
//    // ==================== CONSTRUCTOR TESTS ====================
//
//    @Test
//    public void testConstructorWithViewDetails() {
//        View view = new View(context);
//        ViewDetails viewDetails = new ViewDetails(view);
//
//        SessionReplayViewThingy thingy = new SessionReplayViewThingy(viewDetails);
//
//        Assert.assertNotNull(thingy);
//        Assert.assertEquals(viewDetails, thingy.getViewDetails());
//    }
//
//    @Test
//    public void testConstructorInitializesEmptySubviewsList() {
//        View view = new View(context);
//        ViewDetails viewDetails = new ViewDetails(view);
//
//        SessionReplayViewThingy thingy = new SessionReplayViewThingy(viewDetails);
//
//        Assert.assertNotNull(thingy.getSubviews());
//        Assert.assertTrue(thingy.getSubviews().isEmpty());
//    }
//
//    // ==================== GETTER/SETTER TESTS ====================
//
//    @Test
//    public void testGetViewDetails() {
//        View view = new View(context);
//        ViewDetails viewDetails = new ViewDetails(view);
//
//        SessionReplayViewThingy thingy = new SessionReplayViewThingy(viewDetails);
//
//        Assert.assertEquals(viewDetails, thingy.getViewDetails());
//    }
//
//    @Test
//    public void testShouldRecordSubviews() {
//        View view = new View(context);
//        ViewDetails viewDetails = new ViewDetails(view);
//
//        SessionReplayViewThingy thingy = new SessionReplayViewThingy(viewDetails);
//
//        // Generic View should record subviews
//        Assert.assertTrue(thingy.shouldRecordSubviews());
//    }
//
//    @Test
//    public void testGetSubviews() {
//        View view = new View(context);
//        ViewDetails viewDetails = new ViewDetails(view);
//
//        SessionReplayViewThingy thingy = new SessionReplayViewThingy(viewDetails);
//
//        List<? extends SessionReplayViewThingyInterface> subviews = thingy.getSubviews();
//
//        Assert.assertNotNull(subviews);
//        Assert.assertTrue(subviews.isEmpty());
//    }
//
//    @Test
//    public void testSetSubviews() {
//        View view = new View(context);
//        ViewDetails viewDetails = new ViewDetails(view);
//        SessionReplayViewThingy thingy = new SessionReplayViewThingy(viewDetails);
//
//        // Create subviews
//        View childView1 = new View(context);
//        View childView2 = new View(context);
//        ViewDetails childDetails1 = new ViewDetails(childView1);
//        ViewDetails childDetails2 = new ViewDetails(childView2);
//
//        SessionReplayViewThingy child1 = new SessionReplayViewThingy(childDetails1);
//        SessionReplayViewThingy child2 = new SessionReplayViewThingy(childDetails2);
//
//        List<SessionReplayViewThingy> subviews = Arrays.asList(child1, child2);
//
//        thingy.setSubviews(subviews);
//
//        Assert.assertEquals(2, thingy.getSubviews().size());
//        Assert.assertEquals(child1, thingy.getSubviews().get(0));
//        Assert.assertEquals(child2, thingy.getSubviews().get(1));
//    }
//
//    @Test
//    public void testSetSubviewsWithEmptyList() {
//        View view = new View(context);
//        ViewDetails viewDetails = new ViewDetails(view);
//        SessionReplayViewThingy thingy = new SessionReplayViewThingy(viewDetails);
//
//        thingy.setSubviews(new ArrayList<>());
//
//        Assert.assertNotNull(thingy.getSubviews());
//        Assert.assertTrue(thingy.getSubviews().isEmpty());
//    }
//
//    @Test
//    public void testGetViewId() {
//        View view = new View(context);
//        view.setTag(NewRelicIdGenerator.NR_ID_TAG, 12345);
//        ViewDetails viewDetails = new ViewDetails(view);
//
//        SessionReplayViewThingy thingy = new SessionReplayViewThingy(viewDetails);
//
//        Assert.assertEquals(viewDetails.viewId, thingy.getViewId());
//    }
//
//    @Test
//    public void testGetParentViewId() {
//        FrameLayout parent = new FrameLayout(context);
//        parent.setTag(NewRelicIdGenerator.NR_ID_TAG, 999);
//
//        View childView = new View(context);
//        parent.addView(childView);
//
//        ViewDetails viewDetails = new ViewDetails(childView);
//
//        SessionReplayViewThingy thingy = new SessionReplayViewThingy(viewDetails);
//
//        Assert.assertEquals(viewDetails.parentId, thingy.getParentViewId());
//    }
//
//    // ==================== CSS GENERATION TESTS ====================
//
//    @Test
//    public void testGetCssSelector() {
//        View view = new View(context);
//        ViewDetails viewDetails = new ViewDetails(view);
//
//        SessionReplayViewThingy thingy = new SessionReplayViewThingy(viewDetails);
//
//        String cssSelector = thingy.getCssSelector();
//
//        Assert.assertNotNull(cssSelector);
//        Assert.assertEquals(viewDetails.getCssSelector(), cssSelector);
//    }
//
//    @Test
//    public void testGenerateCssDescription() {
//        View view = new View(context);
//        ViewDetails viewDetails = new ViewDetails(view);
//
//        SessionReplayViewThingy thingy = new SessionReplayViewThingy(viewDetails);
//
//        String cssDescription = thingy.generateCssDescription();
//
//        Assert.assertNotNull(cssDescription);
//        Assert.assertEquals(viewDetails.generateCssDescription(), cssDescription);
//    }
//
//    @Test
//    public void testGenerateInlineCss() {
//        View view = new View(context);
//        view.setBackgroundColor(Color.RED);
//        ViewDetails viewDetails = new ViewDetails(view);
//
//        SessionReplayViewThingy thingy = new SessionReplayViewThingy(viewDetails);
//
//        String inlineCss = thingy.generateInlineCss();
//
//        Assert.assertNotNull(inlineCss);
//        Assert.assertEquals(viewDetails.generateInlineCSS(), inlineCss);
//        Assert.assertTrue(inlineCss.contains("position:"));
//    }
//
//    @Test
//    public void testGenerateInlineCssWithTransparentBackground() {
//        View view = new View(context);
//        view.setBackgroundColor(Color.TRANSPARENT);
//        ViewDetails viewDetails = new ViewDetails(view);
//
//        SessionReplayViewThingy thingy = new SessionReplayViewThingy(viewDetails);
//
//        String inlineCss = thingy.generateInlineCss();
//        Assert.assertNotNull(inlineCss);
//    }
//
//    // ==================== RRWEB NODE GENERATION TESTS ====================
//
//    @Test
//    public void testGenerateRRWebNode() {
//        View view = new View(context);
//        ViewDetails viewDetails = new ViewDetails(view);
//
//        SessionReplayViewThingy thingy = new SessionReplayViewThingy(viewDetails);
//
//        RRWebElementNode node = thingy.generateRRWebNode();
//
//        Assert.assertNotNull(node);
//        Assert.assertNotNull(node.getAttributes());
//        Assert.assertEquals(RRWebElementNode.TAG_TYPE_DIV, node.getTagName());
//        Assert.assertEquals(viewDetails.viewId, node.getId());
//        Assert.assertNotNull(node.getChildNodes());
//    }
//
//    @Test
//    public void testGenerateRRWebNodeHasCorrectAttributes() {
//        View view = new View(context);
//        ViewDetails viewDetails = new ViewDetails(view);
//
//        SessionReplayViewThingy thingy = new SessionReplayViewThingy(viewDetails);
//
//        RRWebElementNode node = thingy.generateRRWebNode();
//
//        Attributes attributes = node.getAttributes();
//        Assert.assertNotNull(attributes);
//        Assert.assertEquals(viewDetails.getCSSSelector(), attributes.getCssSelector());
//    }
//
//    @Test
//    public void testGenerateRRWebNodeWithChildNodes() {
//        View view = new View(context);
//        ViewDetails viewDetails = new ViewDetails(view);
//
//        SessionReplayViewThingy thingy = new SessionReplayViewThingy(viewDetails);
//
//        RRWebElementNode node = thingy.generateRRWebNode();
//
//        Assert.assertNotNull(node.getChildNodes());
//        Assert.assertTrue(node.getChildNodes().isEmpty());
//    }
//
//    // ==================== DIFF GENERATION TESTS ====================
//
//    @Test
//    public void testGenerateDifferences_WithSameView() {
//        View view = new View(context);
//        ViewDetails viewDetails = new ViewDetails(view);
//
//        SessionReplayViewThingy thingy1 = new SessionReplayViewThingy(viewDetails);
//        SessionReplayViewThingy thingy2 = new SessionReplayViewThingy(viewDetails);
//
//        List<MutationRecord> diffs = thingy1.generateDifferences(thingy2);
//
//        Assert.assertNotNull(diffs);
//        Assert.assertEquals(1, diffs.size());
//    }
//
//    @Test
//    public void testGenerateDifferences_WithDifferentPosition() {
//        View view1 = new View(context);
//        view1.layout(0, 0, 100, 100);
//        ViewDetails viewDetails1 = new ViewDetails(view1);
//
//        View view2 = new View(context);
//        view2.layout(50, 50, 150, 150);
//        ViewDetails viewDetails2 = new ViewDetails(view2);
//
//        SessionReplayViewThingy thingy1 = new SessionReplayViewThingy(viewDetails1);
//        SessionReplayViewThingy thingy2 = new SessionReplayViewThingy(viewDetails2);
//
//        List<MutationRecord> diffs = thingy1.generateDifferences(thingy2);
//
//        Assert.assertNotNull(diffs);
//        Assert.assertFalse(diffs.isEmpty());
//        Assert.assertEquals(1, diffs.size());
//
//        // Should contain an AttributeRecord
//        Assert.assertTrue(diffs.get(0) instanceof RRWebMutationData.AttributeRecord);
//        RRWebMutationData.AttributeRecord attrRecord = (RRWebMutationData.AttributeRecord) diffs.get(0);
//        Assert.assertNotNull(attrRecord.getAttributes());
//        Assert.assertNotNull(attrRecord.getAttributes().getMetadata());
//    }
//
//    @Test
//    public void testGenerateDifferences_WithDifferentBackgroundColor() {
//        View view1 = new View(context);
//        view1.setBackgroundColor(Color.RED);
//        ViewDetails viewDetails1 = new ViewDetails(view1);
//
//        View view2 = new View(context);
//        view2.setBackgroundColor(Color.BLUE);
//        ViewDetails viewDetails2 = new ViewDetails(view2);
//
//        SessionReplayViewThingy thingy1 = new SessionReplayViewThingy(viewDetails1);
//        SessionReplayViewThingy thingy2 = new SessionReplayViewThingy(viewDetails2);
//
//        List<MutationRecord> diffs = thingy1.generateDifferences(thingy2);
//
//        Assert.assertNotNull(diffs);
//        Assert.assertFalse(diffs.isEmpty());
//        Assert.assertTrue(diffs.get(0) instanceof RRWebMutationData.AttributeRecord);
//    }
//
//    @Test
//    public void testGenerateDifferences_WithNullOther() {
//        View view = new View(context);
//        ViewDetails viewDetails = new ViewDetails(view);
//
//        SessionReplayViewThingy thingy = new SessionReplayViewThingy(viewDetails);
//
//        List<MutationRecord> diffs = thingy.generateDifferences(null);
//
//        Assert.assertNotNull(diffs);
//        Assert.assertTrue(diffs.isEmpty());
//    }
//
//    @Test
//    public void testGenerateDifferences_WithDifferentType() {
//        View view = new View(context);
//        ViewDetails viewDetails = new ViewDetails(view);
//
//        SessionReplayViewThingy thingy = new SessionReplayViewThingy(viewDetails);
//
//        // Create a mock different type
//        SessionReplayViewThingyInterface otherType = new SessionReplayViewThingyInterface() {
//            @Override
//            public ViewDetails getViewDetails() {
//                return viewDetails;
//            }
//
//            @Override
//            public boolean shouldRecordSubviews() {
//                return false;
//            }
//
//            @Override
//            public List<? extends SessionReplayViewThingyInterface> getSubviews() {
//                return null;
//            }
//
//            @Override
//            public void setSubviews(List<? extends SessionReplayViewThingyInterface> subviews) {
//            }
//
//            @Override
//            public String getCssSelector() {
//                return null;
//            }
//
//            @Override
//            public String generateCssDescription() {
//                return null;
//            }
//
//            @Override
//            public String generateInlineCss() {
//                return null;
//            }
//
//            @Override
//            public RRWebElementNode generateRRWebNode() {
//                return null;
//            }
//
//            @Override
//            public List<MutationRecord> generateDifferences(SessionReplayViewThingyInterface other) {
//                return null;
//            }
//
//            @Override
//            public List<RRWebMutationData.AddRecord> generateAdditionNodes(int parentId) {
//                return null;
//            }
//
//            @Override
//            public int getViewId() {
//                return 0;
//            }
//
//            @Override
//            public int getParentViewId() {
//                return 0;
//            }
//
//            @Override
//            public boolean hasChanged(SessionReplayViewThingyInterface other) {
//                return false;
//            }
//        };
//
//        List<MutationRecord> diffs = thingy.generateDifferences(otherType);
//
//        Assert.assertNotNull(diffs);
//        Assert.assertTrue(diffs.isEmpty());
//    }
//
//    // ==================== ADD RECORD GENERATION TESTS ====================
//
//    @Test
//    public void testGenerateAdditionNodes() {
//        View view = new View(context);
//        ViewDetails viewDetails = new ViewDetails(view);
//
//        SessionReplayViewThingy thingy = new SessionReplayViewThingy(viewDetails);
//
//        int parentId = 123;
//        List<RRWebMutationData.AddRecord> addRecords = thingy.generateAdditionNodes(parentId);
//
//        Assert.assertNotNull(addRecords);
//        Assert.assertEquals(1, addRecords.size());
//
//        RRWebMutationData.AddRecord addRecord = addRecords.get(0);
//        Assert.assertEquals(parentId, addRecord.getParentId());
//        Assert.assertNotNull(addRecord.getNode());
//        Assert.assertTrue(addRecord.getNode() instanceof RRWebElementNode);
//    }
//
//    @Test
//    public void testGenerateAdditionNodesIncludesInlineCss() {
//        View view = new View(context);
//        view.setBackgroundColor(Color.GREEN);
//        ViewDetails viewDetails = new ViewDetails(view);
//
//        SessionReplayViewThingy thingy = new SessionReplayViewThingy(viewDetails);
//
//        List<RRWebMutationData.AddRecord> addRecords = thingy.generateAdditionNodes(456);
//
//        RRWebElementNode node = (RRWebElementNode) addRecords.get(0).getNode();
//        Assert.assertNotNull(node.attributes.metadata);
//        Assert.assertTrue(node.attributes.metadata.containsKey("style"));
//
//        String style = node.attributes.metadata.get("style");
//        Assert.assertNotNull(style);
//        Assert.assertTrue(style.length() > 0);
//    }
//
//    @Test
//    public void testGenerateAdditionNodesWithZeroParentId() {
//        View view = new View(context);
//        ViewDetails viewDetails = new ViewDetails(view);
//
//        SessionReplayViewThingy thingy = new SessionReplayViewThingy(viewDetails);
//
//        List<RRWebMutationData.AddRecord> addRecords = thingy.generateAdditionNodes(0);
//
//        Assert.assertNotNull(addRecords);
//        Assert.assertEquals(1, addRecords.size());
//        Assert.assertEquals(0, addRecords.get(0).getParentId());
//    }
//
//    @Test
//    public void testGenerateAdditionNodesWithNegativeParentId() {
//        View view = new View(context);
//        ViewDetails viewDetails = new ViewDetails(view);
//
//        SessionReplayViewThingy thingy = new SessionReplayViewThingy(viewDetails);
//
//        List<RRWebMutationData.AddRecord> addRecords = thingy.generateAdditionNodes(-1);
//
//        Assert.assertNotNull(addRecords);
//        Assert.assertEquals(1, addRecords.size());
//        Assert.assertEquals(-1, addRecords.get(0).getParentId());
//    }
//
//    // ==================== CHANGE DETECTION TESTS ====================
//
//    @Test
//    public void testHasChanged_WithNull() {
//        View view = new View(context);
//        ViewDetails viewDetails = new ViewDetails(view);
//
//        SessionReplayViewThingy thingy = new SessionReplayViewThingy(viewDetails);
//
//        Assert.assertTrue(thingy.hasChanged(null));
//    }
//
//    @Test
//    public void testHasChanged_WithDifferentType() {
//        View view = new View(context);
//        ViewDetails viewDetails = new ViewDetails(view);
//
//        SessionReplayViewThingy thingy = new SessionReplayViewThingy(viewDetails);
//
//        SessionReplayViewThingyInterface otherType = new SessionReplayViewThingyInterface() {
//            @Override
//            public ViewDetails getViewDetails() {
//                return viewDetails;
//            }
//
//            @Override
//            public boolean shouldRecordSubviews() {
//                return false;
//            }
//
//            @Override
//            public List<? extends SessionReplayViewThingyInterface> getSubviews() {
//                return null;
//            }
//
//            @Override
//            public void setSubviews(List<? extends SessionReplayViewThingyInterface> subviews) {
//            }
//
//            @Override
//            public String getCssSelector() {
//                return null;
//            }
//
//            @Override
//            public String generateCssDescription() {
//                return null;
//            }
//
//            @Override
//            public String generateInlineCss() {
//                return null;
//            }
//
//            @Override
//            public RRWebElementNode generateRRWebNode() {
//                return null;
//            }
//
//            @Override
//            public List<MutationRecord> generateDifferences(SessionReplayViewThingyInterface other) {
//                return null;
//            }
//
//            @Override
//            public List<RRWebMutationData.AddRecord> generateAdditionNodes(int parentId) {
//                return null;
//            }
//
//            @Override
//            public int getViewId() {
//                return 0;
//            }
//
//            @Override
//            public int getParentViewId() {
//                return 0;
//            }
//
//            @Override
//            public boolean hasChanged(SessionReplayViewThingyInterface other) {
//                return false;
//            }
//        };
//
//        Assert.assertTrue(thingy.hasChanged(otherType));
//    }
//
//    @Test
//    public void testHasChanged_WithSameInstance() {
//        View view = new View(context);
//        ViewDetails viewDetails = new ViewDetails(view);
//
//        SessionReplayViewThingy thingy = new SessionReplayViewThingy(viewDetails);
//
//        Assert.assertFalse(thingy.hasChanged(thingy));
//    }
//
//    @Test
//    public void testHasChanged_WithDifferentViews() {
//        View view1 = new View(context);
//        view1.setBackgroundColor(Color.RED);
//        ViewDetails viewDetails1 = new ViewDetails(view1);
//
//        View view2 = new View(context);
//        view2.setBackgroundColor(Color.BLUE);
//        ViewDetails viewDetails2 = new ViewDetails(view2);
//
//        SessionReplayViewThingy thingy1 = new SessionReplayViewThingy(viewDetails1);
//        SessionReplayViewThingy thingy2 = new SessionReplayViewThingy(viewDetails2);
//
//        Assert.assertTrue(thingy1.hasChanged(thingy2));
//    }
//
//    // ==================== VIEWGROUP HIERARCHY TESTS ====================
//
//    @Test
//    public void testViewThingyWithNestedChildren() {
//        LinearLayout parent = new LinearLayout(context);
//        View child1 = new View(context);
//        View child2 = new View(context);
//
//        parent.addView(child1);
//        parent.addView(child2);
//
//        ViewDetails parentDetails = new ViewDetails(parent);
//        ViewDetails child1Details = new ViewDetails(child1);
//        ViewDetails child2Details = new ViewDetails(child2);
//
//        SessionReplayViewThingy parentThingy = new SessionReplayViewThingy(parentDetails);
//        SessionReplayViewThingy child1Thingy = new SessionReplayViewThingy(child1Details);
//        SessionReplayViewThingy child2Thingy = new SessionReplayViewThingy(child2Details);
//
//        parentThingy.setSubviews(Arrays.asList(child1Thingy, child2Thingy));
//
//        Assert.assertEquals(2, parentThingy.getSubviews().size());
//    }
//
//    @Test
//    public void testViewThingyWithDeeplyNestedChildren() {
//        FrameLayout level1 = new FrameLayout(context);
//        FrameLayout level2 = new FrameLayout(context);
//        View level3 = new View(context);
//
//        level1.addView(level2);
//        level2.addView(level3);
//
//        ViewDetails details1 = new ViewDetails(level1);
//        ViewDetails details2 = new ViewDetails(level2);
//        ViewDetails details3 = new ViewDetails(level3);
//
//        SessionReplayViewThingy thingy1 = new SessionReplayViewThingy(details1);
//        SessionReplayViewThingy thingy2 = new SessionReplayViewThingy(details2);
//        SessionReplayViewThingy thingy3 = new SessionReplayViewThingy(details3);
//
//        thingy2.setSubviews(Arrays.asList(thingy3));
//        thingy1.setSubviews(Arrays.asList(thingy2));
//
//        Assert.assertEquals(1, thingy1.getSubviews().size());
//        Assert.assertEquals(1, thingy2.getSubviews().size());
//        Assert.assertEquals(0, thingy3.getSubviews().size());
//    }
//}