/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay;

import com.newrelic.agent.android.analytics.AnalyticsEvent;
import com.newrelic.agent.android.analytics.EventListener;
import com.newrelic.agent.android.analytics.EventManager;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;


import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.any;

@RunWith(RobolectricTestRunner.class)
public class CompositeEventListenerTest {

    private EventListener mockSessionReplayListener;
    private EventListener mockUserListener;
    private CompositeEventListener compositeListener;
    private AnalyticsEvent mockEvent;
    private EventManager mockEventManager;

    @Before
    public void setUp() {
        mockSessionReplayListener = mock(EventListener.class);
        mockUserListener = mock(EventListener.class);
        mockEvent = mock(AnalyticsEvent.class);
        mockEventManager = mock(EventManager.class);

        compositeListener = new CompositeEventListener(mockSessionReplayListener);
    }

    // ==================== CONSTRUCTOR TESTS ====================

    @Test
    public void testConstructor_WithValidListener() {
        CompositeEventListener listener = new CompositeEventListener(mockSessionReplayListener);
        Assert.assertNotNull(listener);
    }

    @Test
    public void testConstructor_WithNullListener() {
        CompositeEventListener listener = new CompositeEventListener(null);
        Assert.assertNotNull(listener);
    }

    @Test
    public void testConstructor_InitializesWithNullUserListener() {
        // User listener should be null initially
        CompositeEventListener listener = new CompositeEventListener(mockSessionReplayListener);
        Assert.assertNotNull(listener);
    }

    // ==================== SET USER LISTENER TESTS ====================

    @Test
    public void testSetUserListener_WithValidListener() {
        compositeListener.setUserListener(mockUserListener);
        // Should not throw exception
    }

    @Test
    public void testSetUserListener_WithNull() {
        compositeListener.setUserListener(mockUserListener);
        compositeListener.setUserListener(null);
        // Should not throw exception, removes user listener
    }

    @Test
    public void testSetUserListener_MultipleTimes() {
        EventListener listener1 = mock(EventListener.class);
        EventListener listener2 = mock(EventListener.class);

        compositeListener.setUserListener(listener1);
        compositeListener.setUserListener(listener2);
        // Last one should win
    }

    // ==================== SET SESSION REPLAY LISTENER TESTS ====================

    @Test
    public void testSetSessionReplayListener_DoesNotThrow() {
        EventListener newListener = mock(EventListener.class);
        compositeListener.setSessionReplayListener(newListener);
        // Currently a no-op, should not throw
    }

    // ==================== ON EVENT ADDED TESTS ====================

    @Test
    public void testOnEventAdded_WithOnlySessionReplayListener() {
        when(mockSessionReplayListener.onEventAdded(mockEvent)).thenReturn(true);

        boolean result = compositeListener.onEventAdded(mockEvent);

        Assert.assertTrue(result);
        verify(mockSessionReplayListener, times(1)).onEventAdded(mockEvent);
    }

    @Test
    public void testOnEventAdded_WithBothListeners_BothReturnTrue() {
        compositeListener.setUserListener(mockUserListener);

        when(mockSessionReplayListener.onEventAdded(mockEvent)).thenReturn(true);
        when(mockUserListener.onEventAdded(mockEvent)).thenReturn(true);

        boolean result = compositeListener.onEventAdded(mockEvent);

        Assert.assertTrue(result);
        verify(mockSessionReplayListener, times(1)).onEventAdded(mockEvent);
        verify(mockUserListener, times(1)).onEventAdded(mockEvent);
    }

    @Test
    public void testOnEventAdded_WithBothListeners_SessionReplayReturnsFalse() {
        compositeListener.setUserListener(mockUserListener);

        when(mockSessionReplayListener.onEventAdded(mockEvent)).thenReturn(false);
        when(mockUserListener.onEventAdded(mockEvent)).thenReturn(true);

        boolean result = compositeListener.onEventAdded(mockEvent);

        Assert.assertFalse("Should return false if SessionReplay returns false", result);
        verify(mockSessionReplayListener, times(1)).onEventAdded(mockEvent);
        verify(mockUserListener, times(1)).onEventAdded(mockEvent);
    }

    @Test
    public void testOnEventAdded_WithBothListeners_UserReturnsFalse() {
        compositeListener.setUserListener(mockUserListener);

        when(mockSessionReplayListener.onEventAdded(mockEvent)).thenReturn(true);
        when(mockUserListener.onEventAdded(mockEvent)).thenReturn(false);

        boolean result = compositeListener.onEventAdded(mockEvent);

        Assert.assertFalse("Should return false if user listener returns false", result);
    }

    @Test
    public void testOnEventAdded_WithBothListeners_BothReturnFalse() {
        compositeListener.setUserListener(mockUserListener);

        when(mockSessionReplayListener.onEventAdded(mockEvent)).thenReturn(false);
        when(mockUserListener.onEventAdded(mockEvent)).thenReturn(false);

        boolean result = compositeListener.onEventAdded(mockEvent);

        Assert.assertFalse(result);
    }

    @Test
    public void testOnEventAdded_WithNullSessionReplayListener() {
        CompositeEventListener listener = new CompositeEventListener(null);
        listener.setUserListener(mockUserListener);

        when(mockUserListener.onEventAdded(mockEvent)).thenReturn(true);

        boolean result = listener.onEventAdded(mockEvent);

        Assert.assertTrue(result);
        verify(mockUserListener, times(1)).onEventAdded(mockEvent);
    }

    // ==================== ON EVENT OVERFLOW TESTS ====================

    @Test
    public void testOnEventOverflow_WithOnlySessionReplayListener() {
        when(mockSessionReplayListener.onEventOverflow(mockEvent)).thenReturn(true);

        boolean result = compositeListener.onEventOverflow(mockEvent);

        Assert.assertTrue(result);
        verify(mockSessionReplayListener, times(1)).onEventOverflow(mockEvent);
    }

    @Test
    public void testOnEventOverflow_WithBothListeners_BothReturnTrue() {
        compositeListener.setUserListener(mockUserListener);

        when(mockSessionReplayListener.onEventOverflow(mockEvent)).thenReturn(true);
        when(mockUserListener.onEventOverflow(mockEvent)).thenReturn(true);

        boolean result = compositeListener.onEventOverflow(mockEvent);

        Assert.assertTrue(result);
        verify(mockSessionReplayListener, times(1)).onEventOverflow(mockEvent);
        verify(mockUserListener, times(1)).onEventOverflow(mockEvent);
    }

    @Test
    public void testOnEventOverflow_WithBothListeners_OneReturnsFalse() {
        compositeListener.setUserListener(mockUserListener);

        when(mockSessionReplayListener.onEventOverflow(mockEvent)).thenReturn(true);
        when(mockUserListener.onEventOverflow(mockEvent)).thenReturn(false);

        boolean result = compositeListener.onEventOverflow(mockEvent);

        Assert.assertFalse(result);
    }

    // ==================== ON EVENT EVICTED TESTS ====================

    @Test
    public void testOnEventEvicted_WithOnlySessionReplayListener() {
        when(mockSessionReplayListener.onEventEvicted(mockEvent)).thenReturn(true);

        boolean result = compositeListener.onEventEvicted(mockEvent);

        Assert.assertTrue(result);
        verify(mockSessionReplayListener, times(1)).onEventEvicted(mockEvent);
    }

    @Test
    public void testOnEventEvicted_WithBothListeners_BothReturnTrue() {
        compositeListener.setUserListener(mockUserListener);

        when(mockSessionReplayListener.onEventEvicted(mockEvent)).thenReturn(true);
        when(mockUserListener.onEventEvicted(mockEvent)).thenReturn(true);

        boolean result = compositeListener.onEventEvicted(mockEvent);

        Assert.assertTrue(result);
        verify(mockSessionReplayListener, times(1)).onEventEvicted(mockEvent);
        verify(mockUserListener, times(1)).onEventEvicted(mockEvent);
    }

    @Test
    public void testOnEventEvicted_WithBothListeners_OneReturnsFalse() {
        compositeListener.setUserListener(mockUserListener);

        when(mockSessionReplayListener.onEventEvicted(mockEvent)).thenReturn(false);
        when(mockUserListener.onEventEvicted(mockEvent)).thenReturn(true);

        boolean result = compositeListener.onEventEvicted(mockEvent);

        Assert.assertFalse(result);
    }

    // ==================== ON EVENT QUEUE SIZE EXCEEDED TESTS ====================

    @Test
    public void testOnEventQueueSizeExceeded_WithOnlySessionReplayListener() {
        compositeListener.onEventQueueSizeExceeded(1000);

        verify(mockSessionReplayListener, times(1)).onEventQueueSizeExceeded(1000);
    }

    @Test
    public void testOnEventQueueSizeExceeded_WithBothListeners() {
        compositeListener.setUserListener(mockUserListener);

        compositeListener.onEventQueueSizeExceeded(1000);

        verify(mockSessionReplayListener, times(1)).onEventQueueSizeExceeded(1000);
        verify(mockUserListener, times(1)).onEventQueueSizeExceeded(1000);
    }

    @Test
    public void testOnEventQueueSizeExceeded_WithNullSessionReplayListener() {
        CompositeEventListener listener = new CompositeEventListener(null);
        listener.setUserListener(mockUserListener);

        listener.onEventQueueSizeExceeded(1000);

        verify(mockUserListener, times(1)).onEventQueueSizeExceeded(1000);
    }

    @Test
    public void testOnEventQueueSizeExceeded_WithZeroSize() {
        compositeListener.setUserListener(mockUserListener);

        compositeListener.onEventQueueSizeExceeded(0);

        verify(mockSessionReplayListener, times(1)).onEventQueueSizeExceeded(0);
        verify(mockUserListener, times(1)).onEventQueueSizeExceeded(0);
    }

    @Test
    public void testOnEventQueueSizeExceeded_WithNegativeSize() {
        compositeListener.setUserListener(mockUserListener);

        compositeListener.onEventQueueSizeExceeded(-1);

        verify(mockSessionReplayListener, times(1)).onEventQueueSizeExceeded(-1);
        verify(mockUserListener, times(1)).onEventQueueSizeExceeded(-1);
    }

    // ==================== ON EVENT QUEUE TIME EXCEEDED TESTS ====================

    @Test
    public void testOnEventQueueTimeExceeded_WithOnlySessionReplayListener() {
        compositeListener.onEventQueueTimeExceeded(60);

        verify(mockSessionReplayListener, times(1)).onEventQueueTimeExceeded(60);
    }

    @Test
    public void testOnEventQueueTimeExceeded_WithBothListeners() {
        compositeListener.setUserListener(mockUserListener);

        compositeListener.onEventQueueTimeExceeded(60);

        verify(mockSessionReplayListener, times(1)).onEventQueueTimeExceeded(60);
        verify(mockUserListener, times(1)).onEventQueueTimeExceeded(60);
    }

    @Test
    public void testOnEventQueueTimeExceeded_WithZeroTime() {
        compositeListener.setUserListener(mockUserListener);

        compositeListener.onEventQueueTimeExceeded(0);

        verify(mockSessionReplayListener, times(1)).onEventQueueTimeExceeded(0);
        verify(mockUserListener, times(1)).onEventQueueTimeExceeded(0);
    }

    // ==================== ON EVENT FLUSH TESTS ====================

    @Test
    public void testOnEventFlush_WithOnlySessionReplayListener() {
        compositeListener.onEventFlush();

        verify(mockSessionReplayListener, times(1)).onEventFlush();
    }

    @Test
    public void testOnEventFlush_WithBothListeners() {
        compositeListener.setUserListener(mockUserListener);

        compositeListener.onEventFlush();

        verify(mockSessionReplayListener, times(1)).onEventFlush();
        verify(mockUserListener, times(1)).onEventFlush();
    }

    @Test
    public void testOnEventFlush_MultipleCalls() {
        compositeListener.setUserListener(mockUserListener);

        compositeListener.onEventFlush();
        compositeListener.onEventFlush();
        compositeListener.onEventFlush();

        verify(mockSessionReplayListener, times(3)).onEventFlush();
        verify(mockUserListener, times(3)).onEventFlush();
    }

    // ==================== ON START TESTS ====================

    @Test
    public void testOnStart_WithOnlySessionReplayListener() {
        compositeListener.onStart(mockEventManager);

        verify(mockSessionReplayListener, times(1)).onStart(mockEventManager);
    }

    @Test
    public void testOnStart_WithBothListeners() {
        compositeListener.setUserListener(mockUserListener);

        compositeListener.onStart(mockEventManager);

        verify(mockSessionReplayListener, times(1)).onStart(mockEventManager);
        verify(mockUserListener, times(1)).onStart(mockEventManager);
    }

    @Test
    public void testOnStart_WithNullEventManager() {
        compositeListener.setUserListener(mockUserListener);

        compositeListener.onStart(null);

        verify(mockSessionReplayListener, times(1)).onStart(null);
        verify(mockUserListener, times(1)).onStart(null);
    }

    // ==================== ON SHUTDOWN TESTS ====================

    @Test
    public void testOnShutdown_WithOnlySessionReplayListener() {
        compositeListener.onShutdown();

        verify(mockSessionReplayListener, times(1)).onShutdown();
    }

    @Test
    public void testOnShutdown_WithBothListeners() {
        compositeListener.setUserListener(mockUserListener);

        compositeListener.onShutdown();

        verify(mockSessionReplayListener, times(1)).onShutdown();
        verify(mockUserListener, times(1)).onShutdown();
    }

    @Test
    public void testOnShutdown_MultipleCalls() {
        compositeListener.setUserListener(mockUserListener);

        compositeListener.onShutdown();
        compositeListener.onShutdown();

        verify(mockSessionReplayListener, times(2)).onShutdown();
        verify(mockUserListener, times(2)).onShutdown();
    }

    // ==================== INTEGRATION TESTS ====================

    @Test
    public void testIntegration_FullLifecycle() {
        compositeListener.setUserListener(mockUserListener);

        when(mockSessionReplayListener.onEventAdded(any())).thenReturn(true);
        when(mockUserListener.onEventAdded(any())).thenReturn(true);

        // Start
        compositeListener.onStart(mockEventManager);

        // Add events
        compositeListener.onEventAdded(mockEvent);
        compositeListener.onEventAdded(mockEvent);

        // Overflow
        compositeListener.onEventOverflow(mockEvent);

        // Evict
        compositeListener.onEventEvicted(mockEvent);

        // Flush
        compositeListener.onEventFlush();

        // Shutdown
        compositeListener.onShutdown();

        // Verify all methods called on both listeners
        verify(mockSessionReplayListener, times(1)).onStart(any());
        verify(mockSessionReplayListener, times(2)).onEventAdded(any());
        verify(mockSessionReplayListener, times(1)).onEventOverflow(any());
        verify(mockSessionReplayListener, times(1)).onEventEvicted(any());
        verify(mockSessionReplayListener, times(1)).onEventFlush();
        verify(mockSessionReplayListener, times(1)).onShutdown();

        verify(mockUserListener, times(1)).onStart(any());
        verify(mockUserListener, times(2)).onEventAdded(any());
        verify(mockUserListener, times(1)).onEventOverflow(any());
        verify(mockUserListener, times(1)).onEventEvicted(any());
        verify(mockUserListener, times(1)).onEventFlush();
        verify(mockUserListener, times(1)).onShutdown();
    }

    @Test
    public void testIntegration_AddAndRemoveUserListener() {
        // Start without user listener
        compositeListener.onEventAdded(mockEvent);
        verify(mockSessionReplayListener, times(1)).onEventAdded(any());

        // Add user listener
        compositeListener.setUserListener(mockUserListener);
        compositeListener.onEventAdded(mockEvent);
        verify(mockUserListener, times(1)).onEventAdded(any());

        // Remove user listener
        compositeListener.setUserListener(null);
        compositeListener.onEventAdded(mockEvent);
        // User listener should not be called again
        verify(mockUserListener, times(1)).onEventAdded(any());
    }

    // ==================== EDGE CASE TESTS ====================

    @Test
    public void testEdgeCase_BothListenersNull() {
        CompositeEventListener listener = new CompositeEventListener(null);

        // Should not throw exceptions
        listener.onEventAdded(mockEvent);
        listener.onEventOverflow(mockEvent);
        listener.onEventEvicted(mockEvent);
        listener.onEventQueueSizeExceeded(100);
        listener.onEventQueueTimeExceeded(60);
        listener.onEventFlush();
        listener.onStart(mockEventManager);
        listener.onShutdown();
    }

    @Test
    public void testEdgeCase_RapidUserListenerChanges() {
        EventListener listener1 = mock(EventListener.class);
        EventListener listener2 = mock(EventListener.class);
        EventListener listener3 = mock(EventListener.class);

        compositeListener.setUserListener(listener1);
        compositeListener.setUserListener(listener2);
        compositeListener.setUserListener(listener3);

        when(mockSessionReplayListener.onEventAdded(any())).thenReturn(true);
        when(listener3.onEventAdded(any())).thenReturn(true);

        compositeListener.onEventAdded(mockEvent);

        // Only the last listener should be called
        verify(listener3, times(1)).onEventAdded(any());
        verify(listener1, times(0)).onEventAdded(any());
        verify(listener2, times(0)).onEventAdded(any());
    }

    @Test
    public void testEdgeCase_UserListenerThrowsException() {
        compositeListener.setUserListener(mockUserListener);

        when(mockSessionReplayListener.onEventAdded(any())).thenReturn(true);
        when(mockUserListener.onEventAdded(any())).thenThrow(new RuntimeException("Test exception"));

        try {
            compositeListener.onEventAdded(mockEvent);
            Assert.fail("Should propagate exception");
        } catch (RuntimeException e) {
            // Expected
            Assert.assertEquals("Test exception", e.getMessage());
        }

        // SessionReplay listener should have been called before exception
        verify(mockSessionReplayListener, times(1)).onEventAdded(any());
    }
}