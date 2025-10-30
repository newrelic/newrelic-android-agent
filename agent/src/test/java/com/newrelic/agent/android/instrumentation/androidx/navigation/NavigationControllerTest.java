/**
 * Copyright 2024 New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation.androidx.navigation;

import androidx.navigation.NavController;

import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.NewRelic;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.NoSuchElementException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for NavigationController instrumentation.
 */
@RunWith(RobolectricTestRunner.class)
public class NavigationControllerTest {

    @Before
    public void setUp() throws Exception {
        NewRelic.enableFeature(FeatureFlag.Jetpack);
    }

    @Test
    public void testNavigateUpSuccess() {
        NavController navController = mock(NavController.class);
        when(navController.navigateUp()).thenReturn(true);

        boolean result = NavigationController.navigateUp(navController);
        
        assertTrue("navigateUp should return true on success", result);
    }

    @Test
    public void testNavigateUpFailure() {
        NavController navController = mock(NavController.class);
        when(navController.navigateUp()).thenReturn(false);

        boolean result = NavigationController.navigateUp(navController);
        
        assertFalse("navigateUp should return false when navigation fails", result);
    }

    @Test
    public void testNavigateUpWithNoSuchElementException() {
        NavController navController = mock(NavController.class);
        when(navController.navigateUp()).thenThrow(new NoSuchElementException("List is empty."));

        try {
            NavigationController.navigateUp(navController);
            fail("Expected NoSuchElementException to be thrown");
        } catch (NoSuchElementException e) {
            assertEquals("List is empty.", e.getMessage());
        }
    }

    @Test
    public void testNavigateUpWithRuntimeException() {
        NavController navController = mock(NavController.class);
        RuntimeException testException = new RuntimeException("Test exception");
        when(navController.navigateUp()).thenThrow(testException);

        try {
            NavigationController.navigateUp(navController);
            fail("Expected RuntimeException to be thrown");
        } catch (RuntimeException e) {
            assertEquals("Test exception", e.getMessage());
        }
    }
}
