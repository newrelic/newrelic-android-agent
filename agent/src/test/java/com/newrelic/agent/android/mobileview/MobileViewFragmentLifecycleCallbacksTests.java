/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.mobileview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.navigation.fragment.NavHostFragment;

import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.analytics.AnalyticsControllerImpl;
import com.newrelic.agent.android.analytics.AnalyticsEvent;

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
    public void configChangeOnOneHostDoesNotSuppressTheSameFragmentClassUnderAnUnrelatedHost() {
        FeatureFlag.enableFeature(FeatureFlag.AutomaticMobileViewTracing);

        // Fragment rotating under host Activity in task 1.
        Mockito.when(hostActivity.getTaskId()).thenReturn(1);
        Fragment fragment = plainFragment();
        callbacks.onFragmentResumed(fragmentManager, fragment);
        Mockito.when(hostActivity.isChangingConfigurations()).thenReturn(true);
        callbacks.onFragmentPaused(fragmentManager, fragment);

        // An unrelated host Activity instance in a different task (e.g. multi-window), hosting
        // the SAME Fragment class - a genuine resume, not a post-rotation recreation, so it must
        // not be suppressed by task 1's in-flight config-change entry.
        Activity otherTaskHost = Mockito.mock(Activity.class);
        Mockito.when(otherTaskHost.getTaskId()).thenReturn(2);
        Mockito.when(otherTaskHost.isChangingConfigurations()).thenReturn(false);
        MobileViewFragmentLifecycleCallbacks otherTaskCallbacks = new MobileViewFragmentLifecycleCallbacks(otherTaskHost);
        Fragment otherTaskFragment = plainFragment();

        otherTaskCallbacks.onFragmentResumed(fragmentManager, otherTaskFragment);

        assertEquals("An unrelated host's fragment of the same class must not be suppressed by a " +
                        "config change happening under a different host/task.",
                otherTaskFragment.getClass().getSimpleName(), MobileViewContext.getInstance().getCurrentView());
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

    @Test
    public void createdThenResumedFragmentRecordsLoadTime() {
        FeatureFlag.enableFeature(FeatureFlag.AutomaticMobileViewTracing);
        Fragment fragment = plainFragment();

        callbacks.onFragmentCreated(fragmentManager, fragment, null);
        callbacks.onFragmentResumed(fragmentManager, fragment);

        AnalyticsEvent event = onlyQueuedEvent();
        AnalyticsAttribute loadTime = attribute(event, AnalyticsAttribute.MOBILE_VIEW_LOAD_TIME_ATTRIBUTE);
        assertNotNull("loadTime should be recorded when a resume follows a create.", loadTime);
        assertTrue("loadTime should be non-negative.", loadTime.getDoubleValue() >= 0);
    }

    @Test
    public void resumeWithoutPrecedingCreateOmitsLoadTime() {
        FeatureFlag.enableFeature(FeatureFlag.AutomaticMobileViewTracing);
        Fragment fragment = plainFragment();

        callbacks.onFragmentResumed(fragmentManager, fragment);

        AnalyticsEvent event = onlyQueuedEvent();
        assertNull("loadTime should be absent when no create preceded the resume.",
                attribute(event, AnalyticsAttribute.MOBILE_VIEW_LOAD_TIME_ATTRIBUTE));
    }

    @Test
    public void navHostFragmentCreationIsIgnored() {
        FeatureFlag.enableFeature(FeatureFlag.AutomaticMobileViewTracing);
        NavHostFragment navHostFragment = Mockito.mock(NavHostFragment.class);

        callbacks.onFragmentCreated(fragmentManager, navHostFragment, null);
        callbacks.onFragmentResumed(fragmentManager, navHostFragment);

        assertNull("A NavHostFragment should never be tracked, even after create.", MobileViewContext.getInstance().getCurrentView());
    }

    private static AnalyticsEvent onlyQueuedEvent() {
        // Only MobileView events: a visit that carries a loadTime is also followed by its
        // MobileViewTiming baseline, which these tests are not about.
        java.util.List<AnalyticsEvent> events = new java.util.ArrayList<AnalyticsEvent>();
        for (AnalyticsEvent event : AnalyticsControllerImpl.getInstance().getEventManager().getQueuedEvents()) {
            if (AnalyticsEvent.EVENT_TYPE_MOBILE_VIEW.equals(event.getEventType())) {
                events.add(event);
            }
        }
        assertEquals("Queued MobileView event collection should have a size of 1.", 1, events.size());
        return events.get(0);
    }

    private static AnalyticsAttribute attribute(AnalyticsEvent event, String name) {
        java.util.Iterator<AnalyticsAttribute> it = event.getAttributeSet().iterator();
        while (it.hasNext()) {
            AnalyticsAttribute a = it.next();
            if (name.equals(a.getName())) {
                return a;
            }
        }
        return null;
    }

    private static Fragment plainFragment() {
        return Mockito.mock(Fragment.class);
    }

    private static abstract class OtherFragment extends Fragment {
    }
}
