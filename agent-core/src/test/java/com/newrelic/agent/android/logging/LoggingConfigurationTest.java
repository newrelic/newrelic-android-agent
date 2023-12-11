/*
 * Copyright (c) 2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.logging;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.test.mock.Providers;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class LoggingConfigurationTest {

    LoggingConfiguration defaultLoggingConfig;

    @Before
    public void setUp() throws Exception {
        defaultLoggingConfig = new LoggingConfiguration();
    }

    @Test
    public void setConfiguration() {
        LoggingConfiguration loggingConfig = new LoggingConfiguration(true, LogReporting.LogLevel.VERBOSE);
        defaultLoggingConfig.setConfiguration(loggingConfig);
        Assert.assertTrue(loggingConfig.getLoggingEnabled());
        Assert.assertEquals(LogReporting.LogLevel.VERBOSE, loggingConfig.getLogLevel());
    }

    @Test
    public void setConfigurationFromAgentConfig() {
        AgentConfiguration agentConfiguration = Providers.provideAgentConfiguration();
        Assert.assertNotNull(agentConfiguration.getLogReportingConfiguration());
        defaultLoggingConfig.setConfiguration(agentConfiguration);
        Assert.assertFalse(defaultLoggingConfig.getLoggingEnabled());
        Assert.assertEquals(LogReporting.LogLevel.NONE, defaultLoggingConfig.getLogLevel());

        agentConfiguration.getLogReportingConfiguration().setLoggingEnabled(true);
        agentConfiguration.getLogReportingConfiguration().setLogLevel(LogReporting.LogLevel.VERBOSE);
        defaultLoggingConfig.setConfiguration(agentConfiguration);
        Assert.assertTrue(defaultLoggingConfig.getLoggingEnabled());
        Assert.assertEquals(LogReporting.LogLevel.VERBOSE, defaultLoggingConfig.getLogLevel());
    }

    @Test
    public void getLoggingEnabled() {
        Assert.assertFalse(defaultLoggingConfig.getLoggingEnabled());
        Assert.assertEquals(LogReporting.LogLevel.NONE, defaultLoggingConfig.getLogLevel());
        defaultLoggingConfig.setLoggingEnabled(false);
        Assert.assertFalse(defaultLoggingConfig.getLoggingEnabled());
    }

    @Test
    public void setLoggingEnabled() {
        defaultLoggingConfig.setLoggingEnabled(false);
        Assert.assertFalse(defaultLoggingConfig.getLoggingEnabled());
    }

    @Test
    public void getLogLevel() {
        Assert.assertEquals(LogReporting.LogLevel.NONE, defaultLoggingConfig.getLogLevel());
    }

    @Test
    public void setLogLevel() {
        Assert.assertEquals(LogReporting.LogLevel.NONE, defaultLoggingConfig.getLogLevel());
        defaultLoggingConfig.setLogLevel(LogReporting.LogLevel.VERBOSE);
        Assert.assertEquals(LogReporting.LogLevel.VERBOSE, defaultLoggingConfig.getLogLevel());
    }

    @Test
    public void testToString() {
        String loggingConfigAsJson = defaultLoggingConfig.toString();
        Assert.assertTrue(loggingConfigAsJson.matches("^\"LoggingConfiguration\" \\{.*\\}$"));
        Assert.assertTrue(loggingConfigAsJson.matches(".*\"enabled\"=false.*"));
        Assert.assertTrue(loggingConfigAsJson.matches(".*\"level\"=\"NONE\".*"));
    }
}