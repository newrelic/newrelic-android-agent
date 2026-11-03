/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.mobileview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class NewRelicViewTimingTests {
    @Before
    public void setUp() {
        MobileViewTestSupport.initAnalyticsController();
        MobileViewContext.getInstance().clear();
        MobileViewTiming.getInstance().clear();
        FeatureFlag.resetFeatures();
        FeatureFlag.disableFeature(FeatureFlag.AutomaticMobileViewTracing);
        FeatureFlag.enableFeature(FeatureFlag.ManualMobileViewTracing);
    }

    @After
    public void tearDown() {
        MobileViewContext.getInstance().clear();
        MobileViewTiming.getInstance().clear();
        FeatureFlag.resetFeatures();
        MobileViewTestSupport.shutdownAnalyticsController();
    }

    @Test
    public void standardNamesMatchTheCrossAgentConstants() {
        assertEquals("timeToFullDisplay", NewRelic.TIME_TO_FULL_DISPLAY);
        assertEquals("timeToInteractive", NewRelic.TIME_TO_INTERACTIVE);
    }

    @Test
    public void recordViewTimingIsRecordedWithOnlyTheManualFlag() {
        assertTrue(NewRelic.recordViewTiming(NewRelic.TIME_TO_FULL_DISPLAY, 900.0));

        AnalyticsEvent timing = onlyTiming();
        assertEquals(NewRelic.TIME_TO_FULL_DISPLAY, attrValue(timing, AnalyticsAttribute.MOBILE_VIEW_TIMING_NAME_ATTRIBUTE));
        assertEquals(900.0, attribute(timing, AnalyticsAttribute.MOBILE_VIEW_TIMING_VALUE_ATTRIBUTE).getDoubleValue(), 0.0);
    }

    @Test
    public void recordViewTimingIsRecordedWithOnlyTheAutomaticFlag() {
        FeatureFlag.disableFeature(FeatureFlag.ManualMobileViewTracing);
        FeatureFlag.enableFeature(FeatureFlag.AutomaticMobileViewTracing);

        assertTrue(NewRelic.recordViewTiming("loadMore", 120.0));
    }

    @Test
    public void bothApisFailWhenBothFlagsAreOff() {
        NewRelic.setCurrentView("ViewOne");
        FeatureFlag.disableFeature(FeatureFlag.ManualMobileViewTracing);
        FeatureFlag.disableFeature(FeatureFlag.AutomaticMobileViewTracing);

        assertFalse(NewRelic.markViewTiming(NewRelic.TIME_TO_FULL_DISPLAY));
        assertFalse(NewRelic.recordViewTiming(NewRelic.TIME_TO_FULL_DISPLAY, 1.0));
        assertTrue(timings().isEmpty());
    }

    @Test
    public void markViewTimingFailsWithNoCurrentViewAndSucceedsOnceOneIsSet() {
        assertFalse("No view is tracked yet.", NewRelic.markViewTiming(NewRelic.TIME_TO_FULL_DISPLAY));
        assertTrue(timings().isEmpty());

        assertTrue(NewRelic.setCurrentView("ViewOne"));
        String viewInstanceId = MobileViewContext.getInstance().getCurrentViewInstanceId();

        assertTrue(NewRelic.markViewTiming(NewRelic.TIME_TO_FULL_DISPLAY));
        AnalyticsEvent timing = onlyTiming();
        assertEquals("ViewOne", attrValue(timing, AnalyticsAttribute.MOBILE_VIEW_NAME_ATTRIBUTE));
        assertEquals(viewInstanceId, attrValue(timing, AnalyticsAttribute.MOBILE_VIEW_INSTANCE_ID_ATTRIBUTE));
    }

    @Test
    public void recordViewTimingWithNoViewCarriesNoIdentity() {
        assertTrue(NewRelic.recordViewTiming("prefetch", 50.0));

        AnalyticsEvent timing = onlyTiming();
        assertNull(attribute(timing, AnalyticsAttribute.MOBILE_VIEW_NAME_ATTRIBUTE));
        assertNull(attribute(timing, AnalyticsAttribute.MOBILE_VIEW_INSTANCE_ID_ATTRIBUTE));
    }

    @Test
    public void reservedBaselineNameIsRejected() {
        NewRelic.setCurrentView("ViewOne");

        assertFalse(NewRelic.markViewTiming("timeToInitialDisplay"));
        assertFalse(NewRelic.recordViewTiming("timeToInitialDisplay", 10.0));
        assertTrue(timings().isEmpty());
    }

    @Test
    public void invalidInputsAreRejectedWithoutThrowing() {
        assertFalse(NewRelic.markViewTiming(null));
        assertFalse(NewRelic.recordViewTiming(null, 1.0));
        assertFalse(NewRelic.recordViewTiming("", 1.0));
        assertFalse(NewRelic.recordViewTiming("t", Double.NaN));
        assertFalse(NewRelic.recordViewTiming("t", -1.0));
        assertFalse(NewRelic.recordViewTiming("t", 600001.0));
        assertTrue(timings().isEmpty());
    }

    @Test
    public void seventeenthTimingForAViewIsRejected() {
        NewRelic.setCurrentView("ViewOne");

        for (int i = 0; i < 16; i++) {
            assertTrue("Timing " + i, NewRelic.recordViewTiming("loadMore", 10.0));
        }
        assertFalse(NewRelic.recordViewTiming("loadMore", 10.0));
        assertEquals(16, timings().size());
    }

    @Test
    public void customRecordCustomEventCannotUseTheReservedEventNames() {
        assertFalse(NewRelic.recordCustomEvent("MobileView", null));
        assertFalse(NewRelic.recordCustomEvent("MobileViewTiming", null));
        assertFalse(NewRelic.recordCustomEvent("MobileViewTiming", "name", null));
        assertTrue(timings().isEmpty());
        assertTrue(NewRelic.recordCustomEvent("MyCustomEvent", null));
    }

    private static List<AnalyticsEvent> timings() {
        List<AnalyticsEvent> timings = new ArrayList<>();
        for (AnalyticsEvent event : AnalyticsControllerImpl.getInstance().getEventManager().getQueuedEvents()) {
            if (AnalyticsEvent.EVENT_TYPE_MOBILE_VIEW_TIMING.equals(event.getEventType())) {
                timings.add(event);
            }
        }
        return timings;
    }

    private static AnalyticsEvent onlyTiming() {
        List<AnalyticsEvent> timings = timings();
        assertEquals("Queued MobileViewTiming events should have a size of 1.", 1, timings.size());
        return timings.get(0);
    }

    private static String attrValue(AnalyticsEvent event, String name) {
        AnalyticsAttribute attribute = attribute(event, name);
        assertNotNull("Missing attribute " + name, attribute);
        return attribute.getStringValue();
    }

    private static AnalyticsAttribute attribute(AnalyticsEvent event, String name) {
        for (AnalyticsAttribute attribute : event.getAttributeSet()) {
            if (name.equals(attribute.getName())) {
                return attribute;
            }
        }
        return null;
    }
}
