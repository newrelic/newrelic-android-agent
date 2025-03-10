/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.payload;

import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.agentdata.AgentDataReporter;
import com.newrelic.agent.android.crash.CrashReporter;
import com.newrelic.agent.android.harvest.Harvest;
import com.newrelic.agent.android.harvest.HarvestLifecycleAware;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.sessionReplay.SessionReplayReporter;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.stats.TicToc;
import com.newrelic.agent.android.util.NamedThreadFactory;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class PayloadController implements HarvestLifecycleAware {

    protected static final AgentLog log = AgentLogManager.getAgentLog();

    public static final long PAYLOAD_COLLECTOR_TIMEOUT = 5000;             // 5 seconds
    public static final long PAYLOAD_REQUEUE_PERIOD_MS = 2 * 60 * 1000;    // requeue failed uploads every 2 minutes

    protected static Lock payloadQueueLock = new ReentrantLock(false);
    protected static AtomicReference<PayloadController> instance = new AtomicReference<>(null);
    protected static ThrottledScheduledThreadPoolExecutor queueExecutor = null;
    protected static ScheduledFuture<?> requeueFuture = null;
    protected static ConcurrentLinkedQueue<PayloadReaper> payloadReaperQueue = null;
    protected static ConcurrentLinkedQueue<PayloadReaper> payloadReaperRetryQueue = null;
    protected static Map<String, Future> reapersInFlight = null;
    protected static boolean opportunisticUploads = false;

    protected static final Runnable dequeueRunnable = new Runnable() {
        @Override
        public void run() {
            if (isInitialized()) {
                instance.get().dequeuePayloadSenders();
            }
        }
    };

    protected static final Runnable requeueRunnable = new Runnable() {
        @Override
        public void run() {
            if (isInitialized()) {
                instance.get().requeuePayloadSenders();
            }
        }
    };

    private final AgentConfiguration agentConfiguration;


    public static PayloadController initialize(final AgentConfiguration agentConfiguration) {
        if (instance.compareAndSet(null, new PayloadController(agentConfiguration))) {

            payloadReaperQueue = new ConcurrentLinkedQueue<PayloadReaper>();
            payloadReaperRetryQueue = new ConcurrentLinkedQueue<PayloadReaper>();
            queueExecutor = new ThrottledScheduledThreadPoolExecutor(agentConfiguration.getIOThreadSize(), new NamedThreadFactory("PayloadWorker"));
            requeueFuture = queueExecutor.scheduleAtFixedRate(requeueRunnable, PayloadController.PAYLOAD_REQUEUE_PERIOD_MS, PayloadController.PAYLOAD_REQUEUE_PERIOD_MS, TimeUnit.MILLISECONDS);
            reapersInFlight = new ConcurrentHashMap<String, Future>();
            opportunisticUploads = false;

            CrashReporter crashReporter = CrashReporter.initialize(agentConfiguration);
            if (crashReporter != null) {
                crashReporter.start();
            } else {
                log.warn("PayloadController: No crash reporter - crash reporting will be disabled");
            }

            AgentDataReporter agentDataReporter = AgentDataReporter.initialize(agentConfiguration);
            if (agentDataReporter != null) {
                agentDataReporter.start();
            } else {
                log.warn("PayloadController: No payload reporter - payload reporting will be disabled");
            }

            SessionReplayReporter sessionReplayReporter = SessionReplayReporter.initialize(agentConfiguration);
            if(sessionReplayReporter != null){
                sessionReplayReporter.start();
            }else{
                log.warn("SessionReplayController: No session replay reporter - session replay reporting will be disabled");
            }

            Harvest.addHarvestListener(instance.get());
        }

        return instance.get();
    }

    public static void shutdown() {
        if (isInitialized()) {
            try {
                Harvest.removeHarvestListener(instance.get());

                if (requeueFuture != null) {
                    requeueFuture.cancel(true);
                    requeueFuture = null;
                }

                // Don't accept any more payloads
                queueExecutor.shutdown();

                // Make sure all blocked threads are cancelled.
                // Threads started during startup could still be running (unlikely)
                try {
                    if (false == queueExecutor.awaitTermination(PAYLOAD_COLLECTOR_TIMEOUT, TimeUnit.MILLISECONDS)) {
                        log.warn("PayloadController: upload thread(s) timed-out before handler");
                        queueExecutor.shutdownNow();
                    }
                    AgentDataReporter.shutdown();
                    CrashReporter.shutdown();
                    SessionReplayReporter.shutdown();

                } catch (InterruptedException e) {
                }

            } finally {
                instance.set(null);
            }
        }
    }

    public static Future submitPayload(PayloadSender payloadSender) {
        return submitPayload(payloadSender, null);
    }

    public static Future submitPayload(final PayloadSender payloadSender, final PayloadSender.CompletionHandler completionHandler) {
        Future future = null;
        final TicToc timer = new TicToc();

        if (isInitialized()) {
            timer.tic();

            final PayloadReaper payloadReaper = new PayloadReaper(payloadSender, completionHandler) {
                @Override
                public PayloadSender call() throws Exception {
                    PayloadSender sender = super.call();
                    if (sender != null && !sender.isSuccessfulResponse() && sender.shouldRetry()) {
                        // resubmit the task
                        payloadReaperRetryQueue.offer(this);
                    }

                    reapersInFlight.remove(getUuid());
                    return sender;
                }
            };

            // if a clone of this sender (same payload UUID) is already queued,
            // remove from any pending queues
            payloadReaperQueue.remove(payloadReaper);
            payloadReaperRetryQueue.remove(payloadReaper);

            future = reapersInFlight.get(payloadReaper.getUuid());
            if (future != null) {
                log.warn("PayloadController: Upload of payload [" + payloadReaper.getUuid() + "] is already in progress.");
            } else {
                if (payloadSender.shouldUploadOpportunistically()) {
                    future = queueExecutor.submit(payloadReaper);
                    reapersInFlight.put(payloadReaper.getUuid(), future);
                } else {
                    // queue the node and let the dequeue runnable process the upload
                    payloadReaperQueue.offer(payloadReaper);
                }
                log.debug("PayloadController: " + String.valueOf(timer.toc()) + "ms. waiting to submit payload [" + payloadReaper.getUuid() + "].");
            }
        }

        return future;
    }

    protected static Future submitPayload(final PayloadReaper payloadReaper) {
        Future future = null;

        if (isInitialized()) {

            // if a clone of this sender (same payload UUID) is already queued,
            // remove from any pending queues
            payloadReaperQueue.remove(payloadReaper);
            payloadReaperRetryQueue.remove(payloadReaper);

            future = reapersInFlight.get(payloadReaper.getUuid());
            if (future != null) {
                log.warn("PayloadController: Upload of payload [" + payloadReaper.getUuid() + "] is already in progress.");
            } else {
                future = queueExecutor.submit(payloadReaper);
                reapersInFlight.put(payloadReaper.getUuid(), future);
            }
        }

        return future;
    }

    /**
     * Upload if opportunistic uploads enabled, and network is active.
     * Otherwise batch the payload for delivery during harvest cycle
     **/
    public static boolean shouldUploadOpportunistically() {
        return opportunisticUploads && Agent.hasReachableNetworkConnection(null);
    }

    public static Future submitCallable(Callable<?> callable) {
        return queueExecutor.submit(callable);
    }

    public static boolean isInitialized() {
        return instance.get() != null;
    }

    protected PayloadController(AgentConfiguration agentConfiguration) {
        this.agentConfiguration = agentConfiguration;
    }

    private final void dequeuePayloadSenders() {
        // if already processing, skip it
        if (payloadQueueLock.tryLock()) {
            try {
                while (!payloadReaperQueue.isEmpty()) {
                    PayloadReaper payloadReaper = payloadReaperQueue.poll();
                    if (payloadReaper != null) {
                        try {
                            submitPayload(payloadReaper);
                        } catch (Exception e) {
                            log.error("PayloadController.dequeuePayloadSenders(): " + e);
                        }
                    }
                }

            } finally {
                payloadQueueLock.unlock();
            }
        }
    }

    private void requeuePayloadSenders() {
        if (payloadQueueLock.tryLock()) {
            try {
                while (!payloadReaperRetryQueue.isEmpty()) {
                    PayloadReaper payloadReaper = payloadReaperRetryQueue.poll();
                    if (payloadReaper != null) {
                        if (!payloadReaper.sender.getPayload().isStale(agentConfiguration.getPayloadTTL())) {
                            submitPayload(payloadReaper);
                        } else {
                            log.warn("PayloadController: Will not re-queue stale payload.");
                        }
                    }
                }
            } finally {
                payloadQueueLock.unlock();
            }
        }
    }

    protected boolean uploadOpportunistically() {
        return opportunisticUploads;
    }

    @Override
    public void onHarvest() {
        PayloadController.queueExecutor.submit(dequeueRunnable);
    }

    public static boolean payloadInFlight(Payload payload) {
        return reapersInFlight.containsKey(payload.getUuid());
    }

    protected static class ThrottledScheduledThreadPoolExecutor extends ScheduledThreadPoolExecutor {

        protected static final int THROTTLE_LIMIT = 16;
        protected static final int THROTTLE_SLEEP = 50;

        public ThrottledScheduledThreadPoolExecutor(int i, ThreadFactory threadFactory) {
            super(i, threadFactory);
        }

        @Override
        public <T> Future<T> submit(Callable<T> callable) {
            if (getQueue().size() >= THROTTLE_LIMIT) {
                StatsEngine.get().inc(MetricNames.SUPPORTABILITY_HEX_UPLOAD_THROTTLED);
            }
            return super.submit(callable);
        }
    }
}