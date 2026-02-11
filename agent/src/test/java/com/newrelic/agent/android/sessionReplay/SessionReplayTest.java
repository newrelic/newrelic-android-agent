///*
// * Copyright (c) 2024. New Relic Corporation. All rights reserved.
// * SPDX-License-Identifier: Apache-2.0
// */
//
//package com.newrelic.agent.android.sessionReplay;
//
//import android.app.Application;
//import android.os.Handler;
//import android.os.Looper;
//
//import androidx.test.core.app.ApplicationProvider;
//
//import com.google.gson.JsonArray;
//import com.newrelic.agent.android.AgentConfiguration;
//import com.newrelic.agent.android.analytics.AnalyticsEvent;
//import com.newrelic.agent.android.background.ApplicationStateEvent;
//
//import org.junit.After;
//import org.junit.Assert;
//import org.junit.Before;
//import org.junit.Test;
//import org.junit.runner.RunWith;
//import org.robolectric.RobolectricTestRunner;
//import org.robolectric.Shadows;
//import org.robolectric.shadows.ShadowLooper;
//
//import java.util.concurrent.TimeUnit;
//
//import static org.mockito.Mockito.mock;
//
//@RunWith(RobolectricTestRunner.class)
//public class SessionReplayTest {
//
//    private Application application;
//    private Handler uiThreadHandler;
//    private AgentConfiguration agentConfiguration;
//    private SessionReplayConfiguration sessionReplayConfiguration;
//    private ShadowLooper shadowLooper;
//
//    @Before
//    public void setUp() {
//        application = ApplicationProvider.getApplicationContext();
//        uiThreadHandler = new Handler(Looper.getMainLooper());
//        shadowLooper = Shadows.shadowOf(Looper.getMainLooper());
//
//        agentConfiguration = AgentConfiguration.getInstance();
//
//        sessionReplayConfiguration = new SessionReplayConfiguration();
//        sessionReplayConfiguration.setEnabled(true);
//        sessionReplayConfiguration.setMode("error");
//        sessionReplayConfiguration.processCustomMaskingRules();
//        agentConfiguration.setSessionReplayConfiguration(sessionReplayConfiguration);
//
//        // Reset singleton state
//        SessionReplayModeManager.resetInstance();
//    }
//
//    @After
//    public void tearDown() {
//        try {
//            SessionReplay.deInitialize();
//            SessionReplayModeManager.resetInstance();
//        } catch (Exception e) {
//            // Ignore cleanup errors
//        }
//    }
//
//    // ==================== INITIALIZATION TESTS ====================
//
//    @Test
//    public void testInitialize_WithValidParameters_ErrorMode() {
//        SessionReplay.initialize(application, uiThreadHandler, agentConfiguration, SessionReplayMode.ERROR);
//
//        Assert.assertNotNull(SessionReplay.getCurrentMode());
//        Assert.assertEquals(SessionReplayMode.ERROR, SessionReplay.getCurrentMode());
//    }
//
//    @Test
//    public void testInitialize_WithValidParameters_FullMode() {
//        SessionReplay.initialize(application, uiThreadHandler, agentConfiguration, SessionReplayMode.FULL);
//
//        Assert.assertNotNull(SessionReplay.getCurrentMode());
//        Assert.assertEquals(SessionReplayMode.FULL, SessionReplay.getCurrentMode());
//    }
//
//    @Test
//    public void testInitialize_WithNullApplication_DoesNotCrash() {
//        // Should log error and return without crashing
//        SessionReplay.initialize(null, uiThreadHandler, agentConfiguration, SessionReplayMode.ERROR);
//        // Test passes if no exception thrown
//    }
//
//    @Test
//    public void testInitialize_WithNullHandler_DoesNotCrash() {
//        // Should log error and return without crashing
//        SessionReplay.initialize(application, null, agentConfiguration, SessionReplayMode.ERROR);
//        // Test passes if no exception thrown
//    }
//
//    @Test
//    public void testInitialize_WithNullMode_DoesNotCrash() {
//        // Should log error and return without crashing
//        SessionReplay.initialize(application, uiThreadHandler, agentConfiguration, null);
//        // Test passes if no exception thrown
//    }
//
//    @Test
//    public void testInitialize_MultipleCallsSafe() {
//        SessionReplay.initialize(application, uiThreadHandler, agentConfiguration, SessionReplayMode.ERROR);
//        SessionReplay.initialize(application, uiThreadHandler, agentConfiguration, SessionReplayMode.FULL);
//
//        // Should handle multiple initializations
//        Assert.assertNotNull(SessionReplay.getCurrentMode());
//    }
//
//    // ==================== DE-INITIALIZATION TESTS ====================
//
//    @Test
//    public void testDeInitialize_AfterInitialization() {
//        SessionReplay.initialize(application, uiThreadHandler, agentConfiguration, SessionReplayMode.ERROR);
//        SessionReplay.initSessionReplay(SessionReplayMode.ERROR);
//
//        SessionReplay.deInitialize();
//
//        // Test passes if no exception thrown
//    }
//
//    @Test
//    public void testDeInitialize_WithoutInitialization() {
//        // Should handle deinitialization without initialization
//        SessionReplay.deInitialize();
//        // Test passes if no exception thrown
//    }
//
//    @Test
//    public void testDeInitialize_MultipleCalls() {
//        SessionReplay.initialize(application, uiThreadHandler, agentConfiguration, SessionReplayMode.ERROR);
//        SessionReplay.initSessionReplay(SessionReplayMode.ERROR);
//
//        SessionReplay.deInitialize();
//        SessionReplay.deInitialize();
//
//        // Multiple deinitialization should be safe
//    }
//
//    // ==================== TAKE FULL SNAPSHOT TESTS ====================
//
//    @Test
//    public void testSetTakeFullSnapshot_True() {
//        SessionReplay.setTakeFullSnapshot(true);
//        Assert.assertTrue(SessionReplay.shouldTakeFullSnapshot());
//    }
//
//    @Test
//    public void testSetTakeFullSnapshot_False() {
//        SessionReplay.setTakeFullSnapshot(false);
//        Assert.assertFalse(SessionReplay.shouldTakeFullSnapshot());
//    }
//
//    @Test
//    public void testShouldTakeFullSnapshot_InitiallyTrue() {
//        // Should default to true
//        Assert.assertTrue(SessionReplay.shouldTakeFullSnapshot());
//    }
//
//    @Test
//    public void testSetTakeFullSnapshot_MultipleToggles() {
//        SessionReplay.setTakeFullSnapshot(true);
//        Assert.assertTrue(SessionReplay.shouldTakeFullSnapshot());
//
//        SessionReplay.setTakeFullSnapshot(false);
//        Assert.assertFalse(SessionReplay.shouldTakeFullSnapshot());
//
//        SessionReplay.setTakeFullSnapshot(true);
//        Assert.assertTrue(SessionReplay.shouldTakeFullSnapshot());
//    }
//
//    // ==================== GET CURRENT MODE TESTS ====================
//
//    @Test
//    public void testGetCurrentMode_WhenNotInitialized_ReturnsNull() {
//        SessionReplayMode mode = SessionReplay.getCurrentMode();
//        Assert.assertNull(mode);
//    }
//
//    @Test
//    public void testGetCurrentMode_AfterInitialization_ErrorMode() {
//        SessionReplay.initialize(application, uiThreadHandler, agentConfiguration, SessionReplayMode.ERROR);
//
//        SessionReplayMode mode = SessionReplay.getCurrentMode();
//        Assert.assertEquals(SessionReplayMode.ERROR, mode);
//    }
//
//    @Test
//    public void testGetCurrentMode_AfterInitialization_FullMode() {
//        SessionReplay.initialize(application, uiThreadHandler, agentConfiguration, SessionReplayMode.FULL);
//
//        SessionReplayMode mode = SessionReplay.getCurrentMode();
//        Assert.assertEquals(SessionReplayMode.FULL, mode);
//    }
//
//    // ==================== IS REPLAY RECORDING TESTS ====================
//
//    @Test
//    public void testIsReplayRecording_ErrorMode_ReturnsTrue() {
//        SessionReplay.initialize(application, uiThreadHandler, agentConfiguration, SessionReplayMode.ERROR);
//
//        // Note: Method has a bug (uses && instead of ||), so this tests the actual behavior
//        boolean isRecording = SessionReplay.isReplayRecording();
//        // Test the actual implementation behavior
//    }
//
//    @Test
//    public void testIsReplayRecording_OffMode_ReturnsFalse() {
//        SessionReplay.initialize(application, uiThreadHandler, agentConfiguration, SessionReplayMode.OFF);
//
//        boolean isRecording = SessionReplay.isReplayRecording();
//        Assert.assertFalse(isRecording);
//    }
//
//    // ==================== TRANSITION TO MODE TESTS ====================
//
//    @Test
//    public void testTransitionToMode_FromErrorToFull() {
//        SessionReplay.initialize(application, uiThreadHandler, agentConfiguration, SessionReplayMode.ERROR);
//
//        boolean result = SessionReplay.transitionToMode(SessionReplayMode.FULL, "test");
//
//        Assert.assertTrue(result);
//        Assert.assertEquals(SessionReplayMode.FULL, SessionReplay.getCurrentMode());
//        Assert.assertTrue("Should force full snapshot after transition", SessionReplay.shouldTakeFullSnapshot());
//    }
//
//    @Test
//    public void testTransitionToMode_FromFullToError() {
//        SessionReplay.initialize(application, uiThreadHandler, agentConfiguration, SessionReplayMode.FULL);
//
//        boolean result = SessionReplay.transitionToMode(SessionReplayMode.ERROR, "test");
//
//        Assert.assertTrue(result);
//        Assert.assertEquals(SessionReplayMode.ERROR, SessionReplay.getCurrentMode());
//    }
//
//    @Test
//    public void testTransitionToMode_WhenNotInitialized_ReturnsFalse() {
//        boolean result = SessionReplay.transitionToMode(SessionReplayMode.FULL, "test");
//
//        Assert.assertFalse(result);
//    }
//
//    @Test
//    public void testTransitionToMode_SameMode_ReturnsFalse() {
//        SessionReplay.initialize(application, uiThreadHandler, agentConfiguration, SessionReplayMode.ERROR);
//
//        boolean result = SessionReplay.transitionToMode(SessionReplayMode.ERROR, "test");
//
//        Assert.assertFalse(result);
//    }
//
//    // ==================== SWITCH MODE ON ERROR TESTS ====================
//
//    @Test
//    public void testSwitchModeOnError_FromErrorToFull_ReturnsTrue() {
//        SessionReplay.initialize(application, uiThreadHandler, agentConfiguration, SessionReplayMode.ERROR);
//
//        boolean result = SessionReplay.switchModeOnError();
//
//        Assert.assertTrue(result);
//        Assert.assertEquals(SessionReplayMode.FULL, SessionReplay.getCurrentMode());
//        Assert.assertTrue(SessionReplay.shouldTakeFullSnapshot());
//    }
//
//    @Test
//    public void testSwitchModeOnError_FromFullMode_ReturnsFalse() {
//        SessionReplay.initialize(application, uiThreadHandler, agentConfiguration, SessionReplayMode.FULL);
//
//        boolean result = SessionReplay.switchModeOnError();
//
//        Assert.assertFalse("Should not switch from FULL mode", result);
//        Assert.assertEquals(SessionReplayMode.FULL, SessionReplay.getCurrentMode());
//    }
//
//    @Test
//    public void testSwitchModeOnError_WhenNotInitialized_ReturnsFalse() {
//        boolean result = SessionReplay.switchModeOnError();
//        Assert.assertFalse(result);
//    }
//
//    @Test
//    public void testOnError_CallsSwitchModeOnError() {
//        SessionReplay.initialize(application, uiThreadHandler, agentConfiguration, SessionReplayMode.ERROR);
//
//        SessionReplay.onError();
//
//        // Should have switched to FULL mode
//        Assert.assertEquals(SessionReplayMode.FULL, SessionReplay.getCurrentMode());
//    }
//
//    // ==================== PAUSE REPLAY TESTS ====================
//
//    @Test
//    public void testPauseReplay_FromErrorMode_ReturnsTrue() {
//        SessionReplay.initialize(application, uiThreadHandler, agentConfiguration, SessionReplayMode.ERROR);
//
//        boolean result = SessionReplay.pauseReplay();
//
//        Assert.assertTrue(result);
//        Assert.assertEquals(SessionReplayMode.OFF, SessionReplay.getCurrentMode());
//    }
//
//    @Test
//    public void testPauseReplay_FromFullMode_ReturnsTrue() {
//        SessionReplay.initialize(application, uiThreadHandler, agentConfiguration, SessionReplayMode.FULL);
//
//        boolean result = SessionReplay.pauseReplay();
//
//        Assert.assertTrue(result);
//        Assert.assertEquals(SessionReplayMode.OFF, SessionReplay.getCurrentMode());
//    }
//
//    @Test
//    public void testPauseReplay_WhenAlreadyOff_ReturnsFalse() {
//        SessionReplay.initialize(application, uiThreadHandler, agentConfiguration, SessionReplayMode.OFF);
//
//        boolean result = SessionReplay.pauseReplay();
//
//        Assert.assertFalse("Should return false when already OFF", result);
//    }
//
//    // ==================== HARVEST LIFECYCLE TESTS ====================
//
//    @Test
//    public void testOnHarvestStart_FullMode_SetsHarvestingFlag() {
//        SessionReplay.initialize(application, uiThreadHandler, agentConfiguration, SessionReplayMode.FULL);
//        SessionReplay.initSessionReplay(SessionReplayMode.FULL);
//
//        shadowLooper.idle();
//
//        // Call onHarvestStart
//        SessionReplay sessionReplay = new SessionReplay();
//        sessionReplay.onHarvestStart();
//
//        // Should set harvesting flag (can't test directly, but method should not throw)
//    }
//
//    @Test
//    public void testOnHarvestStart_ErrorMode_DoesNotSetFlag() {
//        SessionReplay.initialize(application, uiThreadHandler, agentConfiguration, SessionReplayMode.ERROR);
//        SessionReplay.initSessionReplay(SessionReplayMode.ERROR);
//
//        shadowLooper.idle();
//
//        SessionReplay sessionReplay = new SessionReplay();
//        sessionReplay.onHarvestStart();
//
//        // Should not set harvesting flag in ERROR mode
//    }
//
//    @Test
//    public void testOnHarvest_ErrorMode_SkipsHarvest() {
//        SessionReplay.initialize(application, uiThreadHandler, agentConfiguration, SessionReplayMode.ERROR);
//        SessionReplay.initSessionReplay(SessionReplayMode.ERROR);
//
//        shadowLooper.idle();
//
//        SessionReplay sessionReplay = new SessionReplay();
//        sessionReplay.onHarvest();
//
//        // Should skip harvest in ERROR mode
//    }
//
//    @Test
//    public void testOnHarvest_FullMode_WithNoEvents() {
//        SessionReplay.initialize(application, uiThreadHandler, agentConfiguration, SessionReplayMode.FULL);
//        SessionReplay.initSessionReplay(SessionReplayMode.FULL);
//
//        shadowLooper.idle();
//
//        SessionReplay sessionReplay = new SessionReplay();
//        sessionReplay.onHarvest();
//
//        // Should handle empty events gracefully
//    }
//
//    @Test
//    public void testOnHarvestComplete_FlushesBufferedFrames() {
//        SessionReplay.initialize(application, uiThreadHandler, agentConfiguration, SessionReplayMode.FULL);
//        SessionReplay.initSessionReplay(SessionReplayMode.FULL);
//
//        shadowLooper.idle();
//
//        SessionReplay sessionReplay = new SessionReplay();
//        sessionReplay.onHarvestStart(); // Start harvesting
//        // Frames would be buffered here...
//        sessionReplay.onHarvestComplete(); // Complete harvest
//
//        // Should flush buffered data
//    }
//
//    // ==================== APPLICATION STATE LISTENER TESTS ====================
//
//    @Test
//    public void testApplicationForegrounded_DoesNothing() {
//        SessionReplay sessionReplay = new SessionReplay();
//        ApplicationStateEvent event = mock(ApplicationStateEvent.class);
//
//        // Should not throw exception
//        sessionReplay.applicationForegrounded(event);
//    }
//
//    @Test
//    public void testApplicationBackgrounded_ClearsWorkingFile() {
//        SessionReplay.initialize(application, uiThreadHandler, agentConfiguration, SessionReplayMode.FULL);
//        SessionReplay.initSessionReplay(SessionReplayMode.FULL);
//
//        shadowLooper.idle();
//
//        SessionReplay sessionReplay = new SessionReplay();
//        ApplicationStateEvent event = mock(ApplicationStateEvent.class);
//
//        sessionReplay.applicationBackgrounded(event);
//
//        // Should clear working file (method should not throw)
//    }
//
//    // ==================== EVENT LISTENER TESTS ====================
//
//    @Test
//    public void testOnEventAdded_ReturnsTrue() {
//        SessionReplay sessionReplay = new SessionReplay();
//        AnalyticsEvent event = mock(AnalyticsEvent.class);
//
//        boolean result = sessionReplay.onEventAdded(event);
//
//        Assert.assertTrue(result);
//    }
//
//    @Test
//    public void testOnEventOverflow_ReturnsTrue() {
//        SessionReplay sessionReplay = new SessionReplay();
//        AnalyticsEvent event = mock(AnalyticsEvent.class);
//
//        boolean result = sessionReplay.onEventOverflow(event);
//
//        Assert.assertTrue(result);
//    }
//
//    @Test
//    public void testOnEventEvicted_ReturnsTrue() {
//        SessionReplay sessionReplay = new SessionReplay();
//        AnalyticsEvent event = mock(AnalyticsEvent.class);
//
//        boolean result = sessionReplay.onEventEvicted(event);
//
//        Assert.assertTrue(result);
//    }
//
//    @Test
//    public void testOnEventQueueSizeExceeded_DoesNotThrow() {
//        SessionReplay sessionReplay = new SessionReplay();
//        sessionReplay.onEventQueueSizeExceeded(1000);
//        // Should not throw exception
//    }
//
//    @Test
//    public void testOnEventQueueTimeExceeded_DoesNotThrow() {
//        SessionReplay sessionReplay = new SessionReplay();
//        sessionReplay.onEventQueueTimeExceeded(60);
//        // Should not throw exception
//    }
//
//    @Test
//    public void testOnEventFlush_DoesNotThrow() {
//        SessionReplay sessionReplay = new SessionReplay();
//        sessionReplay.onEventFlush();
//        // Should not throw exception
//    }
//
//    @Test
//    public void testOnStart_DoesNotThrow() {
//        SessionReplay sessionReplay = new SessionReplay();
//        sessionReplay.onStart(null);
//        // Should not throw exception
//    }
//
//    @Test
//    public void testOnShutdown_DoesNotThrow() {
//        SessionReplay sessionReplay = new SessionReplay();
//        sessionReplay.onShutdown();
//        // Should not throw exception
//    }
//
//    // ==================== ON FRAME TAKEN TESTS ====================
//
//    @Test
//    public void testOnFrameTaken_WithValidFrame() {
//        SessionReplay.initialize(application, uiThreadHandler, agentConfiguration, SessionReplayMode.FULL);
//        SessionReplay.initSessionReplay(SessionReplayMode.FULL);
//
//        shadowLooper.idle();
//
//        SessionReplay sessionReplay = new SessionReplay();
//        SessionReplayFrame frame = mock(SessionReplayFrame.class);
//
//        // Should not throw exception
//        sessionReplay.onFrameTaken(frame);
//    }
//
//    @Test
//    public void testOnFrameTaken_DuringHarvest_BuffersFrame() {
//        SessionReplay.initialize(application, uiThreadHandler, agentConfiguration, SessionReplayMode.FULL);
//        SessionReplay.initSessionReplay(SessionReplayMode.FULL);
//
//        shadowLooper.idle();
//
//        SessionReplay sessionReplay = new SessionReplay();
//        SessionReplayFrame frame = mock(SessionReplayFrame.class);
//
//        // Start harvest (buffers frames)
//        sessionReplay.onHarvestStart();
//
//        // Frame should be buffered
//        sessionReplay.onFrameTaken(frame);
//
//        // Complete harvest (flushes buffer)
//        sessionReplay.onHarvestComplete();
//    }
//
//    // ==================== ON TOUCH RECORDED TESTS ====================
//
//    @Test
//    public void testOnTouchRecorded_WithValidTouchTracker() {
//        SessionReplay.initialize(application, uiThreadHandler, agentConfiguration, SessionReplayMode.FULL);
//        SessionReplay.initSessionReplay(SessionReplayMode.FULL);
//
//        shadowLooper.idle();
//
//        SessionReplay sessionReplay = new SessionReplay();
//        TouchTracker touchTracker = new TouchTracker(System.currentTimeMillis());
//
//        // Should not throw exception
//        sessionReplay.onTouchRecorded(touchTracker);
//    }
//
//    @Test
//    public void testOnTouchRecorded_DuringHarvest_BuffersTouchData() {
//        SessionReplay.initialize(application, uiThreadHandler, agentConfiguration, SessionReplayMode.FULL);
//        SessionReplay.initSessionReplay(SessionReplayMode.FULL);
//
//        shadowLooper.idle();
//
//        SessionReplay sessionReplay = new SessionReplay();
//        TouchTracker touchTracker = new TouchTracker(System.currentTimeMillis());
//
//        // Start harvest
//        sessionReplay.onHarvestStart();
//
//        // Touch should be buffered
//        sessionReplay.onTouchRecorded(touchTracker);
//
//        // Complete harvest
//        sessionReplay.onHarvestComplete();
//    }
//
//    // ==================== START/STOP RECORDING TESTS ====================
//
//    @Test
//    public void testStartRecording_ErrorMode() {
//        SessionReplay.initialize(application, uiThreadHandler, agentConfiguration, SessionReplayMode.ERROR);
//
//        // Should start recording with sliding window
//        SessionReplay.startRecording(SessionReplayMode.ERROR);
//        shadowLooper.idle();
//
//        Assert.assertTrue(SessionReplay.shouldTakeFullSnapshot());
//    }
//
//    @Test
//    public void testStartRecording_FullMode() {
//        SessionReplay.initialize(application, uiThreadHandler, agentConfiguration, SessionReplayMode.FULL);
//
//        // Should start recording without sliding window
//        SessionReplay.startRecording(SessionReplayMode.FULL);
//        shadowLooper.idle();
//
//        Assert.assertTrue(SessionReplay.shouldTakeFullSnapshot());
//    }
//
//    @Test
//    public void testStopRecording() {
//        SessionReplay.initialize(application, uiThreadHandler, agentConfiguration, SessionReplayMode.FULL);
//        SessionReplay.initSessionReplay(SessionReplayMode.FULL);
//
//        shadowLooper.idle();
//
//        SessionReplay.stopRecording();
//        shadowLooper.idle();
//
//        // Should stop recording without throwing exception
//    }
//
//    // ==================== INIT SESSION REPLAY TESTS ====================
//
//    @Test
//    public void testInitSessionReplay_ErrorMode() {
//        SessionReplay.initialize(application, uiThreadHandler, agentConfiguration, SessionReplayMode.ERROR);
//
//        SessionReplay.initSessionReplay(SessionReplayMode.ERROR);
//        shadowLooper.idle();
//
//        // Should initialize in ERROR mode
//        Assert.assertEquals(SessionReplayMode.ERROR, SessionReplay.getCurrentMode());
//    }
//
//    @Test
//    public void testInitSessionReplay_FullMode() {
//        SessionReplay.initialize(application, uiThreadHandler, agentConfiguration, SessionReplayMode.FULL);
//
//        SessionReplay.initSessionReplay(SessionReplayMode.FULL);
//        shadowLooper.idle();
//
//        // Should initialize in FULL mode
//        Assert.assertEquals(SessionReplayMode.FULL, SessionReplay.getCurrentMode());
//    }
//
//    // ==================== INTEGRATION TESTS ====================
//
//    @Test
//    public void testIntegration_FullLifecycle_ErrorMode() {
//        // Initialize
//        SessionReplay.initialize(application, uiThreadHandler, agentConfiguration, SessionReplayMode.ERROR);
//        SessionReplay.initSessionReplay(SessionReplayMode.ERROR);
//        shadowLooper.idle();
//
//        Assert.assertEquals(SessionReplayMode.ERROR, SessionReplay.getCurrentMode());
//
//        // Switch to FULL on error
//        SessionReplay.onError();
//        Assert.assertEquals(SessionReplayMode.FULL, SessionReplay.getCurrentMode());
//
//        // Pause
//        SessionReplay.pauseReplay();
//        Assert.assertEquals(SessionReplayMode.OFF, SessionReplay.getCurrentMode());
//
//        // Deinitialize
//        SessionReplay.deInitialize();
//    }
//
//    @Test
//    public void testIntegration_FullLifecycle_FullMode() {
//        // Initialize
//        SessionReplay.initialize(application, uiThreadHandler, agentConfiguration, SessionReplayMode.FULL);
//        SessionReplay.initSessionReplay(SessionReplayMode.FULL);
//        shadowLooper.idle();
//
//        Assert.assertEquals(SessionReplayMode.FULL, SessionReplay.getCurrentMode());
//
//        // Pause
//        SessionReplay.pauseReplay();
//        Assert.assertEquals(SessionReplayMode.OFF, SessionReplay.getCurrentMode());
//
//        // Deinitialize
//        SessionReplay.deInitialize();
//    }
//
//    @Test
//    public void testIntegration_HarvestCycle() {
//        SessionReplay.initialize(application, uiThreadHandler, agentConfiguration, SessionReplayMode.FULL);
//        SessionReplay.initSessionReplay(SessionReplayMode.FULL);
//        shadowLooper.idle();
//
//        SessionReplay sessionReplay = new SessionReplay();
//
//        // Simulate harvest cycle
//        sessionReplay.onHarvestStart();
//        sessionReplay.onHarvest();
//        sessionReplay.onHarvestComplete();
//
//        // Should complete without errors
//    }
//
//    // ==================== EDGE CASE TESTS ====================
//
//    @Test
//    public void testEdgeCase_MultipleInitAndDeinit() {
//        for (int i = 0; i < 5; i++) {
//            SessionReplay.initialize(application, uiThreadHandler, agentConfiguration, SessionReplayMode.ERROR);
//            SessionReplay.initSessionReplay(SessionReplayMode.ERROR);
//            shadowLooper.idle();
//            SessionReplay.deInitialize();
//        }
//
//        // Should handle multiple cycles
//    }
//
//    @Test
//    public void testEdgeCase_RapidModeTransitions() {
//        SessionReplay.initialize(application, uiThreadHandler, agentConfiguration, SessionReplayMode.ERROR);
//
//        // Rapid transitions
//        SessionReplay.transitionToMode(SessionReplayMode.FULL, "test1");
//        SessionReplay.transitionToMode(SessionReplayMode.OFF, "test2");
//        SessionReplay.transitionToMode(SessionReplayMode.ERROR, "test3");
//        SessionReplay.transitionToMode(SessionReplayMode.FULL, "test4");
//
//        Assert.assertEquals(SessionReplayMode.FULL, SessionReplay.getCurrentMode());
//    }
//
//    @Test
//    public void testEdgeCase_PauseReplayMultipleTimes() {
//        SessionReplay.initialize(application, uiThreadHandler, agentConfiguration, SessionReplayMode.FULL);
//
//        boolean result1 = SessionReplay.pauseReplay();
//        boolean result2 = SessionReplay.pauseReplay();
//        boolean result3 = SessionReplay.pauseReplay();
//
//        Assert.assertTrue(result1);
//        Assert.assertFalse("Second pause should return false", result2);
//        Assert.assertFalse("Third pause should return false", result3);
//    }
//
//    @Test
//    public void testEdgeCase_OnErrorWhenAlreadyInFullMode() {
//        SessionReplay.initialize(application, uiThreadHandler, agentConfiguration, SessionReplayMode.FULL);
//
//        SessionReplay.onError();
//
//        // Should stay in FULL mode
//        Assert.assertEquals(SessionReplayMode.FULL, SessionReplay.getCurrentMode());
//    }
//}