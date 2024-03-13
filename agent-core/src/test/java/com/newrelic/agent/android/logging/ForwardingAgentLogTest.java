/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.  
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.logging;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.times;

import com.newrelic.agent.android.FeatureFlag;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

public class ForwardingAgentLogTest {
    AgentLog delegate;
    RemoteLogger remoteLogger;
    ForwardingAgentLog forwardingLogger;

    @BeforeClass
    public static void beforeClass() throws Exception {
        FeatureFlag.enableFeature(FeatureFlag.LogReporting);
        LogReporting.setLogLevel(LogLevel.DEBUG);
        Assert.assertTrue(LogReporting.isRemoteLoggingEnabled());
    }

    @Before
    public void setUp() throws Exception {
        delegate = Mockito.spy(new ConsoleAgentLog());
        delegate.setLevel(AgentLog.AUDIT);
        AgentLogManager.setAgentLog(delegate);

        forwardingLogger = Mockito.spy(new ForwardingAgentLog(AgentLogManager.getAgentLog()));

        remoteLogger = Mockito.spy(new RemoteLogger());
        LogReporting.setLogger(remoteLogger);
    }

    @Test
    public void audit() {
        final String msg = "Audit message";

        forwardingLogger.audit(msg);
        Mockito.verify(delegate, times(1)).audit(msg);
        Mockito.verify(remoteLogger, times(1)).logAttributes(anyMap());
    }

    @Test
    public void debug() {
        final String msg = "Debug message";

        forwardingLogger.debug(msg);
        Mockito.verify(delegate, times(1)).debug(msg);
        Mockito.verify(remoteLogger, times(1)).logAttributes(anyMap());
    }

    @Test
    public void verbose() {
        final String msg = "Verbose message";

        forwardingLogger.verbose(msg);
        Mockito.verify(delegate, times(1)).verbose(msg);
        Mockito.verify(remoteLogger, times(1)).logAttributes(anyMap());
    }

    @Test
    public void info() {
        final String msg = "Info message";

        forwardingLogger.info(msg);
        Mockito.verify(delegate, times(1)).info(msg);
        Mockito.verify(remoteLogger, times(1)).logAttributes(anyMap());
    }

    @Test
    public void warn() {
        final String msg = "Warn message";

        forwardingLogger.warn(msg);
        Mockito.verify(delegate, times(1)).warn(msg);
        Mockito.verify(remoteLogger, times(1)).logAttributes(anyMap());
    }

    @Test
    public void error() {
        final String msg = "Error message";

        forwardingLogger.error(msg);
        Mockito.verify(delegate, times(1)).error(msg);
        Mockito.verify(remoteLogger, times(1)).logAttributes(anyMap());
    }

    @Test
    public void ErrorWithThrowable() {
        final String msg = "Error with throwable message";
        final Throwable throwable = new IllegalArgumentException(msg);

        forwardingLogger.error(msg, throwable);
        Mockito.verify(delegate, times(1)).error(msg, throwable);
        Mockito.verify(remoteLogger, times(1)).logAttributes(anyMap());
    }

    @Test
    public void asAttributes() {
        final String msg = "Log message";

        forwardingLogger.debug(msg);
        Mockito.verify(forwardingLogger, times(1)).asAttributes(LogLevel.DEBUG, msg);
    }
}