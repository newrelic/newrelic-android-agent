/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.crash;

import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.AgentImpl;
import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.background.ApplicationStateMonitor;
import com.newrelic.agent.android.test.stub.StubAgentImpl;
import com.newrelic.agent.android.test.stub.StubAnalyticsAttributeStore;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class UncaughtExceptionHandlerTest {
    private CrashReporter crashReporter;
    private UncaughtExceptionHandler uncaughtExceptionHandler;
    private TestCrashStore crashStore;
    private AgentConfiguration agentConfiguration;
    private Crash crash;

    @Before
    public void setUp() throws Exception {
        uncaughtExceptionHandler = spy(new UncaughtExceptionHandler(crashReporter));
        crashStore = spy(new TestCrashStore());
        agentConfiguration = new AgentConfiguration();

        agentConfiguration.setApplicationToken(CrashReporterTests.class.getSimpleName());
        agentConfiguration.setReportCrashes(false);
        agentConfiguration.setCrashStore(crashStore);
        agentConfiguration.setEnableAnalyticsEvents(true);
        agentConfiguration.setAnalyticsAttributeStore(new StubAnalyticsAttributeStore());

        crashReporter = CrashReporter.initialize(agentConfiguration);
        crash = new Crash(new RuntimeException("testStoreExistingCrashes"));

        Thread.setDefaultUncaughtExceptionHandler(new TestExceptionHandler());
        ApplicationStateMonitor.setInstance(new ApplicationStateMonitor());
    }

    @After
    public void tearDown() throws Exception {
        CrashReporter.shutdown();
        UncaughtExceptionHandler.previousExceptionHandler = null;
    }

    @Test
    public void testInstallExceptionHandler() throws Exception {
        Assert.assertNull("Should start with no previous handler", uncaughtExceptionHandler.getPreviousExceptionHandler());

        uncaughtExceptionHandler.installExceptionHandler();
        Assert.assertNotNull("Should save previous handler", uncaughtExceptionHandler.getPreviousExceptionHandler());
        Assert.assertEquals("Should install this handler", Thread.getDefaultUncaughtExceptionHandler(), uncaughtExceptionHandler);
        Assert.assertNotNull("Should save previous handler", uncaughtExceptionHandler.getPreviousExceptionHandler());
    }

    @Test
    public void testUncaughtException() throws Exception {
        Throwable throwable = new RuntimeException("Throwable");

        crashReporter = spy(crashReporter);

        uncaughtExceptionHandler = spy(new UncaughtExceptionHandler(crashReporter));
        crashReporter.setEnabled(false);
        uncaughtExceptionHandler.uncaughtException(Thread.currentThread(), throwable);
        verify(uncaughtExceptionHandler, atLeastOnce()).chainExceptionHandler(
                Mockito.<Thread.UncaughtExceptionHandler>isNull(), any(Thread.class), any(Throwable.class));

        uncaughtExceptionHandler = spy(new UncaughtExceptionHandler(crashReporter));
        crashReporter.setEnabled(true);
        uncaughtExceptionHandler.uncaughtException(Thread.currentThread(), throwable);

        verify(crashReporter, times(1)).storeAndReportCrash(any(Crash.class));
        verify(crashStore, times(1)).store(any(Crash.class));

    }

    @Test
    public void testUncaughtExceptionJIT() throws Exception {
        Throwable throwable = new RuntimeException("Throwable");

        crashReporter = spy(CrashReporter.initialize(agentConfiguration));
        crashReporter.setEnabled(FeatureFlag.featureEnabled(FeatureFlag.CrashReporting));

        CrashReporter.setReportCrashes(true);      // sets JIT crash reporting

        uncaughtExceptionHandler = spy(new UncaughtExceptionHandler(crashReporter));
        uncaughtExceptionHandler.uncaughtException(Thread.currentThread(), throwable);

        verify(crashReporter, times(1)).storeAndReportCrash(any(Crash.class));
        verify(crashStore, times(1)).store(any(Crash.class));
        verify(crashReporter, times(1)).reportCrash(any(Crash.class));
        Assert.assertEquals(1, crashStore.count());
    }

    @Test
    public void testUncaughtExceptionDeferred() throws Exception {
        Throwable throwable = new RuntimeException("Throwable");

        crashReporter = spy(CrashReporter.initialize(agentConfiguration));
        crashReporter.setEnabled(FeatureFlag.featureEnabled(FeatureFlag.CrashReporting));

        CrashReporter.setReportCrashes(false);      // sets crash reporting in nex launch (default)

        uncaughtExceptionHandler = spy(new UncaughtExceptionHandler(crashReporter));
        uncaughtExceptionHandler.uncaughtException(Thread.currentThread(), throwable);

        verify(crashReporter, times(1)).storeAndReportCrash(any(Crash.class));
        verify(crashStore, times(1)).store(any(Crash.class));
        verify(crashReporter, times(0)).reportCrash(any(Crash.class));
        Assert.assertEquals(1, crashStore.count());

    }

    @Test
    public void testUncaughtIAException() throws Exception {
        Throwable throwable = new RuntimeException("Throwable");
        AgentImpl agentImpl = new StubAgentImpl() {
            @Override
            public boolean isInstantApp() {
                return true;
            }
        };

        Agent.setImpl(agentImpl);
        ApplicationStateMonitor asm = spy(new ApplicationStateMonitor());

        ApplicationStateMonitor.setInstance(asm);
        crashReporter = spy(crashReporter);

        uncaughtExceptionHandler = spy(new UncaughtExceptionHandler(crashReporter));
        crashReporter.setEnabled(false);
        uncaughtExceptionHandler.uncaughtException(Thread.currentThread(), throwable);

        verify(asm, atLeastOnce()).uiHidden();
        verify(uncaughtExceptionHandler, atLeastOnce()).chainExceptionHandler(
                Mockito.<Thread.UncaughtExceptionHandler>isNull(), any(Thread.class), any(Throwable.class));

    }

    @Test
    public void testResetExceptionHandler() throws Exception {
        uncaughtExceptionHandler.installExceptionHandler();
        Assert.assertNotNull("Should save previous handler", uncaughtExceptionHandler.getPreviousExceptionHandler());

        uncaughtExceptionHandler.resetExceptionHandler();
        Assert.assertNull("Should restore previous handler", uncaughtExceptionHandler.getPreviousExceptionHandler());
    }

    @Test
    public void testPreExceptionHandler() throws Exception {
        TestExceptionHandler preloadedHandler = new TestExceptionHandler();

        preloadedHandler.installHandler();
        Assert.assertEquals("Should install custom pre-agent handler", Thread.getDefaultUncaughtExceptionHandler(), preloadedHandler);

        uncaughtExceptionHandler.installExceptionHandler();
        Assert.assertEquals("Should install this handler", Thread.getDefaultUncaughtExceptionHandler(), uncaughtExceptionHandler);
        Assert.assertEquals("Should record previous handler", preloadedHandler, uncaughtExceptionHandler.getPreviousExceptionHandler());

        uncaughtExceptionHandler.resetExceptionHandler();
        Assert.assertEquals("Should revert to pre-agent handler", Thread.getDefaultUncaughtExceptionHandler(), preloadedHandler);
    }

    @Test
    public void testPostExceptionHandler() throws Exception {
        TestExceptionHandler postloadedHandler = new TestExceptionHandler();

        uncaughtExceptionHandler.installExceptionHandler();
        Assert.assertEquals("Should install NR handler", Thread.getDefaultUncaughtExceptionHandler(), uncaughtExceptionHandler);

        postloadedHandler.installHandler();
        Assert.assertEquals("Should install custom post-agent handler", Thread.getDefaultUncaughtExceptionHandler(), postloadedHandler);

        uncaughtExceptionHandler.resetExceptionHandler();
        Assert.assertEquals("Should leave custom post-agent handler", Thread.getDefaultUncaughtExceptionHandler(), postloadedHandler);
    }


    public class TestExceptionHandler implements Thread.UncaughtExceptionHandler {
        private Thread.UncaughtExceptionHandler previousExceptionHandler = null;

        public TestExceptionHandler() {
        }

        @Override
        public void uncaughtException(Thread t, Throwable e) {
            if (previousExceptionHandler != null) {
                previousExceptionHandler.uncaughtException(t, e);
            }
        }

        public void installHandler() {
            final Thread.UncaughtExceptionHandler currentExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
            previousExceptionHandler = currentExceptionHandler;
            Thread.setDefaultUncaughtExceptionHandler(this);
        }

        public void restoreHandlerState() {
            if (previousExceptionHandler != null) {
                Thread.setDefaultUncaughtExceptionHandler(previousExceptionHandler);
                previousExceptionHandler = null;
            }
        }
    }

}