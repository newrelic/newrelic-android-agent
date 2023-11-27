/*
 * Copyright (c) 2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.logging;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class LogReportingTest {

    LogReporting logReporting;

    @Before
    public void setUp() throws Exception {
        LogReporting.setLogLevel("Warn");
    }

    @Test
    public void getLogger() {
        Assert.assertNotNull(LogReporting.getLogger());
    }

    @Test
    public void getLogLevel() {
        Assert.assertEquals(LogReporting.getLogLevel(), LogReporting.LogLevel.WARN);
    }

    @Test
    public void setLogLevelAsString() {
        LogReporting.setLogLevel("Warn");
    }

    @Test
    public void testSetLogLevelAsInt() {
        LogReporting.setLogLevel(LogReporting.LogLevel.DEBUG.ordinal());
    }

    @Test
    public void testSetLogLevelAsEnum() {
        LogReporting.setLogLevel(LogReporting.LogLevel.DEBUG);
    }

    @Test
    public void isLevelEnabled() {
        Assert.assertTrue(LogReporting.isLevelEnabled(LogReporting.LogLevel.WARN));
        LogReporting.setLogLevel(LogReporting.LogLevel.ERROR);
        Assert.assertFalse(LogReporting.isLevelEnabled(LogReporting.LogLevel.WARN));
        LogReporting.setLogLevel(LogReporting.LogLevel.VERBOSE);
        Assert.assertTrue(LogReporting.isLevelEnabled(LogReporting.LogLevel.WARN));
    }

    @Test
    public void testLoggingDisabled() {
        LogReporting.setLogLevel(LogReporting.LogLevel.NONE);
        Assert.assertFalse(LogReporting.isLevelEnabled(LogReporting.LogLevel.ERROR));
        Assert.assertFalse(LogReporting.isLevelEnabled(LogReporting.LogLevel.WARN));
        Assert.assertFalse(LogReporting.isLevelEnabled(LogReporting.LogLevel.INFO);
        Assert.assertFalse(LogReporting.isLevelEnabled(LogReporting.LogLevel.DEBUG));
        Assert.assertFalse(LogReporting.isLevelEnabled(LogReporting.LogLevel.VERBOSE));
    }

    @Test
    public void isErrorEnabled() {
    }

    @Test
    public void isWarnEnabled() {
    }

    @Test
    public void isInfoEnabled() {
    }

    @Test
    public void isDebugEnabled() {
    }

    @Test
    public void notice() {
    }

    @Test
    public void noticeWithLevel() {
    }
}