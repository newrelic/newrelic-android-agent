/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay;

import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.logging.ConsoleAgentLog;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.stats.StatsEngine;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class SessionReplayModeManagerTest {

    private SessionReplayConfiguration configuration;

    @BeforeClass
    public static void beforeClass() {
        AgentLogManager.setAgentLog(new ConsoleAgentLog());
    }

    @Before
    public void setUp() {
        // Reset singleton instance before each test
        SessionReplayModeManager.resetInstance();
        StatsEngine.SUPPORTABILITY.getStatsMap().clear();

        configuration = new SessionReplayConfiguration();
        configuration.setEnabled(true);
        configuration.setMode("error");
    }

    @After
    public void tearDown() {
        SessionReplayModeManager.resetInstance();
        StatsEngine.SUPPORTABILITY.getStatsMap().clear();
    }

    @Test
    public void testGetInstanceCreatesNewInstance() {
        SessionReplayModeManager manager = SessionReplayModeManager.getInstance(configuration);
        Assert.assertNotNull(manager);
        Assert.assertEquals(SessionReplayMode.ERROR, manager.getCurrentMode());
    }

    @Test
    public void testGetInstanceReturnsSameInstance() {
        SessionReplayModeManager manager1 = SessionReplayModeManager.getInstance(configuration);
        SessionReplayModeManager manager2 = SessionReplayModeManager.getInstance(configuration);
        Assert.assertSame(manager1, manager2);
    }

    @Test
    public void testGetInstanceWithoutParameterReturnsExisting() {
        SessionReplayModeManager manager1 = SessionReplayModeManager.getInstance(configuration);
        SessionReplayModeManager manager2 = SessionReplayModeManager.getInstance();
        Assert.assertSame(manager1, manager2);
    }

    @Test
    public void testGetInstanceWithoutParameterReturnsNullWhenNotInitialized() {
        Assert.assertNull(SessionReplayModeManager.getInstance());
    }

    @Test
    public void testInitializationWithOffMode() {
        configuration.setMode("off");
        SessionReplayModeManager manager = SessionReplayModeManager.getInstance(configuration);
        Assert.assertEquals(SessionReplayMode.OFF, manager.getCurrentMode());
    }

    @Test
    public void testInitializationWithErrorMode() {
        configuration.setMode("error");
        SessionReplayModeManager manager = SessionReplayModeManager.getInstance(configuration);
        Assert.assertEquals(SessionReplayMode.ERROR, manager.getCurrentMode());
    }

    @Test
    public void testInitializationWithFullMode() {
        configuration.setMode("full");
        SessionReplayModeManager manager = SessionReplayModeManager.getInstance(configuration);
        Assert.assertEquals(SessionReplayMode.FULL, manager.getCurrentMode());
    }

    @Test
    public void testInitializationWithDisabledConfiguration() {
        configuration.setEnabled(false);
        configuration.setMode("full");
        SessionReplayModeManager manager = SessionReplayModeManager.getInstance(configuration);
        // Should be OFF regardless of mode when disabled
        Assert.assertEquals(SessionReplayMode.OFF, manager.getCurrentMode());
    }

    @Test
    public void testInitializationWithInvalidMode() {
        configuration.setMode("invalid");
        SessionReplayModeManager manager = SessionReplayModeManager.getInstance(configuration);
        // Should default to ERROR mode
        Assert.assertEquals(SessionReplayMode.ERROR, manager.getCurrentMode());
    }

    @Test
    public void testGetCurrentMode() {
        SessionReplayModeManager manager = SessionReplayModeManager.getInstance(configuration);
        Assert.assertEquals(SessionReplayMode.ERROR, manager.getCurrentMode());
    }

    @Test
    public void testIsRecordingWhenOff() {
        configuration.setMode("off");
        SessionReplayModeManager manager = SessionReplayModeManager.getInstance(configuration);
        Assert.assertFalse(manager.isRecording());
    }

    @Test
    public void testIsRecordingWhenError() {
        configuration.setMode("error");
        SessionReplayModeManager manager = SessionReplayModeManager.getInstance(configuration);
        Assert.assertTrue(manager.isRecording());
    }

    @Test
    public void testIsRecordingWhenFull() {
        configuration.setMode("full");
        SessionReplayModeManager manager = SessionReplayModeManager.getInstance(configuration);
        Assert.assertTrue(manager.isRecording());
    }

    @Test
    public void testIsFullMode() {
        configuration.setMode("full");
        SessionReplayModeManager manager = SessionReplayModeManager.getInstance(configuration);
        Assert.assertTrue(manager.isFullMode());
        Assert.assertFalse(manager.isErrorMode());
    }

    @Test
    public void testIsErrorMode() {
        configuration.setMode("error");
        SessionReplayModeManager manager = SessionReplayModeManager.getInstance(configuration);
        Assert.assertTrue(manager.isErrorMode());
        Assert.assertFalse(manager.isFullMode());
    }

    @Test
    public void testIsOffMode() {
        configuration.setMode("off");
        SessionReplayModeManager manager = SessionReplayModeManager.getInstance(configuration);
        Assert.assertFalse(manager.isFullMode());
        Assert.assertFalse(manager.isErrorMode());
        Assert.assertFalse(manager.isRecording());
    }

    @Test
    public void testTransitionToNewMode() {
        configuration.setMode("error");
        SessionReplayModeManager manager = SessionReplayModeManager.getInstance(configuration);

        boolean result = manager.transitionTo(SessionReplayMode.FULL, "test transition");
        Assert.assertTrue(result);
        Assert.assertEquals(SessionReplayMode.FULL, manager.getCurrentMode());
    }

    @Test
    public void testTransitionToSameMode() {
        configuration.setMode("error");
        SessionReplayModeManager manager = SessionReplayModeManager.getInstance(configuration);

        boolean result = manager.transitionTo(SessionReplayMode.ERROR, "same mode");
        Assert.assertFalse(result);
        Assert.assertEquals(SessionReplayMode.ERROR, manager.getCurrentMode());
    }

    @Test
    public void testTransitionFromErrorToFull() {
        configuration.setMode("error");
        SessionReplayModeManager manager = SessionReplayModeManager.getInstance(configuration);

        Assert.assertEquals(SessionReplayMode.ERROR, manager.getCurrentMode());
        boolean result = manager.transitionTo(SessionReplayMode.FULL, "error detected");
        Assert.assertTrue(result);
        Assert.assertEquals(SessionReplayMode.FULL, manager.getCurrentMode());
    }

    @Test
    public void testTransitionFromFullToError() {
        configuration.setMode("full");
        SessionReplayModeManager manager = SessionReplayModeManager.getInstance(configuration);

        Assert.assertEquals(SessionReplayMode.FULL, manager.getCurrentMode());
        boolean result = manager.transitionTo(SessionReplayMode.ERROR, "downgrade");
        Assert.assertTrue(result);
        Assert.assertEquals(SessionReplayMode.ERROR, manager.getCurrentMode());
    }

    @Test
    public void testTransitionFromOffToError() {
        configuration.setMode("off");
        SessionReplayModeManager manager = SessionReplayModeManager.getInstance(configuration);

        Assert.assertEquals(SessionReplayMode.OFF, manager.getCurrentMode());
        boolean result = manager.transitionTo(SessionReplayMode.ERROR, "enable recording");
        Assert.assertTrue(result);
        Assert.assertEquals(SessionReplayMode.ERROR, manager.getCurrentMode());
    }

    @Test
    public void testTransitionFromOffToFull() {
        configuration.setMode("off");
        SessionReplayModeManager manager = SessionReplayModeManager.getInstance(configuration);

        Assert.assertEquals(SessionReplayMode.OFF, manager.getCurrentMode());
        boolean result = manager.transitionTo(SessionReplayMode.FULL, "enable full recording");
        Assert.assertTrue(result);
        Assert.assertEquals(SessionReplayMode.FULL, manager.getCurrentMode());
    }

    @Test
    public void testTransitionToOff() {
        configuration.setMode("full");
        SessionReplayModeManager manager = SessionReplayModeManager.getInstance(configuration);

        Assert.assertEquals(SessionReplayMode.FULL, manager.getCurrentMode());
        boolean result = manager.transitionTo(SessionReplayMode.OFF, "disable");
        Assert.assertTrue(result);
        Assert.assertEquals(SessionReplayMode.OFF, manager.getCurrentMode());
    }

    @Test
    public void testMultipleTransitions() {
        configuration.setMode("off");
        SessionReplayModeManager manager = SessionReplayModeManager.getInstance(configuration);

        // OFF -> ERROR
        Assert.assertTrue(manager.transitionTo(SessionReplayMode.ERROR, "transition 1"));
        Assert.assertEquals(SessionReplayMode.ERROR, manager.getCurrentMode());

        // ERROR -> FULL
        Assert.assertTrue(manager.transitionTo(SessionReplayMode.FULL, "transition 2"));
        Assert.assertEquals(SessionReplayMode.FULL, manager.getCurrentMode());

        // FULL -> OFF
        Assert.assertTrue(manager.transitionTo(SessionReplayMode.OFF, "transition 3"));
        Assert.assertEquals(SessionReplayMode.OFF, manager.getCurrentMode());

        // OFF -> FULL (direct)
        Assert.assertTrue(manager.transitionTo(SessionReplayMode.FULL, "transition 4"));
        Assert.assertEquals(SessionReplayMode.FULL, manager.getCurrentMode());
    }

    @Test
    public void testGetConfiguration() {
        SessionReplayModeManager manager = SessionReplayModeManager.getInstance(configuration);
        SessionReplayConfiguration retrievedConfig = manager.getConfiguration();
        Assert.assertSame(configuration, retrievedConfig);
        Assert.assertEquals(configuration.getMode(), retrievedConfig.getMode());
    }

    @Test
    public void testInitializationRecordsSupportabilityMetric() {
        StatsEngine.SUPPORTABILITY.getStatsMap().clear();

        configuration.setMode("error");
        SessionReplayModeManager manager = SessionReplayModeManager.getInstance(configuration);

        String expectedMetric = MetricNames.SUPPORTABILITY_SESSION_REPLAY_MODE + "error";
        Assert.assertTrue(StatsEngine.SUPPORTABILITY.getStatsMap().containsKey(expectedMetric));
    }

    @Test
    public void testTransitionRecordsSupportabilityMetric() {
        StatsEngine.SUPPORTABILITY.getStatsMap().clear();

        configuration.setMode("error");
        SessionReplayModeManager manager = SessionReplayModeManager.getInstance(configuration);

        // Clear the initialization metric
        StatsEngine.SUPPORTABILITY.getStatsMap().clear();

        manager.transitionTo(SessionReplayMode.FULL, "test");

        String expectedMetric = MetricNames.SUPPORTABILITY_SESSION_REPLAY_MODE_TRANSITION + "error_to_full";
        Assert.assertTrue(StatsEngine.SUPPORTABILITY.getStatsMap().containsKey(expectedMetric));
    }

    @Test
    public void testResetInstance() {
        SessionReplayModeManager manager = SessionReplayModeManager.getInstance(configuration);
        Assert.assertNotNull(manager);

        SessionReplayModeManager.resetInstance();
        Assert.assertNull(SessionReplayModeManager.getInstance());
    }

    @Test
    public void testInitializationWithNullMode() {
        configuration.setMode(null);
        SessionReplayModeManager manager = SessionReplayModeManager.getInstance(configuration);
        // Should default to ERROR mode when mode is null
        Assert.assertEquals(SessionReplayMode.ERROR, manager.getCurrentMode());
    }

    @Test
    public void testTransitionWithEmptyTrigger() {
        SessionReplayModeManager manager = SessionReplayModeManager.getInstance(configuration);
        boolean result = manager.transitionTo(SessionReplayMode.FULL, "");
        Assert.assertTrue(result);
        Assert.assertEquals(SessionReplayMode.FULL, manager.getCurrentMode());
    }

    @Test
    public void testTransitionWithNullTrigger() {
        SessionReplayModeManager manager = SessionReplayModeManager.getInstance(configuration);
        boolean result = manager.transitionTo(SessionReplayMode.FULL, null);
        Assert.assertTrue(result);
        Assert.assertEquals(SessionReplayMode.FULL, manager.getCurrentMode());
    }

    @Test
    public void testStateConsistencyAfterMultipleTransitions() {
        SessionReplayModeManager manager = SessionReplayModeManager.getInstance(configuration);

        // Perform many transitions
        for (int i = 0; i < 100; i++) {
            SessionReplayMode targetMode = i % 3 == 0 ? SessionReplayMode.OFF :
                                          i % 3 == 1 ? SessionReplayMode.ERROR :
                                          SessionReplayMode.FULL;
            manager.transitionTo(targetMode, "iteration " + i);
            Assert.assertEquals(targetMode, manager.getCurrentMode());
        }
    }
}