/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
public class NewRelicIdGeneratorTest {

    private static final int ID_OFFSET = 1_000_000;

    // ==================== BASIC ID GENERATION TESTS ====================

    @Test
    public void testGenerateId_ReturnsPositiveId() {
        int id = NewRelicIdGenerator.generateId();

        Assert.assertTrue(id > 0);
    }

    @Test
    public void testGenerateId_StartsFromOffset() {
        // Note: This test might fail if other tests have already called generateId()
        // In real usage, IDs start from ID_OFFSET (1,000,000)
        int id = NewRelicIdGenerator.generateId();

        Assert.assertTrue(id >= ID_OFFSET);
    }

    @Test
    public void testGenerateId_ReturnsUniqueIds() {
        int id1 = NewRelicIdGenerator.generateId();
        int id2 = NewRelicIdGenerator.generateId();

        Assert.assertNotEquals(id1, id2);
    }

    @Test
    public void testGenerateId_IncrementsSequentially() {
        int id1 = NewRelicIdGenerator.generateId();
        int id2 = NewRelicIdGenerator.generateId();

        Assert.assertEquals(id1 + 1, id2);
    }

    @Test
    public void testGenerateId_MultipleCallsProduceUniqueIds() {
        Set<Integer> ids = new HashSet<>();

        for (int i = 0; i < 1000; i++) {
            int id = NewRelicIdGenerator.generateId();
            boolean added = ids.add(id);
            Assert.assertTrue("Duplicate ID generated: " + id, added);
        }

        Assert.assertEquals(1000, ids.size());
    }

    // ==================== COLLISION AVOIDANCE TESTS ====================

    @Test
    public void testGenerateId_AvoidsComposeSemanticsNodeRange() {
        int id = NewRelicIdGenerator.generateId();

        // IDs should be >= 1,000,000 to avoid collision with Compose SemanticsNode IDs (0-999,999)
        Assert.assertTrue(id >= ID_OFFSET);
    }

    @Test
    public void testGenerateId_AllIdsAboveOffset() {
        for (int i = 0; i < 100; i++) {
            int id = NewRelicIdGenerator.generateId();
            Assert.assertTrue("ID " + id + " is below offset", id >= ID_OFFSET);
        }
    }

    // ==================== THREAD SAFETY TESTS ====================

    @Test
    public void testGenerateId_ThreadSafe() throws InterruptedException {
        int threadCount = 10;
        int idsPerThread = 100;
        Set<Integer> allIds = new HashSet<>();
        List<Set<Integer>> threadIds = new ArrayList<>();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            Set<Integer> threadSet = new HashSet<>();
            threadIds.add(threadSet);

            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    for (int j = 0; j < idsPerThread; j++) {
                        int id = NewRelicIdGenerator.generateId();
                        threadSet.add(id);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Start all threads at once
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        Assert.assertTrue("Threads did not complete in time", completed);

        // Collect all IDs from all threads
        for (Set<Integer> threadSet : threadIds) {
            allIds.addAll(threadSet);
        }

        // All IDs should be unique across all threads
        int expectedTotal = threadCount * idsPerThread;
        Assert.assertEquals("Some IDs were duplicated across threads", expectedTotal, allIds.size());
    }

    @Test
    public void testGenerateId_ConcurrentAccess() throws InterruptedException {
        int threadCount = 5;
        int callsPerThread = 50;
        Set<Integer> allIds = new HashSet<>();
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<List<Integer>> results = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            results.add(new ArrayList<>());
            final int threadIndex = i;

            executor.submit(() -> {
                List<Integer> threadResults = results.get(threadIndex);
                for (int j = 0; j < callsPerThread; j++) {
                    int id = NewRelicIdGenerator.generateId();
                    threadResults.add(id);
                }
                latch.countDown();
            });
        }

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        Assert.assertTrue("Threads did not complete in time", completed);

        // Collect all IDs
        for (List<Integer> threadResults : results) {
            allIds.addAll(threadResults);
        }

        // All IDs should be unique
        int expectedTotal = threadCount * callsPerThread;
        Assert.assertEquals("Duplicate IDs found in concurrent access", expectedTotal, allIds.size());
    }

    @Test
    public void testGenerateId_NoRaceConditions() throws InterruptedException {
        int iterations = 100;
        AtomicInteger duplicateCount = new AtomicInteger(0);

        for (int iter = 0; iter < iterations; iter++) {
            Set<Integer> ids = new HashSet<>();
            CountDownLatch latch = new CountDownLatch(2);

            Thread thread1 = new Thread(() -> {
                int id = NewRelicIdGenerator.generateId();
                synchronized (ids) {
                    if (!ids.add(id)) {
                        duplicateCount.incrementAndGet();
                    }
                }
                latch.countDown();
            });

            Thread thread2 = new Thread(() -> {
                int id = NewRelicIdGenerator.generateId();
                synchronized (ids) {
                    if (!ids.add(id)) {
                        duplicateCount.incrementAndGet();
                    }
                }
                latch.countDown();
            });

            thread1.start();
            thread2.start();

            boolean completed = latch.await(5, TimeUnit.SECONDS);
            Assert.assertTrue("Threads did not complete in time", completed);
        }

        Assert.assertEquals("Race conditions detected - duplicate IDs generated", 0, duplicateCount.get());
    }

    // ==================== PERFORMANCE TESTS ====================

    @Test
    public void testGenerateId_FastExecution() {
        long startTime = System.nanoTime();

        for (int i = 0; i < 10000; i++) {
            NewRelicIdGenerator.generateId();
        }

        long endTime = System.nanoTime();
        long durationMs = (endTime - startTime) / 1_000_000;

        // Should complete 10,000 ID generations in less than 100ms
        Assert.assertTrue("ID generation is too slow: " + durationMs + "ms", durationMs < 100);
    }

    @Test
    public void testGenerateId_ConsistentPerformance() {
        int[] batchSizes = {100, 1000, 10000};

        for (int batchSize : batchSizes) {
            long startTime = System.nanoTime();

            for (int i = 0; i < batchSize; i++) {
                NewRelicIdGenerator.generateId();
            }

            long endTime = System.nanoTime();
            long durationMs = (endTime - startTime) / 1_000_000;

            // Performance should scale linearly
            double msPerCall = (double) durationMs / batchSize;
            Assert.assertTrue("Performance degraded for batch size " + batchSize + ": " + msPerCall + "ms per call",
                    msPerCall < 0.01); // Less than 0.01ms per call
        }
    }

    // ==================== SEQUENTIAL ORDERING TESTS ====================

    @Test
    public void testGenerateId_MaintainsOrder() {
        List<Integer> ids = new ArrayList<>();

        for (int i = 0; i < 100; i++) {
            ids.add(NewRelicIdGenerator.generateId());
        }

        // Verify all IDs are in ascending order
        for (int i = 1; i < ids.size(); i++) {
            Assert.assertTrue("IDs are not in ascending order at index " + i,
                    ids.get(i) > ids.get(i - 1));
            Assert.assertEquals("IDs are not sequential at index " + i,
                    ids.get(i - 1) + 1, (int) ids.get(i));
        }
    }

    @Test
    public void testGenerateId_NoGapsInSequence() {
        int firstId = NewRelicIdGenerator.generateId();

        for (int i = 1; i < 100; i++) {
            int nextId = NewRelicIdGenerator.generateId();
            Assert.assertEquals("Gap in ID sequence", firstId + i, nextId);
        }
    }

    // ==================== LARGE-SCALE TESTS ====================

    @Test
    public void testGenerateId_LargeNumberOfIds() {
        Set<Integer> ids = new HashSet<>();
        int count = 100_000;

        for (int i = 0; i < count; i++) {
            int id = NewRelicIdGenerator.generateId();
            ids.add(id);
        }

        // All IDs should be unique
        Assert.assertEquals("Duplicate IDs found in large-scale test", count, ids.size());
    }

    @Test
    public void testGenerateId_DoesNotOverflow() {
        // Generate a large number of IDs to ensure no integer overflow issues
        int previousId = NewRelicIdGenerator.generateId();

        for (int i = 0; i < 10000; i++) {
            int currentId = NewRelicIdGenerator.generateId();
            Assert.assertTrue("Integer overflow detected", currentId > previousId);
            previousId = currentId;
        }
    }

    // ==================== EDGE CASE TESTS ====================

    @Test
    public void testGenerateId_RepeatedCalls() {
        for (int i = 0; i < 1000; i++) {
            int id = NewRelicIdGenerator.generateId();
            Assert.assertTrue(id >= ID_OFFSET);
        }
    }

    @Test
    public void testGenerateId_SingleThreadedUniqueness() {
        Set<Integer> ids = new HashSet<>();
        int count = 10000;

        for (int i = 0; i < count; i++) {
            int id = NewRelicIdGenerator.generateId();
            Assert.assertTrue("Duplicate ID generated in single-threaded test: " + id, ids.add(id));
        }

        Assert.assertEquals(count, ids.size());
    }
}