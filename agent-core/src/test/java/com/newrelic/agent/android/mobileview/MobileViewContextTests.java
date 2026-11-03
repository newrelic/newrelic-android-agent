/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.mobileview;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.analytics.AnalyticsControllerImpl;
import com.newrelic.agent.android.analytics.AnalyticsEvent;
import com.newrelic.agent.android.analytics.EventListener;
import com.newrelic.agent.android.analytics.TestEventStore;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.logging.ConsoleAgentLog;
import com.newrelic.agent.android.test.stub.StubAgentImpl;
import com.newrelic.agent.android.test.stub.StubAnalyticsAttributeStore;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Collection;
import java.util.Iterator;

@RunWith(JUnit4.class)
public class MobileViewContextTests {
    private AnalyticsControllerImpl controller;

    @Before
    public void setUp() {
        AgentLogManager.setAgentLog(new ConsoleAgentLog());
        AgentLogManager.getAgentLog().setLevel(0);

        AgentConfiguration config = new AgentConfiguration();
        config.setEnableAnalyticsEvents(true);
        config.setEventStore(new TestEventStore());
        config.setAnalyticsAttributeStore(new StubAnalyticsAttributeStore());

        controller = (AnalyticsControllerImpl) AnalyticsControllerImpl.getInstance();
        controller.shutdown();
        controller.initialize(config, new StubAgentImpl());

        MobileViewContext.getInstance().clear();
        MobileViewTiming.getInstance().clear();
        FeatureFlag.enableFeature(FeatureFlag.AutomaticMobileViewTracing);
    }

    @After
    public void tearDown() {
        MobileViewContext.getInstance().clear();
        MobileViewTiming.getInstance().clear();
        FeatureFlag.resetFeatures();
    }

    @Test
    public void onViewAppearedRecordsEventWithNoPreviousViewOnFirstCall() {
        MobileViewContext.getInstance().onViewAppeared(
                new MobileViewAppearance("ViewOne", UiPlatform.ANDROID).viewClass("com.example.ViewOne"));

        AnalyticsEvent event = onlyQueuedEvent();
        Assert.assertEquals("Event should be named for the view.", "ViewOne", event.getName());
        Assert.assertEquals("viewName attribute should match.", "ViewOne", attrValue(event, AnalyticsAttribute.MOBILE_VIEW_NAME_ATTRIBUTE));
        Assert.assertEquals("viewClass attribute should match.", "com.example.ViewOne", attrValue(event, AnalyticsAttribute.MOBILE_VIEW_CLASS_ATTRIBUTE));
        Assert.assertNull("First appeared view should have no previousView attribute.", attrValue(event, AnalyticsAttribute.MOBILE_VIEW_PREVIOUS_VIEW_ATTRIBUTE));
        Assert.assertNull("First appeared view should have no previousViewInstanceId attribute.", attrValue(event, AnalyticsAttribute.MOBILE_VIEW_PREVIOUS_VIEW_INSTANCE_ID_ATTRIBUTE));
        Assert.assertEquals("Current view should be updated.", "ViewOne", MobileViewContext.getInstance().getCurrentView());
        Assert.assertEquals("uiFramework attribute should match.", "Android", attrValue(event, AnalyticsAttribute.MOBILE_VIEW_UI_FRAMEWORK_ATTRIBUTE));
        Assert.assertTrue("appeared attribute should be true.", attribute(event, AnalyticsAttribute.MOBILE_VIEW_APPEARED_ATTRIBUTE).getBooleanValue());
        Assert.assertFalse("restarted should be false on first appearance.", attribute(event, AnalyticsAttribute.MOBILE_VIEW_RESTARTED_ATTRIBUTE).getBooleanValue());

        String viewInstanceId = attrValue(event, AnalyticsAttribute.MOBILE_VIEW_INSTANCE_ID_ATTRIBUTE);
        Assert.assertNotNull("viewInstanceId attribute should be present.", viewInstanceId);
        Assert.assertEquals("Context's current viewInstanceId should match the emitted one.",
                viewInstanceId, MobileViewContext.getInstance().getCurrentViewInstanceId());
    }

    @Test
    public void onViewAppearedRecordsPreviousViewOnSecondCall() {
        MobileViewContext.getInstance().onViewAppeared(new MobileViewAppearance("ViewOne", UiPlatform.ANDROID).viewClass("com.example.ViewOne"));
        String firstViewInstanceId = MobileViewContext.getInstance().getCurrentViewInstanceId();
        controller.getEventManager().empty();

        MobileViewContext.getInstance().onViewAppeared(new MobileViewAppearance("ViewTwo", UiPlatform.ANDROID).viewClass("com.example.ViewTwo"));

        AnalyticsEvent event = onlyQueuedEvent();
        Assert.assertEquals("previousView attribute should reference the prior view.", "ViewOne", attrValue(event, AnalyticsAttribute.MOBILE_VIEW_PREVIOUS_VIEW_ATTRIBUTE));
        Assert.assertEquals("previousViewInstanceId attribute should reference the prior view's instance.",
                firstViewInstanceId, attrValue(event, AnalyticsAttribute.MOBILE_VIEW_PREVIOUS_VIEW_INSTANCE_ID_ATTRIBUTE));
        Assert.assertEquals("Current view should be updated to the new view.", "ViewTwo", MobileViewContext.getInstance().getCurrentView());
        Assert.assertEquals("Previous view should be tracked.", "ViewOne", MobileViewContext.getInstance().getPreviousView());
        Assert.assertEquals("Previous viewInstanceId should be tracked.", firstViewInstanceId, MobileViewContext.getInstance().getPreviousViewInstanceId());
        Assert.assertFalse("restarted should be false for a screen not seen before.", attribute(event, AnalyticsAttribute.MOBILE_VIEW_RESTARTED_ATTRIBUTE).getBooleanValue());
    }

    @Test
    public void onViewAppearedSetsRestartedTrueOnRevisit() {
        MobileViewContext.getInstance().onViewAppeared(new MobileViewAppearance("ViewOne", UiPlatform.ANDROID));
        controller.getEventManager().empty();
        MobileViewContext.getInstance().onViewAppeared(new MobileViewAppearance("ViewTwo", UiPlatform.ANDROID));
        controller.getEventManager().empty();

        MobileViewContext.getInstance().onViewAppeared(new MobileViewAppearance("ViewOne", UiPlatform.ANDROID));

        AnalyticsEvent event = onlyQueuedEvent();
        Assert.assertTrue("restarted should be true on a screen's second appearance.",
                attribute(event, AnalyticsAttribute.MOBILE_VIEW_RESTARTED_ATTRIBUTE).getBooleanValue());
    }

    @Test
    public void onViewDisappearedForCurrentViewRecordsTimeVisible() {
        MobileViewContext.getInstance().onViewAppeared(new MobileViewAppearance("ViewOne", UiPlatform.ANDROID).viewClass("com.example.ViewOne"));
        String viewInstanceId = MobileViewContext.getInstance().getCurrentViewInstanceId();
        controller.getEventManager().empty();

        MobileViewContext.getInstance().onViewDisappeared("ViewOne");

        AnalyticsEvent event = onlyQueuedEvent();
        Assert.assertEquals("Event should be named for the view.", "ViewOne", event.getName());
        Assert.assertNotNull("timeVisible attribute should be present.", attribute(event, AnalyticsAttribute.MOBILE_VIEW_TIME_VISIBLE_ATTRIBUTE));
        Assert.assertFalse("appeared attribute should be false on disappear.", attribute(event, AnalyticsAttribute.MOBILE_VIEW_APPEARED_ATTRIBUTE).getBooleanValue());
        Assert.assertEquals("viewInstanceId attribute should match the appearance's instance id.",
                viewInstanceId, attrValue(event, AnalyticsAttribute.MOBILE_VIEW_INSTANCE_ID_ATTRIBUTE));

        // onViewDisappeared clears the dwell-time timer, but leaves getCurrentView() pointing at
        // the last-known view - a subsequent onViewAppeared still needs it as the referrer.
        Assert.assertEquals("Current view should still reference the last-known view.", "ViewOne", MobileViewContext.getInstance().getCurrentView());
    }

    @Test
    public void onViewDisappearedForNonCurrentViewIsNoop() {
        MobileViewContext.getInstance().onViewAppeared(new MobileViewAppearance("ViewOne", UiPlatform.ANDROID).viewClass("com.example.ViewOne"));
        controller.getEventManager().empty();

        MobileViewContext.getInstance().onViewDisappeared("SomeOtherView");

        Collection<AnalyticsEvent> events = controller.getEventManager().getQueuedEvents();
        Assert.assertEquals("Disappearance of a non-current view should not record an event.", 0, events.size());
        Assert.assertEquals("Current view should be unaffected.", "ViewOne", MobileViewContext.getInstance().getCurrentView());
    }

    @Test
    public void onViewAppearedWithLoadTimeRecordsLoadTimeAttribute() {
        MobileViewContext.getInstance().onViewAppeared(
                new MobileViewAppearance("ViewOne", UiPlatform.ANDROID).viewClass("com.example.ViewOne").loadTimeMs(123L));

        AnalyticsEvent event = onlyQueuedEvent();
        AnalyticsAttribute attribute = attribute(event, AnalyticsAttribute.MOBILE_VIEW_LOAD_TIME_ATTRIBUTE);
        Assert.assertNotNull("loadTime attribute should be present when a load time is supplied.", attribute);
        Assert.assertEquals("loadTime attribute should match the supplied value.", 123.0, attribute.getDoubleValue(), 0.0);
    }

    @Test
    public void onViewAppearedWithoutLoadTimeOmitsLoadTimeAttribute() {
        MobileViewContext.getInstance().onViewAppeared(new MobileViewAppearance("ViewOne", UiPlatform.ANDROID).viewClass("com.example.ViewOne"));

        AnalyticsEvent event = onlyQueuedEvent();
        Assert.assertNull("loadTime attribute should be absent when no load time is supplied.",
                attribute(event, AnalyticsAttribute.MOBILE_VIEW_LOAD_TIME_ATTRIBUTE));
    }

    @Test
    public void onViewAppearedWithLoadTimeOnGenuineReconstructionAfterRestartRecordsLoadTimeAttribute() {
        MobileViewContext.getInstance().onViewAppeared(new MobileViewAppearance("ViewOne", UiPlatform.ANDROID).loadTimeMs(50L));
        controller.getEventManager().empty();
        MobileViewContext.getInstance().onViewAppeared(new MobileViewAppearance("ViewTwo", UiPlatform.ANDROID));
        controller.getEventManager().empty();

        // ViewOne is revisited (restarted=true, since its view name has been seen before), but this
        // appearance carries a loadTimeMs because the producer observed a genuine reconstruction of
        // this instance (e.g. the user backed out and relaunched the Activity). loadTime must be
        // honored here: suppressing it based on the view name having appeared before, rather than on
        // whether this specific instance was actually rebuilt, would permanently kill loadTime for
        // every view name after its first appearance (IDD §5.4 - "genuine first construction" is a
        // per-instance notion, not a per-view-name one).
        MobileViewContext.getInstance().onViewAppeared(new MobileViewAppearance("ViewOne", UiPlatform.ANDROID).loadTimeMs(50L));

        AnalyticsEvent event = onlyQueuedEvent();
        Assert.assertTrue("restarted should be true on a screen's second appearance.",
                attribute(event, AnalyticsAttribute.MOBILE_VIEW_RESTARTED_ATTRIBUTE).getBooleanValue());
        AnalyticsAttribute attribute = attribute(event, AnalyticsAttribute.MOBILE_VIEW_LOAD_TIME_ATTRIBUTE);
        Assert.assertNotNull("loadTime attribute should be present when a producer supplies it, even on a restarted appearance.", attribute);
        Assert.assertEquals("loadTime attribute should match the supplied value.", 50.0, attribute.getDoubleValue(), 0.0);
    }

    @Test
    public void onViewAppearedWithoutLoadTimeOnRestartOmitsLoadTimeAttribute() {
        MobileViewContext.getInstance().onViewAppeared(new MobileViewAppearance("ViewOne", UiPlatform.ANDROID).loadTimeMs(50L));
        controller.getEventManager().empty();
        MobileViewContext.getInstance().onViewAppeared(new MobileViewAppearance("ViewTwo", UiPlatform.ANDROID));
        controller.getEventManager().empty();

        // ViewOne resurfaces without being rebuilt (e.g. brought back to the foreground) - the
        // producer correctly supplies no loadTimeMs, so loadTime must be absent (IDD §5.4).
        MobileViewContext.getInstance().onViewAppeared(new MobileViewAppearance("ViewOne", UiPlatform.ANDROID));

        AnalyticsEvent event = onlyQueuedEvent();
        Assert.assertNull("loadTime attribute should be absent when the producer supplies no load time.",
                attribute(event, AnalyticsAttribute.MOBILE_VIEW_LOAD_TIME_ATTRIBUTE));
    }

    @Test
    public void onViewAppearedReturnsNullWhenEventIsRejectedButKeepsCurrentViewAdvanced() {
        MobileViewContext.getInstance().onViewAppeared(new MobileViewAppearance("ViewOne", UiPlatform.ANDROID));
        controller.getEventManager().empty();

        // Force AnalyticsControllerImpl.recordMobileViewEvent() to return false, simulating a
        // rejected event (e.g. queue overflow) without depending on real queue-size internals.
        controller.getEventManager().setEventListener(new RejectingEventListener());
        try {
            String viewInstanceId = MobileViewContext.getInstance().onViewAppeared(
                    new MobileViewAppearance("ViewTwo", UiPlatform.ANDROID));

            // The caller (e.g. NewRelic.setCurrentView()) must be told this appearance was not
            // actually recorded - previously the boolean result of recordMobileViewEvent was
            // discarded, so viewInstanceId was returned as non-null even though no event ever
            // reached the queue.
            Assert.assertNull("viewInstanceId must be null when the underlying event was rejected.", viewInstanceId);
        } finally {
            controller.getEventManager().setEventListener(null);
        }

        // The referrer chain still advances to ViewTwo despite the rejected event, so a later
        // disappear for ViewTwo (driven by the real lifecycle, independent of whether this
        // specific appear event was recorded) is not left desynced from MobileViewContext.
        Assert.assertEquals("Current view should still advance so later lifecycle calls stay consistent.",
                "ViewTwo", MobileViewContext.getInstance().getCurrentView());
    }

    private static class RejectingEventListener implements EventListener {
        @Override
        public boolean onEventAdded(AnalyticsEvent analyticsEvent) {
            return false;
        }

        @Override
        public boolean onEventOverflow(AnalyticsEvent analyticsEvent) {
            return false;
        }

        @Override
        public boolean onEventEvicted(AnalyticsEvent analyticsEvent) {
            return false;
        }

        @Override
        public void onEventQueueSizeExceeded(int currentQueueSize) {
        }

        @Override
        public void onEventQueueTimeExceeded(int maxBufferTimeInSec) {
        }

        @Override
        public void onEventFlush() {
        }

        @Override
        public void onStart(com.newrelic.agent.android.analytics.EventManager eventManager) {
        }

        @Override
        public void onShutdown() {
        }
    }

    @Test
    public void onViewAppearedWithNullOrEmptyNameIsIgnored() {
        MobileViewContext.getInstance().onViewAppeared(new MobileViewAppearance(null, UiPlatform.ANDROID));
        MobileViewContext.getInstance().onViewAppeared(new MobileViewAppearance("", UiPlatform.ANDROID));

        Collection<AnalyticsEvent> events = controller.getEventManager().getQueuedEvents();
        Assert.assertEquals("Null/empty view name should not record an event.", 0, events.size());
        Assert.assertNull("Current view should remain unset.", MobileViewContext.getInstance().getCurrentView());
    }

    @Test
    public void onViewAppearedWithNullUiPlatformIsIgnoredWithoutMarkingViewAsSeen() {
        MobileViewContext.getInstance().onViewAppeared(new MobileViewAppearance("ViewOne", null));

        Collection<AnalyticsEvent> events = controller.getEventManager().getQueuedEvents();
        Assert.assertEquals("Null uiPlatform should not record an event.", 0, events.size());
        Assert.assertNull("Current view should remain unset.", MobileViewContext.getInstance().getCurrentView());

        controller.getEventManager().empty();
        MobileViewContext.getInstance().onViewAppeared(new MobileViewAppearance("ViewOne", UiPlatform.ANDROID));

        AnalyticsEvent event = onlyQueuedEvent();
        Assert.assertFalse("A rejected null-uiPlatform call must not mark the view as already seen.",
                attribute(event, AnalyticsAttribute.MOBILE_VIEW_RESTARTED_ATTRIBUTE).getBooleanValue());
    }

    @Test
    public void onViewDisappearedWithNullOrEmptyNameIsIgnored() {
        MobileViewContext.getInstance().onViewAppeared(new MobileViewAppearance("ViewOne", UiPlatform.ANDROID));
        controller.getEventManager().empty();

        MobileViewContext.getInstance().onViewDisappeared(null);
        MobileViewContext.getInstance().onViewDisappeared("");

        Collection<AnalyticsEvent> events = controller.getEventManager().getQueuedEvents();
        Assert.assertEquals("Null/empty view name should not record an event.", 0, events.size());
        Assert.assertEquals("Current view should be unaffected.", "ViewOne", MobileViewContext.getInstance().getCurrentView());
    }

    @Test
    public void appearanceWithLoadTimeEmitsBaselineTimingForSameInstance() {
        MobileViewContext.getInstance().onViewAppeared(new MobileViewAppearance("ViewOne", UiPlatform.ANDROID).loadTimeMs(123L));
        String viewInstanceId = MobileViewContext.getInstance().getCurrentViewInstanceId();

        java.util.List<AnalyticsEvent> timings = queuedEvents(AnalyticsEvent.EVENT_TYPE_MOBILE_VIEW_TIMING);
        Assert.assertEquals("Exactly one baseline timing should be sent.", 1, timings.size());
        AnalyticsEvent timing = timings.get(0);
        Assert.assertEquals(MobileViewTiming.TIME_TO_INITIAL_DISPLAY, attrValue(timing, AnalyticsAttribute.MOBILE_VIEW_TIMING_NAME_ATTRIBUTE));
        Assert.assertEquals("timingValue should equal the MobileView loadTime.", 123.0,
                attribute(timing, AnalyticsAttribute.MOBILE_VIEW_TIMING_VALUE_ATTRIBUTE).getDoubleValue(), 0.0);
        Assert.assertEquals("ViewOne", attrValue(timing, AnalyticsAttribute.MOBILE_VIEW_NAME_ATTRIBUTE));
        Assert.assertEquals(viewInstanceId, attrValue(timing, AnalyticsAttribute.MOBILE_VIEW_INSTANCE_ID_ATTRIBUTE));
        Assert.assertNull("First view has no referrer.", attrValue(timing, AnalyticsAttribute.MOBILE_VIEW_PREVIOUS_VIEW_ATTRIBUTE));
    }

    @Test
    public void baselineTimingCarriesThePreviousView() {
        MobileViewContext.getInstance().onViewAppeared(new MobileViewAppearance("ViewOne", UiPlatform.ANDROID));
        MobileViewContext.getInstance().onViewAppeared(new MobileViewAppearance("ViewTwo", UiPlatform.ANDROID).loadTimeMs(40L));

        AnalyticsEvent timing = queuedEvents(AnalyticsEvent.EVENT_TYPE_MOBILE_VIEW_TIMING).get(0);
        Assert.assertEquals("ViewOne", attrValue(timing, AnalyticsAttribute.MOBILE_VIEW_PREVIOUS_VIEW_ATTRIBUTE));
    }

    @Test
    public void appearanceWithoutLoadTimeEmitsNoBaselineTiming() {
        MobileViewContext.getInstance().onViewAppeared(new MobileViewAppearance("ViewOne", UiPlatform.ANDROID));

        Assert.assertTrue(queuedEvents(AnalyticsEvent.EVENT_TYPE_MOBILE_VIEW_TIMING).isEmpty());
    }

    @Test
    public void loadTimeAboveTheLimitIsOmittedAndSendsNoBaseline() {
        MobileViewContext.getInstance().onViewAppeared(
                new MobileViewAppearance("ViewOne", UiPlatform.ANDROID).loadTimeMs((long) MobileViewTiming.MAX_MILLISECONDS + 1));

        Assert.assertNull("An implausible loadTime must not be reported.",
                attribute(onlyQueuedEvent(), AnalyticsAttribute.MOBILE_VIEW_LOAD_TIME_ATTRIBUTE));
        Assert.assertTrue(queuedEvents(AnalyticsEvent.EVENT_TYPE_MOBILE_VIEW_TIMING).isEmpty());
    }

    @Test
    public void loadTimeAtTheLimitStillCountsAndZeroIsSent() {
        MobileViewContext.getInstance().onViewAppeared(
                new MobileViewAppearance("ViewOne", UiPlatform.ANDROID).loadTimeMs((long) MobileViewTiming.MAX_MILLISECONDS));
        MobileViewContext.getInstance().onViewAppeared(new MobileViewAppearance("ViewTwo", UiPlatform.ANDROID).loadTimeMs(0L));

        Assert.assertEquals("A measured 0 ms is a real value, as is the limit itself.", 2,
                queuedEvents(AnalyticsEvent.EVENT_TYPE_MOBILE_VIEW_TIMING).size());
    }

    @Test
    public void baselineIsNotEmittedWhenTheMobileViewWasRejected() {
        // An invalid view name is refused before anything is recorded.
        MobileViewContext.getInstance().onViewAppeared(new MobileViewAppearance("", UiPlatform.ANDROID).loadTimeMs(10L));

        Assert.assertTrue(queuedEvents(AnalyticsEvent.EVENT_TYPE_MOBILE_VIEW_TIMING).isEmpty());
    }

    @Test
    public void baselineDoesNotCountAgainstTheCustomerCap() {
        MobileViewContext.getInstance().onViewAppeared(new MobileViewAppearance("ViewOne", UiPlatform.ANDROID).loadTimeMs(10L));

        for (int i = 0; i < MobileViewTiming.MAX_TIMINGS_PER_VIEW_INSTANCE; i++) {
            Assert.assertTrue("Customer timing " + i + " should be recorded.", MobileViewTiming.getInstance().mark("mark"));
        }
        Assert.assertFalse(MobileViewTiming.getInstance().mark("mark"));
    }

    @Test
    public void markMeasuresFromTheConstructionStartSoItEnclosesTheBaseline() throws Exception {
        MobileViewContext.getInstance().onViewAppeared(new MobileViewAppearance("ViewOne", UiPlatform.ANDROID).loadTimeMs(500L));
        Thread.sleep(20);

        Assert.assertTrue(MobileViewTiming.getInstance().mark(MobileViewTiming.TIME_TO_FULL_DISPLAY));

        java.util.List<AnalyticsEvent> timings = queuedEvents(AnalyticsEvent.EVENT_TYPE_MOBILE_VIEW_TIMING);
        Assert.assertEquals(2, timings.size());
        double baseline = timingValue(timings, MobileViewTiming.TIME_TO_INITIAL_DISPLAY);
        double full = timingValue(timings, MobileViewTiming.TIME_TO_FULL_DISPLAY);
        Assert.assertTrue("timeToFullDisplay (" + full + ") should be at least the baseline (" + baseline + ") plus most of the wait.",
                full >= baseline + 15);
    }

    @Test
    public void markFallsBackToTheAppearTimeWhenNoConstructionStartWasObserved() throws Exception {
        MobileViewContext.getInstance().onViewAppeared(new MobileViewAppearance("ViewOne", UiPlatform.ANDROID));
        Thread.sleep(20);

        Assert.assertTrue(MobileViewTiming.getInstance().mark("mark"));

        double value = timingValue(queuedEvents(AnalyticsEvent.EVENT_TYPE_MOBILE_VIEW_TIMING), "mark");
        Assert.assertTrue("Measured from appearance: " + value, value >= 15 && value < MobileViewTiming.MAX_MILLISECONDS);
    }

    @Test
    public void markFailsWithNoViewAndAfterTheViewDisappears() {
        Assert.assertFalse("No view has ever appeared.", MobileViewTiming.getInstance().mark("mark"));

        MobileViewContext.getInstance().onViewAppeared(new MobileViewAppearance("ViewOne", UiPlatform.ANDROID));
        MobileViewContext.getInstance().onViewDisappeared("ViewOne");

        Assert.assertFalse("The view is gone, so there is no zero point.", MobileViewTiming.getInstance().mark("mark"));
        Assert.assertTrue(queuedEvents(AnalyticsEvent.EVENT_TYPE_MOBILE_VIEW_TIMING).isEmpty());
    }

    @Test
    public void recordWorksWithNoViewAndCarriesNoIdentity() {
        Assert.assertTrue(MobileViewTiming.getInstance().record("prefetch", 250.0));

        AnalyticsEvent timing = queuedEvents(AnalyticsEvent.EVENT_TYPE_MOBILE_VIEW_TIMING).get(0);
        Assert.assertEquals("prefetch", attrValue(timing, AnalyticsAttribute.MOBILE_VIEW_TIMING_NAME_ATTRIBUTE));
        Assert.assertEquals(250.0, attribute(timing, AnalyticsAttribute.MOBILE_VIEW_TIMING_VALUE_ATTRIBUTE).getDoubleValue(), 0.0);
        Assert.assertNull(attribute(timing, AnalyticsAttribute.MOBILE_VIEW_NAME_ATTRIBUTE));
        Assert.assertNull(attribute(timing, AnalyticsAttribute.MOBILE_VIEW_INSTANCE_ID_ATTRIBUTE));
    }

    @Test
    public void recordWithACurrentViewCarriesItsIdentity() {
        MobileViewContext.getInstance().onViewAppeared(new MobileViewAppearance("ViewOne", UiPlatform.ANDROID));
        String viewInstanceId = MobileViewContext.getInstance().getCurrentViewInstanceId();

        Assert.assertTrue(MobileViewTiming.getInstance().record("prefetch", 250.0));

        AnalyticsEvent timing = queuedEvents(AnalyticsEvent.EVENT_TYPE_MOBILE_VIEW_TIMING).get(0);
        Assert.assertEquals("ViewOne", attrValue(timing, AnalyticsAttribute.MOBILE_VIEW_NAME_ATTRIBUTE));
        Assert.assertEquals(viewInstanceId, attrValue(timing, AnalyticsAttribute.MOBILE_VIEW_INSTANCE_ID_ATTRIBUTE));
    }

    @Test
    public void recordAndMarkFailWhenBothMobileViewsFlagsAreOff() {
        MobileViewContext.getInstance().onViewAppeared(new MobileViewAppearance("ViewOne", UiPlatform.ANDROID));
        FeatureFlag.disableFeature(FeatureFlag.AutomaticMobileViewTracing);
        FeatureFlag.disableFeature(FeatureFlag.ManualMobileViewTracing);

        Assert.assertFalse(MobileViewTiming.getInstance().mark("mark"));
        Assert.assertFalse(MobileViewTiming.getInstance().record("prefetch", 1.0));
        Assert.assertTrue(queuedEvents(AnalyticsEvent.EVENT_TYPE_MOBILE_VIEW_TIMING).isEmpty());
    }

    @Test
    public void negativeZeroIsRecordedAsPositiveZero() {
        Assert.assertTrue(MobileViewTiming.getInstance().record("zero", -0.0));

        AnalyticsEvent timing = queuedEvents(AnalyticsEvent.EVENT_TYPE_MOBILE_VIEW_TIMING).get(0);
        double value = attribute(timing, AnalyticsAttribute.MOBILE_VIEW_TIMING_VALUE_ATTRIBUTE).getDoubleValue();
        Assert.assertEquals("Must not serialize as -0.0.", 0L, Double.doubleToLongBits(value));
    }

    @Test
    public void timingRefusedByAnUninitializedControllerDoesNotUseUpTheCap() {
        controller.shutdown();
        for (int i = 0; i < MobileViewTiming.MAX_TIMINGS_PER_VIEW_INSTANCE * 2; i++) {
            Assert.assertFalse(MobileViewTiming.getInstance().record("early", 1.0));
        }

        AgentConfiguration config = new AgentConfiguration();
        config.setEnableAnalyticsEvents(true);
        config.setEventStore(new TestEventStore());
        config.setAnalyticsAttributeStore(new StubAnalyticsAttributeStore());
        controller.initialize(config, new StubAgentImpl());

        for (int i = 0; i < MobileViewTiming.MAX_TIMINGS_PER_VIEW_INSTANCE; i++) {
            Assert.assertTrue("Calls made before the agent started must not have used up the shared bucket (" + i + ").",
                    MobileViewTiming.getInstance().record("later", 1.0));
        }
    }

    @Test
    public void viewNameIsTruncatedAtTheLimitAndAShorterNameIsUntouched() {
        String atLimit = repeat('a', MobileViewContext.MAX_VIEW_NAME_LENGTH);
        MobileViewContext.getInstance().onViewAppeared(new MobileViewAppearance(atLimit, UiPlatform.ANDROID));
        Assert.assertEquals("A name at the limit is untouched.", atLimit, MobileViewContext.getInstance().getCurrentView());
        controller.getEventManager().empty();

        String tooLong = repeat('b', MobileViewContext.MAX_VIEW_NAME_LENGTH + 72);
        MobileViewContext.getInstance().onViewAppeared(new MobileViewAppearance(tooLong, UiPlatform.ANDROID));

        AnalyticsEvent event = onlyQueuedEvent();
        Assert.assertEquals(repeat('b', MobileViewContext.MAX_VIEW_NAME_LENGTH), attrValue(event, AnalyticsAttribute.MOBILE_VIEW_NAME_ATTRIBUTE));
        Assert.assertEquals("The stored current view is the truncated name.", repeat('b', MobileViewContext.MAX_VIEW_NAME_LENGTH),
                MobileViewContext.getInstance().getCurrentView());
        Assert.assertEquals("previousView is the stored name of the prior view.", atLimit,
                attrValue(event, AnalyticsAttribute.MOBILE_VIEW_PREVIOUS_VIEW_ATTRIBUTE));
    }

    @Test
    public void previousViewIsTruncatedWhenItWasAppearedWithALongName() {
        String tooLong = repeat('c', MobileViewContext.MAX_VIEW_NAME_LENGTH + 10);
        MobileViewContext.getInstance().onViewAppeared(new MobileViewAppearance(tooLong, UiPlatform.ANDROID));
        controller.getEventManager().empty();

        MobileViewContext.getInstance().onViewAppeared(new MobileViewAppearance("Next", UiPlatform.ANDROID));

        Assert.assertEquals(repeat('c', MobileViewContext.MAX_VIEW_NAME_LENGTH),
                attrValue(onlyQueuedEvent(), AnalyticsAttribute.MOBILE_VIEW_PREVIOUS_VIEW_ATTRIBUTE));
    }

    @Test
    public void disappearWithTheFullLongNameStillClosesTheVisit() {
        String tooLong = repeat('d', MobileViewContext.MAX_VIEW_NAME_LENGTH + 40);
        MobileViewContext.getInstance().onViewAppeared(new MobileViewAppearance(tooLong, UiPlatform.ANDROID));
        controller.getEventManager().empty();

        // Producers report the untruncated name on disappear as well.
        MobileViewContext.getInstance().onViewDisappeared(tooLong);

        AnalyticsEvent event = onlyQueuedEvent();
        Assert.assertFalse(attribute(event, AnalyticsAttribute.MOBILE_VIEW_APPEARED_ATTRIBUTE).getBooleanValue());
        Assert.assertNotNull("A disappear that fails to match would drop timeVisible.",
                attribute(event, AnalyticsAttribute.MOBILE_VIEW_TIME_VISIBLE_ATTRIBUTE));
        Assert.assertEquals(repeat('d', MobileViewContext.MAX_VIEW_NAME_LENGTH), attrValue(event, AnalyticsAttribute.MOBILE_VIEW_NAME_ATTRIBUTE));
    }

    @Test
    public void truncationDoesNotSplitASurrogatePair() {
        // 127 characters, then an emoji (a surrogate pair) that straddles the limit.
        String name = repeat('e', MobileViewContext.MAX_VIEW_NAME_LENGTH - 1) + "\uD83D\uDE00" + "tail";

        String truncated = MobileViewContext.truncateViewName(name);

        Assert.assertEquals("The half pair is dropped rather than left dangling.", MobileViewContext.MAX_VIEW_NAME_LENGTH - 1, truncated.length());
        Assert.assertFalse(Character.isHighSurrogate(truncated.charAt(truncated.length() - 1)));
        Assert.assertNull(MobileViewContext.truncateViewName(null));
    }

    @Test
    public void timingCarriesTheTruncatedViewName() {
        String tooLong = repeat('f', MobileViewContext.MAX_VIEW_NAME_LENGTH + 5);
        MobileViewContext.getInstance().onViewAppeared(new MobileViewAppearance(tooLong, UiPlatform.ANDROID).loadTimeMs(10L));

        AnalyticsEvent timing = queuedEvents(AnalyticsEvent.EVENT_TYPE_MOBILE_VIEW_TIMING).get(0);
        Assert.assertEquals(repeat('f', MobileViewContext.MAX_VIEW_NAME_LENGTH), attrValue(timing, AnalyticsAttribute.MOBILE_VIEW_NAME_ATTRIBUTE));
    }

    private double timingValue(java.util.List<AnalyticsEvent> timings, String timingName) {
        for (AnalyticsEvent timing : timings) {
            if (timingName.equals(attrValue(timing, AnalyticsAttribute.MOBILE_VIEW_TIMING_NAME_ATTRIBUTE))) {
                return attribute(timing, AnalyticsAttribute.MOBILE_VIEW_TIMING_VALUE_ATTRIBUTE).getDoubleValue();
            }
        }
        Assert.fail("No timing named " + timingName);
        return -1;
    }

    private static String repeat(char c, int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            sb.append(c);
        }
        return sb.toString();
    }

    private AnalyticsEvent onlyQueuedEvent() {
        // Only MobileView events: a visit that carries a loadTime is also followed by its
        // MobileViewTiming baseline, which these tests are not about.
        java.util.List<AnalyticsEvent> events = queuedEvents(AnalyticsEvent.EVENT_TYPE_MOBILE_VIEW);
        Assert.assertEquals("Queued MobileView event collection should have a size of 1.", 1, events.size());
        return events.get(0);
    }

    private java.util.List<AnalyticsEvent> queuedEvents(String eventType) {
        java.util.List<AnalyticsEvent> events = new java.util.ArrayList<AnalyticsEvent>();
        Collection<AnalyticsEvent> queued = controller.getEventManager().getQueuedEvents();
        for (AnalyticsEvent event : queued) {
            if (eventType.equals(event.getEventType())) {
                events.add(event);
            }
        }
        return events;
    }

    private static String attrValue(AnalyticsEvent event, String name) {
        AnalyticsAttribute attribute = attribute(event, name);
        return attribute == null ? null : attribute.getStringValue();
    }

    private static AnalyticsAttribute attribute(AnalyticsEvent event, String name) {
        Iterator<AnalyticsAttribute> it = event.getAttributeSet().iterator();
        while (it.hasNext()) {
            AnalyticsAttribute a = it.next();
            if (name.equals(a.getName())) {
                return a;
            }
        }
        return null;
    }
}
