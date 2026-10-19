/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.mobileview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.NewRelic;
import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.analytics.AnalyticsControllerImpl;
import com.newrelic.agent.android.analytics.AnalyticsEvent;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Collection;
import java.util.Iterator;

@RunWith(RobolectricTestRunner.class)
public class NewRelicSetCurrentViewTests {
    @Before
    public void setUp() {
        MobileViewTestSupport.initAnalyticsController();
        MobileViewContext.getInstance().clear();
        FeatureFlag.resetFeatures();
        FeatureFlag.enableFeature(FeatureFlag.ManualMobileViewTracing);
    }

    @After
    public void tearDown() {
        MobileViewContext.getInstance().clear();
        FeatureFlag.resetFeatures();
        MobileViewTestSupport.shutdownAnalyticsController();
    }

    @Test
    public void setCurrentViewWithoutPlatformDefaultsToAndroid() {
        NewRelic.setCurrentView("ReactNativeScreen", null);

        AnalyticsEvent event = onlyQueuedEvent();
        assertEquals("uiFramework should default to Android when no platform is specified.",
                UiPlatform.ANDROID.getWireValue(), attrValue(event, AnalyticsAttribute.MOBILE_VIEW_UI_FRAMEWORK_ATTRIBUTE));
    }

    @Test
    public void setCurrentViewWithExplicitPlatformHonorsIt() {
        // A manual caller (e.g. a React Native/Flutter bridge) reporting a non-Android view
        // must be able to say so, rather than every manual event being hardcoded to Android
        // regardless of the actual rendering platform.
        boolean recorded = NewRelic.setCurrentView("ComposeScreen", null, UiPlatform.COMPOSE);

        assertTrue("setCurrentView should report success when the event is recorded.", recorded);
        AnalyticsEvent event = onlyQueuedEvent();
        assertEquals("uiFramework should match the explicitly supplied platform.",
                UiPlatform.COMPOSE.getWireValue(), attrValue(event, AnalyticsAttribute.MOBILE_VIEW_UI_FRAMEWORK_ATTRIBUTE));
    }

    @Test
    public void setCurrentViewWithNullPlatformFailsCleanly() {
        boolean recorded = NewRelic.setCurrentView("ViewOne", null, null);

        assertEquals("A null uiPlatform should be rejected rather than silently defaulted.", false, recorded);
        Collection<AnalyticsEvent> events = AnalyticsControllerImpl.getInstance().getEventManager().getQueuedEvents();
        assertEquals("No event should be recorded for a null uiPlatform.", 0, events.size());
    }

    private static AnalyticsEvent onlyQueuedEvent() {
        Collection<AnalyticsEvent> events = AnalyticsControllerImpl.getInstance().getEventManager().getQueuedEvents();
        assertEquals("Queued event collection should have a size of 1.", 1, events.size());
        return events.iterator().next();
    }

    private static String attrValue(AnalyticsEvent event, String name) {
        Iterator<AnalyticsAttribute> it = event.getAttributeSet().iterator();
        while (it.hasNext()) {
            AnalyticsAttribute a = it.next();
            if (name.equals(a.getName())) {
                return a.getStringValue();
            }
        }
        return null;
    }
}
