/*
 * Copyright (c) 2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.logging;

import static com.newrelic.agent.android.logging.LogReporting.agentLogger;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atMostOnce;
import static org.mockito.Mockito.verify;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.FeatureFlag;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Map;

public class LogReportingTest extends LoggingTests {

    @Before
    public void setUp() throws Exception {
        LogReporting.setLogLevel("InFo");
        agentLogger = Mockito.spy(agentLogger);
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

        LogReporting.initialize(reportsDir, AgentConfiguration.getInstance());
        Assert.assertNotNull("LogReporter not initialized", LogReporter.getInstance());
        Assert.assertFalse("LogReport is disabled by default", LogReporter.getInstance().isEnabled());

        FeatureFlag.enableFeature(FeatureFlag.LogReporting);
        LogReporting.initialize(reportsDir, AgentConfiguration.getInstance());
        Assert.assertFalse("LogReport was not started due to configuration settings", LogReporter.getInstance().isEnabled());

        FeatureFlag.enableFeature(FeatureFlag.LogReporting);
        AgentConfiguration.getInstance().getLogReportingConfiguration().setLoggingEnabled(true);
        LogReporting.initialize(reportsDir, AgentConfiguration.getInstance());
        Assert.assertTrue("LogReport was started due to configuration settings", LogReporter.getInstance().isStarted());

        Assert.assertTrue(LogReporting.getLogger() instanceof RemoteLogger);
    }

    @Test
    public void testInvalidLogMessages() {
        Logger logger = LogReporting.getLogger();

        logger.log(LogLevel.ERROR, null);
        verify(agentLogger, atMostOnce()).log(LogLevel.ERROR, LogReporting.INVALID_MSG);

        logger.logAttributes(null);
        verify(agentLogger, atMostOnce()).logAttributes(anyMap());

        logger.logThrowable(LogLevel.WARN, null, (Throwable) null);
        verify(agentLogger, atMostOnce()).logThrowable(any(LogLevel.class), isNull(), any(IllegalArgumentException.class));

        logger.logAll((Throwable) null, (Map<String, Object>) null);
        verify(agentLogger, atMostOnce()).logAll(any(IllegalArgumentException.class), anyMap());

        // TODO LogReporting.validateLogData(LogReporting.validator, null);
    }
}