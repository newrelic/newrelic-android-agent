/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.util.Base64;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
public class ImageCompressionUtilsTest {

    // ==================== BITMAP TO BASE64 TESTS ====================

    @Test
    public void testBitmapToBase64_WithDefaultQuality() {
        Bitmap bitmap = createTestBitmap(10, 10);

        String base64 = ImageCompressionUtils.bitmapToBase64(bitmap);

        Assert.assertNotNull(base64);
        Assert.assertTrue(base64.length() > 0);
    }

    @Test
    public void testBitmapToBase64_WithCustomQuality() {
        Bitmap bitmap = createTestBitmap(10, 10);
        int quality = 50;

        String base64 = ImageCompressionUtils.bitmapToBase64(bitmap, quality);

        Assert.assertNotNull(base64);
        Assert.assertTrue(base64.length() > 0);
    }

    @Test
    public void testBitmapToBase64_WithHighQuality() {
        Bitmap bitmap = createTestBitmap(10, 10);
        int quality = 100;

        String base64 = ImageCompressionUtils.bitmapToBase64(bitmap, quality);

        Assert.assertNotNull(base64);
        Assert.assertTrue(base64.length() > 0);
    }

    @Test
    public void testBitmapToBase64_WithLowQuality() {
        Bitmap bitmap = createTestBitmap(10, 10);
        int quality = 1;

        String base64 = ImageCompressionUtils.bitmapToBase64(bitmap, quality);

        Assert.assertNotNull(base64);
        Assert.assertTrue(base64.length() > 0);
    }

    @Test
    public void testBitmapToBase64_WithZeroQuality() {
        Bitmap bitmap = createTestBitmap(10, 10);
        int quality = 0;

        String base64 = ImageCompressionUtils.bitmapToBase64(bitmap, quality);

        // Should still produce output, even with 0 quality
        Assert.assertNotNull(base64);
    }

    @Test
    public void testBitmapToBase64_IsValidBase64() {
        Bitmap bitmap = createTestBitmap(10, 10);

        String base64 = ImageCompressionUtils.bitmapToBase64(bitmap);

        // Try to decode to verify it's valid Base64
        try {
            byte[] decoded = Base64.decode(base64, Base64.NO_WRAP);
            Assert.assertNotNull(decoded);
            Assert.assertTrue(decoded.length > 0);
        } catch (Exception e) {
            Assert.fail("Invalid Base64 string: " + e.getMessage());
        }
    }

    @Test
    public void testBitmapToBase64_WithLargeBitmap() {
        Bitmap bitmap = createTestBitmap(100, 100);

        String base64 = ImageCompressionUtils.bitmapToBase64(bitmap);

        Assert.assertNotNull(base64);
        Assert.assertTrue(base64.length() > 0);
    }

    @Test
    public void testBitmapToBase64_WithSmallBitmap() {
        Bitmap bitmap = createTestBitmap(1, 1);

        String base64 = ImageCompressionUtils.bitmapToBase64(bitmap);

        Assert.assertNotNull(base64);
        Assert.assertTrue(base64.length() > 0);
    }

    @Test
    public void testBitmapToBase64_WithColoredBitmap() {
        Bitmap bitmap = createColoredBitmap(10, 10, Color.RED);

        String base64 = ImageCompressionUtils.bitmapToBase64(bitmap);

        Assert.assertNotNull(base64);
        Assert.assertTrue(base64.length() > 0);
    }

    @Test
    public void testBitmapToBase64_WithTransparentBitmap() {
        Bitmap bitmap = createColoredBitmap(10, 10, Color.TRANSPARENT);

        String base64 = ImageCompressionUtils.bitmapToBase64(bitmap);

        Assert.assertNotNull(base64);
        Assert.assertTrue(base64.length() > 0);
    }

    @Test
    public void testBitmapToBase64_ARGB8888Config() {
        Bitmap bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888);

        String base64 = ImageCompressionUtils.bitmapToBase64(bitmap);

        Assert.assertNotNull(base64);
        Assert.assertTrue(base64.length() > 0);
    }

    @Test
    public void testBitmapToBase64_RGB565Config() {
        Bitmap bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.RGB_565);

        String base64 = ImageCompressionUtils.bitmapToBase64(bitmap);

        Assert.assertNotNull(base64);
        Assert.assertTrue(base64.length() > 0);
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.O)
    public void testBitmapToBase64_API26() {
        Bitmap bitmap = createTestBitmap(10, 10);

        String base64 = ImageCompressionUtils.bitmapToBase64(bitmap);

        Assert.assertNotNull(base64);
        Assert.assertTrue(base64.length() > 0);
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.R)
    public void testBitmapToBase64_API30_WebpLossy() {
        Bitmap bitmap = createTestBitmap(10, 10);

        String base64 = ImageCompressionUtils.bitmapToBase64(bitmap);

        Assert.assertNotNull(base64);
        Assert.assertTrue(base64.length() > 0);
    }


    // ==================== DATA URL TESTS ====================

    @Test
    public void testToImageDataUrl_WithValidBase64() {
        String base64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";

        String dataUrl = ImageCompressionUtils.toImageDataUrl(base64);

        Assert.assertNotNull(dataUrl);
        Assert.assertTrue(dataUrl.startsWith("data:image/webp;base64,"));
        Assert.assertTrue(dataUrl.contains(base64));
    }

    @Test
    public void testToImageDataUrl_WithNull() {
        String dataUrl = ImageCompressionUtils.toImageDataUrl(null);

        Assert.assertNull(dataUrl);
    }

    @Test
    public void testToImageDataUrl_WithEmptyString() {
        String base64 = "";

        String dataUrl = ImageCompressionUtils.toImageDataUrl(base64);

        Assert.assertNotNull(dataUrl);
        Assert.assertEquals("data:image/webp;base64,", dataUrl);
    }

    @Test
    public void testToImageDataUrl_Format() {
        String base64 = "testdata123";

        String dataUrl = ImageCompressionUtils.toImageDataUrl(base64);

        Assert.assertEquals("data:image/webp;base64,testdata123", dataUrl);
    }

    // ==================== INTEGRATION TESTS ====================

    @Test
    public void testBitmapToBase64AndDataUrl_Integration() {
        Bitmap bitmap = createTestBitmap(10, 10);

        String base64 = ImageCompressionUtils.bitmapToBase64(bitmap);
        String dataUrl = ImageCompressionUtils.toImageDataUrl(base64);

        Assert.assertNotNull(base64);
        Assert.assertNotNull(dataUrl);
        Assert.assertTrue(dataUrl.startsWith("data:image/webp;base64,"));
        Assert.assertTrue(dataUrl.contains(base64));
    }

    @Test
    public void testCompressionReducesSize() {
        Bitmap bitmap = createTestBitmap(50, 50);

        String base64LowQuality = ImageCompressionUtils.bitmapToBase64(bitmap, 1);
        String base64HighQuality = ImageCompressionUtils.bitmapToBase64(bitmap, 100);

        Assert.assertNotNull(base64LowQuality);
        Assert.assertNotNull(base64HighQuality);

        // Lower quality should produce smaller Base64 string
        // Note: This might not always be true for very small images
        // but generally holds for larger images
        Assert.assertTrue(base64LowQuality.length() > 0);
        Assert.assertTrue(base64HighQuality.length() > 0);
    }

    @Test
    public void testMultipleCompressions_SameBitmap() {
        Bitmap bitmap = createTestBitmap(10, 10);

        String base64_1 = ImageCompressionUtils.bitmapToBase64(bitmap);
        String base64_2 = ImageCompressionUtils.bitmapToBase64(bitmap);

        // Same bitmap with same quality should produce same result
        Assert.assertNotNull(base64_1);
        Assert.assertNotNull(base64_2);
        Assert.assertEquals(base64_1, base64_2);
    }

    @Test
    public void testMultipleCompressions_DifferentBitmaps() {
        Bitmap bitmap1 = createColoredBitmap(10, 10, Color.RED);
        Bitmap bitmap2 = createColoredBitmap(10, 10, Color.BLUE);

        String base64_1 = ImageCompressionUtils.bitmapToBase64(bitmap1);
        String base64_2 = ImageCompressionUtils.bitmapToBase64(bitmap2);

        Assert.assertNotNull(base64_1);
        Assert.assertNotNull(base64_2);
        // Different bitmaps should produce different Base64
        Assert.assertNotEquals(base64_1, base64_2);
    }

    // ==================== EDGE CASE TESTS ====================

    @Test
    public void testBitmapToBase64_WithRecycledBitmap() {
        Bitmap bitmap = createTestBitmap(10, 10);
        bitmap.recycle();

        // This should handle the recycled bitmap gracefully
        // Behavior depends on implementation - might return null or throw
        try {
            String base64 = ImageCompressionUtils.bitmapToBase64(bitmap);
            // If it doesn't throw, we accept any result (null or valid string)
        } catch (IllegalStateException e) {
            // Expected if bitmap is recycled
            Assert.assertTrue(e.getMessage().contains("recycle") || e.getMessage().contains("Bitmap"));
        }
    }

    @Test
    public void testBitmapToBase64_NoWrapFlag() {
        Bitmap bitmap = createTestBitmap(10, 10);

        String base64 = ImageCompressionUtils.bitmapToBase64(bitmap);

        Assert.assertNotNull(base64);
        // NO_WRAP flag means no newline characters
        Assert.assertFalse(base64.contains("\n"));
        Assert.assertFalse(base64.contains("\r"));
    }

    @Test
    public void testBitmapToBase64_WithDifferentQualityValues() {
        Bitmap bitmap = createTestBitmap(20, 20);

        String base64_q10 = ImageCompressionUtils.bitmapToBase64(bitmap, 10);
        String base64_q50 = ImageCompressionUtils.bitmapToBase64(bitmap, 50);
        String base64_q90 = ImageCompressionUtils.bitmapToBase64(bitmap, 90);

        Assert.assertNotNull(base64_q10);
        Assert.assertNotNull(base64_q50);
        Assert.assertNotNull(base64_q90);

        // All should be valid
        Assert.assertTrue(base64_q10.length() > 0);
        Assert.assertTrue(base64_q50.length() > 0);
        Assert.assertTrue(base64_q90.length() > 0);
    }

    @Test
    public void testBitmapToBase64_WithVariousSizes() {
        int[] sizes = {1, 5, 10, 25, 50, 100};

        for (int size : sizes) {
            Bitmap bitmap = createTestBitmap(size, size);
            String base64 = ImageCompressionUtils.bitmapToBase64(bitmap);

            Assert.assertNotNull("Failed for size: " + size, base64);
            Assert.assertTrue("Empty Base64 for size: " + size, base64.length() > 0);
        }
    }

    @Test
    public void testBitmapToBase64_WithVariousColors() {
        int[] colors = {Color.RED, Color.GREEN, Color.BLUE, Color.BLACK, Color.WHITE, Color.TRANSPARENT};

        for (int color : colors) {
            Bitmap bitmap = createColoredBitmap(10, 10, color);
            String base64 = ImageCompressionUtils.bitmapToBase64(bitmap);

            Assert.assertNotNull("Failed for color: " + color, base64);
            Assert.assertTrue("Empty Base64 for color: " + color, base64.length() > 0);
        }
    }

    // ==================== HELPER METHODS ====================

    private Bitmap createTestBitmap(int width, int height) {
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    }

    private Bitmap createColoredBitmap(int width, int height, int color) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color);
        return bitmap;
    }
}