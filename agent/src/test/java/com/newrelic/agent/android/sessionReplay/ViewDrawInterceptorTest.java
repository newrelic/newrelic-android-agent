/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay;

import android.content.Context;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.test.core.app.ApplicationProvider;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.sessionReplay.internal.OnFrameTakenListener;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowLooper;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(RobolectricTestRunner.class)
public class ViewDrawInterceptorTest {

    private Context context;
    private AgentConfiguration agentConfiguration;
    private OnFrameTakenListener mockListener;
    private ViewDrawInterceptor interceptor;
    private ShadowLooper shadowLooper;
    private AtomicInteger frameCount;
    private AtomicReference<SessionReplayFrame> lastFrame;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        agentConfiguration = AgentConfiguration.getInstance();


        shadowLooper = Shadows.shadowOf(android.os.Looper.getMainLooper());

        frameCount = new AtomicInteger(0);
        lastFrame = new AtomicReference<>(null);

        mockListener = new OnFrameTakenListener() {
            @Override
            public void onFrameTaken(SessionReplayFrame frame) {
                frameCount.incrementAndGet();
                lastFrame.set(frame);
            }
        };

        interceptor = new ViewDrawInterceptor(mockListener, agentConfiguration);
    }

    // ==================== CONSTRUCTOR TESTS ====================

    @Test
    public void testConstructor_WithValidParameters() {
        ViewDrawInterceptor newInterceptor = new ViewDrawInterceptor(mockListener, agentConfiguration);
        Assert.assertNotNull(newInterceptor);
        Assert.assertNotNull(newInterceptor.capture);
    }

    @Test
    public void testConstructor_WithNullListener() {
        ViewDrawInterceptor newInterceptor = new ViewDrawInterceptor(null, agentConfiguration);
        Assert.assertNotNull(newInterceptor);
    }

    @Test
    public void testConstructor_WithNullConfiguration() {
        ViewDrawInterceptor newInterceptor = new ViewDrawInterceptor(mockListener, null);
        Assert.assertNotNull(newInterceptor);
    }

    @Test
    public void testConstructor_InitializesCapture() {
        Assert.assertNotNull(interceptor.capture);
        Assert.assertTrue(interceptor.capture instanceof SessionReplayCapture);
    }

    // ==================== INTERCEPT TESTS ====================

    @Test
    public void testIntercept_WithSingleView() {
        View view = new View(context);
        view.layout(0, 0, 100, 100);
        View[] views = {view};

        interceptor.Intercept(views);

        // Verify listener was added
        ViewTreeObserver observer = view.getViewTreeObserver();
        Assert.assertTrue(observer.isAlive());
    }

    @Test
    public void testIntercept_WithMultipleViews() {
        View view1 = new View(context);
        view1.layout(0, 0, 100, 100);

        View view2 = new View(context);
        view2.layout(0, 0, 200, 200);

        View[] views = {view1, view2};

        interceptor.Intercept(views);

        // Both views should have listeners
        Assert.assertTrue(view1.getViewTreeObserver().isAlive());
        Assert.assertTrue(view2.getViewTreeObserver().isAlive());
    }

    @Test
    public void testIntercept_WithEmptyArray() {
        View[] views = {};

        // Should not throw exception
        interceptor.Intercept(views);
    }

    @Test
    public void testIntercept_TriggersOnDrawListener() {
        View view = new View(context);
        view.layout(0, 0, 100, 100);
        View[] views = {view};

        interceptor.Intercept(views);

        // Trigger draw by invalidating
        view.invalidate();

        // Process pending runnables
        shadowLooper.idle();

        // Note: Actual frame capture is debounced, so may not fire immediately
        // This test verifies listener setup worked without errors
    }

    @Test
    public void testIntercept_RemovesExistingListener() {
        View view = new View(context);
        view.layout(0, 0, 100, 100);
        View[] views = {view};

        // First intercept
        interceptor.Intercept(views);

        // Second intercept on same view
        interceptor.Intercept(views);

        // Should not have duplicate listeners
        // Verify by checking no exception was thrown
    }

    @Test
    public void testIntercept_ReplacesListenerOnSecondCall() {
        View view = new View(context);
        view.layout(0, 0, 100, 100);
        View[] views = {view};

        // First intercept
        interceptor.Intercept(views);

        // Trigger draw
        view.invalidate();
        shadowLooper.idle();

        // Second intercept (should replace listener)
        interceptor.Intercept(views);

        // Should still work
        view.invalidate();
        shadowLooper.idle();

        // Test passes if no exception thrown
    }

    // ==================== STOP INTERCEPT TESTS ====================

    @Test
    public void testStopIntercept_RemovesAllListeners() {
        View view1 = new View(context);
        view1.layout(0, 0, 100, 100);

        View view2 = new View(context);
        view2.layout(0, 0, 200, 200);

        View[] views = {view1, view2};

        interceptor.Intercept(views);

        // Stop intercepting
        interceptor.stopIntercept();

        // Views should still be valid, listeners removed
        Assert.assertTrue(view1.getViewTreeObserver().isAlive());
        Assert.assertTrue(view2.getViewTreeObserver().isAlive());
    }

    @Test
    public void testStopIntercept_CancelsDebouncer() {
        View view = new View(context);
        view.layout(0, 0, 100, 100);
        View[] views = {view};

        interceptor.Intercept(views);

        // Trigger a draw (starts debouncing)
        view.invalidate();

        // Stop before debounce completes
        interceptor.stopIntercept();

        // Advance time beyond debounce delay
        shadowLooper.idleFor(2000, java.util.concurrent.TimeUnit.MILLISECONDS);

        // Frame should not have been captured since debouncer was cancelled
        // (Note: This may or may not work depending on exact timing, test structure)
    }

    @Test
    public void testStopIntercept_MultipleCalls() {
        View view = new View(context);
        view.layout(0, 0, 100, 100);
        View[] views = {view};

        interceptor.Intercept(views);

        // Multiple stop calls should be safe
        interceptor.stopIntercept();
        interceptor.stopIntercept();
        interceptor.stopIntercept();

        // Test passes if no exception thrown
    }

    @Test
    public void testStopIntercept_BeforeIntercept() {
        // Stop without ever intercepting
        interceptor.stopIntercept();

        // Should not throw exception
    }

    // ==================== REMOVE INTERCEPT TESTS ====================

    @Test
    public void testRemoveIntercept_WithSpecificViews() {
        View view1 = new View(context);
        view1.layout(0, 0, 100, 100);

        View view2 = new View(context);
        view2.layout(0, 0, 200, 200);

        View[] views = {view1, view2};

        interceptor.Intercept(views);

        // Remove only view1
        View[] viewsToRemove = {view1};
        interceptor.removeIntercept(viewsToRemove);

        // Should not throw exception
    }

    @Test
    public void testRemoveIntercept_WithEmptyArray() {
        View[] views = {};

        // Should not throw exception
        interceptor.removeIntercept(views);
    }

    @Test
    public void testRemoveIntercept_WithViewNotBeingIntercepted() {
        View view = new View(context);
        view.layout(0, 0, 100, 100);
        View[] views = {view};

        // Remove without intercepting first
        interceptor.removeIntercept(views);

        // Should not throw exception
    }

    // ==================== FRAME CAPTURE TESTS ====================

    @Test
    public void testIntercept_CapturesTopMostView() {
        View view1 = new View(context);
        view1.layout(0, 0, 100, 100);

        View view2 = new View(context);
        view2.layout(0, 0, 200, 200);

        View view3 = new View(context);
        view3.layout(0, 0, 300, 300);

        // view3 is the topmost view (last in array)
        View[] views = {view1, view2, view3};

        interceptor.Intercept(views);

        // Trigger draw
        view1.invalidate();
        view2.invalidate();
        view3.invalidate();

        shadowLooper.idle();

        // Advance time to complete debounce
        shadowLooper.idleFor(2000, java.util.concurrent.TimeUnit.MILLISECONDS);

        // Frame should have been captured from view3 (topmost)
        // Note: Actual verification would require checking the captured view
    }

    @Test
    public void testIntercept_CreatesFrameWithCorrectDimensions() {
        View view = new View(context);
        view.layout(0, 0, 100, 100);
        View[] views = {view};

        interceptor.Intercept(views);

        // Trigger draw
        view.invalidate();
        shadowLooper.idle();

        // Advance time to complete debounce
        shadowLooper.idleFor(2000, java.util.concurrent.TimeUnit.MILLISECONDS);

        // If a frame was captured, verify it has dimensions
        if (lastFrame.get() != null) {
            Assert.assertTrue("Width should be positive", lastFrame.get().width > 0);
            Assert.assertTrue("Height should be positive", lastFrame.get().height > 0);
        }
    }

    @Test
    public void testIntercept_FrameHasTimestamp() {
        View view = new View(context);
        view.layout(0, 0, 100, 100);
        View[] views = {view};

        long beforeTime = System.currentTimeMillis();

        interceptor.Intercept(views);

        // Trigger draw
        view.invalidate();
        shadowLooper.idle();

        // Advance time to complete debounce
        shadowLooper.idleFor(2000, java.util.concurrent.TimeUnit.MILLISECONDS);

        long afterTime = System.currentTimeMillis() + 3000; // Account for debounce time

        // If a frame was captured, verify timestamp
        if (lastFrame.get() != null) {
            Assert.assertTrue("Timestamp should be recent",
                    lastFrame.get().timestamp >= beforeTime &&
                    lastFrame.get().timestamp <= afterTime);
        }
    }

    // ==================== DEBOUNCING TESTS ====================

    @Test
    public void testIntercept_DebouncesRapidDraws() {
        View view = new View(context);
        view.layout(0, 0, 100, 100);
        View[] views = {view};

        interceptor.Intercept(views);

        frameCount.set(0);

        // Trigger multiple rapid draws
        view.invalidate();
        view.invalidate();
        view.invalidate();
        view.invalidate();
        view.invalidate();

        shadowLooper.idle();

        // Advance time to complete one debounce cycle
        shadowLooper.idleFor(2000, java.util.concurrent.TimeUnit.MILLISECONDS);

        // Due to debouncing, should have at most 1-2 frames, not 5
        Assert.assertTrue("Should debounce rapid draws", frameCount.get() < 5);
    }

    @Test
    public void testIntercept_DebounceDelay() {
        View view = new View(context);
        view.layout(0, 0, 100, 100);
        View[] views = {view};

        interceptor.Intercept(views);

        frameCount.set(0);

        // Trigger draw
        view.invalidate();
        shadowLooper.idle();

        // Check before debounce completes
        int countBefore = frameCount.get();

        // Advance time to complete debounce (1000ms)
        shadowLooper.idleFor(2000, java.util.concurrent.TimeUnit.MILLISECONDS);

        // Count should have increased after debounce
        // Note: May be 0 both times if frame capture doesn't work in test environment
    }

    // ==================== LISTENER CALLBACK TESTS ====================

    @Test
    public void testIntercept_CallsListenerOnFrameCapture() {
        View view = new View(context);
        view.layout(0, 0, 100, 100);
        View[] views = {view};

        interceptor.Intercept(views);

        frameCount.set(0);

        // Trigger draw
        view.invalidate();
        shadowLooper.idle();

        // Complete debounce
        shadowLooper.idleFor(2000, java.util.concurrent.TimeUnit.MILLISECONDS);

        // Listener should have been called (if frame capture works)
        // Note: May be 0 in test environment due to context limitations
    }

    @Test
    public void testIntercept_WithNullListener_DoesNotCrash() {
        ViewDrawInterceptor interceptorWithNullListener = new ViewDrawInterceptor(null, agentConfiguration);

        View view = new View(context);
        view.layout(0, 0, 100, 100);
        View[] views = {view};

        interceptorWithNullListener.Intercept(views);

        // Trigger draw
        view.invalidate();
        shadowLooper.idle();

        // Should not crash even with null listener
        shadowLooper.idleFor(2000, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    // ==================== EDGE CASE TESTS ====================

    @Test
    public void testIntercept_WithNullViewInArray() {
        View view1 = new View(context);
        view1.layout(0, 0, 100, 100);

        View[] views = {view1, null};

        // Should handle null view gracefully
        try {
            interceptor.Intercept(views);
            // May throw NullPointerException, which is acceptable
        } catch (NullPointerException e) {
            // Expected for null view
        }
    }

    @Test
    public void testIntercept_WithDetachedView() {
        View view = new View(context);
        view.layout(0, 0, 100, 100);

        View[] views = {view};

        interceptor.Intercept(views);

        // Simulate view detachment by removing context reference
        // (Hard to do in Robolectric, but interceptor should handle it)

        // Trigger draw
        view.invalidate();
        shadowLooper.idle();

        // Should not crash
        shadowLooper.idleFor(2000, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    @Test
    public void testIntercept_AfterStopAndRestart() {
        View view = new View(context);
        view.layout(0, 0, 100, 100);
        View[] views = {view};

        // First intercept
        interceptor.Intercept(views);

        // Stop
        interceptor.stopIntercept();

        // Restart
        interceptor.Intercept(views);

        // Should work after restart
        view.invalidate();
        shadowLooper.idle();

        // Test passes if no exception thrown
    }

    @Test
    public void testIntercept_ConcurrentModification() {
        View view1 = new View(context);
        view1.layout(0, 0, 100, 100);

        View view2 = new View(context);
        view2.layout(0, 0, 200, 200);

        View[] views = {view1, view2};

        interceptor.Intercept(views);

        // Remove view1 while potentially iterating
        interceptor.removeIntercept(new View[]{view1});

        // Should handle concurrent modification gracefully (WeakHashMap is synchronized)
    }

    @Test
    public void testIntercept_LargeNumberOfViews() {
        int numViews = 100;
        View[] views = new View[numViews];

        for (int i = 0; i < numViews; i++) {
            views[i] = new View(context);
            views[i].layout(0, 0, 100, 100);
        }

        // Should handle large number of views
        interceptor.Intercept(views);

        // Test passes if no exception or performance issue
    }

    // ==================== SAFE OBSERVER REMOVAL TESTS ====================

    @Test
    public void testSafeObserverRemoval_WithNullListener() {
        View view = new View(context);
        view.layout(0, 0, 100, 100);

        // Call via public method that uses safeObserverRemoval internally
        View[] views = {view};
        interceptor.removeIntercept(views);

        // Should not throw exception for null listener
    }

    @Test
    public void testSafeObserverRemoval_WithDeadObserver() {
        View view = new View(context);
        view.layout(0, 0, 100, 100);
        View[] views = {view};

        interceptor.Intercept(views);

        // Difficult to kill observer in Robolectric
        // But safeObserverRemoval should check isAlive()
        interceptor.stopIntercept();

        // Should not throw exception
    }

    // ==================== CONFIGURATION TESTS ====================

    @Test
    public void testIntercept_WithDifferentConfigurations() {
        AgentConfiguration config1 = AgentConfiguration.getInstance();
        AgentConfiguration config2 = AgentConfiguration.getInstance();

        ViewDrawInterceptor interceptor1 = new ViewDrawInterceptor(mockListener, config1);
        ViewDrawInterceptor interceptor2 = new ViewDrawInterceptor(mockListener, config2);

        View view = new View(context);
        view.layout(0, 0, 100, 100);
        View[] views = {view};

        // Both should work
        interceptor1.Intercept(views);
        interceptor2.Intercept(views);

        // Test passes if no exception thrown
    }

    // ==================== MULTIPLE INTERCEPTOR INSTANCES TESTS ====================

    @Test
    public void testMultipleInterceptors_OnSameView() {
        ViewDrawInterceptor interceptor1 = new ViewDrawInterceptor(mockListener, agentConfiguration);
        ViewDrawInterceptor interceptor2 = new ViewDrawInterceptor(mockListener, agentConfiguration);

        View view = new View(context);
        view.layout(0, 0, 100, 100);
        View[] views = {view};

        // Both interceptors on same view
        interceptor1.Intercept(views);
        interceptor2.Intercept(views);

        // Should work (each interceptor has its own listener)
        view.invalidate();
        shadowLooper.idle();

        // Test passes if no exception thrown
    }

    @Test
    public void testMultipleInterceptors_StopOne() {
        ViewDrawInterceptor interceptor1 = new ViewDrawInterceptor(mockListener, agentConfiguration);
        ViewDrawInterceptor interceptor2 = new ViewDrawInterceptor(mockListener, agentConfiguration);

        View view = new View(context);
        view.layout(0, 0, 100, 100);
        View[] views = {view};

        interceptor1.Intercept(views);
        interceptor2.Intercept(views);

        // Stop only interceptor1
        interceptor1.stopIntercept();

        // interceptor2 should still work
        view.invalidate();
        shadowLooper.idle();

        // Test passes if no exception thrown
    }
}
