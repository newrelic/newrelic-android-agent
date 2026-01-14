/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowLooper;

import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
public class DebouncerTest {

    private ShadowLooper shadowLooper;

    @Before
    public void setUp() {
        shadowLooper = Shadows.shadowOf(android.os.Looper.getMainLooper());
    }

    // ==================== CONSTRUCTOR TESTS ====================

    @Test
    public void testConstructor_WithDynamicOptimizationTrue() {
        Debouncer debouncer = new Debouncer(true);
        Assert.assertNotNull(debouncer);
    }

    @Test
    public void testConstructor_WithDynamicOptimizationFalse() {
        Debouncer debouncer = new Debouncer(false);
        Assert.assertNotNull(debouncer);
    }

    // ==================== FIRST REQUEST TESTS ====================

    @Test
    public void testDebounce_FirstRequestSchedulesExecution() {
        Debouncer debouncer = new Debouncer(false);
        AtomicInteger executionCount = new AtomicInteger(0);

        debouncer.debounce(() -> executionCount.incrementAndGet());

        // First request should be scheduled with debounce delay
        Assert.assertEquals(0, executionCount.get());

        // Advance time by debounce time (64ms)
        shadowLooper.idleFor(64, java.util.concurrent.TimeUnit.MILLISECONDS);

        // Should have executed
        Assert.assertEquals(1, executionCount.get());
    }

    @Test
    public void testDebounce_FirstRequestDoesNotExecuteImmediately() {
        Debouncer debouncer = new Debouncer(false);
        AtomicInteger executionCount = new AtomicInteger(0);

        debouncer.debounce(() -> executionCount.incrementAndGet());

        // Should not execute immediately
        Assert.assertEquals(0, executionCount.get());
    }

    // ==================== DEBOUNCING BEHAVIOR TESTS ====================

    @Test
    public void testDebounce_MultipleRapidCalls_OnlyLastExecutes() {
        Debouncer debouncer = new Debouncer(false);
        AtomicInteger executionCount = new AtomicInteger(0);

        // First call to initialize timestamp
        debouncer.debounce(() -> executionCount.incrementAndGet());
        shadowLooper.idleFor(64, java.util.concurrent.TimeUnit.MILLISECONDS);
        Assert.assertEquals(1, executionCount.get());

        // Multiple rapid calls
        debouncer.debounce(() -> executionCount.set(10));
        debouncer.debounce(() -> executionCount.set(20));
        debouncer.debounce(() -> executionCount.set(30));

        // None should execute yet
        Assert.assertEquals(1, executionCount.get());

        // Advance time - only the last call should execute
        shadowLooper.idleFor(64, java.util.concurrent.TimeUnit.MILLISECONDS);

        Assert.assertEquals(30, executionCount.get());
    }

    @Test
    public void testDebounce_CancelsPreviousPendingExecution() {
        Debouncer debouncer = new Debouncer(false);
        AtomicInteger firstCallCount = new AtomicInteger(0);
        AtomicInteger secondCallCount = new AtomicInteger(0);

        // Initialize first
        debouncer.debounce(() -> {});
        shadowLooper.idleFor(64, java.util.concurrent.TimeUnit.MILLISECONDS);

        // First call
        debouncer.debounce(() -> firstCallCount.incrementAndGet());

        // Second call before first executes
        shadowLooper.idleFor(30, java.util.concurrent.TimeUnit.MILLISECONDS);
        debouncer.debounce(() -> secondCallCount.incrementAndGet());

        // Advance to complete debounce time from second call
        shadowLooper.idleFor(64, java.util.concurrent.TimeUnit.MILLISECONDS);

        // Only second call should execute
        Assert.assertEquals(0, firstCallCount.get());
        Assert.assertEquals(1, secondCallCount.get());
    }

    @Test
    public void testDebounce_WaitsForDebounceDelay() {
        Debouncer debouncer = new Debouncer(false);
        AtomicInteger executionCount = new AtomicInteger(0);

        // Initialize
        debouncer.debounce(() -> {});
        shadowLooper.idleFor(64, java.util.concurrent.TimeUnit.MILLISECONDS);

        debouncer.debounce(() -> executionCount.incrementAndGet());

        // Should not execute before delay
        shadowLooper.idleFor(30, java.util.concurrent.TimeUnit.MILLISECONDS);
        Assert.assertEquals(0, executionCount.get());

        // Should execute after full delay
        shadowLooper.idleFor(34, java.util.concurrent.TimeUnit.MILLISECONDS);
        Assert.assertEquals(1, executionCount.get());
    }

    // ==================== MAX DELAY THRESHOLD TESTS ====================

    @Test
    public void testDebounce_ExecutesImmediatelyAfterMaxDelay() throws InterruptedException {
        Debouncer debouncer = new Debouncer(false);
        AtomicInteger executionCount = new AtomicInteger(0);

        // First call to initialize
        debouncer.debounce(() -> executionCount.incrementAndGet());
        shadowLooper.idleFor(64, java.util.concurrent.TimeUnit.MILLISECONDS);
        Assert.assertEquals(1, executionCount.get());

        // Wait for max delay threshold (1000ms)
        Thread.sleep(1100);

        // Next call should execute immediately since max delay exceeded
        debouncer.debounce(() -> executionCount.incrementAndGet());

        // Should execute immediately (no need to advance looper)
        Assert.assertEquals(2, executionCount.get());
    }

    @Test
    public void testDebounce_WithinMaxDelay_UsesDebouncing() {
        Debouncer debouncer = new Debouncer(false);
        AtomicInteger executionCount = new AtomicInteger(0);

        // First call
        debouncer.debounce(() -> executionCount.incrementAndGet());
        shadowLooper.idleFor(64, java.util.concurrent.TimeUnit.MILLISECONDS);
        Assert.assertEquals(1, executionCount.get());

        // Second call within max delay threshold (< 1000ms)
        shadowLooper.idleFor(100, java.util.concurrent.TimeUnit.MILLISECONDS);
        debouncer.debounce(() -> executionCount.incrementAndGet());

        // Should not execute immediately
        Assert.assertEquals(1, executionCount.get());

        // Should execute after debounce delay
        shadowLooper.idleFor(64, java.util.concurrent.TimeUnit.MILLISECONDS);
        Assert.assertEquals(2, executionCount.get());
    }

    // ==================== CANCEL TESTS ====================

    @Test
    public void testCancel_PreventsPendingExecution() {
        Debouncer debouncer = new Debouncer(false);
        AtomicInteger executionCount = new AtomicInteger(0);

        // Initialize
        debouncer.debounce(() -> {});
        shadowLooper.idleFor(64, java.util.concurrent.TimeUnit.MILLISECONDS);

        debouncer.debounce(() -> executionCount.incrementAndGet());

        // Cancel before execution
        debouncer.cancel();

        // Advance time - should not execute
        shadowLooper.idleFor(100, java.util.concurrent.TimeUnit.MILLISECONDS);

        Assert.assertEquals(0, executionCount.get());
    }

    @Test
    public void testCancel_MultipleCalls() {
        Debouncer debouncer = new Debouncer(false);
        AtomicInteger executionCount = new AtomicInteger(0);

        // Initialize
        debouncer.debounce(() -> {});
        shadowLooper.idleFor(64, java.util.concurrent.TimeUnit.MILLISECONDS);

        debouncer.debounce(() -> executionCount.incrementAndGet());

        // Multiple cancel calls should not cause issues
        debouncer.cancel();
        debouncer.cancel();
        debouncer.cancel();

        shadowLooper.idleFor(100, java.util.concurrent.TimeUnit.MILLISECONDS);

        Assert.assertEquals(0, executionCount.get());
    }

    @Test
    public void testCancel_ThenDebounceAgain() {
        Debouncer debouncer = new Debouncer(false);
        AtomicInteger executionCount = new AtomicInteger(0);

        // Initialize
        debouncer.debounce(() -> {});
        shadowLooper.idleFor(64, java.util.concurrent.TimeUnit.MILLISECONDS);

        // Schedule execution
        debouncer.debounce(() -> executionCount.set(1));

        // Cancel
        debouncer.cancel();

        // Schedule new execution
        debouncer.debounce(() -> executionCount.set(2));

        shadowLooper.idleFor(64, java.util.concurrent.TimeUnit.MILLISECONDS);

        // Only second execution should run
        Assert.assertEquals(2, executionCount.get());
    }

    @Test
    public void testCancel_WhenNothingScheduled() {
        Debouncer debouncer = new Debouncer(false);

        // Cancel when nothing is scheduled - should not cause issues
        debouncer.cancel();
    }

    // ==================== EXECUTION TESTS ====================

    @Test
    public void testDebounce_RunnableExecutesCorrectly() {
        Debouncer debouncer = new Debouncer(false);
        StringBuilder result = new StringBuilder();

        // Initialize
        debouncer.debounce(() -> {});
        shadowLooper.idleFor(64, java.util.concurrent.TimeUnit.MILLISECONDS);

        debouncer.debounce(() -> result.append("executed"));

        shadowLooper.idleFor(64, java.util.concurrent.TimeUnit.MILLISECONDS);

        Assert.assertEquals("executed", result.toString());
    }

    @Test
    public void testDebounce_MultipleSequentialExecutions() {
        Debouncer debouncer = new Debouncer(false);
        AtomicInteger executionCount = new AtomicInteger(0);

        // First execution
        debouncer.debounce(() -> executionCount.incrementAndGet());
        shadowLooper.idleFor(64, java.util.concurrent.TimeUnit.MILLISECONDS);
        Assert.assertEquals(1, executionCount.get());

        // Second execution
        shadowLooper.idleFor(100, java.util.concurrent.TimeUnit.MILLISECONDS);
        debouncer.debounce(() -> executionCount.incrementAndGet());
        shadowLooper.idleFor(64, java.util.concurrent.TimeUnit.MILLISECONDS);
        Assert.assertEquals(2, executionCount.get());

        // Third execution
        shadowLooper.idleFor(100, java.util.concurrent.TimeUnit.MILLISECONDS);
        debouncer.debounce(() -> executionCount.incrementAndGet());
        shadowLooper.idleFor(64, java.util.concurrent.TimeUnit.MILLISECONDS);
        Assert.assertEquals(3, executionCount.get());
    }

    @Test
    public void testDebounce_WithRunnableThatThrows() {
        Debouncer debouncer = new Debouncer(false);
        AtomicInteger executionCount = new AtomicInteger(0);

        // Initialize
        debouncer.debounce(() -> {});
        shadowLooper.idleFor(64, java.util.concurrent.TimeUnit.MILLISECONDS);

        debouncer.debounce(() -> {
            executionCount.incrementAndGet();
            throw new RuntimeException("Test exception");
        });

        try {
            shadowLooper.idleFor(64, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            // Expected
        }

        // Should have attempted execution
        Assert.assertEquals(1, executionCount.get());
    }

    // ==================== TIMING TESTS ====================

    @Test
    public void testDebounce_DebounceTimeIs64Ms() {
        Debouncer debouncer = new Debouncer(false);
        AtomicInteger executionCount = new AtomicInteger(0);

        // Initialize
        debouncer.debounce(() -> {});
        shadowLooper.idleFor(64, java.util.concurrent.TimeUnit.MILLISECONDS);

        debouncer.debounce(() -> executionCount.incrementAndGet());

        // Should not execute at 63ms
        shadowLooper.idleFor(63, java.util.concurrent.TimeUnit.MILLISECONDS);
        Assert.assertEquals(0, executionCount.get());

        // Should execute at 64ms (1ms more)
        shadowLooper.idleFor(1, java.util.concurrent.TimeUnit.MILLISECONDS);
        Assert.assertEquals(1, executionCount.get());
    }

    @Test
    public void testDebounce_RespectsMaxDelayThreshold() throws InterruptedException {
        Debouncer debouncer = new Debouncer(false);
        AtomicInteger executionCount = new AtomicInteger(0);

        // First call
        debouncer.debounce(() -> executionCount.incrementAndGet());
        shadowLooper.idleFor(64, java.util.concurrent.TimeUnit.MILLISECONDS);
        Assert.assertEquals(1, executionCount.get());

        // Wait beyond max delay (1000ms)
        Thread.sleep(1100);

        // Should execute immediately
        debouncer.debounce(() -> executionCount.incrementAndGet());
        Assert.assertEquals(2, executionCount.get());
    }

    // ==================== EDGE CASE TESTS ====================

    @Test
    public void testDebounce_WithNullRunnable_DoesNotCrash() {
        Debouncer debouncer = new Debouncer(false);

        try {
            debouncer.debounce(null);
            shadowLooper.idleFor(64, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (NullPointerException e) {
            // Expected - runnable cannot be null
        }
    }

    @Test
    public void testDebounce_RapidFireCalls() {
        Debouncer debouncer = new Debouncer(false);
        AtomicInteger executionCount = new AtomicInteger(0);

        // Initialize
        debouncer.debounce(() -> {});
        shadowLooper.idleFor(64, java.util.concurrent.TimeUnit.MILLISECONDS);

        // 100 rapid calls
        for (int i = 0; i < 100; i++) {
            final int value = i;
            debouncer.debounce(() -> executionCount.set(value));
        }

        shadowLooper.idleFor(64, java.util.concurrent.TimeUnit.MILLISECONDS);

        // Only last call should execute
        Assert.assertEquals(99, executionCount.get());
    }

    @Test
    public void testDebounce_AlternatingDebouncingAndCancel() {
        Debouncer debouncer = new Debouncer(false);
        AtomicInteger executionCount = new AtomicInteger(0);

        // Initialize
        debouncer.debounce(() -> {});
        shadowLooper.idleFor(64, java.util.concurrent.TimeUnit.MILLISECONDS);

        debouncer.debounce(() -> executionCount.incrementAndGet());
        debouncer.cancel();

        shadowLooper.idleFor(64, java.util.concurrent.TimeUnit.MILLISECONDS);
        Assert.assertEquals(0, executionCount.get());

        debouncer.debounce(() -> executionCount.incrementAndGet());
        shadowLooper.idleFor(64, java.util.concurrent.TimeUnit.MILLISECONDS);
        Assert.assertEquals(1, executionCount.get());

        debouncer.debounce(() -> executionCount.incrementAndGet());
        debouncer.cancel();
        shadowLooper.idleFor(64, java.util.concurrent.TimeUnit.MILLISECONDS);
        Assert.assertEquals(1, executionCount.get());
    }

    @Test
    public void testDebounce_WithDynamicOptimizationParameter() {
        // Test both true and false values
        Debouncer debouncerTrue = new Debouncer(true);
        Debouncer debouncerFalse = new Debouncer(false);

        AtomicInteger countTrue = new AtomicInteger(0);
        AtomicInteger countFalse = new AtomicInteger(0);

        debouncerTrue.debounce(() -> countTrue.incrementAndGet());
        debouncerFalse.debounce(() -> countFalse.incrementAndGet());

        shadowLooper.idleFor(64, java.util.concurrent.TimeUnit.MILLISECONDS);

        // Both should execute regardless of dynamicOptimization value
        Assert.assertEquals(1, countTrue.get());
        Assert.assertEquals(1, countFalse.get());
    }
}