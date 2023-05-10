/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.distributedtracing;

import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.instrumentation.TransactionState;
import com.newrelic.agent.android.test.mock.Providers;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TraceFacadeTest {
    private final TraceFacade traceFacade = DistributedTracing.getInstance();
    private TransactionState transactionState;


    @BeforeClass
    public static void beforeClass() throws Exception {
        FeatureFlag.enableFeature(FeatureFlag.DistributedTracing);
    }

    @Before
    public void before() {
        TraceConfiguration config = new TraceConfiguration("1", "1", "");
        TraceConfiguration.setInstance(config);
        transactionState = Providers.provideTransactionState();
    }

    @Test
    public void payloadIntegration() throws Exception {
        TraceContext traceContext = traceFacade.startTrace(transactionState);
        TracePayload requestPayload = traceContext.getTracePayload();
        Assert.assertEquals(traceContext.getTraceId(), requestPayload.traceContext.traceId);
    }

    @Test
    public void payloadAsJson() throws Exception {
        TraceContext traceContext = traceFacade.startTrace(transactionState);
        TracePayload requestPayload = traceContext.getTracePayload();
        Assert.assertNotNull(requestPayload.asJson().toString());
    }

    @Test
    public void consistencyBetweenPayloads() throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(2);

        Callable<TraceContext> tripCallable = new Callable<TraceContext>() {
            @Override
            public TraceContext call() throws Exception {
                return traceFacade.startTrace(transactionState);
            }
        };

        final TraceContext traceContext = (TraceContext) executorService.submit(tripCallable).get();

        Callable<TracePayload> callable = new Callable<TracePayload>() {
            @Override
            public TracePayload call() throws Exception {
                return traceContext.tracePayload;
            }
        };

        TracePayload payload1 = (TracePayload) executorService.submit(callable).get();
        TracePayload payload2 = (TracePayload) executorService.submit(callable).get();

        Assert.assertTrue(traceContext.getTraceId().equals(payload1.getTraceId())
                && payload1.getTraceId().equals(payload2.getTraceId()));
    }
}