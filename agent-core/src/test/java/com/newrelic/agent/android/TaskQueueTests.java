/*
 * Copyright (c) 2022-2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android;

import com.newrelic.agent.android.TaskQueue;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@RunWith(JUnit4.class)
public class TaskQueueTests {
    private final static Lock testLock = new ReentrantLock();

    @Test
    public void testTaskQueueAndDequeue() {
        testLock.lock();

        TaskQueue.clear();

        final int numObjects = 500000;
        for (int i = 0; i < numObjects; i++) {
            TaskQueue.queue(i);
        }
        Assert.assertEquals(numObjects, TaskQueue.size());

        TaskQueue.backgroundDequeue();
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Assert.fail(e.getMessage());
            return;
        }
        Assert.assertEquals(0, TaskQueue.size());

        testLock.unlock();
    }
}
