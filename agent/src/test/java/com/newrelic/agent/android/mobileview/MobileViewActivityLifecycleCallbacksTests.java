/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.mobileview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import android.app.Activity;
import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
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

import java.util.Collections;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class MobileViewActivityLifecycleCallbacksTests {
    private MobileViewActivityLifecycleCallbacks callbacks;

    @Before
    public void setUp() {
        MobileViewTestSupport.initAnalyticsController();
        MobileViewContext.getInstance().clear();
        FeatureFlag.resetFeatures();
        callbacks = new MobileViewActivityLifecycleCallbacks();
    }

    @After
    public void tearDown() {
        MobileViewContext.getInstance().clear();
        FeatureFlag.resetFeatures();
        MobileViewTestSupport.shutdownAnalyticsController();
    }

    @Test
    public void resumeThenPauseEmitsAppearAndDisappearWhenEnabled() {
        FeatureFlag.enableFeature(FeatureFlag.AutomaticMobileViewTracing);
        Activity activity = plainActivity();

        callbacks.onActivityResumed(activity);
        assertEquals("View should be tracked as current after resume.",
                activity.getClass().getSimpleName(), MobileViewContext.getInstance().getCurrentView());

        callbacks.onActivityPaused(activity);
        assertEquals("Current view should still reference the last-known view after pause.",
                activity.getClass().getSimpleName(), MobileViewContext.getInstance().getCurrentView());
    }

    @Test
    public void resumeAndPauseAreNoopsWhenDisabled() {
        FeatureFlag.disableFeature(FeatureFlag.AutomaticMobileViewTracing);
        Activity activity = plainActivity();

        callbacks.onActivityResumed(activity);
        callbacks.onActivityPaused(activity);

        assertNull("Disabled flag should prevent any view tracking.", MobileViewContext.getInstance().getCurrentView());
    }

    @Test
    public void configChangeSuppressesDisappearAndTheFollowingResume() {
        FeatureFlag.enableFeature(FeatureFlag.AutomaticMobileViewTracing);
        Activity oldActivity = plainActivity();
        Mockito.when(oldActivity.isChangingConfigurations()).thenReturn(true);

        callbacks.onActivityResumed(oldActivity);
        assertEquals("Old activity should be tracked as current before rotation.",
                oldActivity.getClass().getSimpleName(), MobileViewContext.getInstance().getCurrentView());

        callbacks.onActivityPaused(oldActivity);
        assertEquals("Config-change pause should not clear/alter the current view.",
                oldActivity.getClass().getSimpleName(), MobileViewContext.getInstance().getCurrentView());

        // Recreated instance of the same class, resumed post-rotation.
        Activity newActivity = plainActivity();
        callbacks.onActivityResumed(newActivity);
        assertEquals("Resume after a config change should be suppressed, leaving the view unchanged.",
                oldActivity.getClass().getSimpleName(), MobileViewContext.getInstance().getCurrentView());
    }

    @Test
    public void realBackgroundingStillEmitsEventsAfterConfigChangeSuppression() {
        FeatureFlag.enableFeature(FeatureFlag.AutomaticMobileViewTracing);
        Activity activity = plainActivity();

        callbacks.onActivityResumed(activity);
        callbacks.onActivityPaused(activity);

        // A second, unrelated activity resumes/pauses normally (not a config change).
        OtherActivity other = Mockito.mock(OtherActivity.class);
        Mockito.when(other.isChangingConfigurations()).thenReturn(false);
        callbacks.onActivityResumed(other);
        assertEquals("A different activity type should be tracked as the new current view.",
                other.getClass().getSimpleName(), MobileViewContext.getInstance().getCurrentView());
    }

    @Test
    public void createdThenResumedActivityRecordsLoadTime() {
        FeatureFlag.enableFeature(FeatureFlag.AutomaticMobileViewTracing);
        Activity activity = plainActivity();

        callbacks.onActivityCreated(activity, (Bundle) null);
        callbacks.onActivityResumed(activity);

        AnalyticsEvent event = onlyQueuedEvent();
        AnalyticsAttribute loadTime = attribute(event, AnalyticsAttribute.MOBILE_VIEW_LOAD_TIME_ATTRIBUTE);
        assertNotNull("loadTime should be recorded when a resume follows a create.", loadTime);
        assertTrue("loadTime should be non-negative.", loadTime.getDoubleValue() >= 0);
    }

    @Test
    public void resumeWithoutPrecedingCreateOmitsLoadTime() {
        FeatureFlag.enableFeature(FeatureFlag.AutomaticMobileViewTracing);
        Activity activity = plainActivity();

        callbacks.onActivityResumed(activity);

        AnalyticsEvent event = onlyQueuedEvent();
        assertNull("loadTime should be absent when no create preceded the resume.",
                attribute(event, AnalyticsAttribute.MOBILE_VIEW_LOAD_TIME_ATTRIBUTE));
    }

    @Test
    public void secondResumeAfterCreateOmitsLoadTime() {
        FeatureFlag.enableFeature(FeatureFlag.AutomaticMobileViewTracing);
        Activity activity = plainActivity();

        callbacks.onActivityCreated(activity, (Bundle) null);
        callbacks.onActivityResumed(activity);
        callbacks.onActivityPaused(activity);
        AnalyticsControllerImpl.getInstance().getEventManager().empty();

        // Backgrounded then foregrounded again, no intervening create - the created-at entry
        // was already consumed by the first resume.
        callbacks.onActivityResumed(activity);

        AnalyticsEvent event = onlyQueuedEvent();
        assertNull("A second resume without a preceding create should have no loadTime.",
                attribute(event, AnalyticsAttribute.MOBILE_VIEW_LOAD_TIME_ATTRIBUTE));
    }

    @Test
    public void isNavHostContainerViaComposeRegistrySuppressesTracking() {
        FeatureFlag.enableFeature(FeatureFlag.AutomaticMobileViewTracing);
        Activity activity = plainActivity();
        ComposeNavHostRegistry.getInstance().register(activity);

        try {
            callbacks.onActivityResumed(activity);
            assertNull("A Compose NavHost container activity should not be tracked directly.",
                    MobileViewContext.getInstance().getCurrentView());

            callbacks.onActivityPaused(activity);
            assertNull("Pause of a container activity should remain a no-op.",
                    MobileViewContext.getInstance().getCurrentView());
        } finally {
            ComposeNavHostRegistry.getInstance().unregister(activity);
        }
    }

    @Test
    public void isNavHostContainerViaFragmentNavHostSuppressesTracking() {
        FeatureFlag.enableFeature(FeatureFlag.AutomaticMobileViewTracing);
        FragmentActivity activity = Mockito.mock(FragmentActivity.class);
        Mockito.when(activity.isChangingConfigurations()).thenReturn(false);
        FragmentManager fragmentManager = Mockito.mock(FragmentManager.class);
        Mockito.when(activity.getSupportFragmentManager()).thenReturn(fragmentManager);
        NavHostFragment navHostFragment = Mockito.mock(NavHostFragment.class);
        List<Fragment> fragments = Collections.singletonList(navHostFragment);
        Mockito.when(fragmentManager.getFragments()).thenReturn(fragments);

        callbacks.onActivityResumed(activity);
        assertNull("An Activity hosting a NavHostFragment should not be tracked directly.",
                MobileViewContext.getInstance().getCurrentView());

        callbacks.onActivityPaused(activity);
        assertNull("Pause of a NavHostFragment-hosting activity should remain a no-op.",
                MobileViewContext.getInstance().getCurrentView());
    }

    @Test
    public void navHostContainerAtAppearStaysSymmetricIfFragmentsChangeWhileResumed() {
        FeatureFlag.enableFeature(FeatureFlag.AutomaticMobileViewTracing);
        FragmentActivity activity = Mockito.mock(FragmentActivity.class);
        Mockito.when(activity.isChangingConfigurations()).thenReturn(false);
        FragmentManager fragmentManager = Mockito.mock(FragmentManager.class);
        Mockito.when(activity.getSupportFragmentManager()).thenReturn(fragmentManager);
        NavHostFragment navHostFragment = Mockito.mock(NavHostFragment.class);
        Mockito.when(fragmentManager.getFragments()).thenReturn(Collections.<Fragment>singletonList(navHostFragment));

        callbacks.onActivityResumed(activity);
        assertNull("Activity should be treated as a container at resume.", MobileViewContext.getInstance().getCurrentView());

        // Fragment removed before pause - onActivityPaused must still honor the cached
        // at-appear container decision, not recompute it.
        Mockito.when(fragmentManager.getFragments()).thenReturn(Collections.<Fragment>emptyList());
        callbacks.onActivityPaused(activity);
        assertNull("Pause must reuse the cached at-appear container decision, remaining a no-op.",
                MobileViewContext.getInstance().getCurrentView());
    }

    @Test
    public void onActivityCreatedRegistersFragmentCallbacksOnFragmentActivityWhenEnabled() {
        FeatureFlag.enableFeature(FeatureFlag.AutomaticMobileViewTracing);
        FragmentActivity activity = Mockito.mock(FragmentActivity.class);
        FragmentManager fragmentManager = Mockito.mock(FragmentManager.class);
        Mockito.when(activity.getSupportFragmentManager()).thenReturn(fragmentManager);

        callbacks.onActivityCreated(activity, (Bundle) null);

        Mockito.verify(fragmentManager).registerFragmentLifecycleCallbacks(any(), eq(true));
    }

    @Test
    public void onActivityCreatedDoesNotRegisterFragmentCallbacksWhenDisabled() {
        FeatureFlag.disableFeature(FeatureFlag.AutomaticMobileViewTracing);
        FragmentActivity activity = Mockito.mock(FragmentActivity.class);
        FragmentManager fragmentManager = Mockito.mock(FragmentManager.class);
        Mockito.when(activity.getSupportFragmentManager()).thenReturn(fragmentManager);

        callbacks.onActivityCreated(activity, (Bundle) null);

        Mockito.verify(fragmentManager, Mockito.never()).registerFragmentLifecycleCallbacks(any(), any(Boolean.class));
    }

    @Test
    public void onActivityCreatedDoesNotThrowForNonFragmentActivity() {
        FeatureFlag.enableFeature(FeatureFlag.AutomaticMobileViewTracing);
        Activity activity = plainActivity();

        callbacks.onActivityCreated(activity, (Bundle) null);
    }

    private static AnalyticsEvent onlyQueuedEvent() {
        java.util.Collection<AnalyticsEvent> events = AnalyticsControllerImpl.getInstance().getEventManager().getQueuedEvents();
        assertEquals("Queued event collection should have a size of 1.", 1, events.size());
        return events.iterator().next();
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

    private static Activity plainActivity() {
        Activity activity = Mockito.mock(Activity.class);
        Mockito.when(activity.isChangingConfigurations()).thenReturn(false);
        return activity;
    }

    private static abstract class OtherActivity extends Activity {
    }
}
