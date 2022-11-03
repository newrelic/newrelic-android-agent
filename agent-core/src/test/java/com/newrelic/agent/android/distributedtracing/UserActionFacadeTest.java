/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.distributedtracing;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.analytics.AnalyticsControllerImpl;
import com.newrelic.agent.android.analytics.AnalyticsEvent;
import com.newrelic.agent.android.test.mock.Providers;
import com.newrelic.agent.android.test.stub.StubAgentImpl;
import com.newrelic.agent.android.test.stub.StubAnalyticsAttributeStore;

import org.junit.Before;
import org.junit.Test;

import java.util.Collection;
import java.util.Iterator;

import static com.newrelic.agent.android.analytics.AnalyticsAttributeTests.getAttributeByName;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class UserActionFacadeTest {

    private final UserActionFacade userActionFacade = new UserActionFacade();
    private AnalyticsControllerImpl analyticsController;
    private AgentConfiguration config;

    @Before
    public void preTest() {
        FeatureFlag.enableFeature(FeatureFlag.DistributedTracing);
        config = new AgentConfiguration();
        config.setEnableAnalyticsEvents(true);
        config.setAnalyticsAttributeStore(new StubAnalyticsAttributeStore());
        analyticsController = AnalyticsControllerImpl.getInstance();
        AnalyticsControllerImpl.shutdown();
    }

    @Test
    public void recordUserAction() throws Exception {
        AnalyticsControllerImpl.initialize(config, new StubAgentImpl());
        UserActionFacade.setTraceFacade(Providers.provideTraceFacade());
        userActionFacade.recordUserAction(UserActionType.AppBackground);
        Collection<AnalyticsEvent> queuedEvents = analyticsController.getEventManager().getQueuedEvents();
        Iterator<AnalyticsEvent> iterator = queuedEvents.iterator();
        AnalyticsEvent event = iterator.next();
        Collection<AnalyticsAttribute> attributes = event.getAttributeSet();
        assertEquals(queuedEvents.size(), 1);

        assertEquals(AnalyticsEvent.EVENT_TYPE_MOBILE_USER_ACTION, event.getEventType());
        assertNull(event.getName());
        assertNull("nr.guid should be null on payload enabled events",
                DistributedTracingTest.getAttributeByName(attributes, DistributedTracing.NR_GUID_ATTRIBUTE));
        assertNull("nr.traceId should not be null on payload enabled events",
                DistributedTracingTest.getAttributeByName(attributes, DistributedTracing.NR_TRACE_ID_ATTRIBUTE));
    }

    @Test
    public void shouldIncludeActionType() throws Exception {
        AnalyticsControllerImpl.initialize(config, new StubAgentImpl());
        UserActionFacade.setTraceFacade(Providers.provideTraceFacade());
        userActionFacade.recordUserAction(UserActionType.AppBackground);
        Collection<AnalyticsEvent> queuedEvents = analyticsController.getEventManager().getQueuedEvents();
        Iterator<AnalyticsEvent> iterator = queuedEvents.iterator();
        AnalyticsEvent event = iterator.next();
        Collection<AnalyticsAttribute> attributes = event.getAttributeSet();
        assertEquals(queuedEvents.size(), 1);
        assertNotNull("Should contain actionType attribute", getAttributeByName(attributes, DistributedTracing.ACTION_TYPE_ATTRIBUTE));
        assertNull("Should not contain name attribute", getAttributeByName(attributes, AnalyticsAttribute.EVENT_NAME_ATTRIBUTE));
    }
}