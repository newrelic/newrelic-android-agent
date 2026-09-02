/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.mobileview;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.analytics.AnalyticsControllerImpl;
import com.newrelic.agent.android.analytics.AnalyticsEvent;
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
        MobileViewContext.getInstance().onViewAppeared("ViewOne", "com.example.ViewOne", null);

        AnalyticsEvent event = onlyQueuedEvent();
        Assert.assertEquals("Event should be named for the view.", "ViewOne", event.getName());
        Assert.assertEquals("viewName attribute should match.", "ViewOne", attrValue(event, AnalyticsAttribute.MOBILE_VIEW_NAME_ATTRIBUTE));
        Assert.assertEquals("viewClass attribute should match.", "com.example.ViewOne", attrValue(event, AnalyticsAttribute.MOBILE_VIEW_CLASS_ATTRIBUTE));
        Assert.assertNull("First appeared view should have no previousView attribute.", attrValue(event, AnalyticsAttribute.MOBILE_VIEW_PREVIOUS_VIEW_ATTRIBUTE));
        Assert.assertEquals("Current view should be updated.", "ViewOne", MobileViewContext.getInstance().getCurrentView());
    }

    @Test
    public void onViewAppearedRecordsPreviousViewOnSecondCall() {
        MobileViewContext.getInstance().onViewAppeared("ViewOne", "com.example.ViewOne", null);
        controller.getEventManager().empty();

        MobileViewContext.getInstance().onViewAppeared("ViewTwo", "com.example.ViewTwo", null);

        AnalyticsEvent event = onlyQueuedEvent();
        Assert.assertEquals("previousView attribute should reference the prior view.", "ViewOne", attrValue(event, AnalyticsAttribute.MOBILE_VIEW_PREVIOUS_VIEW_ATTRIBUTE));
        Assert.assertEquals("Current view should be updated to the new view.", "ViewTwo", MobileViewContext.getInstance().getCurrentView());
        Assert.assertEquals("Previous view should be tracked.", "ViewOne", MobileViewContext.getInstance().getPreviousView());
    }

    @Test
    public void onViewDisappearedForCurrentViewRecordsTimeVisible() {
        MobileViewContext.getInstance().onViewAppeared("ViewOne", "com.example.ViewOne", null);
        controller.getEventManager().empty();

        MobileViewContext.getInstance().onViewDisappeared("ViewOne");

        AnalyticsEvent event = onlyQueuedEvent();
        Assert.assertEquals("Event should be named for the view.", "ViewOne", event.getName());
        Assert.assertNotNull("timeVisible attribute should be present.", attribute(event, AnalyticsAttribute.MOBILE_VIEW_TIME_VISIBLE_ATTRIBUTE));

        // onViewDisappeared clears the dwell-time timer, but leaves getCurrentView() pointing at
        // the last-known view - a subsequent onViewAppeared still needs it as the referrer.
        Assert.assertEquals("Current view should still reference the last-known view.", "ViewOne", MobileViewContext.getInstance().getCurrentView());
    }

    @Test
    public void onViewDisappearedForNonCurrentViewIsNoop() {
        MobileViewContext.getInstance().onViewAppeared("ViewOne", "com.example.ViewOne", null);
        controller.getEventManager().empty();

        MobileViewContext.getInstance().onViewDisappeared("SomeOtherView");

        Collection<AnalyticsEvent> events = controller.getEventManager().getQueuedEvents();
        Assert.assertEquals("Disappearance of a non-current view should not record an event.", 0, events.size());
        Assert.assertEquals("Current view should be unaffected.", "ViewOne", MobileViewContext.getInstance().getCurrentView());
    }

    @Test
    public void onViewAppearedWithNullOrEmptyNameIsIgnored() {
        MobileViewContext.getInstance().onViewAppeared(null, "com.example.ViewOne", null);
        MobileViewContext.getInstance().onViewAppeared("", "com.example.ViewOne", null);

        Collection<AnalyticsEvent> events = controller.getEventManager().getQueuedEvents();
        Assert.assertEquals("Null/empty view name should not record an event.", 0, events.size());
        Assert.assertNull("Current view should remain unset.", MobileViewContext.getInstance().getCurrentView());
    }

    @Test
    public void onViewDisappearedWithNullOrEmptyNameIsIgnored() {
        MobileViewContext.getInstance().onViewAppeared("ViewOne", "com.example.ViewOne", null);
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
