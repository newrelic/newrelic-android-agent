/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import com.newrelic.agent.android.R;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
public class ViewTouchHandlerTest {

    private Context context;
    private SessionReplayConfiguration configuration;
    private ViewTouchHandler handler;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        configuration = new SessionReplayConfiguration();
        configuration.setEnabled(true);
        configuration.processCustomMaskingRules();
        handler = new ViewTouchHandler(configuration);
    }

    // ==================== CONSTRUCTOR TESTS ====================

    @Test
    public void testConstructor_WithValidConfiguration() {
        ViewTouchHandler newHandler = new ViewTouchHandler(configuration);
        Assert.assertNotNull(newHandler);
    }

    @Test
    public void testConstructor_WithNullConfiguration() {
        ViewTouchHandler newHandler = new ViewTouchHandler(null);
        Assert.assertNotNull(newHandler);
    }

    // ==================== FIND VIEW AT COORDS - NULL TESTS ====================

    @Test
    public void testFindViewAtCoords_NullRootView() {
        Object result = handler.findViewAtCoords(null, 100, 100);
        Assert.assertNull(result);
    }

    // ==================== FIND VIEW AT COORDS - SINGLE VIEW TESTS ====================

    @Test
    public void testFindViewAtCoords_SingleView_WithinBounds() {
        View view = new View(context);
        view.layout(0, 0, 100, 100);

        Object result = handler.findViewAtCoords(view, 50, 50);

        Assert.assertNotNull(result);
        Assert.assertEquals(view, result);
    }

    @Test
    public void testFindViewAtCoords_SingleView_OutsideBounds() {
        View view = new View(context);
        view.layout(0, 0, 100, 100);

        Object result = handler.findViewAtCoords(view, 200, 200);

        Assert.assertNull(result);
    }

    @Test
    public void testFindViewAtCoords_SingleView_AtTopLeftCorner() {
        View view = new View(context);
        view.layout(0, 0, 100, 100);

        Object result = handler.findViewAtCoords(view, 0, 0);

        Assert.assertNotNull(result);
        Assert.assertEquals(view, result);
    }

    @Test
    public void testFindViewAtCoords_SingleView_AtBottomRightCorner() {
        View view = new View(context);
        view.layout(0, 0, 100, 100);

        Object result = handler.findViewAtCoords(view, 100, 100);

        Assert.assertNotNull(result);
        Assert.assertEquals(view, result);
    }

    @Test
    public void testFindViewAtCoords_SingleView_JustOutsideBounds() {
        View view = new View(context);
        view.layout(0, 0, 100, 100);

        Object result = handler.findViewAtCoords(view, 101, 101);

        Assert.assertNull(result);
    }

    // ==================== FIND VIEW AT COORDS - VIEWGROUP TESTS ====================

    @Test
    public void testFindViewAtCoords_EmptyViewGroup() {
        ViewGroup viewGroup = new FrameLayout(context);
        viewGroup.layout(0, 0, 200, 200);

        Object result = handler.findViewAtCoords(viewGroup, 100, 100);

        // No children, should return the parent
        Assert.assertNotNull(result);
        Assert.assertEquals(viewGroup, result);
    }

    @Test
    public void testFindViewAtCoords_ViewGroupWithSingleChild() {
        ViewGroup viewGroup = new FrameLayout(context);
        viewGroup.layout(0, 0, 200, 200);

        View child = new View(context);
        child.layout(50, 50, 150, 150);
        viewGroup.addView(child);

        Object result = handler.findViewAtCoords(viewGroup, 100, 100);

        // Touch is within child bounds
        Assert.assertNotNull(result);
        Assert.assertEquals(child, result);
    }

    @Test
    public void testFindViewAtCoords_ViewGroupWithMultipleChildren_TouchesFirstChild() {
        ViewGroup viewGroup = new FrameLayout(context);
        viewGroup.layout(0, 0, 300, 300);

        View child1 = new View(context);
        child1.layout(0, 0, 100, 100);
        viewGroup.addView(child1);

        View child2 = new View(context);
        child2.layout(100, 100, 200, 200);
        viewGroup.addView(child2);

        Object result = handler.findViewAtCoords(viewGroup, 50, 50);

        Assert.assertEquals(child2, result);
    }


    @Test
    public void testFindViewAtCoords_ViewGroupWithMultipleChildren_TouchesNoChild() {
        ViewGroup viewGroup = new FrameLayout(context);
        viewGroup.layout(0, 0, 300, 300);

        View child1 = new View(context);
        child1.layout(0, 0, 50, 50);
        viewGroup.addView(child1);

        View child2 = new View(context);
        child2.layout(100, 100, 150, 150);
        viewGroup.addView(child2);

        // Touch at (75, 75) is between children
        Object result = handler.findViewAtCoords(viewGroup, 75, 75);

        // Should return parent since no child contains the point
        Assert.assertEquals(viewGroup, result);
    }




    // ==================== FIND VIEW AT COORDS - Z-ORDER TESTS ====================

    @Test
    public void testFindViewAtCoords_OverlappingChildren_ReturnsTopMost() {
        ViewGroup viewGroup = new FrameLayout(context);
        viewGroup.layout(0, 0, 300, 300);

        // Both children overlap at the same location
        View bottomChild = new View(context);
        bottomChild.layout(50, 50, 150, 150);
        viewGroup.addView(bottomChild); // Added first, so at bottom

        View topChild = new View(context);
        topChild.layout(50, 50, 150, 150);
        viewGroup.addView(topChild); // Added second, so on top

        Object result = handler.findViewAtCoords(viewGroup, 100, 100);

        // Should return the top-most child (last added)
        Assert.assertEquals(topChild, result);
    }

    // ==================== GET VIEW STABLE ID TESTS ====================

    @Test
    public void testGetViewStableId_NullView_ReturnsMinusOne() {
        int id = handler.getViewStableId(null);
        Assert.assertEquals(-1, id);
    }

    @Test
    public void testGetViewStableId_FirstCall_GeneratesId() {
        View view = new View(context);

        int id = handler.getViewStableId(view);

        Assert.assertTrue(id > 0);
        Assert.assertTrue(id >= 1000000); // NewRelicIdGenerator starts at 1000000
    }

    @Test
    public void testGetViewStableId_SecondCall_ReturnsSameId() {
        View view = new View(context);

        int id1 = handler.getViewStableId(view);
        int id2 = handler.getViewStableId(view);

        Assert.assertEquals(id1, id2);
    }

    @Test
    public void testGetViewStableId_DifferentViews_GetDifferentIds() {
        View view1 = new View(context);
        View view2 = new View(context);

        int id1 = handler.getViewStableId(view1);
        int id2 = handler.getViewStableId(view2);

        Assert.assertNotEquals(id1, id2);
    }

    @Test
    public void testGetViewStableId_MultipleCalls_SameId() {
        View view = new View(context);

        int id1 = handler.getViewStableId(view);
        int id2 = handler.getViewStableId(view);
        int id3 = handler.getViewStableId(view);
        int id4 = handler.getViewStableId(view);

        Assert.assertEquals(id1, id2);
        Assert.assertEquals(id2, id3);
        Assert.assertEquals(id3, id4);
    }

    @Test
    public void testGetViewStableId_PersistedAcrossHandlerInstances() {
        View view = new View(context);

        int id1 = handler.getViewStableId(view);

        // Create new handler instance
        ViewTouchHandler newHandler = new ViewTouchHandler(configuration);
        int id2 = newHandler.getViewStableId(view);

        // ID should be the same because it's stored in the view's tag
        Assert.assertEquals(id1, id2);
    }

    // ==================== GET MASKED VIEW IF NEEDED - NULL TESTS ====================

    @Test
    public void testGetMaskedViewIfNeeded_NullView_ReturnsNull() {
        View result = handler.getMaskedViewIfNeeded(null, true);
        Assert.assertNull(result);

        result = handler.getMaskedViewIfNeeded(null, false);
        Assert.assertNull(result);
    }

    // ==================== GET MASKED VIEW IF NEEDED - BASIC MASKING ====================

    @Test
    public void testGetMaskedViewIfNeeded_ShouldMaskTrue_NoTags_ReturnsMasked() {
        View view = new View(context);

        View result = handler.getMaskedViewIfNeeded(view, true);

        // Should be masked (returns null)
        Assert.assertNull(result);
    }

    @Test
    public void testGetMaskedViewIfNeeded_ShouldMaskFalse_NoTags_ReturnsUnmasked() {
        View view = new View(context);

        View result = handler.getMaskedViewIfNeeded(view, false);

        // Should not be masked (returns view)
        Assert.assertNotNull(result);
        Assert.assertEquals(view, result);
    }

    // ==================== GET MASKED VIEW IF NEEDED - NR-UNMASK TAG ====================

    @Test
    public void testGetMaskedViewIfNeeded_WithUnmaskTag_ReturnsUnmasked() {
        View view = new View(context);
        view.setTag("nr-unmask");

        View result = handler.getMaskedViewIfNeeded(view, true);

        // Should not be masked despite shouldMask=true
        Assert.assertNotNull(result);
        Assert.assertEquals(view, result);
    }

    @Test
    public void testGetMaskedViewIfNeeded_WithUnmaskPrivacyTag_ReturnsUnmasked() {
        View view = new View(context);
        view.setTag(R.id.newrelic_privacy, "nr-unmask");

        View result = handler.getMaskedViewIfNeeded(view, true);

        // Should not be masked
        Assert.assertNotNull(result);
        Assert.assertEquals(view, result);
    }

    // ==================== GET MASKED VIEW IF NEEDED - NR-MASK TAG ====================

    @Test
    public void testGetMaskedViewIfNeeded_WithMaskTag_ReturnsMasked() {
        View view = new View(context);
        view.setTag("nr-mask");

        View result = handler.getMaskedViewIfNeeded(view, false);

        // Should be masked despite shouldMask=false
        Assert.assertNull(result);
    }

    @Test
    public void testGetMaskedViewIfNeeded_WithMaskPrivacyTag_ReturnsMasked() {
        View view = new View(context);
        view.setTag(R.id.newrelic_privacy, "nr-mask");

        View result = handler.getMaskedViewIfNeeded(view, false);

        // Should be masked
        Assert.assertNull(result);
    }

    // ==================== GET MASKED VIEW IF NEEDED - CONFIG TAG MASKING ====================

    @Test
    public void testGetMaskedViewIfNeeded_WithConfigUnmaskTag_ReturnsUnmasked() {
        // Setup configuration with unmask tag
        SessionReplayConfiguration.CustomMaskingRule rule = new SessionReplayConfiguration.CustomMaskingRule();
        rule.setIdentifier("tag");
        rule.setOperator("equals");
        rule.setType("unmask");
        List<String> names = new ArrayList<>();
        names.add("custom-unmask");
        rule.setName(names);

        List<SessionReplayConfiguration.CustomMaskingRule> rules = new ArrayList<>();
        rules.add(rule);
        configuration.setCustomMaskingRules(rules);
        configuration.processCustomMaskingRules();

        View view = new View(context);
        view.setTag("custom-unmask");

        View result = handler.getMaskedViewIfNeeded(view, true);

        // Should not be masked
        Assert.assertNotNull(result);
        Assert.assertEquals(view, result);
    }

    @Test
    public void testGetMaskedViewIfNeeded_WithConfigMaskTag_ReturnsMasked() {
        // Setup configuration with mask tag
        SessionReplayConfiguration.CustomMaskingRule rule = new SessionReplayConfiguration.CustomMaskingRule();
        rule.setIdentifier("tag");
        rule.setOperator("equals");
        rule.setType("mask");
        List<String> names = new ArrayList<>();
        names.add("custom-mask");
        rule.setName(names);

        List<SessionReplayConfiguration.CustomMaskingRule> rules = new ArrayList<>();
        rules.add(rule);
        configuration.setCustomMaskingRules(rules);
        configuration.processCustomMaskingRules();

        View view = new View(context);
        view.setTag("custom-mask");

        View result = handler.getMaskedViewIfNeeded(view, false);

        // Should be masked
        Assert.assertNull(result);
    }

    // ==================== GET MASKED VIEW IF NEEDED - CLASS MASKING ====================

    @Test
    public void testGetMaskedViewIfNeeded_WithUnmaskClass_ReturnsUnmasked() {
        // Setup configuration with unmask class
        SessionReplayConfiguration.CustomMaskingRule rule = new SessionReplayConfiguration.CustomMaskingRule();
        rule.setIdentifier("class");
        rule.setOperator("equals");
        rule.setType("unmask");
        List<String> names = new ArrayList<>();
        names.add(TextView.class.getName());
        rule.setName(names);

        List<SessionReplayConfiguration.CustomMaskingRule> rules = new ArrayList<>();
        rules.add(rule);
        configuration.setCustomMaskingRules(rules);
        configuration.processCustomMaskingRules();

        TextView textView = new TextView(context);

        View result = handler.getMaskedViewIfNeeded(textView, true);

        // Should not be masked
        Assert.assertNotNull(result);
        Assert.assertEquals(textView, result);
    }

    @Test
    public void testGetMaskedViewIfNeeded_WithMaskClass_ReturnsMasked() {
        // Setup configuration with mask class
        SessionReplayConfiguration.CustomMaskingRule rule = new SessionReplayConfiguration.CustomMaskingRule();
        rule.setIdentifier("class");
        rule.setOperator("equals");
        rule.setType("mask");
        List<String> names = new ArrayList<>();
        names.add(TextView.class.getName());
        rule.setName(names);

        List<SessionReplayConfiguration.CustomMaskingRule> rules = new ArrayList<>();
        rules.add(rule);
        configuration.setCustomMaskingRules(rules);
        configuration.processCustomMaskingRules();

        TextView textView = new TextView(context);

        View result = handler.getMaskedViewIfNeeded(textView, false);

        // Should be masked
        Assert.assertNull(result);
    }

    @Test
    public void testGetMaskedViewIfNeeded_SuperclassMasking_WorksForSubclass() {
        // Setup configuration to mask View class
        SessionReplayConfiguration.CustomMaskingRule rule = new SessionReplayConfiguration.CustomMaskingRule();
        rule.setIdentifier("class");
        rule.setOperator("equals");
        rule.setType("mask");
        List<String> names = new ArrayList<>();
        names.add(View.class.getName());
        rule.setName(names);

        List<SessionReplayConfiguration.CustomMaskingRule> rules = new ArrayList<>();
        rules.add(rule);
        configuration.setCustomMaskingRules(rules);
        configuration.processCustomMaskingRules();

        // TextView extends View, so should also be masked
        TextView textView = new TextView(context);

        View result = handler.getMaskedViewIfNeeded(textView, false);

        // Should be masked due to superclass
        Assert.assertNull(result);
    }

    // ==================== GET MASKED VIEW IF NEEDED - PRECEDENCE TESTS ====================

    @Test
    public void testGetMaskedViewIfNeeded_UnmaskTagOverridesMasking() {
        View view = new View(context);
        view.setTag("nr-unmask");

        // Even with shouldMask=true, unmask tag should win
        View result = handler.getMaskedViewIfNeeded(view, true);

        Assert.assertNotNull(result);
        Assert.assertEquals(view, result);
    }

    @Test
    public void testGetMaskedViewIfNeeded_MaskTagOverridesNoMasking() {
        View view = new View(context);
        view.setTag("nr-mask");

        // Even with shouldMask=false, mask tag should win
        View result = handler.getMaskedViewIfNeeded(view, false);

        Assert.assertNull(result);
    }

    @Test
    public void testGetMaskedViewIfNeeded_BothTags_UnmaskTakesPrecedence() {
        View view = new View(context);
        view.setTag("nr-unmask"); // Main tag
        view.setTag(R.id.newrelic_privacy, "nr-mask"); // Privacy tag

        // When both are present, unmask is checked first
        View result = handler.getMaskedViewIfNeeded(view, true);

        // Should not be masked (unmask takes precedence)
        Assert.assertNotNull(result);
        Assert.assertEquals(view, result);
    }

    // ==================== GET MASKED VIEW IF NEEDED - COMPLEX SCENARIOS ====================

    @Test
    public void testGetMaskedViewIfNeeded_MultipleUnmaskMechanisms() {
        // Setup unmask class
        SessionReplayConfiguration.CustomMaskingRule rule = new SessionReplayConfiguration.CustomMaskingRule();
        rule.setIdentifier("class");
        rule.setOperator("equals");
        rule.setType("unmask");
        List<String> names = new ArrayList<>();
        names.add(TextView.class.getName());
        rule.setName(names);

        List<SessionReplayConfiguration.CustomMaskingRule> rules = new ArrayList<>();
        rules.add(rule);
        configuration.setCustomMaskingRules(rules);
        configuration.processCustomMaskingRules();

        TextView textView = new TextView(context);
        textView.setTag("nr-unmask"); // Also has unmask tag

        View result = handler.getMaskedViewIfNeeded(textView, true);

        // Multiple unmask mechanisms, should not be masked
        Assert.assertNotNull(result);
        Assert.assertEquals(textView, result);
    }

    @Test
    public void testGetMaskedViewIfNeeded_MultipleMaskMechanisms() {
        // Setup mask class
        SessionReplayConfiguration.CustomMaskingRule rule = new SessionReplayConfiguration.CustomMaskingRule();
        rule.setIdentifier("class");
        rule.setOperator("equals");
        rule.setType("mask");
        List<String> names = new ArrayList<>();
        names.add(TextView.class.getName());
        rule.setName(names);

        List<SessionReplayConfiguration.CustomMaskingRule> rules = new ArrayList<>();
        rules.add(rule);
        configuration.setCustomMaskingRules(rules);
        configuration.processCustomMaskingRules();

        TextView textView = new TextView(context);
        textView.setTag("nr-mask"); // Also has mask tag

        View result = handler.getMaskedViewIfNeeded(textView, false);

        // Multiple mask mechanisms, should be masked
        Assert.assertNull(result);
    }

    // ==================== EDGE CASE TESTS ====================

    @Test
    public void testFindViewAtCoords_NegativeCoordinates() {
        View view = new View(context);
        view.layout(0, 0, 100, 100);

        Object result = handler.findViewAtCoords(view, -10, -10);

        Assert.assertNull(result);
    }

    @Test
    public void testFindViewAtCoords_VeryLargeCoordinates() {
        View view = new View(context);
        view.layout(0, 0, 100, 100);

        Object result = handler.findViewAtCoords(view, Integer.MAX_VALUE, Integer.MAX_VALUE);

        Assert.assertNull(result);
    }


    @Test
    public void testGetViewStableId_IdIsConsistent() {
        View view = new View(context);

        Set<Integer> ids = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            ids.add(handler.getViewStableId(view));
        }

        // Should always return the same ID
        Assert.assertEquals(1, ids.size());
    }

    @Test
    public void testGetMaskedViewIfNeeded_WithNullTag_NoException() {
        View view = new View(context);
        view.setTag(null);

        // Should not throw exception
        View result = handler.getMaskedViewIfNeeded(view, false);

        Assert.assertNotNull(result);
        Assert.assertEquals(view, result);
    }

    @Test
    public void testGetMaskedViewIfNeeded_WithEmptyStringTag() {
        View view = new View(context);
        view.setTag("");

        View result = handler.getMaskedViewIfNeeded(view, false);

        // Empty string is not a special tag
        Assert.assertNotNull(result);
        Assert.assertEquals(view, result);
    }

    // ==================== INTEGRATION TESTS ====================

    @Test
    public void testCompleteFlow_FindAndGetId() {
        ViewGroup viewGroup = new FrameLayout(context);
        viewGroup.layout(0, 0, 300, 300);

        View child = new View(context);
        child.layout(50, 50, 150, 150);
        viewGroup.addView(child);

        // Find view at coordinates
        Object foundView = handler.findViewAtCoords(viewGroup, 100, 100);
        Assert.assertEquals(child, foundView);

        // Get stable ID for found view
        int id = handler.getViewStableId((View) foundView);
        Assert.assertTrue(id > 0);
    }

    @Test
    public void testCompleteFlow_FindCheckMaskAndGetId() {
        ViewGroup viewGroup = new FrameLayout(context);
        viewGroup.layout(0, 0, 300, 300);

        View child = new View(context);
        child.layout(50, 50, 150, 150);
        child.setTag("nr-unmask");
        viewGroup.addView(child);

        // Find view
        Object foundView = handler.findViewAtCoords(viewGroup, 100, 100);
        Assert.assertEquals(child, foundView);

        // Check masking
        View unmaskedView = handler.getMaskedViewIfNeeded((View) foundView, true);
        Assert.assertNotNull(unmaskedView);

        // Get ID
        int id = handler.getViewStableId(unmaskedView);
        Assert.assertTrue(id > 0);
    }
}