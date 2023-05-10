/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.agentdata;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.analytics.AnalyticsControllerImpl;
import com.newrelic.agent.android.test.stub.StubAgentImpl;
import com.newrelic.agent.android.test.stub.StubAnalyticsAttributeStore;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Set;

public class HexAttributeTest {
    private AnalyticsControllerImpl controller;

    @Before
    public void setUp() throws Exception {
        AgentConfiguration config = new AgentConfiguration();

        config.setEnableAnalyticsEvents(true);
        config.setAnalyticsAttributeStore(new StubAnalyticsAttributeStore());
        controller = AnalyticsControllerImpl.getInstance();

        AnalyticsControllerImpl.shutdown();
        AnalyticsControllerImpl.initialize(config, new StubAgentImpl());
    }

    @After
    public void tearDown() throws Exception {
        controller.getEventManager().empty();
        AnalyticsControllerImpl.shutdown();
    }

    @Test
    public void testSessionWhitelist() throws Exception {
        Set<AnalyticsAttribute> sessionAttributes = AnalyticsControllerImpl.getInstance().getSessionAttributes();

        for (AnalyticsAttribute attr : sessionAttributes) {
            Assert.assertTrue(HexAttribute.HEX_SESSION_ATTR_WHITELIST.contains(attr.getName()));
        }

        for( String attr : HexAttribute.HEX_SESSION_ATTR_WHITELIST) {
            Assert.assertTrue(sessionAttributes.contains(new AnalyticsAttribute(attr, 1)));
        }
    }
}