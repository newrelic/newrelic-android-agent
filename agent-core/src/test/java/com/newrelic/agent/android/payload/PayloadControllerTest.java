/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.payload;

import static com.newrelic.agent.android.payload.PayloadController.instance;
import static com.newrelic.agent.android.payload.PayloadController.payloadReaperQueue;
import static com.newrelic.agent.android.payload.PayloadController.payloadReaperRetryQueue;
import static com.newrelic.agent.android.payload.PayloadController.queueExecutor;

import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.agentdata.AgentDataReporter;
import com.newrelic.agent.android.agentdata.AgentDataSender;
import com.newrelic.agent.android.crash.CrashReporter;
import com.newrelic.agent.android.crash.CrashReporterTests;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.logging.ConsoleAgentLog;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.test.mock.TestFuture;
import com.newrelic.agent.android.test.stub.StubAgentImpl;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import javax.net.ssl.HttpsURLConnection;

@RunWith(Parameterized.class)
public class PayloadControllerTest {

    private AgentConfiguration agentConfiguration;
    private PayloadController payloadController;
    private Payload payload;
    private PayloadSender payloadSender;
    private boolean radioEnabled = false;
    private boolean opportunisticUploads = false;

    @Parameterized.Parameters
    public static Collection networkState() {
        return Arrays.asList(new Object[][]{
                {false, false}, // {radioEnabled, opportunisticUploads}
                {false, true},
                {true, false},
                {true, true},
        });
    }

    public PayloadControllerTest(boolean radioEnabled, boolean opportunisticUploads) {
        this.radioEnabled = radioEnabled;
        this.opportunisticUploads = opportunisticUploads;
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        AgentLogManager.setAgentLog(new ConsoleAgentLog());
        AgentLogManager.getAgentLog().setLevel(AgentLog.AUDIT);
    }

    @Before
    public void setUp() throws Exception {
        agentConfiguration = Mockito.spy(new AgentConfiguration());
        agentConfiguration.setApplicationToken(CrashReporterTests.class.getSimpleName());
        agentConfiguration.setEnableAnalyticsEvents(true);
        agentConfiguration.setReportCrashes(false);
        agentConfiguration.setReportHandledExceptions(true);

        Agent.setImpl(new StubAgentImpl() {
            @Override
            public boolean hasReachableNetworkConnection(String reachableHost) {
                return radioEnabled;
            }
        });

        payloadController = Mockito.spy(PayloadController.initialize(agentConfiguration));

        PayloadController.queueExecutor = Mockito.spy(PayloadController.queueExecutor);
        PayloadController.payloadReaperQueue = Mockito.spy(PayloadController.payloadReaperQueue);
        PayloadController.payloadReaperRetryQueue = Mockito.spy(PayloadController.payloadReaperRetryQueue);

        Mockito.doReturn(opportunisticUploads).when(payloadController).uploadOpportunistically();
        Mockito.doReturn(new TestFuture()).when(PayloadController.queueExecutor).submit(ArgumentMatchers.any(PayloadReaper.class));

        instance.set(payloadController);

        payload = new Payload("the tea's too hot".getBytes());
        payloadSender = Mockito.spy(providePayloadSender(payload));

        Mockito.doReturn(opportunisticUploads).when(payloadSender).shouldUploadOpportunistically();

        StatsEngine.reset();
    }

    @After
    public void tearDown() throws Exception {
        PayloadController.shutdown();
    }

    @Test
    public void getInstance() throws Exception {
        PayloadController controller = PayloadController.initialize(agentConfiguration);
        Assert.assertEquals(controller, instance.get());
    }

    @Test
    public void initialize() throws Exception {
        Assert.assertTrue(PayloadController.isInitialized());
        Assert.assertNotNull(PayloadController.requeueFuture);
        Assert.assertNotNull(AgentDataReporter.getInstance());
        Assert.assertNotNull(CrashReporter.getInstance());
    }

    @Test
    public void shutdown() throws Exception {
        PayloadController.shutdown();

        Mockito.verify(queueExecutor, Mockito.times(1)).shutdown();
        Assert.assertNull(PayloadController.instance.get());
        Assert.assertNull(PayloadController.requeueFuture);
        Assert.assertNull(AgentDataReporter.getInstance());
        Assert.assertNull(CrashReporter.getInstance());
    }


    @Test
    public void submitPayload() throws Exception {
        PayloadController.submitPayload(payloadSender);

        InOrder order = Mockito.inOrder(queueExecutor, payloadReaperRetryQueue, payloadReaperQueue);
        order.verify(PayloadController.payloadReaperRetryQueue).remove(Mockito.any(PayloadReaper.class));

        if (payloadSender.shouldUploadOpportunistically()) {
            order.verify(PayloadController.queueExecutor).submit(Mockito.any(PayloadReaper.class));
            Mockito.verify(PayloadController.queueExecutor, Mockito.times(1)).submit(Mockito.any(PayloadReaper.class));
        } else {
            order.verify(PayloadController.payloadReaperQueue).offer(Mockito.any(PayloadReaper.class));
            Mockito.verify(PayloadController.payloadReaperQueue, Mockito.times(1)).offer(Mockito.any(PayloadReaper.class));
        }

        Mockito.verify(PayloadController.payloadReaperRetryQueue, Mockito.times(1)).remove(Mockito.any(PayloadReaper.class));
        Mockito.verify(PayloadController.payloadReaperQueue, Mockito.times(1)).remove(Mockito.any(PayloadReaper.class));
    }

    @Test
    public void submitPayloadWithCompletionHandler() throws Exception {
        Mockito.doCallRealMethod().when(PayloadController.queueExecutor).submit(ArgumentMatchers.any(PayloadReaper.class));

        Mockito.doReturn(200).when(payloadSender).getResponseCode();
        Future future = PayloadController.submitPayload(payloadSender, new PayloadSender.CompletionHandler() {
            @Override
            public void onResponse(PayloadSender payloadSender) {
                Assert.assertEquals(payloadSender.getResponseCode(), 200);
            }

            @Override
            public void onException(PayloadSender payloadSender, Exception e) {
                Assert.fail();
            }
        });

        if (future != null) {
            future.get();
        } else {
            drainPayloadControllerQueue();
        }

        Mockito.doReturn(666).when(payloadSender).getResponseCode();
        future = PayloadController.submitPayload(payloadSender, new PayloadSender.CompletionHandler() {
            @Override
            public void onResponse(PayloadSender payloadSender) {
                Assert.assertEquals(payloadSender.getResponseCode(), 666);
                throw new RuntimeException("Response exception");
            }

            @Override
            public void onException(PayloadSender payloadSender, Exception e) {
                Assert.assertNotNull(payloadSender);
                Assert.assertNotNull(e);
            }
        });

        if (future != null) {
            future.get();
        } else {
            drainPayloadControllerQueue();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void uploadPayloadIfNetworkPresent() throws Exception {
        PayloadController.submitPayload(payloadSender);
        if (payloadSender.shouldUploadOpportunistically()) {
            Mockito.verify(PayloadController.queueExecutor).submit(Mockito.any(Callable.class));
        } else {
            Mockito.verify(PayloadController.payloadReaperQueue).offer(Mockito.any(PayloadReaper.class));
        }
    }

    @Test
    public void cachePayloadIfNetworkUnavailable() throws Exception {
        setReachabilityEnabled(false);
        Mockito.doReturn(false).when(payloadSender).shouldUploadOpportunistically();
        PayloadController.submitPayload(payloadSender);
        Mockito.verify(payloadReaperQueue, Mockito.atLeastOnce()).offer(Mockito.any(PayloadReaper.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void uploadPayloadImmediately() throws Exception {
        setReachabilityEnabled(true);
        Mockito.doReturn(true).when(payloadSender).shouldUploadOpportunistically();
        PayloadController.submitPayload(payloadSender, null);
        Mockito.verify(PayloadController.queueExecutor).submit(Mockito.any(Callable.class));
    }

    @Test
    public void testRequeueFailedUpload() throws Exception {
        Mockito.doReturn(true).when(payloadSender).shouldRetry();
        Mockito.doReturn(666).when(payloadSender).getResponseCode();

        Mockito.doCallRealMethod().when(PayloadController.queueExecutor).submit(Mockito.any(PayloadReaper.class));

        PayloadReaper payloadReaper = new PayloadReaper(payloadSender, null);

        // insert the same node into the retry queue
        PayloadController.payloadReaperRetryQueue.offer(payloadReaper);

        Future future = PayloadController.submitPayload(payloadSender);
        if (future != null) {
            future.get();
        } else {
            drainPayloadControllerQueue();
        }

        InOrder order = Mockito.inOrder(PayloadController.queueExecutor, PayloadController.payloadReaperQueue, PayloadController.payloadReaperRetryQueue);
        order.verify(PayloadController.payloadReaperQueue).remove(Mockito.any(PayloadReaper.class));
        order.verify(PayloadController.payloadReaperRetryQueue).remove(Mockito.any(PayloadReaper.class));

        if (future != null && payloadSender.shouldUploadOpportunistically()) {
            order.verify(PayloadController.queueExecutor).submit(Mockito.any(PayloadReaper.class));
        } else {
            order.verify(PayloadController.payloadReaperQueue).offer(Mockito.any(PayloadReaper.class));
        }
        order.verify(PayloadController.payloadReaperRetryQueue).offer(Mockito.any(PayloadReaper.class));

        Mockito.verify(PayloadController.payloadReaperRetryQueue, Mockito.atLeastOnce()).remove(Mockito.any(PayloadReaper.class));
        Mockito.verify(PayloadController.payloadReaperQueue, Mockito.atLeastOnce()).remove(Mockito.any(PayloadReaper.class));

        if (future != null && payloadSender.shouldUploadOpportunistically()) {
            Mockito.verify(PayloadController.queueExecutor, Mockito.times(1)).submit(Mockito.any(PayloadReaper.class));
        } else {
            Mockito.verify(PayloadController.payloadReaperQueue, Mockito.times(1)).offer(Mockito.any(PayloadReaper.class));
        }
        Mockito.verify(PayloadController.payloadReaperRetryQueue, Mockito.times(2)).offer(Mockito.any(PayloadReaper.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void submitCallable() throws Exception {
        Callable callable = new Callable() {
            @Override
            public Object call() throws Exception {
                return null;
            }
        };

        PayloadController.submitCallable(callable);
        Mockito.verify(PayloadController.queueExecutor, Mockito.atLeastOnce()).submit(callable);
    }

    @Test
    public void testDequeueRunnable() throws Exception {
        payloadReaperQueue.add(new PayloadReaper(providePayloadSender("Payload #1".getBytes()), null));
        payloadReaperQueue.add(new PayloadReaper(providePayloadSender("Payload #2".getBytes()), null));
        payloadReaperQueue.add(new PayloadReaper(providePayloadSender("Payload #3".getBytes()), null));
        Assert.assertEquals(payloadReaperQueue.size(), 3);
        PayloadController.dequeueRunnable.run();
        Mockito.verify(queueExecutor, Mockito.times(3)).submit(Mockito.any(PayloadReaper.class));
        Assert.assertEquals(payloadReaperQueue.size(), 0);
    }

    @Test
    public void testRequeueRunnable() throws Exception {
        payloadReaperRetryQueue.add(new PayloadReaper(providePayloadSender("Payload #1".getBytes()), null));
        payloadReaperRetryQueue.add(new PayloadReaper(providePayloadSender("Payload #2".getBytes()), null));
        payloadReaperRetryQueue.add(new PayloadReaper(providePayloadSender("Payload #3".getBytes()), null));
        Assert.assertEquals(payloadReaperRetryQueue.size(), 3);
        PayloadController.requeueRunnable.run();
        Mockito.verify(queueExecutor, Mockito.atLeastOnce()).submit(Mockito.any(PayloadReaper.class));
        Assert.assertEquals(payloadReaperRetryQueue.size(), 0);
    }

    @Test
    public void expireStaleCacheItems() throws Exception {
        payloadReaperRetryQueue.add(new PayloadReaper(new AgentDataSender("Payload #1".getBytes(), agentConfiguration), null));
        payloadReaperRetryQueue.add(new PayloadReaper(new AgentDataSender("Payload #2".getBytes(), agentConfiguration), null));
        payloadReaperRetryQueue.add(new PayloadReaper(new AgentDataSender("Payload #3".getBytes(), agentConfiguration), null));
        Assert.assertEquals(payloadReaperRetryQueue.size(), 3);
        Mockito.doReturn(1).when(agentConfiguration).getPayloadTTL();
        Thread.sleep(100);
        PayloadController.requeueRunnable.run();
        Mockito.verify(payloadReaperQueue, Mockito.never()).offer(Mockito.any(PayloadReaper.class));
        Assert.assertEquals(payloadReaperQueue.size(), 0);
        Assert.assertEquals(payloadReaperRetryQueue.size(), 0);
    }

    @Test
    public void testPayloadInFlight() throws Exception {
        final int limit = 10;

        Mockito.doCallRealMethod().when(PayloadController.queueExecutor).submit(Mockito.any(PayloadReaper.class));

        if (payloadSender.shouldUploadOpportunistically()) {
            for (int i = 0; i < limit; i++) {
                Assert.assertNotNull(PayloadController.submitPayload(payloadSender));
                Assert.assertTrue(PayloadController.payloadInFlight(payloadSender.getPayload()));
            }
            Mockito.verify(PayloadController.queueExecutor, Mockito.times(1)).submit(Mockito.any(PayloadReaper.class));
            Mockito.verify(PayloadController.payloadReaperQueue, Mockito.times(0)).offer(Mockito.any(PayloadReaper.class));
        } else {
            for (int i = 0; i < limit; i++) {
                PayloadController.submitPayload(payloadSender);
                Assert.assertEquals(PayloadController.payloadReaperQueue.size(), 1);
            }

            Mockito.verify(PayloadController.queueExecutor, Mockito.times(0)).submit(Mockito.any(PayloadReaper.class));
            Mockito.verify(PayloadController.payloadReaperQueue, Mockito.times(limit)).offer(Mockito.any(PayloadReaper.class));
        }

        Mockito.verify(PayloadController.payloadReaperQueue, Mockito.times(limit)).remove(Mockito.any(PayloadReaper.class));
        Mockito.verify(PayloadController.payloadReaperRetryQueue, Mockito.times(limit)).remove(Mockito.any(PayloadReaper.class));
    }

    @Test
    public void testDuplicatePayloads() throws Exception {
        Mockito.doCallRealMethod().when(PayloadController.queueExecutor).submit(Mockito.any(PayloadReaper.class));

        int limit = 10;
        for (int i = 0; i < limit; i++) {
            PayloadController.submitPayload(providePayloadSender("the coffee's just right?".getBytes()));
        }

        if (payloadSender.shouldUploadOpportunistically()) {
            Mockito.verify(PayloadController.queueExecutor, Mockito.times(limit)).submit(Mockito.any(PayloadReaper.class));
        } else {
            Mockito.verify(PayloadController.payloadReaperQueue, Mockito.times(limit)).offer(Mockito.any(PayloadReaper.class));
        }
    }

    @Test
    public void testThrottledExecutor() throws Exception {
        Mockito.doCallRealMethod().when(PayloadController.queueExecutor).submit(Mockito.any(PayloadReaper.class));

        int limit = PayloadController.ThrottledScheduledThreadPoolExecutor.THROTTLE_LIMIT * 2;
        for (int i = 0; i < limit; i++) {
            PayloadController.submitPayload(providePayloadSender(UUID.randomUUID().toString().getBytes()));
        }

        if (payloadSender.shouldUploadOpportunistically()) {
            Mockito.verify(PayloadController.queueExecutor, Mockito.times(limit)).submit(ArgumentMatchers.any(PayloadReaper.class));
        } else {
            Mockito.verify(PayloadController.payloadReaperQueue, Mockito.times(limit)).offer(ArgumentMatchers.any(PayloadReaper.class));
            drainPayloadControllerQueue();
        }

        Assert.assertTrue(StatsEngine.get().getStatsMap().containsKey(MetricNames.SUPPORTABILITY_HEX_UPLOAD_THROTTLED));
    }


    private void setReachabilityEnabled(final boolean state) {
        StubAgentImpl impl = new StubAgentImpl() {
            @Override
            public boolean hasReachableNetworkConnection(String reachableHost) {
                return state;
            }
        };
        Agent.setImpl(impl);
    }

    private PayloadSender providePayloadSender(byte[] payload) {
        return providePayloadSender(new Payload(payload));
    }

    private PayloadSender providePayloadSender(Payload payload) {
        return new PayloadSender(payload, agentConfiguration) {
            @Override
            protected HttpURLConnection getConnection() throws IOException {
                HttpURLConnection connection = Mockito.spy((HttpURLConnection) new URL("http://www.newrelic.com").openConnection());
                Mockito.doReturn(false).when(connection).getDoOutput();
                Mockito.doReturn(false).when(connection).getDoInput();
                Mockito.doReturn(HttpsURLConnection.HTTP_OK).when(connection).getResponseCode();
                Mockito.doNothing().when(connection).connect();
                return connection;
            }

            @Override
            public PayloadSender call() throws Exception {
                Thread.sleep(PayloadController.ThrottledScheduledThreadPoolExecutor.THROTTLE_SLEEP);
                return this;
            }

            @Override
            protected boolean shouldUploadOpportunistically() {
                return opportunisticUploads;
            }
        };
    }

    private void drainPayloadControllerQueue() throws ExecutionException, InterruptedException {
        PayloadController.dequeueRunnable.run();
        for (Future future : PayloadController.reapersInFlight.values()) {
            future.get();
        }
    }

}