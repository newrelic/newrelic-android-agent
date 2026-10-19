/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.mobileview;

import com.newrelic.agent.android.AgentConfiguration;
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
    }

    @After
    public void tearDown() {
        MobileViewContext.getInstance().clear();
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

    private AnalyticsEvent onlyQueuedEvent() {
        Collection<AnalyticsEvent> events = controller.getEventManager().getQueuedEvents();
        Assert.assertEquals("Queued event collection should have a size of 1.", 1, events.size());
        return events.iterator().next();
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
