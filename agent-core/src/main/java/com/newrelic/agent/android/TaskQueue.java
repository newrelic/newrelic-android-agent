/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android;

import com.newrelic.agent.android.harvest.AgentHealth;
import com.newrelic.agent.android.harvest.AgentHealthException;
import com.newrelic.agent.android.harvest.Harvest;
import com.newrelic.agent.android.harvest.HarvestAdapter;
import com.newrelic.agent.android.measurement.HttpTransactionMeasurement;
import com.newrelic.agent.android.metric.Metric;
import com.newrelic.agent.android.tracing.ActivityTrace;
import com.newrelic.agent.android.tracing.Trace;
import com.newrelic.agent.android.util.NamedThreadFactory;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * This class queues objects for asynchronous distribution to the internal Harvest and Measurement APIs. This allows
 * callers to return immediately without waiting for the Measurement or Harvest engines to complete.
 * <p/>
 * The internal queue is cleared asynchronously on a background thread once per DEQUEUE_PERIOD_MS milliseconds.
 * <p/>
 * The queue is also cleared synchronously upon an onHarvest event, ensuring that any queued objects are harvested.
 */
public class TaskQueue extends HarvestAdapter {

    private static final long DEQUEUE_PERIOD_MS = 1000;

    private static final ScheduledExecutorService queueExecutor = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("TaskQueue"));
    private static final ConcurrentLinkedQueue<Object> queue = new ConcurrentLinkedQueue<Object>();
    private static final Runnable dequeueTask = () -> TaskQueue.dequeue();
    protected static Future dequeueFuture;

    /**
     * Enqueue an object into the internal queue.
     *
     * @param object The Object to queue. See dequeue for supported object types.
     */
    public static void queue(final Object object) {
        queue.add(object);
    }

    /**
     * Execute the dequeue task on a dedicated background thread.
     */
    public static void backgroundDequeue() {
        queueExecutor.execute(dequeueTask);
    }

    /**
     * Execute the dequeue tasks and wait for it to complete before returning. Only used on harvest.
     */
    public static void synchronousDequeue() {
        final Future future = queueExecutor.submit(dequeueTask);
        try {
            future.get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }

    /**
     * Start the periodic dequeue task.
     */
    public static void start() {
        if (dequeueFuture == null) {
            dequeueFuture = queueExecutor.scheduleWithFixedDelay(dequeueTask, 0, DEQUEUE_PERIOD_MS, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Stop the periodic dequeue task.
     */
    public static void stop() {
        if (dequeueFuture != null) {
            dequeueFuture.cancel(true);
            dequeueFuture = null;
        }
    }

    /**
     * Dequeue all waiting objects and forward them to their respective API calls. Currently a naive instance type based
     * strategy is used to determine the destination API call, for simplicity. This could be refactored to use reflection
     * such as a Method Map, or an enum of explicitly supported API calls.
     */
    private static void dequeue() {
        if (queue.size() == 0)
            return;

        /*
          Temporarily disable automatic broadcasting of new measurements. Other threads may generate measurements, which
          will not be immediately broadcast. That's okay, because we'll broadcast all measurements as soon as the
          queue is emptied.
         */
        Measurements.setBroadcastNewMeasurements(false);
        while (!queue.isEmpty()) {
            try {
                final Object object = queue.remove();

                // ActivityTrace ->  Harvest.addActivityTrace
                if (object instanceof ActivityTrace) {
                    Harvest.addActivityTrace((ActivityTrace) object);
                    continue;
                }

                // Metric ->  Harvest.addMetric
                if (object instanceof Metric) {
                    Harvest.addMetric((Metric) object);
                    continue;
                }

                // AgentHealthException -> Harvest.addAgentHealthException
                if (object instanceof AgentHealthException) {
                    Harvest.addAgentHealthException((AgentHealthException) object);
                    continue;
                }

                // Trace ->  Measurements.addTracedMethod
                if (object instanceof Trace) {
                    Measurements.addTracedMethod((Trace) object);
                    continue;
                }

                // HttpTransactionMeasurement -> Measurements.addHttpTransaction
                if (object instanceof HttpTransactionMeasurement) {
                    Measurements.addHttpTransaction((HttpTransactionMeasurement) object);
                }
            } catch (Exception e) {
                e.printStackTrace();
                AgentHealth.noticeException(e);
            }
        }

        // Broadcast all of the objects and re-enable automatic broadcasting.
        Measurements.broadcast();
        Measurements.setBroadcastNewMeasurements(true);
    }

    /**
     * Returns the size of the internal queue.
     *
     * @return size of the internal queue.
     */
    public static int size() {
        return queue.size();
    }

    /**
     * Clear the internal queue.
     */
    public static void clear() {
        queue.clear();
    }

    /**
     * Expose the implementation queue
     */
    protected static Queue<Object> getQueue() {
        return queue;
    }
}
