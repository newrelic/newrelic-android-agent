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
import org.junit.Test;

import java.util.Map;

public class TraceListenerTest {

    private TransactionState transactionState;
    private TraceContext traceContext;

    @Before
    public void setUp() throws Exception {
        FeatureFlag.enableFeature(FeatureFlag.DistributedTracing);
        transactionState = Providers.provideTransactionState();
        traceContext = DistributedTracing.getInstance().startTrace(transactionState);
    }

    @Test
    public void testDistributedTraceListener() {
        final TraceListener listener = new TraceListener() {
            @Override
            public void onTraceCreated(Map<String, String> requestContext) {
                Assert.assertTrue(requestContext.containsKey(DistributedTracing.NR_TRACE_ID_ATTRIBUTE));
            }

            @Override
            public void onSpanCreated(Map<String, String> requestContext) {
                Assert.assertTrue(requestContext.containsKey(DistributedTracing.NR_SPAN_ID_ATTRIBUTE));
            }
        };

        FeatureFlag.disableFeature(FeatureFlag.DistributedTracing);
        DistributedTracing.setDistributedTraceListener(listener);
        Assert.assertEquals(DistributedTracing.instance.traceListener.get(), DistributedTracing.instance);

        FeatureFlag.enableFeature(FeatureFlag.DistributedTracing);
        DistributedTracing.setDistributedTraceListener(null);
        Assert.assertNotNull(DistributedTracing.instance.traceListener.get());
        Assert.assertEquals(DistributedTracing.instance.traceListener.get(), DistributedTracing.instance);

        DistributedTracing.setDistributedTraceListener(listener);
        Assert.assertEquals(listener, DistributedTracing.instance.traceListener.get());
        Assert.assertNotNull(DistributedTracing.instance.traceListener.get());
        Assert.assertEquals(DistributedTracing.instance.traceListener.get(), listener);
    }

    @Test
    public void testThrowingDistributedTraceListener() {
        final String listenerTraceId = DistributedTracing.generateTraceId();
        final String listenerPayloadId = DistributedTracing.generateSpanId();
        final TraceListener throwingListener = new TraceListener() {
            @Override
            public void onTraceCreated(Map<String, String> requestContext) {
                throw new NullPointerException(listenerTraceId);
            }

            @Override
            public void onSpanCreated(Map<String, String> requestContext) {
                throw new ArrayIndexOutOfBoundsException(listenerPayloadId);
            }
        };

        DistributedTracing.setDistributedTraceListener(throwingListener);
        Assert.assertEquals(throwingListener, DistributedTracing.instance.traceListener.get());

        TraceContext traceContext = TraceContext.createTraceContext(null);
        Assert.assertNotEquals(listenerTraceId, traceContext.getTraceId());
        Assert.assertNotEquals(listenerPayloadId, traceContext.getTracePayload().getSpanId());
    }

    @Test
    public void testInvalidDistributedTraceListener() {
        final TraceListener invalidListener = new TraceListener() {
            @Override
            public void onTraceCreated(Map<String, String> requestContext) {
            }

            @Override
            public void onSpanCreated(Map<String, String> requestContext) {
            }
        };

        DistributedTracing.setDistributedTraceListener(invalidListener);
        Assert.assertEquals(invalidListener, DistributedTracing.instance.traceListener.get());

        TraceContext traceContext = TraceContext.createTraceContext(null);

        Assert.assertNotNull(traceContext.getTraceId());
        Assert.assertFalse(traceContext.getTraceId().isEmpty());
        Assert.assertEquals(32, traceContext.getTraceId().length());

        Assert.assertNotNull(traceContext.getTracePayload());
        Assert.assertFalse(traceContext.getTracePayload().getSpanId().isEmpty());
        Assert.assertEquals(16, traceContext.getTracePayload().getSpanId().length());
    }

    public void testInvalidTraceIds() {
        final TraceListener listener = new TraceListener() {
            @Override
            public void onTraceCreated(Map<String, String> requestContext) {

            }
            @Override
            public void onSpanCreated(Map<String, String> requestContext) {

            }
        };

        DistributedTracing.setDistributedTraceListener(listener);
        Assert.assertEquals(listener, DistributedTracing.instance.traceListener.get());

        TraceContext traceContext = TraceContext.createTraceContext(null);

        Assert.assertNotNull(traceContext.getTraceId());
        Assert.assertFalse(traceContext.getTraceId().isEmpty());
        Assert.assertEquals(32, traceContext.getTraceId().length());
        Assert.assertNotEquals(TraceContext.INVALID_TRACE_ID, traceContext.getTraceId());

        Assert.assertNotNull(traceContext.getTracePayload());
        Assert.assertFalse(traceContext.getTracePayload().getSpanId().isEmpty());
        Assert.assertEquals(16, traceContext.getTracePayload().getSpanId().length());
        Assert.assertNotEquals(TraceContext.INVALID_TRACE_ID, traceContext.getTraceId());
    }

}