/*
 * Copyright (c) 2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.NewRelic;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.logging.Logger;
import com.newrelic.agent.android.logging.RemoteLogger;
import com.newrelic.agent.android.logging.LogLevel;
import com.newrelic.agent.android.logging.LogReporting;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

public class LogInstrumentationTest {

    private AgentLog agentLogger;
    private LogReporting.AgentLogger localLogger;
    private Logger remoteLogger;

    @Before
    public void setUp() throws Exception {
        NewRelic.disableFeature(FeatureFlag.LogReporting);

        // the default logger writes to logcat
        Assert.assertTrue(LogReporting.getLogger() instanceof LogReporting.AgentLogger);
        localLogger = (LogReporting.AgentLogger) Mockito.spy(LogReporting.getLogger());
        remoteLogger = Mockito.spy(new RemoteLogger());
        agentLogger = Mockito.spy(AgentLogManager.getAgentLog());

        AgentLogManager.setAgentLog(agentLogger);
        LogReporting.setLogger(remoteLogger);
        LogReporting.setLogLevel(LogLevel.DEBUG);

        NewRelic.enableFeature(FeatureFlag.LogReporting);

        Mockito.reset(agentLogger);
    }

    @After
    public void tearDown() throws Exception {
        NewRelic.disableFeature(FeatureFlag.LogReporting);
        LogReporting.setLogger(localLogger);    // unwind
    }

    @Test
    public void testLog() throws IOException {
        final String msg = "debug log message";

        Assert.assertNotEquals(0, LogInstrumentation.d("TAG", msg));
        verify(remoteLogger, times(1)).logAttributes(anyMap());
        verify(localLogger, times(0)).logToAgent(any(LogLevel.class), anyString());
        verify(agentLogger, never()).debug(anyString());
    }

    @Test
    public void textLogWithThrowable() {
        final String msg = "debug log message";
        final Throwable throwable = new RuntimeException(msg);

        Assert.assertNotEquals(0, LogInstrumentation.e("TAG", msg, throwable));
        verify(remoteLogger, times(1)).logAll(any(Throwable.class), anyMap());
        verify(localLogger, times(0)).logToAgent(any(LogLevel.class), anyString());
        verify(agentLogger, never()).error(anyString());
    }

    @Test
    public void textLogWithNulThrowable() {
        final String msg = "debug log message";

        Assert.assertNotEquals(0, LogInstrumentation.i("TAG", msg, null));
        verify(remoteLogger, times(1)).logAll(any(), anyMap());
        verify(localLogger, times(0)).logToAgent(any(LogLevel.class), anyString());
        verify(agentLogger, never()).info(anyString());
    }

    @Test
    public void testWithLoggingDisabled() {
        final String msg = "info log message";
        final Throwable throwable = new RuntimeException(msg);

        LogReporting.setLogLevel(LogLevel.NONE);

        Assert.assertEquals(0, LogInstrumentation.i("TAG", msg, throwable));
        verify(remoteLogger, times(1)).logAll(any(Throwable.class), anyMap());
        verify(localLogger, times(0)).logToAgent(any(LogLevel.class), anyString());
        verify(agentLogger, never()).info(anyString());
    }
}