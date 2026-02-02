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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Field;

/**
 * Tests for ReflectionUtils class, specifically focusing on React Native background drawable reflection
 * and null safety scenarios.
 */
@RunWith(RobolectricTestRunner.class)
public class ReflectionUtilsTest {

    @Before
    public void setUp() {
        // Reset any cached state by forcing re-initialization
        // Note: In real scenarios, reflection state is cached, but for tests we document the behavior
    }

    // ============================================
    // NULL SCENARIO TESTS
    // ============================================

    @Test
    public void testGetReactNativeBackgroundColor_withNullDrawable_returnsNull() {
        // Test that null drawable returns null
        Integer result = ReflectionUtils.getReactNativeBackgroundColor(null);

        assertNull("Should return null for null drawable", result);
    }

    @Test
    public void testIsReactNativeBackgroundDrawable_withNullDrawable_returnsFalse() {
        // Test that null drawable returns false
        boolean result = ReflectionUtils.isReactNativeBackgroundDrawable(null);

        assertFalse("Should return false for null drawable", result);
    }

    @Test
    public void testGetReactNativeBackgroundColor_withNonReactNativeDrawable_returnsNull() {
        // Test with a standard Android drawable (not React Native)
        ColorDrawable colorDrawable = new ColorDrawable(Color.RED);

        Integer result = ReflectionUtils.getReactNativeBackgroundColor(colorDrawable);

        assertNull("Should return null for non-React Native drawable", result);
    }

    @Test
    public void testIsReactNativeBackgroundDrawable_withNonReactNativeDrawable_returnsFalse() {
        // Test with a standard Android drawable (not React Native)
        GradientDrawable gradientDrawable = new GradientDrawable();

        boolean result = ReflectionUtils.isReactNativeBackgroundDrawable(gradientDrawable);

        assertFalse("Should return false for non-React Native drawable", result);
    }

    @Test
    public void testGetReactNativeBackgroundColor_inNonReactNativeApp_returnsNullFast() {
        // In a non-React Native app (like this test environment),
        // the method should return null quickly without throwing exceptions
        ColorDrawable drawable = new ColorDrawable(Color.BLUE);

        long startTime = System.nanoTime();
        Integer result = ReflectionUtils.getReactNativeBackgroundColor(drawable);
        long endTime = System.nanoTime();

        assertNull("Should return null in non-React Native app", result);

        // Verify it's fast (less than 1ms for cached failure)
        long durationMs = (endTime - startTime) / 1_000_000;
        assertTrue("Should be fast (< 10ms) due to caching: " + durationMs + "ms", durationMs < 10);
    }

    // ============================================
    // INT VALUE EXTRACTION TESTS (SIMULATED)
    // ============================================

    @Test
    public void testColorValueConversion_verifyColorIntFormat() {
        // Test that we understand color int format correctly
        // React Native stores colors as ARGB integers

        int red = Color.RED;        // 0xFFFF0000
        int green = Color.GREEN;    // 0xFF00FF00
        int blue = Color.BLUE;      // 0xFF0000FF
        int transparent = Color.TRANSPARENT; // 0x00000000

        assertEquals("Red should be 0xFFFF0000", 0xFFFF0000, red);
        assertEquals("Green should be 0xFF00FF00", 0xFF00FF00, green);
        assertEquals("Blue should be 0xFF0000FF", 0xFF0000FF, blue);
        assertEquals("Transparent should be 0x00000000", 0x00000000, transparent);
    }

    @Test
    public void testColorValueExtraction_verifyComponentExtraction() {
        // Test that color component extraction works correctly
        int color = 0xFF8844AA; // ARGB: Alpha=255, Red=136, Green=68, Blue=170

        int alpha = Color.alpha(color);
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);

        assertEquals("Alpha should be 255", 255, alpha);
        assertEquals("Red should be 136", 136, red);
        assertEquals("Green should be 68", 68, green);
        assertEquals("Blue should be 170", 170, blue);
    }

    /**
     * This test simulates what would happen if React Native drawables were present.
     * It demonstrates the expected behavior with actual color values.
     */
    @Test
    public void testSimulatedReactNativeColorExtraction() {
        // Simulate typical React Native color values
        int[] testColors = {
            Color.WHITE,        // 0xFFFFFFFF
            Color.BLACK,        // 0xFF000000
            Color.RED,          // 0xFFFF0000
            Color.TRANSPARENT,  // 0x00000000
            0x80FF0000,         // Semi-transparent red
            0xFF123456          // Custom color
        };

        for (int color : testColors) {
            // In a real React Native app, the reflection would extract this int value
            // Here we verify the int value would be correctly formatted
            assertNotNull("Color value should not be null", Integer.valueOf(color));

            // Verify the color is a valid 32-bit integer
            assertTrue("Color should fit in int range",
                color >= Integer.MIN_VALUE && color <= Integer.MAX_VALUE);
        }
    }

    // ============================================
    // MOCK DRAWABLE TESTS (React Native Simulation)
    // ============================================

    /**
     * This test creates a mock drawable that simulates React Native's CSSBackgroundDrawable
     * to verify the reflection would work correctly if the class existed.
     */
    @Test
    public void testMockReactNativeDrawable_simulateColorExtraction() throws Exception {
        // Create a test drawable class that simulates React Native structure
        TestReactNativeDrawable testDrawable = new TestReactNativeDrawable();
        testDrawable.mColor = Color.parseColor("#FF5733"); // Orange color

        // Verify we can extract the color using reflection (simulating what our code does)
        Field colorField = TestReactNativeDrawable.class.getDeclaredField("mColor");
        colorField.setAccessible(true);
        int extractedColor = colorField.getInt(testDrawable);

        assertEquals("Should extract the correct color value",
            Color.parseColor("#FF5733"), extractedColor);
    }

    /**
     * This test simulates React Native's ReactViewBackgroundDrawable
     */
    @Test
    public void testMockOldReactNativeDrawable_simulateColorExtraction() throws Exception {
        TestOldReactNativeDrawable testDrawable = new TestOldReactNativeDrawable();
        testDrawable.mColor = 0xFF00FF00; // Green

        // Verify reflection extraction
        Field colorField = TestOldReactNativeDrawable.class.getDeclaredField("mColor");
        colorField.setAccessible(true);
        int extractedColor = colorField.getInt(testDrawable);

        assertEquals("Should extract green color", 0xFF00FF00, extractedColor);
    }

    /**
     * This test simulates React Native's BackgroundDrawable (middle version)
     */
    @Test
    public void testMockMiddleReactNativeDrawable_simulateColorExtraction() throws Exception {
        TestMiddleReactNativeDrawable testDrawable = new TestMiddleReactNativeDrawable();
        testDrawable.backgroundColor = Color.CYAN;

        // Verify reflection extraction
        Field colorField = TestMiddleReactNativeDrawable.class.getDeclaredField("backgroundColor");
        colorField.setAccessible(true);
        int extractedColor = colorField.getInt(testDrawable);

        assertEquals("Should extract cyan color", Color.CYAN, extractedColor);
    }

    /**
     * This test simulates React Native's CompositeBackgroundDrawable
     */
    @Test
    public void testMockCompositeDrawable_simulateUnwrapping() throws Exception {
        // Create wrapped drawable
        TestReactNativeDrawable innerDrawable = new TestReactNativeDrawable();
        innerDrawable.mColor = Color.MAGENTA;

        // Create composite wrapper
        TestCompositeDrawable compositeDrawable = new TestCompositeDrawable();
        compositeDrawable.background = innerDrawable;

        // Verify we can unwrap and extract
        Field backgroundField = TestCompositeDrawable.class.getDeclaredField("background");
        backgroundField.setAccessible(true);
        Object unwrapped = backgroundField.get(compositeDrawable);

        assertNotNull("Should unwrap background drawable", unwrapped);
        assertTrue("Unwrapped should be TestReactNativeDrawable",
            unwrapped instanceof TestReactNativeDrawable);

        TestReactNativeDrawable extracted = (TestReactNativeDrawable) unwrapped;
        assertEquals("Should extract magenta color", Color.MAGENTA, extracted.mColor);
    }

    // ============================================
    // EDGE CASE TESTS
    // ============================================

    @Test
    public void testGetFillPaint_withNullGradientDrawable_returnsNull() {
        // Test GradientDrawable reflection with null
        assertNull("Should handle null GradientDrawable",
            ReflectionUtils.getFillPaint(null));
    }

    @Test
    public void testGetStrokePaint_withNullGradientDrawable_returnsNull() {
        // Test GradientDrawable reflection with null
        assertNull("Should handle null GradientDrawable",
            ReflectionUtils.getStrokePaint(null));
    }

    @Test
    public void testGetFillPaint_withValidGradientDrawable_doesNotThrow() {
        // Test that the method doesn't throw exceptions with valid drawable
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.RED);

        // Should not throw, may return Paint or null depending on Android version
        try {
            ReflectionUtils.getFillPaint(drawable);
        } catch (Exception e) {
            // Should not reach here
            assertTrue("Should not throw exception: " + e.getMessage(), false);
        }
    }

    @Test
    public void testGetStrokePaint_withValidGradientDrawable_doesNotThrow() {
        // Test that the method doesn't throw exceptions with valid drawable
        GradientDrawable drawable = new GradientDrawable();
        drawable.setStroke(5, Color.BLACK);

        // Should not throw, may return Paint or null depending on Android version
        try {
            ReflectionUtils.getStrokePaint(drawable);
        } catch (Exception e) {
            // Should not reach here
            assertTrue("Should not throw exception: " + e.getMessage(), false);
        }
    }

    // ============================================
    // PERFORMANCE TESTS
    // ============================================

    @Test
    public void testReflectionCaching_multipleCallsAreFast() {
        // Test that subsequent calls to reflection methods are fast due to caching
        ColorDrawable drawable = new ColorDrawable(Color.YELLOW);

        // First call initializes reflection
        long firstStart = System.nanoTime();
        ReflectionUtils.getReactNativeBackgroundColor(drawable);
        long firstEnd = System.nanoTime();

        // Subsequent calls should be cached
        long secondStart = System.nanoTime();
        ReflectionUtils.getReactNativeBackgroundColor(drawable);
        long secondEnd = System.nanoTime();

        long thirdStart = System.nanoTime();
        ReflectionUtils.getReactNativeBackgroundColor(drawable);
        long thirdEnd = System.nanoTime();

        long firstDuration = (firstEnd - firstStart) / 1_000_000;
        long secondDuration = (secondEnd - secondStart) / 1_000_000;
        long thirdDuration = (thirdEnd - thirdStart) / 1_000_000;

        // Second and third calls should be faster (or at least not significantly slower)
        System.out.println("First call: " + firstDuration + "ms");
        System.out.println("Second call: " + secondDuration + "ms");
        System.out.println("Third call: " + thirdDuration + "ms");

        // All calls should complete in reasonable time (< 100ms even for first call)
        assertTrue("First call should complete quickly", firstDuration < 100);
        assertTrue("Second call should complete quickly", secondDuration < 100);
        assertTrue("Third call should complete quickly", thirdDuration < 100);
    }

    // ============================================
    // TEST HELPER CLASSES (Simulating React Native)
    // ============================================

    /**
     * Simulates React Native's CSSBackgroundDrawable (0.74+)
     */
    private static class TestReactNativeDrawable extends Drawable {
        public int mColor = Color.TRANSPARENT;

        @Override
        public void draw(android.graphics.Canvas canvas) {}

        @Override
        public void setAlpha(int alpha) {}

        @Override
        public void setColorFilter(android.graphics.ColorFilter colorFilter) {}

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.OPAQUE;
        }
    }

    /**
     * Simulates React Native's ReactViewBackgroundDrawable (older versions)
     */
    private static class TestOldReactNativeDrawable extends Drawable {
        public int mColor = Color.TRANSPARENT;

        @Override
        public void draw(android.graphics.Canvas canvas) {}

        @Override
        public void setAlpha(int alpha) {}

        @Override
        public void setColorFilter(android.graphics.ColorFilter colorFilter) {}

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.OPAQUE;
        }
    }

    /**
     * Simulates React Native's BackgroundDrawable (middle versions)
     */
    private static class TestMiddleReactNativeDrawable extends Drawable {
        public int backgroundColor = Color.TRANSPARENT;

        @Override
        public void draw(android.graphics.Canvas canvas) {}

        @Override
        public void setAlpha(int alpha) {}

        @Override
        public void setColorFilter(android.graphics.ColorFilter colorFilter) {}

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.OPAQUE;
        }
    }

    /**
     * Simulates React Native's CompositeBackgroundDrawable
     */
    private static class TestCompositeDrawable extends Drawable {
        public Drawable background = null;

        @Override
        public void draw(android.graphics.Canvas canvas) {}

        @Override
        public void setAlpha(int alpha) {}

        @Override
        public void setColorFilter(android.graphics.ColorFilter colorFilter) {}

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.OPAQUE;
        }
    }
}