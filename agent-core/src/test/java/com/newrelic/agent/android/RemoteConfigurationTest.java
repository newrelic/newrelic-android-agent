/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android;

import com.newrelic.agent.android.aei.ApplicationExitConfiguration;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.logging.ConsoleAgentLog;
import com.newrelic.agent.android.logging.LogLevel;
import com.newrelic.agent.android.logging.LogReportingConfiguration;
import com.newrelic.agent.android.sessionReplay.SessionReplayConfiguration;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class RemoteConfigurationTest {

    private RemoteConfiguration remoteConfig;

    @BeforeClass
    public static void beforeClass() {
        AgentLogManager.setAgentLog(new ConsoleAgentLog());
    }

    @Before
    public void setUp() throws Exception {
        remoteConfig = new RemoteConfiguration();
        remoteConfig.setApplicationExitConfiguration(new ApplicationExitConfiguration(false));

        FeatureFlag.enableFeature(FeatureFlag.ApplicationExitReporting);
    }

    @Test
    public void setApplicationExitConfiguration() {
        Assert.assertNotNull(remoteConfig.getApplicationExitConfiguration());
        Assert.assertFalse(remoteConfig.getApplicationExitConfiguration().isEnabled());

        remoteConfig.setApplicationExitConfiguration(new ApplicationExitConfiguration(true));
        Assert.assertNotNull(remoteConfig.getApplicationExitConfiguration());
        Assert.assertTrue(remoteConfig.getApplicationExitConfiguration().isEnabled());
    }

    @Test
    public void getLogReportingConfiguration() {
        Assert.assertNotNull(remoteConfig.getLogReportingConfiguration());
        Assert.assertFalse(remoteConfig.getLogReportingConfiguration().getLoggingEnabled());
        Assert.assertEquals(LogLevel.INFO, remoteConfig.getLogReportingConfiguration().getLogLevel());
    }

    @Test
    public void testSetAndGetSessionReplayConfiguration() {
        RemoteConfiguration config = new RemoteConfiguration();
        SessionReplayConfiguration sessionReplayConfig = new SessionReplayConfiguration();

        config.setSessionReplayConfiguration(sessionReplayConfig);

        Assert.assertEquals(sessionReplayConfig, config.getSessionReplayConfiguration());
    }


    @Test
    public void testDefaultConstructor() {
        RemoteConfiguration config = new RemoteConfiguration();

        Assert.assertNotNull(config.getApplicationExitConfiguration());
        Assert.assertNotNull(config.getLogReportingConfiguration());
        Assert.assertNotNull(config.getSessionReplayConfiguration());

        Assert.assertTrue(config.getApplicationExitConfiguration().isEnabled());
        Assert.assertFalse(config.getLogReportingConfiguration().getLoggingEnabled());
        Assert.assertEquals(LogLevel.INFO, config.getLogReportingConfiguration().getLogLevel());
    }


    @Test
    public void setLogReportingConfiguration() {
        remoteConfig.setLogReportingConfiguration(new LogReportingConfiguration(true, LogLevel.VERBOSE));
        Assert.assertNotNull(remoteConfig.getLogReportingConfiguration());
        Assert.assertTrue(remoteConfig.getLogReportingConfiguration().getLoggingEnabled());
        Assert.assertEquals(LogLevel.VERBOSE, remoteConfig.getLogReportingConfiguration().getLogLevel());
    }

    @Test
    public void testEquals() {
        RemoteConfiguration lhs = new RemoteConfiguration();
        RemoteConfiguration rhs = new RemoteConfiguration();

        Assert.assertFalse(lhs == rhs);
        Assert.assertTrue(lhs.equals(rhs));
        Assert.assertEquals(lhs, rhs);
    }

    @Test
    public void onHarvestConfigurationChanged() {
        remoteConfig.onHarvestConfigurationChanged();
    }
}