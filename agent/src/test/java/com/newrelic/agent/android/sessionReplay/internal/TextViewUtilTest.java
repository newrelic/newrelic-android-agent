/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Color;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/**
 * Unit tests for TextViewUtil class
 * Tests text color extraction, React Native detection, and color formatting utilities
 */
@RunWith(RobolectricTestRunner.class)
public class TextViewUtilTest {

    private TextView standardTextView;

    @Before
    public void setUp() {
        standardTextView = new TextView(RuntimeEnvironment.application);
    }

    // ============================================
    // getFirstForegroundColorFromSpannable() TESTS
    // ============================================

    @Test
    public void testGetFirstForegroundColorFromSpannable_withNull_returnsNull() {
        Integer result = TextViewUtil.getFirstForegroundColorFromSpannable(null);

        assertNull("Should return null for null spannable", result);
    }

    @Test
    public void testGetFirstForegroundColorFromSpannable_withNoSpans_returnsNull() {
        SpannableString spannable = new SpannableString("Plain text");

        Integer result = TextViewUtil.getFirstForegroundColorFromSpannable(spannable);

        assertNull("Should return null when no ForegroundColorSpan present", result);
    }

    @Test
    public void testGetFirstForegroundColorFromSpannable_withSingleSpan_returnsColor() {
        SpannableString spannable = new SpannableString("Colored text");
        int expectedColor = Color.RED;
        spannable.setSpan(new ForegroundColorSpan(expectedColor), 0, 7, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        Integer result = TextViewUtil.getFirstForegroundColorFromSpannable(spannable);

        assertNotNull("Should return color", result);
        assertEquals("Should return red color", expectedColor, result.intValue());
    }

    @Test
    public void testGetFirstForegroundColorFromSpannable_withMultipleSpans_returnsFirstColor() {
        SpannableString spannable = new SpannableString("Multi colored text");

        // Add multiple color spans
        int firstColor = Color.RED;
        int secondColor = Color.BLUE;
        int thirdColor = Color.GREEN;

        spannable.setSpan(new ForegroundColorSpan(firstColor), 0, 5, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(new ForegroundColorSpan(secondColor), 6, 13, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(new ForegroundColorSpan(thirdColor), 14, 18, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        Integer result = TextViewUtil.getFirstForegroundColorFromSpannable(spannable);

        assertNotNull("Should return color", result);
        assertEquals("Should return first span color (RED)", firstColor, result.intValue());
    }

    @Test
    public void testGetFirstForegroundColorFromSpannable_withTransparentColor_returnsTransparent() {
        SpannableString spannable = new SpannableString("Transparent text");
        int transparentColor = Color.TRANSPARENT; // 0x00000000
        spannable.setSpan(new ForegroundColorSpan(transparentColor), 0, 11, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        Integer result = TextViewUtil.getFirstForegroundColorFromSpannable(spannable);

        assertNotNull("Should return color", result);
        assertEquals("Should return transparent color", transparentColor, result.intValue());
    }

    @Test
    public void testGetFirstForegroundColorFromSpannable_withCustomArgbColor_returnsColor() {
        SpannableString spannable = new SpannableString("Custom ARGB text");
        int customColor = 0xFF8844AA; // ARGB: Alpha=255, Red=136, Green=68, Blue=170
        spannable.setSpan(new ForegroundColorSpan(customColor), 0, 6, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        Integer result = TextViewUtil.getFirstForegroundColorFromSpannable(spannable);

        assertNotNull("Should return color", result);
        assertEquals("Should return custom ARGB color", customColor, result.intValue());
    }

    @Test
    public void testGetFirstForegroundColorFromSpannable_withEmptyString_returnsNull() {
        SpannableString spannable = new SpannableString("");

        Integer result = TextViewUtil.getFirstForegroundColorFromSpannable(spannable);

        assertNull("Should return null for empty spannable", result);
    }

    // ============================================
    // isReactNativeTextView() TESTS
    // ============================================

    @Test
    public void testIsReactNativeTextView_withNull_returnsFalse() {
        boolean result = TextViewUtil.isReactNativeTextView(null);

        assertFalse("Should return false for null TextView", result);
    }

    @Test
    public void testIsReactNativeTextView_withStandardTextView_returnsFalse() {
        boolean result = TextViewUtil.isReactNativeTextView(standardTextView);

        assertFalse("Should return false for standard TextView", result);
    }

    @Test
    public void testIsReactNativeTextView_withCustomSubclass_returnsFalse() {
        // Test with a custom TextView subclass (not ReactTextView)
        // Note: Testing the true case would require actual React Native dependency
        CustomTextView customTextView = new CustomTextView();

        boolean result = TextViewUtil.isReactNativeTextView(customTextView);

        assertFalse("Should return false for non-ReactTextView subclass", result);
    }

    @Test
    public void testIsReactNativeTextView_checksExactClassName() {
        // Verify that the method checks for exact class name match
        // Any TextView that is not com.facebook.react.views.text.ReactTextView should return false
        TextView[] nonReactViews = {
            standardTextView,
            new CustomTextView(),
            new android.widget.EditText(RuntimeEnvironment.application)
        };

        for (TextView textView : nonReactViews) {
            boolean result = TextViewUtil.isReactNativeTextView(textView);
            assertFalse("Should return false for " + textView.getClass().getName(), result);
        }
    }

    // ============================================
    // getTextColor() TESTS
    // ============================================

    @Test
    public void testGetTextColor_withNull_returnsDefaultBlack() {
        int result = TextViewUtil.getTextColor(null);

        assertEquals("Should return default black color", 0xFF000000, result);
    }

    @Test
    public void testGetTextColor_withStandardTextView_returnsCurrentTextColor() {
        int expectedColor = Color.BLUE;
        standardTextView.setTextColor(expectedColor);

        int result = TextViewUtil.getTextColor(standardTextView);

        assertEquals("Should return TextView's current text color", expectedColor, result);
    }

    @Test
    public void testGetTextColor_withNonReactNativeTextView_usesStandardExtraction() {
        // This test verifies that non-React Native TextViews use standard color extraction
        // Actual React Native spannable extraction is tested in ReflectionUtilsTest

        CustomTextView customTextView = new CustomTextView();
        customTextView.setTextColor(Color.RED);

        int result = TextViewUtil.getTextColor(customTextView);

        // Should use standard getCurrentTextColor()
        assertEquals("Should return standard text color", Color.RED, result);
    }

    // ============================================
    // colorToRgbHex() TESTS
    // ============================================

    @Test
    public void testColorToRgbHex_withBlack_returns000000() {
        String result = TextViewUtil.colorToRgbHex(Color.BLACK);

        assertEquals("Should return 000000 for black", "000000", result);
    }

    @Test
    public void testColorToRgbHex_withWhite_returnsffffff() {
        String result = TextViewUtil.colorToRgbHex(Color.WHITE);

        assertEquals("Should return ffffff for white", "ffffff", result);
    }

    @Test
    public void testColorToRgbHex_withRed_returnsff0000() {
        String result = TextViewUtil.colorToRgbHex(Color.RED);

        assertEquals("Should return ff0000 for red", "ff0000", result);
    }

    @Test
    public void testColorToRgbHex_withGreen_returns00ff00() {
        String result = TextViewUtil.colorToRgbHex(Color.GREEN);

        assertEquals("Should return 00ff00 for green", "00ff00", result);
    }

    @Test
    public void testColorToRgbHex_withBlue_returns0000ff() {
        String result = TextViewUtil.colorToRgbHex(Color.BLUE);

        assertEquals("Should return 0000ff for blue", "0000ff", result);
    }

    @Test
    public void testColorToRgbHex_withCustomColor_returnsCorrectHex() {
        int customColor = 0xFF8844AA; // RGB: 8844AA
        String result = TextViewUtil.colorToRgbHex(customColor);

        assertEquals("Should return 8844aa for custom color", "8844aa", result);
    }

    @Test
    public void testColorToRgbHex_masksOffAlphaChannel() {
        // Test that alpha channel is properly masked off
        int colorWithAlpha = 0x80FF0000; // 50% transparent red
        String result = TextViewUtil.colorToRgbHex(colorWithAlpha);

        assertEquals("Should mask off alpha and return ff0000", "ff0000", result);
    }

    @Test
    public void testColorToRgbHex_withTransparent_returns000000() {
        // Transparent is 0x00000000, masking off alpha gives 000000
        String result = TextViewUtil.colorToRgbHex(Color.TRANSPARENT);

        assertEquals("Should return 000000 (alpha masked)", "000000", result);
    }

    @Test
    public void testColorToRgbHex_leadingZerosPreserved() {
        // Test color that starts with zeros
        int colorWithLeadingZero = 0xFF000123; // RGB: 000123
        String result = TextViewUtil.colorToRgbHex(colorWithLeadingZero);

        assertEquals("Should preserve leading zeros", "000123", result);
    }

    // ============================================
    // INTEGRATION TESTS
    // ============================================

    @Test
    public void testIntegration_standardTextViewColorExtraction() {
        // End-to-end test: standard TextView color extraction and formatting
        standardTextView.setTextColor(0xFFFF5733); // Orange

        int color = TextViewUtil.getTextColor(standardTextView);
        String hexColor = TextViewUtil.colorToRgbHex(color);

        assertEquals("Should extract correct color", 0xFFFF5733, color);
        assertEquals("Should format as hex correctly", "ff5733", hexColor);
    }

    @Test
    public void testIntegration_colorExtractionAndFormattingPipeline() {
        // Test the complete pipeline used by SessionReplayTextViewThingy
        standardTextView.setTextColor(Color.CYAN);

        // This is how SessionReplayTextViewThingy uses it
        int textColorInt = TextViewUtil.getTextColor(standardTextView);
        String textColorHex = TextViewUtil.colorToRgbHex(textColorInt);

        assertEquals("Should extract cyan color", Color.CYAN, textColorInt);
        assertEquals("Should format cyan as hex", "00ffff", textColorHex);
    }

    @Test
    public void testIntegration_spannableColorExtractionPipeline() {
        // Test extracting color from a spannable and formatting it
        SpannableString spannable = new SpannableString("Red text");
        spannable.setSpan(new ForegroundColorSpan(Color.RED), 0, 3, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        Integer extractedColor = TextViewUtil.getFirstForegroundColorFromSpannable(spannable);
        assertNotNull("Should extract color from spannable", extractedColor);

        String hexColor = TextViewUtil.colorToRgbHex(extractedColor);
        assertEquals("Should format red as ff0000", "ff0000", hexColor);
    }

    // ============================================
    // EDGE CASE TESTS
    // ============================================

    @Test
    public void testEdgeCase_multipleOverlappingSpans_returnsFirstInArray() {
        SpannableString spannable = new SpannableString("Overlapping spans");

        // Add overlapping spans (both cover same text)
        spannable.setSpan(new ForegroundColorSpan(Color.RED), 0, 11, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(new ForegroundColorSpan(Color.BLUE), 0, 11, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        Integer result = TextViewUtil.getFirstForegroundColorFromSpannable(spannable);

        assertNotNull("Should return a color", result);
        // The first span in the array is returned (order depends on how spans are stored)
    }

    @Test
    public void testEdgeCase_veryLongHexColor_formatsCorrectly() {
        // Test with maximum color value
        int maxColor = 0xFFFFFFFF; // All bits set
        String result = TextViewUtil.colorToRgbHex(maxColor);

        assertEquals("Should format max color correctly", "ffffff", result);
    }

    @Test
    public void testEdgeCase_nullTextViewClassNameCheck() {
        // Ensure null check happens before class name check
        TextView nullTextView = null;

        // Should not throw NullPointerException
        boolean result = TextViewUtil.isReactNativeTextView(nullTextView);

        assertFalse("Should safely return false for null", result);
    }

    // ============================================
    // TEST HELPER CLASSES
    // ============================================

    /**
     * Custom TextView subclass for testing
     * Used to verify that non-React Native TextViews are handled correctly
     */
    private static class CustomTextView extends TextView {
        public CustomTextView() {
            super(RuntimeEnvironment.application);
        }
    }
}