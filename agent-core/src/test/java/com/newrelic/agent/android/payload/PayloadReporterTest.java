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

import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class PayloadReporterTest {
    private AgentConfiguration agentConfiguration;
    private PayloadReporter payloadreporter;

    @Before
    public void setUp() throws Exception {
        agentConfiguration = spy(new AgentConfiguration());
        agentConfiguration.setApplicationToken(CrashReporterTests.class.getSimpleName());
        agentConfiguration.setEnableAnalyticsEvents(true);
        agentConfiguration.setReportCrashes(true);
        agentConfiguration.setReportHandledExceptions(true);

        payloadreporter = spy(new PayloadReporter(agentConfiguration) {
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
        verify(payloadreporter, times(1)).start();
    }

    @Test
    public void stop() throws Exception {
        payloadreporter.stop();
        verify(payloadreporter, times(1)).stop();
    }

    @Test
    public void isEnabled() throws Exception {
        payloadreporter.setEnabled(true);
        Assert.assertTrue(payloadreporter.isEnabled());

        payloadreporter.setEnabled(false);
        Assert.assertFalse(payloadreporter.isEnabled());
    }

    @Test
    public void setEnabled() throws Exception {
        payloadreporter.setEnabled(true);
        Assert.assertTrue(payloadreporter.isEnabled());
    }

    @Test
    public void getAgentConfiguration() throws Exception {
        Assert.assertEquals(payloadreporter.getAgentConfiguration(), agentConfiguration);
    }

}