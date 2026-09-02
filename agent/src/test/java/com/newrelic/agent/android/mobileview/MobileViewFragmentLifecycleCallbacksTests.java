/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.mobileview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.app.Activity;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.navigation.fragment.NavHostFragment;

import com.newrelic.agent.android.FeatureFlag;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class MobileViewFragmentLifecycleCallbacksTests {
    private Activity hostActivity;
    private FragmentManager fragmentManager;
    private MobileViewFragmentLifecycleCallbacks callbacks;

    @Before
    public void setUp() {
        MobileViewTestSupport.initAnalyticsController();
        MobileViewContext.getInstance().clear();
        FeatureFlag.resetFeatures();
        MobileViewFragmentLifecycleCallbacks.clearChangingConfigurationsForTest();

        hostActivity = Mockito.mock(Activity.class);
        Mockito.when(hostActivity.isChangingConfigurations()).thenReturn(false);
        fragmentManager = Mockito.mock(FragmentManager.class);
        callbacks = new MobileViewFragmentLifecycleCallbacks(hostActivity);
    }

    @After
    public void tearDown() {
        MobileViewContext.getInstance().clear();
        FeatureFlag.resetFeatures();
        MobileViewFragmentLifecycleCallbacks.clearChangingConfigurationsForTest();
        MobileViewTestSupport.shutdownAnalyticsController();
    }

    @Test
    public void resumeThenPauseEmitsAppearAndDisappearWhenEnabled() {
        FeatureFlag.enableFeature(FeatureFlag.AutomaticMobileViewTracing);
        Fragment fragment = plainFragment();

        callbacks.onFragmentResumed(fragmentManager, fragment);
        assertEquals("Fragment should be tracked as current view after resume.",
                fragment.getClass().getSimpleName(), MobileViewContext.getInstance().getCurrentView());

        callbacks.onFragmentPaused(fragmentManager, fragment);
        assertEquals("Current view should still reference the last-known fragment after pause.",
                fragment.getClass().getSimpleName(), MobileViewContext.getInstance().getCurrentView());
    }

    @Test
    public void resumeAndPauseAreNoopsWhenDisabled() {
        FeatureFlag.disableFeature(FeatureFlag.AutomaticMobileViewTracing);
        Fragment fragment = plainFragment();

        callbacks.onFragmentResumed(fragmentManager, fragment);
        callbacks.onFragmentPaused(fragmentManager, fragment);

        assertNull("Disabled flag should prevent any view tracking.", MobileViewContext.getInstance().getCurrentView());
    }

    @Test
    public void navHostFragmentIsAlwaysSkippedRegardlessOfFlagState() {
        FeatureFlag.enableFeature(FeatureFlag.AutomaticMobileViewTracing);
        NavHostFragment navHostFragment = Mockito.mock(NavHostFragment.class);

        callbacks.onFragmentResumed(fragmentManager, navHostFragment);
        assertNull("A NavHostFragment should never be tracked directly.", MobileViewContext.getInstance().getCurrentView());

        callbacks.onFragmentPaused(fragmentManager, navHostFragment);
        assertNull("Pause of a NavHostFragment should remain a no-op.", MobileViewContext.getInstance().getCurrentView());
    }

    @Test
    public void configChangeOnHostActivitySuppressesDisappearAndTheFollowingResume() {
        FeatureFlag.enableFeature(FeatureFlag.AutomaticMobileViewTracing);
        Fragment fragment = plainFragment();

        callbacks.onFragmentResumed(fragmentManager, fragment);
        assertEquals("Fragment should be tracked as current before rotation.",
                fragment.getClass().getSimpleName(), MobileViewContext.getInstance().getCurrentView());

        Mockito.when(hostActivity.isChangingConfigurations()).thenReturn(true);
        callbacks.onFragmentPaused(fragmentManager, fragment);
        assertEquals("Config-change pause should not clear/alter the current view.",
                fragment.getClass().getSimpleName(), MobileViewContext.getInstance().getCurrentView());

        // New MobileViewFragmentLifecycleCallbacks instance, registered on the recreated
        // host Activity's FragmentManager post-rotation - mirrors production wiring where
        // a fresh instance is created per Activity instance.
        Activity recreatedHost = Mockito.mock(Activity.class);
        Mockito.when(recreatedHost.isChangingConfigurations()).thenReturn(false);
        MobileViewFragmentLifecycleCallbacks recreatedCallbacks = new MobileViewFragmentLifecycleCallbacks(recreatedHost);
        Fragment recreatedFragment = plainFragment();

        recreatedCallbacks.onFragmentResumed(fragmentManager, recreatedFragment);
        assertEquals("Resume after a config change should be suppressed, leaving the view unchanged.",
                fragment.getClass().getSimpleName(), MobileViewContext.getInstance().getCurrentView());
    }

    @Test
    public void realBackgroundingAfterConfigChangeSuppressionStillTracksNewFragments() {
        FeatureFlag.enableFeature(FeatureFlag.AutomaticMobileViewTracing);
        Fragment fragment = plainFragment();

        callbacks.onFragmentResumed(fragmentManager, fragment);
        callbacks.onFragmentPaused(fragmentManager, fragment);

        OtherFragment other = Mockito.mock(OtherFragment.class);
        callbacks.onFragmentResumed(fragmentManager, other);
        assertEquals("A different fragment type should be tracked as the new current view.",
                other.getClass().getSimpleName(), MobileViewContext.getInstance().getCurrentView());
    }

    private static Fragment plainFragment() {
        return Mockito.mock(Fragment.class);
    }

    private static abstract class OtherFragment extends Fragment {
    }
}
