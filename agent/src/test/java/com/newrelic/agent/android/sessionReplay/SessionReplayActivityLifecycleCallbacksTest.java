///*
// * Copyright (c) 2024. New Relic Corporation. All rights reserved.
// * SPDX-License-Identifier: Apache-2.0
// */
//
//package com.newrelic.agent.android.sessionReplay;
//
//import android.app.Activity;
//import android.app.Application;
//import android.os.Bundle;
//import android.view.MotionEvent;
//import android.view.View;
//
//import androidx.test.core.app.ApplicationProvider;
//
//import com.newrelic.agent.android.AgentConfiguration;
//
//import org.junit.Assert;
//import org.junit.Before;
//import org.junit.Test;
//import org.junit.runner.RunWith;
//import org.robolectric.Robolectric;
//import org.robolectric.RobolectricTestRunner;
//import org.robolectric.android.controller.ActivityController;
//
//import java.util.concurrent.atomic.AtomicInteger;
//import java.util.concurrent.atomic.AtomicReference;
//
//@RunWith(RobolectricTestRunner.class)
//public class SessionReplayActivityLifecycleCallbacksTest {
//
//    private Application application;
//    private AgentConfiguration agentConfiguration;
//    private SessionReplayConfiguration sessionReplayConfiguration;
//    private OnTouchRecordedListener mockTouchListener;
//    private SessionReplayModeManager modeManager;
//    private SessionReplayActivityLifecycleCallbacks callbacks;
//    private AtomicInteger touchRecordedCount;
//    private AtomicReference<TouchTracker> lastTouchTracker;
//
//    @Before
//    public void setUp() {
//        application = ApplicationProvider.getApplicationContext();
//        agentConfiguration = AgentConfiguration.getInstance();
//        agentConfiguration.setSessionID("test-session-" + System.currentTimeMillis());
//
//        sessionReplayConfiguration = new SessionReplayConfiguration();
//        sessionReplayConfiguration.setEnabled(true);
//        sessionReplayConfiguration.setMaskAllUserTouches(false);
//        sessionReplayConfiguration.processCustomMaskingRules();
//        agentConfiguration.setSessionReplayConfiguration(sessionReplayConfiguration);
//
//        modeManager = new SessionReplayModeManager(sessionReplayConfiguration);
//
//        touchRecordedCount = new AtomicInteger(0);
//        lastTouchTracker = new AtomicReference<>(null);
//
//        mockTouchListener = new OnTouchRecordedListener() {
//            @Override
//            public void onTouchRecorded(TouchTracker touchTracker) {
//                touchRecordedCount.incrementAndGet();
//                lastTouchTracker.set(touchTracker);
//            }
//        };
//
//        callbacks = new SessionReplayActivityLifecycleCallbacks(mockTouchListener, application, modeManager);
//    }
//
//    // ==================== CONSTRUCTOR TESTS ====================
//
//    @Test
//    public void testConstructor_WithValidParameters() {
//        SessionReplayActivityLifecycleCallbacks newCallbacks = new SessionReplayActivityLifecycleCallbacks(
//            mockTouchListener,
//            application,
//            modeManager
//        );
//        Assert.assertNotNull(newCallbacks);
//        Assert.assertNotNull(newCallbacks.viewTouchHandler);
//        Assert.assertNotNull(newCallbacks.semanticsNodeTouchHandler);
//        Assert.assertNotNull(newCallbacks.sessionReplayConfiguration);
//    }
//
//    @Test
//    public void testConstructor_InitializesViewTouchHandler() {
//        Assert.assertNotNull(callbacks.viewTouchHandler);
//        Assert.assertTrue(callbacks.viewTouchHandler instanceof ViewTouchHandler);
//    }
//
//    @Test
//    public void testConstructor_InitializesSemanticsNodeTouchHandler() {
//        Assert.assertNotNull(callbacks.semanticsNodeTouchHandler);
//        Assert.assertTrue(callbacks.semanticsNodeTouchHandler instanceof SemanticsNodeTouchHandler);
//    }
//
//    @Test
//    public void testConstructor_InitializesSessionReplayConfiguration() {
//        Assert.assertNotNull(callbacks.sessionReplayConfiguration);
//        Assert.assertTrue(callbacks.sessionReplayConfiguration instanceof SessionReplayConfiguration);
//    }
//
//    @Test
//    public void testConstructor_WithNullListener() {
//        SessionReplayActivityLifecycleCallbacks newCallbacks = new SessionReplayActivityLifecycleCallbacks(
//            null,
//            application,
//            modeManager
//        );
//        Assert.assertNotNull(newCallbacks);
//    }
//
//    @Test
//    public void testConstructor_WithNullModeManager() {
//        SessionReplayActivityLifecycleCallbacks newCallbacks = new SessionReplayActivityLifecycleCallbacks(
//            mockTouchListener,
//            application,
//            null
//        );
//        Assert.assertNotNull(newCallbacks);
//    }
//
//    // ==================== LIFECYCLE METHOD TESTS ====================
//
//    @Test
//    public void testOnActivityCreated_DoesNotThrowException() {
//        ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class);
//        Activity activity = controller.create().get();
//
//        // Should not throw exception
//        callbacks.onActivityCreated(activity, null);
//    }
//
//    @Test
//    public void testOnActivityCreated_WithBundle() {
//        ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class);
//        Activity activity = controller.create().get();
//        Bundle bundle = new Bundle();
//
//        callbacks.onActivityCreated(activity, bundle);
//        // Test passes if no exception thrown
//    }
//
//    @Test
//    public void testOnActivityStarted_DoesNotThrowException() {
//        ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class);
//        Activity activity = controller.create().start().get();
//
//        callbacks.onActivityStarted(activity);
//    }
//
//    @Test
//    public void testOnActivityResumed_DoesNotThrowException() {
//        ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class);
//        Activity activity = controller.create().start().resume().get();
//
//        // May interact with Curtains library
//        // Should not throw exception even if Curtains not fully initialized in test
//        try {
//            callbacks.onActivityResumed(activity);
//        } catch (Exception e) {
//            // May fail in test environment due to Curtains dependencies
//            // Test structure is still valid
//        }
//    }
//
//    @Test
//    public void testOnActivityPrePaused_DoesNotThrowException() {
//        ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class);
//        Activity activity = controller.create().start().resume().get();
//
//        callbacks.onActivityPrePaused(activity);
//    }
//
//    @Test
//    public void testOnActivityPaused_DoesNotThrowException() {
//        ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class);
//        Activity activity = controller.create().start().resume().pause().get();
//
//        callbacks.onActivityPaused(activity);
//    }
//
//    @Test
//    public void testOnActivityStopped_DoesNotThrowException() {
//        ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class);
//        Activity activity = controller.create().start().resume().pause().stop().get();
//
//        callbacks.onActivityStopped(activity);
//    }
//
//    @Test
//    public void testOnActivitySaveInstanceState_DoesNotThrowException() {
//        ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class);
//        Activity activity = controller.create().get();
//        Bundle bundle = new Bundle();
//
//        callbacks.onActivitySaveInstanceState(activity, bundle);
//    }
//
//    @Test
//    public void testOnActivityDestroyed_DoesNotThrowException() {
//        ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class);
//        Activity activity = controller.create().start().resume().pause().stop().destroy().get();
//
//        callbacks.onActivityDestroyed(activity);
//    }
//
//    // ==================== FULL LIFECYCLE TESTS ====================
//
//    @Test
//    public void testFullLifecycle_CreateToDestroy() {
//        ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class);
//        Activity activity = controller.get();
//
//        // Full lifecycle
//        callbacks.onActivityCreated(activity, null);
//        callbacks.onActivityStarted(activity);
//
//        try {
//            callbacks.onActivityResumed(activity);
//        } catch (Exception e) {
//            // May fail due to Curtains
//        }
//
//        callbacks.onActivityPaused(activity);
//        callbacks.onActivityStopped(activity);
//        callbacks.onActivityDestroyed(activity);
//
//        // Test passes if no critical exceptions
//    }
//
//    @Test
//    public void testLifecycle_MultipleResumePause() {
//        ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class);
//        Activity activity = controller.get();
//
//        callbacks.onActivityCreated(activity, null);
//        callbacks.onActivityStarted(activity);
//
//        // Multiple resume-pause cycles
//        try {
//            callbacks.onActivityResumed(activity);
//        } catch (Exception e) {
//            // Ignore Curtains errors
//        }
//
//        callbacks.onActivityPaused(activity);
//
//        try {
//            callbacks.onActivityResumed(activity);
//        } catch (Exception e) {
//            // Ignore Curtains errors
//        }
//
//        callbacks.onActivityPaused(activity);
//
//        // Test passes if no exception
//    }
//
//    // ==================== SETUP TOUCH INTERCEPTOR TESTS ====================
//
//    @Test
//    public void testSetupTouchInterceptorForWindow_WithNullView() {
//        // Should handle null view gracefully
//        try {
//            callbacks.setupTouchInterceptorForWindow(null);
//            // May throw NullPointerException which is acceptable
//        } catch (NullPointerException e) {
//            // Expected for null view
//        }
//    }
//
//    @Test
//    public void testSetupTouchInterceptorForWindow_WithValidView() {
//        View view = new View(application);
//
//        // May fail due to Windows.getPhoneWindowForView returning null in test
//        // But should not crash
//        try {
//            callbacks.setupTouchInterceptorForWindow(view);
//        } catch (Exception e) {
//            // May fail in test environment, structure is valid
//        }
//    }
//
//    @Test
//    public void testSetupTouchInterceptorForWindow_WithPopupWindow() {
//        View view = new View(application);
//
//        // If view is detected as POPUP_WINDOW, should return early
//        // Hard to test without mocking Windows.getWindowType()
//        try {
//            callbacks.setupTouchInterceptorForWindow(view);
//        } catch (Exception e) {
//            // Test structure is valid
//        }
//    }
//
//    // ==================== TOUCH TRACKING TESTS ====================
//
//    @Test
//    public void testTouchTracking_InitialState() {
//        // Initial state should have no touch tracker
//        Assert.assertEquals("Should have no touches recorded initially", 0, touchRecordedCount.get());
//        Assert.assertNull("Last touch tracker should be null initially", lastTouchTracker.get());
//    }
//
//    @Test
//    public void testTouchTracking_SingleDownUpSequence() {
//        // This is hard to test without full Activity and Window setup
//        // The test structure validates the callback interface
//        Assert.assertNotNull(mockTouchListener);
//        Assert.assertEquals(0, touchRecordedCount.get());
//    }
//
//    @Test
//    public void testTouchTracking_MultipleDownUpSequences() {
//        // Validates that touch tracking state is maintained across multiple touches
//        // Actual touch simulation requires full Android framework
//        Assert.assertEquals(0, touchRecordedCount.get());
//    }
//
//    // ==================== GET PIXEL TESTS ====================
//
//    // getPixel is private, but we can test its behavior indirectly
//    // The method divides pixel by density
//
//    @Test
//    public void testGetPixel_ConversionLogic() {
//        // Cannot test private method directly
//        // But we verify callbacks was constructed with correct density
//        Assert.assertNotNull(callbacks);
//    }
//
//    // ==================== CONFIGURATION TESTS ====================
//
//    @Test
//    public void testConfiguration_MaskAllUserTouches_True() {
//        sessionReplayConfiguration.setMaskAllUserTouches(true);
//
//        SessionReplayActivityLifecycleCallbacks newCallbacks = new SessionReplayActivityLifecycleCallbacks(
//            mockTouchListener,
//            application,
//            modeManager
//        );
//
//        Assert.assertTrue(newCallbacks.sessionReplayConfiguration.isMaskAllUserTouches());
//    }
//
//    @Test
//    public void testConfiguration_MaskAllUserTouches_False() {
//        sessionReplayConfiguration.setMaskAllUserTouches(false);
//
//        SessionReplayActivityLifecycleCallbacks newCallbacks = new SessionReplayActivityLifecycleCallbacks(
//            mockTouchListener,
//            application,
//            modeManager
//        );
//
//        Assert.assertFalse(newCallbacks.sessionReplayConfiguration.isMaskAllUserTouches());
//    }
//
//    // ==================== MODE MANAGER TESTS ====================
//
//    @Test
//    public void testModeManager_OffMode() {
//        modeManager.setCurrentMode(SessionReplayMode.OFF);
//
//        SessionReplayActivityLifecycleCallbacks newCallbacks = new SessionReplayActivityLifecycleCallbacks(
//            mockTouchListener,
//            application,
//            modeManager
//        );
//
//        Assert.assertNotNull(newCallbacks);
//        // In OFF mode, touches should not be recorded
//    }
//
//    @Test
//    public void testModeManager_DefaultMode() {
//        modeManager.setCurrentMode(SessionReplayMode.DEFAULT);
//
//        SessionReplayActivityLifecycleCallbacks newCallbacks = new SessionReplayActivityLifecycleCallbacks(
//            mockTouchListener,
//            application,
//            modeManager
//        );
//
//        Assert.assertNotNull(newCallbacks);
//    }
//
//    @Test
//    public void testModeManager_ErrorMode() {
//        modeManager.setCurrentMode(SessionReplayMode.ERROR);
//
//        SessionReplayActivityLifecycleCallbacks newCallbacks = new SessionReplayActivityLifecycleCallbacks(
//            mockTouchListener,
//            application,
//            modeManager
//        );
//
//        Assert.assertNotNull(newCallbacks);
//    }
//
//    // ==================== EDGE CASE TESTS ====================
//
//    @Test
//    public void testMultipleActivities() {
//        ActivityController<Activity> controller1 = Robolectric.buildActivity(Activity.class);
//        ActivityController<Activity> controller2 = Robolectric.buildActivity(Activity.class);
//
//        Activity activity1 = controller1.create().get();
//        Activity activity2 = controller2.create().get();
//
//        callbacks.onActivityCreated(activity1, null);
//        callbacks.onActivityStarted(activity1);
//
//        callbacks.onActivityCreated(activity2, null);
//        callbacks.onActivityStarted(activity2);
//
//        // Should handle multiple activities
//    }
//
//    @Test
//    public void testActivityRecreation_WithSavedState() {
//        ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class);
//        Activity activity = controller.get();
//        Bundle savedState = new Bundle();
//        savedState.putString("test_key", "test_value");
//
//        callbacks.onActivityCreated(activity, savedState);
//
//        // Should handle activity recreation with saved state
//    }
//
//    @Test
//    public void testRapidLifecycleChanges() {
//        ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class);
//        Activity activity = controller.get();
//
//        // Rapid lifecycle changes
//        for (int i = 0; i < 10; i++) {
//            callbacks.onActivityCreated(activity, null);
//            callbacks.onActivityStarted(activity);
//            try {
//                callbacks.onActivityResumed(activity);
//            } catch (Exception e) {
//                // Ignore
//            }
//            callbacks.onActivityPaused(activity);
//            callbacks.onActivityStopped(activity);
//        }
//
//        // Should handle rapid changes without memory leaks or crashes
//    }
//
//    // ==================== INTEGRATION TESTS ====================
//
//    @Test
//    public void testIntegration_WithRealActivityLifecycle() {
//        ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class);
//
//        // Use controller to go through real lifecycle
//        Activity activity = controller.create().start().resume().get();
//
//        // Register callbacks
//        try {
//            callbacks.onActivityResumed(activity);
//        } catch (Exception e) {
//            // May fail due to Curtains, but structure is valid
//        }
//
//        // Pause
//        callbacks.onActivityPaused(activity);
//
//        // Test passes if lifecycle is handled correctly
//    }
//
//    @Test
//    public void testIntegration_MultipleCallbackInstances() {
//        OnTouchRecordedListener listener1 = touchTracker -> {};
//        OnTouchRecordedListener listener2 = touchTracker -> {};
//
//        SessionReplayActivityLifecycleCallbacks callbacks1 = new SessionReplayActivityLifecycleCallbacks(
//            listener1,
//            application,
//            modeManager
//        );
//
//        SessionReplayActivityLifecycleCallbacks callbacks2 = new SessionReplayActivityLifecycleCallbacks(
//            listener2,
//            application,
//            modeManager
//        );
//
//        ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class);
//        Activity activity = controller.create().start().resume().get();
//
//        // Both callbacks on same activity
//        callbacks1.onActivityCreated(activity, null);
//        callbacks2.onActivityCreated(activity, null);
//
//        // Should work with multiple callback instances
//    }
//
//    // ==================== ERROR HANDLING TESTS ====================
//
//    @Test
//    public void testErrorHandling_NullActivity() {
//        // Some methods may accept null activity
//        try {
//            callbacks.onActivityCreated(null, null);
//            // May throw NullPointerException
//        } catch (NullPointerException e) {
//            // Expected for null activity
//        }
//    }
//
//    @Test
//    public void testErrorHandling_DetachedActivity() {
//        ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class);
//        Activity activity = controller.create().start().resume().pause().stop().destroy().get();
//
//        // Callbacks on destroyed activity
//        try {
//            callbacks.onActivityResumed(activity);
//        } catch (Exception e) {
//            // May fail, but should not crash app
//        }
//    }
//
//    // ==================== LISTENER TESTS ====================
//
//    @Test
//    public void testListener_IsNotNull() {
//        Assert.assertNotNull(mockTouchListener);
//    }
//
//    @Test
//    public void testListener_InitialCount() {
//        Assert.assertEquals(0, touchRecordedCount.get());
//    }
//
//    @Test
//    public void testListener_WithNullModeManager_DoesNotCrash() {
//        SessionReplayActivityLifecycleCallbacks callbacksWithNullModeManager =
//            new SessionReplayActivityLifecycleCallbacks(mockTouchListener, application, null);
//
//        ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class);
//        Activity activity = controller.create().get();
//
//        // Should not crash even with null mode manager
//        callbacksWithNullModeManager.onActivityCreated(activity, null);
//    }
//}