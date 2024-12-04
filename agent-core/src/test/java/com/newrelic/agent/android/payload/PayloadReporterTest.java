/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.payload;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.crash.CrashReporterTests;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class PayloadReporterTest {
    private AgentConfiguration agentConfiguration;
    private PayloadReporter payloadreporter;

    @Before
    public void setUp() throws Exception {
        agentConfiguration = Mockito.spy(new AgentConfiguration());
        agentConfiguration.setApplicationToken(CrashReporterTests.class.getSimpleName());
        agentConfiguration.setEnableAnalyticsEvents(true);
        agentConfiguration.setReportCrashes(true);
        agentConfiguration.setReportHandledExceptions(true);

        payloadreporter = Mockito.spy(new PayloadReporter(agentConfiguration) {
            @Override
            protected void start() {
            }

            @Override
            protected void stop() {
            }
        });
    }

    @Test
    public void start() throws Exception {
        payloadreporter.start();
        Mockito.verify(payloadreporter, Mockito.times(1)).start();
    }

    @Test
    public void stop() throws Exception {
        payloadreporter.stop();
        Mockito.verify(payloadreporter, Mockito.times(1)).stop();
    }

    @Test
    public void isEnabled() {
        payloadreporter.setEnabled(true);
        Assert.assertTrue(payloadreporter.isEnabled());

        payloadreporter.setEnabled(false);
        Assert.assertFalse(payloadreporter.isEnabled());
    }

    @Test
    public void setEnabled() {
        payloadreporter.setEnabled(true);
        Assert.assertTrue(payloadreporter.isEnabled());
    }

    @Test
    public void getAgentConfiguration() {
        Assert.assertEquals(payloadreporter.getAgentConfiguration(), agentConfiguration);
    }

}