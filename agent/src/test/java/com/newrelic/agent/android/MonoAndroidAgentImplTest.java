/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.newrelic.agent.android.background.ApplicationStateMonitor;
import com.newrelic.agent.android.harvest.ApplicationInformation;
import com.newrelic.agent.android.harvest.ConnectInformation;
import com.newrelic.agent.android.harvest.DeviceInformation;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.logging.ConsoleAgentLog;
import com.newrelic.agent.android.test.mock.Providers;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class MonoAndroidAgentImplTest {

    private final static String ENABLED_APP_TOKEN_PROD = "AA9a2d52a0ed09d8ca54e6317d9c92074f2e9b307b";

    private SpyContext spyContext;
    private AndroidAgentImpl agentImpl;
    private AgentConfiguration agentConfig;

    @BeforeClass
    public static void classSetUp() throws Exception {
        AgentLogManager.setAgentLog(new ConsoleAgentLog());
    }

    @Before
    public void setUp() throws Exception {
        spyContext = new SpyContext();
        agentConfig = new AgentConfiguration();
        agentConfig.setApplicationToken(ENABLED_APP_TOKEN_PROD);
    }

    public void setUpAgent() throws Exception {
        agentImpl = new AndroidAgentImpl(spyContext.getContext(), agentConfig);
        Agent.setImpl(agentImpl);

        ApplicationInformation appInfo = Providers.provideApplicationInformation();
        DeviceInformation devInfo = Providers.provideDeviceInformation();

        // By default, SavedState is created as clone's of the app's ConnectInformation
        SavedState savedState = spy(agentImpl.getSavedState());
        ConnectInformation savedConnInfo = new ConnectInformation(appInfo, devInfo);
        when(savedState.getConnectionToken()).thenReturn(String.valueOf(agentConfig.getApplicationToken().hashCode()));
        when(savedState.getConnectInformation()).thenReturn(savedConnInfo);
        agentImpl.setSavedState(savedState);
        agentImpl.getSavedState().clear();
    }

    @After
    public void tearDown() throws Exception {
        Agent.stop();
    }

    @Test
    public void testApplicationStateMonitorOnMonoXamarin() throws Exception {
        if (Agent.getMonoInstrumentationFlag().equals("YES")) {
            agentConfig.setApplicationFramework(ApplicationFramework.Xamarin);
            spyContext = new SpyContext();
            setUpAgent();
            try {
                ApplicationStateMonitor asm = ApplicationStateMonitor.getInstance();
                Assert.assertFalse("Should be foregrounded on start", ApplicationStateMonitor.isAppInBackground());
                asm.activityStopped();
                Thread.sleep(5000);
                Assert.assertTrue("Should be backgrounded on stop", ApplicationStateMonitor.isAppInBackground());
                asm.activityStarted();
                Thread.sleep(5000);
                asm.activityStopped();
                Thread.sleep(5000);
                Assert.assertTrue("Should be backgrounded after restart", ApplicationStateMonitor.isAppInBackground());
            } catch (Exception e) {
                Assert.fail("Should not throw exception on mono agent impl");
            }
            ApplicationStateMonitor.getInstance().removeApplicationStateListener(agentImpl);
        }

    }

    @Test
    public void testApplicationStateMonitorOnMonoMAUI() throws Exception{
        if (Agent.getMonoInstrumentationFlag().equals("YES")) {
            agentConfig.setApplicationFramework(ApplicationFramework.MAUI);
            setUpAgent();
            try {
                ApplicationStateMonitor asm = ApplicationStateMonitor.getInstance();
                Assert.assertFalse("Should be foregrounded on start", ApplicationStateMonitor.isAppInBackground());
                asm.activityStopped();
                Thread.sleep(5000);
                Assert.assertTrue("Should be backgrounded on stop", ApplicationStateMonitor.isAppInBackground());
                asm.activityStarted();
                Thread.sleep(5000);
                Assert.assertFalse("Should be foregrounded on start after being backgrounded", ApplicationStateMonitor.isAppInBackground());
                asm.activityStopped();
                Thread.sleep(5000);
                Assert.assertTrue("Should be backgrounded after restart", ApplicationStateMonitor.isAppInBackground());
            } catch (Exception e) {
                Assert.fail("Should not throw exception on mono agent impl");
            }
            ApplicationStateMonitor.getInstance().removeApplicationStateListener(agentImpl);
        }
    }

}

