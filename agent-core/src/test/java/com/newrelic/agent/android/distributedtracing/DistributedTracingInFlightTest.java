/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.distributedtracing;

import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.instrumentation.TransactionState;
import com.newrelic.agent.android.test.mock.Providers;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DistributedTracingInFlightTest {

    private TransactionState transactionState;

    @Before
    public void setUp() {
        FeatureFlag.enableFeature(FeatureFlag.DistributedTracing);
        DistributedTracing.setDistributedTraceListener(null);
        InFlightTraces.getInstance().reset();
        transactionState = Providers.provideTransactionState();
    }

    @After
    public void tearDown() {
        FeatureFlag.enableFeature(FeatureFlag.DistributedTracing);
        DistributedTracing.setDistributedTraceListener(null);
        InFlightTraces.getInstance().reset();
    }

    private static void assertInFlight(TraceContext traceContext) {
        final Map<String, String> current = InFlightTraces.getInstance().currentTraceAttributes();

        Assert.assertNotNull(current);
        Assert.assertEquals(traceContext.getTraceId(), current.get(DistributedTracing.NR_TRACE_ID_ATTRIBUTE));
        Assert.assertEquals(traceContext.getTracePayload().getSpanId(), current.get(DistributedTracing.NR_SPAN_ID_ATTRIBUTE));
    }

    @Test
    public void startTraceRegistersTheTraceWithTheDefaultListener() {
        final TraceContext traceContext = DistributedTracing.getInstance().startTrace(transactionState);

        assertInFlight(traceContext);
    }

    @Test
    public void startTraceRegistersTheTraceAndStillNotifiesAnAppListener() {
        final List<String> callbacks = new ArrayList<>();
        DistributedTracing.setDistributedTraceListener(new TraceListener() {
            @Override
            public void onTraceCreated(Map<String, String> requestContext) {
                callbacks.add("trace:" + requestContext.get(DistributedTracing.NR_TRACE_ID_ATTRIBUTE));
            }

            @Override
            public void onSpanCreated(Map<String, String> requestContext) {
                callbacks.add("span:" + requestContext.get(DistributedTracing.NR_SPAN_ID_ATTRIBUTE));
            }
        });

        final TraceContext traceContext = DistributedTracing.getInstance().startTrace(transactionState);

        assertInFlight(traceContext);
        Assert.assertEquals(2, callbacks.size());
        Assert.assertEquals("trace:" + traceContext.getTraceId(), callbacks.get(0));
        Assert.assertEquals("span:" + traceContext.getTracePayload().getSpanId(), callbacks.get(1));
    }

    @Test
    public void startTraceRegistersTheTraceWhenTheAppListenerThrows() {
        final TraceListener throwingListener = new TraceListener() {
            @Override
            public void onTraceCreated(Map<String, String> requestContext) {
                throw new IllegalStateException("onTraceCreated");
            }

            @Override
            public void onSpanCreated(Map<String, String> requestContext) {
                throw new IllegalStateException("onSpanCreated");
            }
        };
        DistributedTracing.setDistributedTraceListener(throwingListener);

        final TraceContext traceContext = DistributedTracing.getInstance().startTrace(transactionState);

        assertInFlight(traceContext);
        Assert.assertSame("The throwing listener is removed, as before",
                DistributedTracing.getInstance(), DistributedTracing.instance.traceListener.get());
    }

    @Test
    public void startTraceRegistersNothingWhenDistributedTracingIsDisabled() {
        // TransactionStateUtil.setTrace() reaches startTrace() without checking the flag
        FeatureFlag.disableFeature(FeatureFlag.DistributedTracing);

        final TraceContext traceContext = DistributedTracing.getInstance().startTrace(transactionState);

        Assert.assertNotNull(traceContext);
        Assert.assertNull(InFlightTraces.getInstance().currentTraceAttributes());
    }
}
