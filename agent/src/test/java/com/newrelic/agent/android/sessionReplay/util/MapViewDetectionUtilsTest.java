/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay.util;

import android.content.Context;
import android.view.View;
import android.widget.TextView;
import android.widget.ImageView;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.*;

/**
 * Unit tests for MapViewDetectionUtils to ensure proper MapView detection
 * and prevention of false positives.
 */
@RunWith(RobolectricTestRunner.class)
public class MapViewDetectionUtilsTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void testIsMapView_withRegularView_shouldReturnFalse() {
        View regularView = new View(context);

        boolean isMapView = MapViewDetectionUtils.isMapView(regularView);

        assertFalse("Regular View should not be detected as MapView", isMapView);
    }

    @Test
    public void testIsMapView_withTextView_shouldReturnFalse() {
        TextView textView = new TextView(context);
        textView.setText("Regular text content");

        boolean isMapView = MapViewDetectionUtils.isMapView(textView);

        assertFalse("TextView should not be detected as MapView", isMapView);
    }

    @Test
    public void testIsMapView_withImageView_shouldReturnFalse() {
        ImageView imageView = new ImageView(context);

        boolean isMapView = MapViewDetectionUtils.isMapView(imageView);

        assertFalse("ImageView should not be detected as MapView", isMapView);
    }

    @Test
    public void testIsMapView_withMapRelatedText_shouldReturnFalse() {
        // These should NOT be detected as MapViews to prevent false positives
        TextView locationLabel = new TextView(context);
        locationLabel.setText("Current Location");

        TextView navigationButton = new TextView(context);
        navigationButton.setText("Navigate");

        TextView routeText = new TextView(context);
        routeText.setText("Route 66");

        assertFalse("Location text should not be detected as MapView",
                   MapViewDetectionUtils.isMapView(locationLabel));
        assertFalse("Navigation text should not be detected as MapView",
                   MapViewDetectionUtils.isMapView(navigationButton));
        assertFalse("Route text should not be detected as MapView",
                   MapViewDetectionUtils.isMapView(routeText));
    }

    @Test
    public void testIsMapView_withMapIcon_shouldReturnFalse() {
        ImageView mapIcon = new ImageView(context);
        mapIcon.setContentDescription("Map icon");

        boolean isMapView = MapViewDetectionUtils.isMapView(mapIcon);

        assertFalse("Map icon should not be detected as MapView", isMapView);
    }

    @Test
    public void testIsMapView_withNullView_shouldReturnFalse() {
        boolean isMapView = MapViewDetectionUtils.isMapView((View) null);

        assertFalse("Null view should return false", isMapView);
    }

    @Test
    public void testIsMapView_performanceTest() {
        // Test that detection doesn't cause performance issues with multiple calls
        View testView = new TextView(context);

        long startTime = System.currentTimeMillis();
        for (int i = 0; i < 100; i++) {
            MapViewDetectionUtils.isMapView(testView);
        }
        long endTime = System.currentTimeMillis();

        long duration = endTime - startTime;
        assertTrue("Detection should be fast (took " + duration + "ms)", duration < 1000);
    }
}