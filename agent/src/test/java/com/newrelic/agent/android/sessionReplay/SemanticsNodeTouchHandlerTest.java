///*
// * Copyright (c) 2024. New Relic Corporation. All rights reserved.
// * SPDX-License-Identifier: Apache-2.0
// */
//
//package com.newrelic.agent.android.sessionReplay;
//
//import android.content.Context;
//import android.view.View;
//
//import androidx.compose.ui.geometry.Rect;
//import androidx.compose.ui.platform.AndroidComposeView;
//import androidx.compose.ui.semantics.SemanticsNode;
//import androidx.compose.ui.semantics.SemanticsOwner;
//import androidx.test.core.app.ApplicationProvider;
//
//import com.newrelic.agent.android.AgentConfiguration;
//
//import org.junit.Assert;
//import org.junit.Before;
//import org.junit.Test;
//import org.junit.runner.RunWith;
//import org.robolectric.RobolectricTestRunner;
//
//import java.util.ArrayList;
//import java.util.List;
//
//import static org.mockito.Mockito.mock;
//import static org.mockito.Mockito.when;
//
//@RunWith(RobolectricTestRunner.class)
//public class SemanticsNodeTouchHandlerTest {
//
//    private Context context;
//    private SessionReplayConfiguration configuration;
//    private SemanticsNodeTouchHandler handler;
//
//    @Before
//    public void setUp() {
//        context = ApplicationProvider.getApplicationContext();
//
//        AgentConfiguration agentConfiguration = AgentConfiguration.getInstance();
//        configuration = new SessionReplayConfiguration();
//        configuration.setEnabled(true);
//        configuration.setMaskAllUserTouches(false);
//        configuration.processCustomMaskingRules();
//        agentConfiguration.setSessionReplayConfiguration(configuration);
//
//        handler = new SemanticsNodeTouchHandler(configuration);
//    }
//
//    // ==================== CONSTRUCTOR TESTS ====================
//
//    @Test
//    public void testConstructor_WithValidConfiguration() {
//        SemanticsNodeTouchHandler newHandler = new SemanticsNodeTouchHandler(configuration);
//        Assert.assertNotNull(newHandler);
//    }
//
//    @Test
//    public void testConstructor_WithNullConfiguration() {
//        SemanticsNodeTouchHandler newHandler = new SemanticsNodeTouchHandler(null);
//        Assert.assertNotNull(newHandler);
//    }
//
//    // ==================== GET COMPOSE SEMANTICS NODE - NULL TESTS ====================
//
//    @Test
//    public void testGetComposeSemanticsNode_NullView_ReturnsNull() {
//        Object result = handler.getComposeSemanticsNode(null, 100, 100);
//        Assert.assertNull(result);
//    }
//
//    @Test
//    public void testGetComposeSemanticsNode_ViewWithNullParent_ReturnsNull() {
//        View view = new View(context);
//        // View has no parent
//
//        Object result = handler.getComposeSemanticsNode(view, 100, 100);
//        Assert.assertNull(result);
//    }
//
//    @Test
//    public void testGetComposeSemanticsNode_RegularView_ReturnsNull() {
//        View view = new View(context);
//        // Not an AndroidComposeView
//
//        Object result = handler.getComposeSemanticsNode(view, 100, 100);
//        Assert.assertNull(result);
//    }
//
//    @Test
//    public void testGetComposeSemanticsNode_WithAndroidComposeView() {
//        // Mock AndroidComposeView
//        AndroidComposeView composeView = mock(AndroidComposeView.class);
//        SemanticsOwner semanticsOwner = mock(SemanticsOwner.class);
//        SemanticsNode rootNode = mock(SemanticsNode.class);
//        Rect bounds = new Rect(0f, 0f, 200f, 200f);
//
//        when(composeView.getSemanticsOwner()).thenReturn(semanticsOwner);
//        when(semanticsOwner.getUnmergedRootSemanticsNode()).thenReturn(rootNode);
//        when(rootNode.getBoundsInRoot()).thenReturn(bounds);
//        when(rootNode.getChildren()).thenReturn(new ArrayList<>());
//
//        // Touch within bounds
//        Object result = handler.getComposeSemanticsNode(composeView, 100, 100);
//
//        // Should return the root node
//        Assert.assertNotNull(result);
//        Assert.assertEquals(rootNode, result);
//    }
//
//    @Test
//    public void testGetComposeSemanticsNode_WithException_ReturnsNull() {
//        // Mock view that throws exception
//        View view = mock(View.class);
//        when(view.getParent()).thenThrow(new RuntimeException("Test exception"));
//
//        Object result = handler.getComposeSemanticsNode(view, 100, 100);
//
//        // Should catch exception and return null
//        Assert.assertNull(result);
//    }
//
//    // ==================== FIND NODE AT POSITION - NULL TESTS ====================
//
//    @Test
//    public void testFindNodeAtPosition_NullNode_ReturnsNull() {
//        Object result = handler.findNodeAtPosition(null, 100, 100);
//        Assert.assertNull(result);
//    }
//
//    @Test
//    public void testFindNodeAtPosition_NodeWithNullBounds_ReturnsNull() {
//        SemanticsNode node = mock(SemanticsNode.class);
//        when(node.getBoundsInRoot()).thenReturn(null);
//
//        Object result = handler.findNodeAtPosition(node, 100, 100);
//
//        Assert.assertNull(result);
//    }
//
//    // ==================== FIND NODE AT POSITION - BOUNDS TESTS ====================
//
//    @Test
//    public void testFindNodeAtPosition_TouchWithinBounds_ReturnsNode() {
//        SemanticsNode node = mock(SemanticsNode.class);
//        Rect bounds = new Rect(0f, 0f, 200f, 200f);
//
//        when(node.getBoundsInRoot()).thenReturn(bounds);
//        when(node.getChildren()).thenReturn(new ArrayList<>());
//
//        // Touch at (100, 100) is within (0, 0, 200, 200)
//        Object result = handler.findNodeAtPosition(node, 100, 100);
//
//        Assert.assertNotNull(result);
//        Assert.assertEquals(node, result);
//    }
//
//    @Test
//    public void testFindNodeAtPosition_TouchOutsideBounds_ReturnsNull() {
//        SemanticsNode node = mock(SemanticsNode.class);
//        Rect bounds = new Rect(0f, 0f, 100f, 100f);
//
//        when(node.getBoundsInRoot()).thenReturn(bounds);
//        when(node.getChildren()).thenReturn(new ArrayList<>());
//
//        // Touch at (200, 200) is outside (0, 0, 100, 100)
//        Object result = handler.findNodeAtPosition(node, 200, 200);
//
//        Assert.assertNull(result);
//    }
//
//    @Test
//    public void testFindNodeAtPosition_TouchAtTopLeftCorner() {
//        SemanticsNode node = mock(SemanticsNode.class);
//        Rect bounds = new Rect(0f, 0f, 100f, 100f);
//
//        when(node.getBoundsInRoot()).thenReturn(bounds);
//        when(node.getChildren()).thenReturn(new ArrayList<>());
//
//        Object result = handler.findNodeAtPosition(node, 0, 0);
//
//        Assert.assertNotNull(result);
//        Assert.assertEquals(node, result);
//    }
//
//    @Test
//    public void testFindNodeAtPosition_TouchAtBottomRightCorner() {
//        SemanticsNode node = mock(SemanticsNode.class);
//        Rect bounds = new Rect(0f, 0f, 100f, 100f);
//
//        when(node.getBoundsInRoot()).thenReturn(bounds);
//        when(node.getChildren()).thenReturn(new ArrayList<>());
//
//        Object result = handler.findNodeAtPosition(node, 100, 100);
//
//        Assert.assertNotNull(result);
//        Assert.assertEquals(node, result);
//    }
//
//    @Test
//    public void testFindNodeAtPosition_TouchJustOutsideBounds() {
//        SemanticsNode node = mock(SemanticsNode.class);
//        Rect bounds = new Rect(0f, 0f, 100f, 100f);
//
//        when(node.getBoundsInRoot()).thenReturn(bounds);
//        when(node.getChildren()).thenReturn(new ArrayList<>());
//
//        Object result = handler.findNodeAtPosition(node, 101, 101);
//
//        Assert.assertNull(result);
//    }
//
//    // ==================== FIND NODE AT POSITION - HIERARCHY TESTS ====================
//
//    @Test
//    public void testFindNodeAtPosition_NoChildren_ReturnsParent() {
//        SemanticsNode parentNode = mock(SemanticsNode.class);
//        Rect bounds = new Rect(0f, 0f, 200f, 200f);
//
//        when(parentNode.getBoundsInRoot()).thenReturn(bounds);
//        when(parentNode.getChildren()).thenReturn(new ArrayList<>());
//
//        Object result = handler.findNodeAtPosition(parentNode, 100, 100);
//
//        Assert.assertEquals(parentNode, result);
//    }
//
//    @Test
//    public void testFindNodeAtPosition_WithChildren_TouchesChild() {
//        SemanticsNode parentNode = mock(SemanticsNode.class);
//        SemanticsNode childNode = mock(SemanticsNode.class);
//
//        Rect parentBounds = new Rect(0f, 0f, 200f, 200f);
//        Rect childBounds = new Rect(50f, 50f, 150f, 150f);
//
//        List<SemanticsNode> children = new ArrayList<>();
//        children.add(childNode);
//
//        when(parentNode.getBoundsInRoot()).thenReturn(parentBounds);
//        when(parentNode.getChildren()).thenReturn(children);
//        when(childNode.getBoundsInRoot()).thenReturn(childBounds);
//        when(childNode.getChildren()).thenReturn(new ArrayList<>());
//
//        // Touch at (100, 100) is within child bounds
//        Object result = handler.findNodeAtPosition(parentNode, 100, 100);
//
//        Assert.assertEquals(childNode, result);
//    }
//
//    @Test
//    public void testFindNodeAtPosition_WithChildren_TouchesNoChild_ReturnsParent() {
//        SemanticsNode parentNode = mock(SemanticsNode.class);
//        SemanticsNode childNode = mock(SemanticsNode.class);
//
//        Rect parentBounds = new Rect(0f, 0f, 200f, 200f);
//        Rect childBounds = new Rect(100f, 100f, 150f, 150f);
//
//        List<SemanticsNode> children = new ArrayList<>();
//        children.add(childNode);
//
//        when(parentNode.getBoundsInRoot()).thenReturn(parentBounds);
//        when(parentNode.getChildren()).thenReturn(children);
//        when(childNode.getBoundsInRoot()).thenReturn(childBounds);
//        when(childNode.getChildren()).thenReturn(new ArrayList<>());
//
//        // Touch at (50, 50) is within parent but not child
//        Object result = handler.findNodeAtPosition(parentNode, 50, 50);
//
//        Assert.assertEquals(parentNode, result);
//    }
//
//    @Test
//    public void testFindNodeAtPosition_NestedHierarchy() {
//        SemanticsNode rootNode = mock(SemanticsNode.class);
//        SemanticsNode middleNode = mock(SemanticsNode.class);
//        SemanticsNode leafNode = mock(SemanticsNode.class);
//
//        Rect rootBounds = new Rect(0f, 0f, 300f, 300f);
//        Rect middleBounds = new Rect(50f, 50f, 250f, 250f);
//        Rect leafBounds = new Rect(100f, 100f, 200f, 200f);
//
//        List<SemanticsNode> rootChildren = new ArrayList<>();
//        rootChildren.add(middleNode);
//        List<SemanticsNode> middleChildren = new ArrayList<>();
//        middleChildren.add(leafNode);
//
//        when(rootNode.getBoundsInRoot()).thenReturn(rootBounds);
//        when(rootNode.getChildren()).thenReturn(rootChildren);
//        when(middleNode.getBoundsInRoot()).thenReturn(middleBounds);
//        when(middleNode.getChildren()).thenReturn(middleChildren);
//        when(leafNode.getBoundsInRoot()).thenReturn(leafBounds);
//        when(leafNode.getChildren()).thenReturn(new ArrayList<>());
//
//        // Touch at (150, 150) should find leaf node
//        Object result = handler.findNodeAtPosition(rootNode, 150, 150);
//
//        Assert.assertEquals(leafNode, result);
//    }
//
//    @Test
//    public void testFindNodeAtPosition_MultipleChildren_FindsCorrectOne() {
//        SemanticsNode parentNode = mock(SemanticsNode.class);
//        SemanticsNode child1 = mock(SemanticsNode.class);
//        SemanticsNode child2 = mock(SemanticsNode.class);
//        SemanticsNode child3 = mock(SemanticsNode.class);
//
//        Rect parentBounds = new Rect(0f, 0f, 300f, 300f);
//        Rect bounds1 = new Rect(0f, 0f, 100f, 100f);
//        Rect bounds2 = new Rect(100f, 100f, 200f, 200f);
//        Rect bounds3 = new Rect(200f, 200f, 300f, 300f);
//
//        List<SemanticsNode> children = new ArrayList<>();
//        children.add(child1);
//        children.add(child2);
//        children.add(child3);
//
//        when(parentNode.getBoundsInRoot()).thenReturn(parentBounds);
//        when(parentNode.getChildren()).thenReturn(children);
//        when(child1.getBoundsInRoot()).thenReturn(bounds1);
//        when(child1.getChildren()).thenReturn(new ArrayList<>());
//        when(child2.getBoundsInRoot()).thenReturn(bounds2);
//        when(child2.getChildren()).thenReturn(new ArrayList<>());
//        when(child3.getBoundsInRoot()).thenReturn(bounds3);
//        when(child3.getChildren()).thenReturn(new ArrayList<>());
//
//        // Touch at (150, 150) should find child2
//        Object result = handler.findNodeAtPosition(parentNode, 150, 150);
//
//        Assert.assertEquals(child2, result);
//    }
//
//    // ==================== GET SEMANTICS NODE STABLE ID - NULL TESTS ====================
//
//    @Test
//    public void testGetSemanticsNodeStableId_NullNode_ReturnsMinusOne() {
//        int id = handler.getSemanticsNodeStableId(null);
//        Assert.assertEquals(-1, id);
//    }
//
//    // ==================== GET SEMANTICS NODE STABLE ID - BASIC TESTS ====================
//
//    @Test
//    public void testGetSemanticsNodeStableId_ValidNode_ReturnsNodeId() {
//        SemanticsNode node = mock(SemanticsNode.class);
//        when(node.getId()).thenReturn(12345);
//
//        int id = handler.getSemanticsNodeStableId(node);
//
//        Assert.assertEquals(12345, id);
//    }
//
//    @Test
//    public void testGetSemanticsNodeStableId_DifferentNodes_DifferentIds() {
//        SemanticsNode node1 = mock(SemanticsNode.class);
//        SemanticsNode node2 = mock(SemanticsNode.class);
//
//        when(node1.getId()).thenReturn(100);
//        when(node2.getId()).thenReturn(200);
//
//        int id1 = handler.getSemanticsNodeStableId(node1);
//        int id2 = handler.getSemanticsNodeStableId(node2);
//
//        Assert.assertEquals(100, id1);
//        Assert.assertEquals(200, id2);
//        Assert.assertNotEquals(id1, id2);
//    }
//
//    // ==================== GET SEMANTICS NODE STABLE ID - MASKING TESTS ====================
//
//    @Test
//    public void testGetSemanticsNodeStableId_MaskAllUserTouches_ReturnsMaskedId() {
//        configuration.setMaskAllUserTouches(true);
//
//        SemanticsNode node = mock(SemanticsNode.class);
//        when(node.getId()).thenReturn(12345);
//
//        int id = handler.getSemanticsNodeStableId(node);
//
//        // Should return MASKED_TOUCH_ID (0) instead of actual ID
//        Assert.assertEquals(0, id);
//    }
//
//    @Test
//    public void testGetSemanticsNodeStableId_NoMasking_ReturnsActualId() {
//        configuration.setMaskAllUserTouches(false);
//
//        SemanticsNode node = mock(SemanticsNode.class);
//        when(node.getId()).thenReturn(12345);
//
//        int id = handler.getSemanticsNodeStableId(node);
//
//        // Should return actual ID
//        Assert.assertEquals(12345, id);
//    }
//
//    @Test
//    public void testGetSemanticsNodeStableId_MultipleCalls_SameNode_SameId() {
//        SemanticsNode node = mock(SemanticsNode.class);
//        when(node.getId()).thenReturn(12345);
//
//        int id1 = handler.getSemanticsNodeStableId(node);
//        int id2 = handler.getSemanticsNodeStableId(node);
//        int id3 = handler.getSemanticsNodeStableId(node);
//
//        Assert.assertEquals(id1, id2);
//        Assert.assertEquals(id2, id3);
//    }
//
//    // ==================== EDGE CASE TESTS ====================
//
//    @Test
//    public void testFindNodeAtPosition_NegativeCoordinates() {
//        SemanticsNode node = mock(SemanticsNode.class);
//        Rect bounds = new Rect(0f, 0f, 100f, 100f);
//
//        when(node.getBoundsInRoot()).thenReturn(bounds);
//        when(node.getChildren()).thenReturn(new ArrayList<>());
//
//        Object result = handler.findNodeAtPosition(node, -10, -10);
//
//        Assert.assertNull(result);
//    }
//
//    @Test
//    public void testFindNodeAtPosition_VeryLargeCoordinates() {
//        SemanticsNode node = mock(SemanticsNode.class);
//        Rect bounds = new Rect(0f, 0f, 100f, 100f);
//
//        when(node.getBoundsInRoot()).thenReturn(bounds);
//        when(node.getChildren()).thenReturn(new ArrayList<>());
//
//        Object result = handler.findNodeAtPosition(node, Integer.MAX_VALUE, Integer.MAX_VALUE);
//
//        Assert.assertNull(result);
//    }
//
//    @Test
//    public void testFindNodeAtPosition_ZeroSizedBounds() {
//        SemanticsNode node = mock(SemanticsNode.class);
//        Rect bounds = new Rect(50f, 50f, 50f, 50f); // Zero width/height
//
//        when(node.getBoundsInRoot()).thenReturn(bounds);
//        when(node.getChildren()).thenReturn(new ArrayList<>());
//
//        Object result = handler.findNodeAtPosition(node, 50, 50);
//
//        // Touch exactly at the point should be included (<=, >= checks)
//        Assert.assertNotNull(result);
//    }
//
//    @Test
//    public void testGetSemanticsNodeStableId_ZeroId() {
//        SemanticsNode node = mock(SemanticsNode.class);
//        when(node.getId()).thenReturn(0);
//
//        int id = handler.getSemanticsNodeStableId(node);
//
//        Assert.assertEquals(0, id);
//    }
//
//    @Test
//    public void testGetSemanticsNodeStableId_NegativeId() {
//        SemanticsNode node = mock(SemanticsNode.class);
//        when(node.getId()).thenReturn(-1);
//
//        int id = handler.getSemanticsNodeStableId(node);
//
//        Assert.assertEquals(-1, id);
//    }
//
//    // ==================== CONFIGURATION TESTS ====================
//
//    @Test
//    public void testConfiguration_MaskAllUserTouches_ToggleOn() {
//        SemanticsNode node = mock(SemanticsNode.class);
//        when(node.getId()).thenReturn(12345);
//
//        configuration.setMaskAllUserTouches(false);
//        int idBefore = handler.getSemanticsNodeStableId(node);
//
//        configuration.setMaskAllUserTouches(true);
//        int idAfter = handler.getSemanticsNodeStableId(node);
//
//        Assert.assertEquals(12345, idBefore);
//        Assert.assertEquals(0, idAfter); // Masked
//    }
//
//    @Test
//    public void testConfiguration_WithDifferentConfigurations() {
//        SessionReplayConfiguration config1 = new SessionReplayConfiguration();
//        config1.setMaskAllUserTouches(true);
//
//        SessionReplayConfiguration config2 = new SessionReplayConfiguration();
//        config2.setMaskAllUserTouches(false);
//
//        SemanticsNodeTouchHandler handler1 = new SemanticsNodeTouchHandler(config1);
//        SemanticsNodeTouchHandler handler2 = new SemanticsNodeTouchHandler(config2);
//
//        SemanticsNode node = mock(SemanticsNode.class);
//        when(node.getId()).thenReturn(12345);
//
//        int id1 = handler1.getSemanticsNodeStableId(node);
//        int id2 = handler2.getSemanticsNodeStableId(node);
//
//        Assert.assertEquals(0, id1); // Masked
//        Assert.assertEquals(12345, id2); // Not masked
//    }
//
//    // ==================== INTEGRATION TESTS ====================
//
//    @Test
//    public void testIntegration_FindAndGetId() {
//        SemanticsNode node = mock(SemanticsNode.class);
//        Rect bounds = new Rect(0f, 0f, 200f, 200f);
//
//        when(node.getBoundsInRoot()).thenReturn(bounds);
//        when(node.getChildren()).thenReturn(new ArrayList<>());
//        when(node.getId()).thenReturn(12345);
//
//        // Find node at position
//        Object foundNode = handler.findNodeAtPosition(node, 100, 100);
//        Assert.assertNotNull(foundNode);
//
//        // Get stable ID for found node
//        int id = handler.getSemanticsNodeStableId((SemanticsNode) foundNode);
//        Assert.assertEquals(12345, id);
//    }
//
//    @Test
//    public void testIntegration_HierarchyTraversal() {
//        // Create a 3-level hierarchy
//        SemanticsNode root = mock(SemanticsNode.class);
//        SemanticsNode middle = mock(SemanticsNode.class);
//        SemanticsNode leaf = mock(SemanticsNode.class);
//
//        Rect rootBounds = new Rect(0f, 0f, 300f, 300f);
//        Rect middleBounds = new Rect(50f, 50f, 250f, 250f);
//        Rect leafBounds = new Rect(100f, 100f, 200f, 200f);
//
//        List<SemanticsNode> rootChildren = new ArrayList<>();
//        rootChildren.add(middle);
//        List<SemanticsNode> middleChildren = new ArrayList<>();
//        middleChildren.add(leaf);
//
//        when(root.getBoundsInRoot()).thenReturn(rootBounds);
//        when(root.getChildren()).thenReturn(rootChildren);
//        when(root.getId()).thenReturn(1);
//
//        when(middle.getBoundsInRoot()).thenReturn(middleBounds);
//        when(middle.getChildren()).thenReturn(middleChildren);
//        when(middle.getId()).thenReturn(2);
//
//        when(leaf.getBoundsInRoot()).thenReturn(leafBounds);
//        when(leaf.getChildren()).thenReturn(new ArrayList<>());
//        when(leaf.getId()).thenReturn(3);
//
//        // Find leaf node
//        Object found = handler.findNodeAtPosition(root, 150, 150);
//        Assert.assertEquals(leaf, found);
//
//        // Get its ID
//        int id = handler.getSemanticsNodeStableId((SemanticsNode) found);
//        Assert.assertEquals(3, id);
//    }
//}