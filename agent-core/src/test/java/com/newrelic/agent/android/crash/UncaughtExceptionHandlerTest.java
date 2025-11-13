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
import com.newrelic.agent.android.harvest.DataToken;
import com.newrelic.agent.android.test.stub.StubAgentImpl;
import com.newrelic.agent.android.test.stub.StubAnalyticsAttributeStore;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

public class UncaughtExceptionHandlerTest {
    private CrashReporter crashReporter;
    private UncaughtExceptionHandler uncaughtExceptionHandler;
    private TestCrashStore crashStore;
    private AgentConfiguration agentConfiguration;

    @Before
    public void setUp() throws Exception {
        uncaughtExceptionHandler = Mockito.spy(new UncaughtExceptionHandler(crashReporter));
        crashStore = Mockito.spy(new TestCrashStore());
        agentConfiguration = new AgentConfiguration();

        agentConfiguration.setApplicationToken(CrashReporterTests.class.getSimpleName());
        agentConfiguration.setReportCrashes(false);
        agentConfiguration.setCrashStore(crashStore);
        agentConfiguration.setEnableAnalyticsEvents(true);
        agentConfiguration.setAnalyticsAttributeStore(new StubAnalyticsAttributeStore());

        crashReporter = CrashReporter.initialize(agentConfiguration);

        Thread.setDefaultUncaughtExceptionHandler(new TestExceptionHandler());
        ApplicationStateMonitor.setInstance(new ApplicationStateMonitor());
    }

    @After
    public void tearDown() throws Exception {
        CrashReporter.shutdown();
        UncaughtExceptionHandler.previousExceptionHandler = null;
    }

    @Test
    public void testInstallExceptionHandler() {
        Assert.assertNull("Should start with no previous handler", uncaughtExceptionHandler.getPreviousExceptionHandler());

        uncaughtExceptionHandler.installExceptionHandler();
        Assert.assertNotNull("Should save previous handler", uncaughtExceptionHandler.getPreviousExceptionHandler());
        Assert.assertEquals("Should install this handler", Thread.getDefaultUncaughtExceptionHandler(), uncaughtExceptionHandler);
        Assert.assertNotNull("Should save previous handler", uncaughtExceptionHandler.getPreviousExceptionHandler());
    }

    @Test
    public void testUncaughtException() {
        Throwable throwable = new RuntimeException("Throwable");

        crashReporter = Mockito.spy(crashReporter);

        uncaughtExceptionHandler = Mockito.spy(new UncaughtExceptionHandler(crashReporter));
        crashReporter.setEnabled(false);
        uncaughtExceptionHandler.uncaughtException(Thread.currentThread(), throwable);
        Mockito.verify(uncaughtExceptionHandler, Mockito.atLeastOnce()).chainExceptionHandler(
                Mockito.<Thread.UncaughtExceptionHandler>isNull(), ArgumentMatchers.any(Thread.class), ArgumentMatchers.any(Throwable.class));

        uncaughtExceptionHandler = Mockito.spy(new UncaughtExceptionHandler(crashReporter));
        crashReporter.setEnabled(true);
        uncaughtExceptionHandler.uncaughtException(Thread.currentThread(), throwable);

        Mockito.verify(crashReporter, Mockito.times(1)).storeAndReportCrash(ArgumentMatchers.any(Crash.class));
        Mockito.verify(crashStore, Mockito.times(1)).store(ArgumentMatchers.any(Crash.class));

    }

    @Test
    public void testUncaughtExceptionJIT() {
        Throwable throwable = new RuntimeException("Throwable");

        crashReporter = Mockito.spy(CrashReporter.initialize(agentConfiguration));
        crashReporter.setEnabled(FeatureFlag.featureEnabled(FeatureFlag.CrashReporting));

        CrashReporter.setReportCrashes(true);      // sets JIT crash reporting

        uncaughtExceptionHandler = Mockito.spy(new UncaughtExceptionHandler(crashReporter));
        uncaughtExceptionHandler.uncaughtException(Thread.currentThread(), throwable);

        Mockito.verify(crashReporter, Mockito.times(1)).storeAndReportCrash(ArgumentMatchers.any(Crash.class));
        Mockito.verify(crashStore, Mockito.times(1)).store(ArgumentMatchers.any(Crash.class));
        Mockito.verify(crashReporter, Mockito.times(1)).reportCrash(ArgumentMatchers.any(Crash.class));
        Assert.assertEquals(1, crashStore.count());
    }

    @Test
    public void testUncaughtExceptionDeferred() {
        Throwable throwable = new RuntimeException("Throwable");

        crashReporter = Mockito.spy(CrashReporter.initialize(agentConfiguration));
        crashReporter.setEnabled(FeatureFlag.featureEnabled(FeatureFlag.CrashReporting));

        CrashReporter.setReportCrashes(false);      // sets crash reporting in nex launch (default)

        uncaughtExceptionHandler = Mockito.spy(new UncaughtExceptionHandler(crashReporter));
        uncaughtExceptionHandler.uncaughtException(Thread.currentThread(), throwable);

        Mockito.verify(crashReporter, Mockito.times(1)).storeAndReportCrash(ArgumentMatchers.any(Crash.class));
        Mockito.verify(crashStore, Mockito.times(1)).store(ArgumentMatchers.any(Crash.class));
        Mockito.verify(crashReporter, Mockito.times(0)).reportCrash(ArgumentMatchers.any(Crash.class));
        Assert.assertEquals(1, crashStore.count());

    }

    @Test
    public void testUncaughtIAException() {
        Throwable throwable = new RuntimeException("Throwable");
        AgentImpl agentImpl = new StubAgentImpl() {
            @Override
            public boolean isInstantApp() {
                return true;
            }
        };

        Agent.setImpl(agentImpl);
        ApplicationStateMonitor asm = Mockito.spy(new ApplicationStateMonitor());

        ApplicationStateMonitor.setInstance(asm);
        crashReporter = Mockito.spy(crashReporter);

        uncaughtExceptionHandler = Mockito.spy(new UncaughtExceptionHandler(crashReporter));
        crashReporter.setEnabled(false);
        uncaughtExceptionHandler.uncaughtException(Thread.currentThread(), throwable);

        Mockito.verify(asm, Mockito.atLeastOnce()).uiHidden();
        Mockito.verify(uncaughtExceptionHandler, Mockito.atLeastOnce()).chainExceptionHandler(
                Mockito.<Thread.UncaughtExceptionHandler>isNull(), ArgumentMatchers.any(Thread.class), ArgumentMatchers.any(Throwable.class));

    }

    @Test
    public void testResetExceptionHandler() {
        uncaughtExceptionHandler.installExceptionHandler();
        Assert.assertNotNull("Should save previous handler", uncaughtExceptionHandler.getPreviousExceptionHandler());

        uncaughtExceptionHandler.resetExceptionHandler();
        Assert.assertNull("Should restore previous handler", uncaughtExceptionHandler.getPreviousExceptionHandler());
    }

    @Test
    public void testPreExceptionHandler() {
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
    public void testPostExceptionHandler() {
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