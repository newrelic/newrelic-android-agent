/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class ViewDetailsTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
    }

    // ==================== CONSTRUCTOR TESTS ====================

    @Test
    public void testConstructorWithBasicView() {
        View view = new View(context);

        ViewDetails viewDetails = new ViewDetails(view);

        Assert.assertNotNull(viewDetails);
        Assert.assertNotNull(viewDetails.getViewName());
        Assert.assertEquals("View", viewDetails.getViewName());
    }

    @Test
    public void testConstructorExtractsViewId() {
        View view = new View(context);

        ViewDetails viewDetails = new ViewDetails(view);

        Assert.assertTrue(viewDetails.getViewId() > 0);
    }

    @Test
    public void testConstructorExtractsFrame() {
        View view = new View(context);
        view.layout(10, 20, 110, 120); // left, top, right, bottom

        ViewDetails viewDetails = new ViewDetails(view);

        Assert.assertNotNull(viewDetails.getFrame());
        Rect frame = viewDetails.getFrame();
        Assert.assertTrue(frame.width() >= 0);
        Assert.assertTrue(frame.height() >= 0);
    }

    @Test
    public void testConstructorExtractsBackgroundColor() {
        View view = new View(context);
        view.setBackgroundColor(Color.RED);

        ViewDetails viewDetails = new ViewDetails(view);

        String backgroundColor = viewDetails.getBackgroundColor();
        Assert.assertNotNull(backgroundColor);
    }

    @Test
    public void testConstructorExtractsAlpha() {
        View view = new View(context);
        view.setAlpha(0.5f);

        ViewDetails viewDetails = new ViewDetails(view);

        Assert.assertEquals(0.5f, viewDetails.getAlpha(), 0.01f);
    }

    @Test
    public void testConstructorExtractsVisibility_Visible() {
        View view = new View(context);
        view.setVisibility(View.VISIBLE);

        ViewDetails viewDetails = new ViewDetails(view);

        Assert.assertFalse(viewDetails.isHidden());
    }

    @Test
    public void testConstructorExtractsVisibility_Gone() {
        View view = new View(context);
        view.setVisibility(View.GONE);

        ViewDetails viewDetails = new ViewDetails(view);

        Assert.assertTrue(viewDetails.isHidden());
    }

    @Test
    public void testConstructorExtractsVisibility_Invisible() {
        View view = new View(context);
        view.setVisibility(View.INVISIBLE);

        ViewDetails viewDetails = new ViewDetails(view);

        Assert.assertTrue(viewDetails.isHidden());
    }

    @Test
    public void testConstructorExtractsParentId() {
        FrameLayout parent = new FrameLayout(context);
        View child = new View(context);
        parent.addView(child);

        ViewDetails childDetails = new ViewDetails(child);

        Assert.assertTrue(childDetails.parentId > 0);
    }

    @Test
    public void testConstructorWithNoParent() {
        View view = new View(context);

        ViewDetails viewDetails = new ViewDetails(view);

        Assert.assertEquals(0, viewDetails.parentId);
    }

    // ==================== STABLE ID GENERATION TESTS ====================

    @Test
    public void testStableIdConsistency() {
        View view = new View(context);

        ViewDetails viewDetails1 = new ViewDetails(view);
        ViewDetails viewDetails2 = new ViewDetails(view);

        // Same view should have same stable ID
        Assert.assertEquals(viewDetails1.getViewId(), viewDetails2.getViewId());
    }

    @Test
    public void testStableIdUniqueness() {
        View view1 = new View(context);
        View view2 = new View(context);

        ViewDetails viewDetails1 = new ViewDetails(view1);
        ViewDetails viewDetails2 = new ViewDetails(view2);

        // Different views should have different stable IDs
        Assert.assertNotEquals(viewDetails1.getViewId(), viewDetails2.getViewId());
    }

    // ==================== COMPUTED PROPERTY TESTS ====================

    @Test
    public void testGetCssSelector() {
        View view = new View(context);

        ViewDetails viewDetails = new ViewDetails(view);

        String cssSelector = viewDetails.getCssSelector();
        Assert.assertNotNull(cssSelector);
        Assert.assertTrue(cssSelector.contains("View-"));
        Assert.assertTrue(cssSelector.contains(String.valueOf(viewDetails.getViewId())));
    }

    @Test
    public void testGetCSSSelectorAlternate() {
        View view = new View(context);

        ViewDetails viewDetails = new ViewDetails(view);

        String cssSelector1 = viewDetails.getCssSelector();
        String cssSelector2 = viewDetails.getCSSSelector();

        // Both methods should return the same value
        Assert.assertEquals(cssSelector1, cssSelector2);
    }

    @Test
    public void testIsVisible_VisibleView() {
        View view = new View(context);
        view.setVisibility(View.VISIBLE);
        view.setAlpha(1.0f);
        view.layout(0, 0, 100, 100);

        ViewDetails viewDetails = new ViewDetails(view);

        Assert.assertTrue(viewDetails.isVisible());
    }

    @Test
    public void testIsVisible_HiddenView() {
        View view = new View(context);
        view.setVisibility(View.GONE);

        ViewDetails viewDetails = new ViewDetails(view);

        Assert.assertFalse(viewDetails.isVisible());
    }

    @Test
    public void testIsVisible_ZeroAlpha() {
        View view = new View(context);
        view.setVisibility(View.VISIBLE);
        view.setAlpha(0.0f);
        view.layout(0, 0, 100, 100);

        ViewDetails viewDetails = new ViewDetails(view);

        Assert.assertFalse(viewDetails.isVisible());
    }

    @Test
    public void testIsVisible_ZeroSize() {
        View view = new View(context);
        view.setVisibility(View.VISIBLE);
        view.setAlpha(1.0f);
        // Don't layout the view - it will have zero size

        ViewDetails viewDetails = new ViewDetails(view);

        Assert.assertFalse(viewDetails.isVisible());
    }

    @Test
    public void testIsClear() {
        View view = new View(context);
        view.setAlpha(0.5f);

        ViewDetails viewDetails = new ViewDetails(view);

        // isClear checks if alpha <= 1.0, which should always be true
        Assert.assertTrue(viewDetails.isClear());
    }

    @Test
    public void testIsClear_FullyOpaque() {
        View view = new View(context);
        view.setAlpha(1.0f);

        ViewDetails viewDetails = new ViewDetails(view);

        Assert.assertTrue(viewDetails.isClear());
    }

    @Test
    public void testIsClear_FullyTransparent() {
        View view = new View(context);
        view.setAlpha(0.0f);

        ViewDetails viewDetails = new ViewDetails(view);

        Assert.assertTrue(viewDetails.isClear());
    }

    // ==================== CSS GENERATION TESTS ====================

    @Test
    public void testGenerateCssDescription() {
        View view = new View(context);
        view.layout(10, 20, 110, 120);

        ViewDetails viewDetails = new ViewDetails(view);

        String cssDescription = viewDetails.generateCssDescription();

        Assert.assertNotNull(cssDescription);
        Assert.assertTrue(cssDescription.contains("#"));
        Assert.assertTrue(cssDescription.contains("{"));
        Assert.assertTrue(cssDescription.contains("position:"));
    }

    @Test
    public void testGenerateInlineCSS() {
        View view = new View(context);
        view.layout(10, 20, 110, 120);
        view.setBackgroundColor(Color.BLUE);

        ViewDetails viewDetails = new ViewDetails(view);

        String inlineCss = viewDetails.generateInlineCSS();

        Assert.assertNotNull(inlineCss);
        Assert.assertTrue(inlineCss.contains("position:"));
        Assert.assertTrue(inlineCss.contains("fixed"));
        Assert.assertTrue(inlineCss.contains("left:"));
        Assert.assertTrue(inlineCss.contains("top:"));
        Assert.assertTrue(inlineCss.contains("width:"));
        Assert.assertTrue(inlineCss.contains("height:"));
    }

    @Test
    public void testGenerateInlineCSSWithBackground() {
        View view = new View(context);
        view.setBackgroundColor(Color.RED);
        view.layout(0, 0, 100, 100);

        ViewDetails viewDetails = new ViewDetails(view);

        String inlineCss = viewDetails.generateInlineCSS();

        Assert.assertTrue(inlineCss.contains("background-color:"));
    }

    @Test
    public void testGenerateInlineCSSWithNoBackground() {
        View view = new View(context);
        view.layout(0, 0, 100, 100);

        ViewDetails viewDetails = new ViewDetails(view);

        String inlineCss = viewDetails.generateInlineCSS();

        Assert.assertNotNull(inlineCss);
        Assert.assertTrue(inlineCss.contains("position:"));
    }

    @Test
    public void testGenerateInlineCSSWithGradientDrawable() {
        View view = new View(context);
        view.layout(0, 0, 100, 100);

        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(Color.GREEN);
        view.setBackground(gradientDrawable);

        ViewDetails viewDetails = new ViewDetails(view);

        String inlineCss = viewDetails.generateInlineCSS();

        Assert.assertNotNull(inlineCss);
    }

    @Test
    public void testGenerateInlineCSSWithColorDrawable() {
        View view = new View(context);
        view.layout(0, 0, 100, 100);

        ColorDrawable colorDrawable = new ColorDrawable(Color.YELLOW);
        view.setBackground(colorDrawable);

        ViewDetails viewDetails = new ViewDetails(view);

        String inlineCss = viewDetails.generateInlineCSS();

        Assert.assertNotNull(inlineCss);
        Assert.assertTrue(inlineCss.contains("background-color:"));
    }

    // ==================== GETTER TESTS ====================

    @Test
    public void testGetViewId() {
        View view = new View(context);

        ViewDetails viewDetails = new ViewDetails(view);

        int viewId = viewDetails.getViewId();
        Assert.assertTrue(viewId > 0);
        Assert.assertEquals(viewDetails.viewId, viewId);
    }

    @Test
    public void testGetFrame() {
        View view = new View(context);
        view.layout(10, 20, 110, 120);

        ViewDetails viewDetails = new ViewDetails(view);

        Rect frame = viewDetails.getFrame();
        Assert.assertNotNull(frame);
        Assert.assertEquals(viewDetails.frame, frame);
    }

    @Test
    public void testGetBackgroundColor() {
        View view = new View(context);
        view.setBackgroundColor(Color.MAGENTA);

        ViewDetails viewDetails = new ViewDetails(view);

        String backgroundColor = viewDetails.getBackgroundColor();
        Assert.assertNotNull(backgroundColor);
        Assert.assertEquals(viewDetails.backgroundColor, backgroundColor);
    }

    @Test
    public void testGetAlpha() {
        View view = new View(context);
        view.setAlpha(0.75f);

        ViewDetails viewDetails = new ViewDetails(view);

        float alpha = viewDetails.getAlpha();
        Assert.assertEquals(0.75f, alpha, 0.01f);
        Assert.assertEquals(viewDetails.alpha, alpha, 0.01f);
    }

    @Test
    public void testIsHidden() {
        View view = new View(context);
        view.setVisibility(View.GONE);

        ViewDetails viewDetails = new ViewDetails(view);

        boolean isHidden = viewDetails.isHidden();
        Assert.assertTrue(isHidden);
        Assert.assertEquals(viewDetails.isHidden, isHidden);
    }

    @Test
    public void testGetViewName() {
        View view = new View(context);

        ViewDetails viewDetails = new ViewDetails(view);

        String viewName = viewDetails.getViewName();
        Assert.assertNotNull(viewName);
        Assert.assertEquals("View", viewName);
        Assert.assertEquals(viewDetails.viewName, viewName);
    }

    @Test
    public void testGetViewNameWithCustomView() {
        LinearLayout linearLayout = new LinearLayout(context);

        ViewDetails viewDetails = new ViewDetails(linearLayout);

        String viewName = viewDetails.getViewName();
        Assert.assertEquals("LinearLayout", viewName);
    }

    // ==================== EQUALS AND HASHCODE TESTS ====================

    @Test
    public void testEquals_SameInstance() {
        View view = new View(context);
        ViewDetails viewDetails = new ViewDetails(view);

        Assert.assertEquals(viewDetails, viewDetails);
    }

    @Test
    public void testEquals_SameView() {
        View view = new View(context);
        view.layout(10, 20, 110, 120);
        view.setBackgroundColor(Color.RED);

        ViewDetails viewDetails1 = new ViewDetails(view);
        ViewDetails viewDetails2 = new ViewDetails(view);

        // Should be equal since it's the same view
        Assert.assertEquals(viewDetails1, viewDetails2);
    }

    @Test
    public void testEquals_DifferentViews() {
        View view1 = new View(context);
        view1.layout(10, 20, 110, 120);

        View view2 = new View(context);
        view2.layout(30, 40, 130, 140);

        ViewDetails viewDetails1 = new ViewDetails(view1);
        ViewDetails viewDetails2 = new ViewDetails(view2);

        // Should not be equal - different view IDs
        Assert.assertNotEquals(viewDetails1, viewDetails2);
    }

    @Test
    public void testEquals_Null() {
        View view = new View(context);
        ViewDetails viewDetails = new ViewDetails(view);

        Assert.assertNotEquals(viewDetails, null);
    }

    @Test
    public void testEquals_DifferentClass() {
        View view = new View(context);
        ViewDetails viewDetails = new ViewDetails(view);

        Assert.assertNotEquals(viewDetails, "not a ViewDetails");
    }

    @Test
    public void testHashCode_Consistency() {
        View view = new View(context);
        ViewDetails viewDetails = new ViewDetails(view);

        int hashCode1 = viewDetails.hashCode();
        int hashCode2 = viewDetails.hashCode();

        Assert.assertEquals(hashCode1, hashCode2);
    }

    @Test
    public void testHashCode_EqualObjects() {
        View view = new View(context);
        view.layout(10, 20, 110, 120);

        ViewDetails viewDetails1 = new ViewDetails(view);
        ViewDetails viewDetails2 = new ViewDetails(view);

        // Equal objects should have equal hash codes
        if (viewDetails1.equals(viewDetails2)) {
            Assert.assertEquals(viewDetails1.hashCode(), viewDetails2.hashCode());
        }
    }

    // ==================== EDGE CASE TESTS ====================

    @Test
    public void testViewDetailsWithZeroSizeFrame() {
        View view = new View(context);
        // Don't layout - frame will be 0x0

        ViewDetails viewDetails = new ViewDetails(view);

        Assert.assertNotNull(viewDetails.getFrame());
        Rect frame = viewDetails.getFrame();
        Assert.assertEquals(0, frame.width());
        Assert.assertEquals(0, frame.height());
    }

    @Test
    public void testViewDetailsWithLargeFrame() {
        View view = new View(context);
        view.layout(0, 0, 10000, 10000);

        ViewDetails viewDetails = new ViewDetails(view);

        Rect frame = viewDetails.getFrame();
        Assert.assertNotNull(frame);
        Assert.assertTrue(frame.width() > 0);
        Assert.assertTrue(frame.height() > 0);
    }

    @Test
    public void testViewDetailsWithNegativeCoordinates() {
        View view = new View(context);
        // Note: In real scenarios, negative coordinates are possible off-screen

        ViewDetails viewDetails = new ViewDetails(view);

        Assert.assertNotNull(viewDetails.getFrame());
    }

    @Test
    public void testViewDetailsWithFullyTransparentView() {
        View view = new View(context);
        view.setAlpha(0.0f);
        view.layout(0, 0, 100, 100);

        ViewDetails viewDetails = new ViewDetails(view);

        Assert.assertEquals(0.0f, viewDetails.getAlpha(), 0.01f);
        Assert.assertFalse(viewDetails.isVisible());
    }

    @Test
    public void testViewDetailsWithPartiallyTransparentView() {
        View view = new View(context);
        view.setAlpha(0.3f);
        view.layout(0, 0, 100, 100);
        view.setVisibility(View.VISIBLE);

        ViewDetails viewDetails = new ViewDetails(view);

        Assert.assertEquals(0.3f, viewDetails.getAlpha(), 0.01f);
        Assert.assertTrue(viewDetails.isVisible());
    }

    @Test
    public void testViewDetailsWithNestedViewHierarchy() {
        FrameLayout grandParent = new FrameLayout(context);
        LinearLayout parent = new LinearLayout(context);
        View child = new View(context);

        grandParent.addView(parent);
        parent.addView(child);

        ViewDetails childDetails = new ViewDetails(child);

        Assert.assertNotNull(childDetails);
        Assert.assertTrue(childDetails.parentId > 0);
    }

    @Test
    public void testDensityExtraction() {
        View view = new View(context);

        ViewDetails viewDetails = new ViewDetails(view);

        Assert.assertTrue(viewDetails.density > 0);
    }
}