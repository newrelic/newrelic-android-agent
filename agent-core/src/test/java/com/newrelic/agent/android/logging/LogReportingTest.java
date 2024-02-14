/*
 * Copyright (c) 2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.logging;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.FeatureFlag;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class LogReportingTest extends LoggingTests {

    @Before
    public void setUp() throws Exception {
        LogReporting.setLogLevel("InFo");
    }

    @Test
    public void getLogger() {
        Assert.assertNotNull(LogReporting.getLogger());
    }

    @Test
    public void getLogLevel() {
        Assert.assertEquals(LogReporting.getLogLevel(), LogLevel.INFO);
    }

    @Test
    public void setLogLevelAsString() {
        LogReporting.setLogLevel("verBOSE");
        Assert.assertEquals(LogReporting.getLogLevel(), LogLevel.VERBOSE);
    }

    @Test
    public void testSetLogLevelAsInt() {
        LogReporting.setLogLevel(4);
        Assert.assertEquals(4, LogReporting.getLogLevelAsInt());
        Assert.assertEquals(LogReporting.getLogLevel(), LogLevel.VERBOSE);
        Assert.assertFalse(LogReporting.isLevelEnabled(LogLevel.DEBUG));
    }

    @Test
    public void testSetLogLevelAsEnum() {
        LogReporting.setLogLevel(LogLevel.VERBOSE);
        Assert.assertFalse(LogReporting.isLevelEnabled(LogLevel.DEBUG));
    }

    @Test
    public void isLevelEnabled() {
        Assert.assertTrue(LogReporting.isLevelEnabled(LogLevel.WARN));
        LogReporting.setLogLevel(LogLevel.ERROR);
        Assert.assertFalse(LogReporting.isLevelEnabled(LogLevel.WARN));
        LogReporting.setLogLevel(LogLevel.VERBOSE);
        Assert.assertTrue(LogReporting.isLevelEnabled(LogLevel.WARN));
    }

    @Test
    public void testLoggingDisabled() {
        LogReporting.setLogLevel(LogLevel.NONE);
        Assert.assertFalse(LogReporting.isLevelEnabled(LogLevel.ERROR));
        Assert.assertFalse(LogReporting.isLevelEnabled(LogLevel.WARN));
        Assert.assertFalse(LogReporting.isLevelEnabled(LogLevel.INFO));
        Assert.assertFalse(LogReporting.isLevelEnabled(LogLevel.VERBOSE));
        Assert.assertFalse(LogReporting.isLevelEnabled(LogLevel.DEBUG));
    }

    @Test
    public void initializeLogReporting() throws Exception {
        AgentConfiguration.getInstance().getLogReportingConfiguration().setLogLevel(LogLevel.DEBUG);

        FeatureFlag.disableFeature(FeatureFlag.LogReporting);
        LogReporting.initialize(reportsDir, AgentConfiguration.getInstance());
        Assert.assertNotNull("LogReporter not initialized", LogReporter.getInstance());
        Assert.assertFalse("LogReport was enabled despite configuration settings", LogReporter.getInstance().isEnabled());

        FeatureFlag.enableFeature(FeatureFlag.LogReporting);
        AgentConfiguration.getInstance().getLogReportingConfiguration().setLoggingEnabled(false);
        LogReporting.initialize(reportsDir, AgentConfiguration.getInstance());
        Assert.assertFalse("LogReport was started despite configuration settings", LogReporter.getInstance().isEnabled());

        FeatureFlag.enableFeature(FeatureFlag.LogReporting);
        AgentConfiguration.getInstance().getLogReportingConfiguration().setLoggingEnabled(true);
        LogReporting.initialize(reportsDir, AgentConfiguration.getInstance());
        Assert.assertTrue("LogReport not enabled despite configuration settings", LogReporter.getInstance().isEnabled());
        Assert.assertTrue("LogReport not started despite configuration settings", LogReporter.getInstance().isStarted());

        Assert.assertTrue(LogReporting.getLogger() instanceof RemoteLogger);
    }
}