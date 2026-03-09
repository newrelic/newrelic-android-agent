/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay;

import android.content.Context;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class SessionReplayFrameTest {

    private Context context;
    private SessionReplayViewThingyInterface mockThingy;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        View view = new View(context);
        ViewDetails viewDetails = new ViewDetails(view);
        mockThingy = new SessionReplayViewThingy(viewDetails);
    }

    // ==================== CONSTRUCTOR TESTS ====================

    @Test
    public void testConstructor_WithValidParameters() {
        long timestamp = System.currentTimeMillis();
        int width = 1080;
        int height = 1920;

        SessionReplayFrame frame = new SessionReplayFrame(mockThingy, timestamp, width, height);

        Assert.assertNotNull(frame);
        Assert.assertEquals(mockThingy, frame.rootThingy);
        Assert.assertEquals(timestamp, frame.timestamp);
        Assert.assertEquals(width, frame.width);
        Assert.assertEquals(height, frame.height);
    }

    @Test
    public void testConstructor_WithNullRootThingy() {
        long timestamp = System.currentTimeMillis();
        int width = 1080;
        int height = 1920;

        SessionReplayFrame frame = new SessionReplayFrame(null, timestamp, width, height);

        Assert.assertNotNull(frame);
        Assert.assertNull(frame.rootThingy);
        Assert.assertEquals(timestamp, frame.timestamp);
        Assert.assertEquals(width, frame.width);
        Assert.assertEquals(height, frame.height);
    }

    @Test
    public void testConstructor_WithZeroTimestamp() {
        SessionReplayFrame frame = new SessionReplayFrame(mockThingy, 0L, 1080, 1920);

        Assert.assertNotNull(frame);
        Assert.assertEquals(0L, frame.timestamp);
    }

    @Test
    public void testConstructor_WithNegativeTimestamp() {
        SessionReplayFrame frame = new SessionReplayFrame(mockThingy, -1000L, 1080, 1920);

        Assert.assertNotNull(frame);
        Assert.assertEquals(-1000L, frame.timestamp);
    }

    @Test
    public void testConstructor_WithZeroDimensions() {
        long timestamp = System.currentTimeMillis();

        SessionReplayFrame frame = new SessionReplayFrame(mockThingy, timestamp, 0, 0);

        Assert.assertNotNull(frame);
        Assert.assertEquals(0, frame.width);
        Assert.assertEquals(0, frame.height);
    }

    @Test
    public void testConstructor_WithNegativeDimensions() {
        long timestamp = System.currentTimeMillis();

        SessionReplayFrame frame = new SessionReplayFrame(mockThingy, timestamp, -100, -200);

        Assert.assertNotNull(frame);
        Assert.assertEquals(-100, frame.width);
        Assert.assertEquals(-200, frame.height);
    }

    @Test
    public void testConstructor_WithMaxValues() {
        SessionReplayFrame frame = new SessionReplayFrame(
            mockThingy,
            Long.MAX_VALUE,
            Integer.MAX_VALUE,
            Integer.MAX_VALUE
        );

        Assert.assertNotNull(frame);
        Assert.assertEquals(Long.MAX_VALUE, frame.timestamp);
        Assert.assertEquals(Integer.MAX_VALUE, frame.width);
        Assert.assertEquals(Integer.MAX_VALUE, frame.height);
    }

    @Test
    public void testConstructor_WithMinValues() {
        SessionReplayFrame frame = new SessionReplayFrame(
            mockThingy,
            Long.MIN_VALUE,
            Integer.MIN_VALUE,
            Integer.MIN_VALUE
        );

        Assert.assertNotNull(frame);
        Assert.assertEquals(Long.MIN_VALUE, frame.timestamp);
        Assert.assertEquals(Integer.MIN_VALUE, frame.width);
        Assert.assertEquals(Integer.MIN_VALUE, frame.height);
    }

    // ==================== FIELD ACCESS TESTS ====================

    @Test
    public void testFields_ArePubliclyAccessible() {
        long timestamp = 1234567890L;
        int width = 1080;
        int height = 1920;

        SessionReplayFrame frame = new SessionReplayFrame(mockThingy, timestamp, width, height);

        // Verify direct field access
        Assert.assertEquals(mockThingy, frame.rootThingy);
        Assert.assertEquals(timestamp, frame.timestamp);
        Assert.assertEquals(width, frame.width);
        Assert.assertEquals(height, frame.height);
    }

    @Test
    public void testFields_AreMutable() {
        long timestamp = 1000L;
        int width = 100;
        int height = 200;

        SessionReplayFrame frame = new SessionReplayFrame(mockThingy, timestamp, width, height);

        // Modify fields
        View newView = new View(context);
        ViewDetails newViewDetails = new ViewDetails(newView);
        SessionReplayViewThingyInterface newThingy = new SessionReplayViewThingy(newViewDetails);

        frame.rootThingy = newThingy;
        frame.timestamp = 2000L;
        frame.width = 300;
        frame.height = 400;

        // Verify modifications
        Assert.assertEquals(newThingy, frame.rootThingy);
        Assert.assertEquals(2000L, frame.timestamp);
        Assert.assertEquals(300, frame.width);
        Assert.assertEquals(400, frame.height);
    }

    @Test
    public void testFields_CanBeSetToNull() {
        SessionReplayFrame frame = new SessionReplayFrame(mockThingy, 1000L, 100, 200);

        // Set rootThingy to null
        frame.rootThingy = null;

        Assert.assertNull(frame.rootThingy);
    }

    // ==================== TYPICAL USE CASE TESTS ====================

    @Test
    public void testFrame_WithTypicalSmartphoneDimensions() {
        long timestamp = System.currentTimeMillis();
        int width = 1080;   // Full HD width
        int height = 1920;  // Full HD height

        SessionReplayFrame frame = new SessionReplayFrame(mockThingy, timestamp, width, height);

        Assert.assertEquals(1080, frame.width);
        Assert.assertEquals(1920, frame.height);
    }

    @Test
    public void testFrame_WithTabletDimensions() {
        long timestamp = System.currentTimeMillis();
        int width = 1536;   // Tablet width
        int height = 2048;  // Tablet height

        SessionReplayFrame frame = new SessionReplayFrame(mockThingy, timestamp, width, height);

        Assert.assertEquals(1536, frame.width);
        Assert.assertEquals(2048, frame.height);
    }

    @Test
    public void testFrame_WithLandscapeDimensions() {
        long timestamp = System.currentTimeMillis();
        int width = 1920;   // Landscape width
        int height = 1080;  // Landscape height

        SessionReplayFrame frame = new SessionReplayFrame(mockThingy, timestamp, width, height);

        Assert.assertEquals(1920, frame.width);
        Assert.assertEquals(1080, frame.height);
    }

    // ==================== TIMESTAMP TESTS ====================

    @Test
    public void testFrame_WithCurrentTimestamp() {
        long beforeTimestamp = System.currentTimeMillis();
        SessionReplayFrame frame = new SessionReplayFrame(mockThingy, beforeTimestamp, 1080, 1920);
        long afterTimestamp = System.currentTimeMillis();

        Assert.assertTrue(frame.timestamp >= beforeTimestamp);
        Assert.assertTrue(frame.timestamp <= afterTimestamp);
    }

    @Test
    public void testFrame_WithPastTimestamp() {
        long pastTimestamp = System.currentTimeMillis() - 10000L; // 10 seconds ago

        SessionReplayFrame frame = new SessionReplayFrame(mockThingy, pastTimestamp, 1080, 1920);

        Assert.assertEquals(pastTimestamp, frame.timestamp);
        Assert.assertTrue(frame.timestamp < System.currentTimeMillis());
    }

    @Test
    public void testFrame_WithFutureTimestamp() {
        long futureTimestamp = System.currentTimeMillis() + 10000L; // 10 seconds from now

        SessionReplayFrame frame = new SessionReplayFrame(mockThingy, futureTimestamp, 1080, 1920);

        Assert.assertEquals(futureTimestamp, frame.timestamp);
        Assert.assertTrue(frame.timestamp > System.currentTimeMillis());
    }

    // ==================== MULTIPLE FRAMES TESTS ====================

    @Test
    public void testMultipleFrames_WithDifferentTimestamps() {
        long timestamp1 = 1000L;
        long timestamp2 = 2000L;
        long timestamp3 = 3000L;

        SessionReplayFrame frame1 = new SessionReplayFrame(mockThingy, timestamp1, 1080, 1920);
        SessionReplayFrame frame2 = new SessionReplayFrame(mockThingy, timestamp2, 1080, 1920);
        SessionReplayFrame frame3 = new SessionReplayFrame(mockThingy, timestamp3, 1080, 1920);

        Assert.assertEquals(timestamp1, frame1.timestamp);
        Assert.assertEquals(timestamp2, frame2.timestamp);
        Assert.assertEquals(timestamp3, frame3.timestamp);

        // Verify frames are independent
        Assert.assertNotSame(frame1, frame2);
        Assert.assertNotSame(frame2, frame3);
    }

    @Test
    public void testMultipleFrames_WithDifferentDimensions() {
        long timestamp = System.currentTimeMillis();

        SessionReplayFrame frame1 = new SessionReplayFrame(mockThingy, timestamp, 1080, 1920);
        SessionReplayFrame frame2 = new SessionReplayFrame(mockThingy, timestamp, 1920, 1080);
        SessionReplayFrame frame3 = new SessionReplayFrame(mockThingy, timestamp, 1536, 2048);

        Assert.assertEquals(1080, frame1.width);
        Assert.assertEquals(1920, frame1.height);
        Assert.assertEquals(1920, frame2.width);
        Assert.assertEquals(1080, frame2.height);
        Assert.assertEquals(1536, frame3.width);
        Assert.assertEquals(2048, frame3.height);
    }

    @Test
    public void testMultipleFrames_WithDifferentThingies() {
        long timestamp = System.currentTimeMillis();

        View view1 = new View(context);
        View view2 = new View(context);
        View view3 = new View(context);

        ViewDetails viewDetails1 = new ViewDetails(view1);
        ViewDetails viewDetails2 = new ViewDetails(view2);
        ViewDetails viewDetails3 = new ViewDetails(view3);

        SessionReplayViewThingyInterface thingy1 = new SessionReplayViewThingy(viewDetails1);
        SessionReplayViewThingyInterface thingy2 = new SessionReplayViewThingy(viewDetails2);
        SessionReplayViewThingyInterface thingy3 = new SessionReplayViewThingy(viewDetails3);

        SessionReplayFrame frame1 = new SessionReplayFrame(thingy1, timestamp, 1080, 1920);
        SessionReplayFrame frame2 = new SessionReplayFrame(thingy2, timestamp, 1080, 1920);
        SessionReplayFrame frame3 = new SessionReplayFrame(thingy3, timestamp, 1080, 1920);

        Assert.assertEquals(thingy1, frame1.rootThingy);
        Assert.assertEquals(thingy2, frame2.rootThingy);
        Assert.assertEquals(thingy3, frame3.rootThingy);

        Assert.assertNotSame(frame1.rootThingy, frame2.rootThingy);
        Assert.assertNotSame(frame2.rootThingy, frame3.rootThingy);
    }

    // ==================== EDGE CASE TESTS ====================

    @Test
    public void testFrame_WithSquareDimensions() {
        long timestamp = System.currentTimeMillis();
        int dimension = 1080;

        SessionReplayFrame frame = new SessionReplayFrame(mockThingy, timestamp, dimension, dimension);

        Assert.assertEquals(dimension, frame.width);
        Assert.assertEquals(dimension, frame.height);
    }

    @Test
    public void testFrame_WithVeryLargeDimensions() {
        long timestamp = System.currentTimeMillis();
        int width = 10000;
        int height = 20000;

        SessionReplayFrame frame = new SessionReplayFrame(mockThingy, timestamp, width, height);

        Assert.assertEquals(10000, frame.width);
        Assert.assertEquals(20000, frame.height);
    }

    @Test
    public void testFrame_WithOnePixelDimensions() {
        long timestamp = System.currentTimeMillis();

        SessionReplayFrame frame = new SessionReplayFrame(mockThingy, timestamp, 1, 1);

        Assert.assertEquals(1, frame.width);
        Assert.assertEquals(1, frame.height);
    }

    @Test
    public void testFrame_WithMismatchedWidthHeight() {
        long timestamp = System.currentTimeMillis();

        SessionReplayFrame frame = new SessionReplayFrame(mockThingy, timestamp, 100, 10000);

        Assert.assertEquals(100, frame.width);
        Assert.assertEquals(10000, frame.height);
        Assert.assertTrue(frame.height > frame.width);
    }

    // ==================== MODIFICATION TESTS ====================

    @Test
    public void testFrame_ModifyTimestamp() {
        SessionReplayFrame frame = new SessionReplayFrame(mockThingy, 1000L, 1080, 1920);

        frame.timestamp = 2000L;

        Assert.assertEquals(2000L, frame.timestamp);
    }

    @Test
    public void testFrame_ModifyDimensions() {
        SessionReplayFrame frame = new SessionReplayFrame(mockThingy, 1000L, 1080, 1920);

        frame.width = 1920;
        frame.height = 1080;

        Assert.assertEquals(1920, frame.width);
        Assert.assertEquals(1080, frame.height);
    }

    @Test
    public void testFrame_ModifyAllFields() {
        SessionReplayFrame frame = new SessionReplayFrame(mockThingy, 1000L, 1080, 1920);

        View newView = new View(context);
        ViewDetails newViewDetails = new ViewDetails(newView);
        SessionReplayViewThingyInterface newThingy = new SessionReplayViewThingy(newViewDetails);

        frame.rootThingy = newThingy;
        frame.timestamp = 2000L;
        frame.width = 1536;
        frame.height = 2048;

        Assert.assertEquals(newThingy, frame.rootThingy);
        Assert.assertEquals(2000L, frame.timestamp);
        Assert.assertEquals(1536, frame.width);
        Assert.assertEquals(2048, frame.height);
    }

    @Test
    public void testFrame_ModifyMultipleTimes() {
        SessionReplayFrame frame = new SessionReplayFrame(mockThingy, 1000L, 1080, 1920);

        // First modification
        frame.timestamp = 2000L;
        Assert.assertEquals(2000L, frame.timestamp);

        // Second modification
        frame.timestamp = 3000L;
        Assert.assertEquals(3000L, frame.timestamp);

        // Third modification
        frame.timestamp = 4000L;
        Assert.assertEquals(4000L, frame.timestamp);
    }

    // ==================== REFERENCE TESTS ====================

    @Test
    public void testFrame_ThingyReference_IsSameObject() {
        SessionReplayFrame frame = new SessionReplayFrame(mockThingy, 1000L, 1080, 1920);

        Assert.assertSame(mockThingy, frame.rootThingy);
    }

    @Test
    public void testFrame_ThingyReference_CanBeSharedAcrossFrames() {
        long timestamp = System.currentTimeMillis();

        SessionReplayFrame frame1 = new SessionReplayFrame(mockThingy, timestamp, 1080, 1920);
        SessionReplayFrame frame2 = new SessionReplayFrame(mockThingy, timestamp + 100, 1080, 1920);

        // Both frames reference the same thingy
        Assert.assertSame(frame1.rootThingy, frame2.rootThingy);
        Assert.assertSame(mockThingy, frame1.rootThingy);
        Assert.assertSame(mockThingy, frame2.rootThingy);
    }

    // ==================== COMPARISON TESTS ====================

    @Test
    public void testFrames_WithSameValues_AreNotEqual() {
        long timestamp = 1000L;
        int width = 1080;
        int height = 1920;

        SessionReplayFrame frame1 = new SessionReplayFrame(mockThingy, timestamp, width, height);
        SessionReplayFrame frame2 = new SessionReplayFrame(mockThingy, timestamp, width, height);

        // Note: SessionReplayFrame doesn't override equals(), so == checks object identity
        Assert.assertNotSame(frame1, frame2);
    }

    @Test
    public void testFrames_SelfReference_IsEqual() {
        SessionReplayFrame frame = new SessionReplayFrame(mockThingy, 1000L, 1080, 1920);

        Assert.assertSame(frame, frame);
    }

    // ==================== DIMENSION CALCULATION TESTS ====================

    @Test
    public void testFrame_AspectRatio_Portrait() {
        long timestamp = System.currentTimeMillis();
        SessionReplayFrame frame = new SessionReplayFrame(mockThingy, timestamp, 1080, 1920);

        float aspectRatio = (float) frame.width / frame.height;

        Assert.assertTrue(aspectRatio < 1.0f); // Portrait
        Assert.assertEquals(0.5625f, aspectRatio, 0.001f); // 9:16 ratio
    }

    @Test
    public void testFrame_AspectRatio_Landscape() {
        long timestamp = System.currentTimeMillis();
        SessionReplayFrame frame = new SessionReplayFrame(mockThingy, timestamp, 1920, 1080);

        float aspectRatio = (float) frame.width / frame.height;

        Assert.assertTrue(aspectRatio > 1.0f); // Landscape
        Assert.assertEquals(1.777f, aspectRatio, 0.001f); // 16:9 ratio
    }

    @Test
    public void testFrame_AspectRatio_Square() {
        long timestamp = System.currentTimeMillis();
        SessionReplayFrame frame = new SessionReplayFrame(mockThingy, timestamp, 1080, 1080);

        float aspectRatio = (float) frame.width / frame.height;

        Assert.assertEquals(1.0f, aspectRatio, 0.001f); // Square
    }
}