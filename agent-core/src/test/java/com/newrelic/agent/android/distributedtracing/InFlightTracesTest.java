/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.distributedtracing;

import com.newrelic.agent.android.FeatureFlag;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InFlightTracesTest {

    private final InFlightTraces traces = InFlightTraces.getInstance();

    // Owners are held weakly by the tracker, so keep them reachable for the length of each test
    private final List<Object> owners = new ArrayList<>();

    @Before
    public void setUp() {
        FeatureFlag.enableFeature(FeatureFlag.DistributedTracing);
        traces.reset();
    }

    @After
    public void tearDown() {
        traces.reset();
        FeatureFlag.enableFeature(FeatureFlag.DistributedTracing);
    }

    private void start(String traceId, String spanId) {
        final Object owner = new Object();
        owners.add(owner);
        traces.register(new WeakReference<>(owner), traceId, spanId);
    }

    private static void assertCurrent(Map<String, String> attributes, String traceId, String spanId) {
        Assert.assertNotNull(attributes);
        Assert.assertEquals(traceId, attributes.get(DistributedTracing.NR_TRACE_ID_ATTRIBUTE));
        Assert.assertEquals(spanId, attributes.get(DistributedTracing.NR_SPAN_ID_ATTRIBUTE));
    }

    @Test
    public void noCurrentTraceWhenNothingIsInFlight() {
        Assert.assertNull(traces.currentTraceAttributes());
    }

    @Test
    public void newestStartedTraceIsCurrent() {
        start("trace-1", "span-1");
        start("trace-2", "span-2");

        assertCurrent(traces.currentTraceAttributes(), "trace-2", "span-2");
    }

    @Test
    public void finishingTheNewestFallsBackToThePrevious() {
        start("trace-1", "span-1");
        start("trace-2", "span-2");

        traces.traceFinished("trace-2");

        assertCurrent(traces.currentTraceAttributes(), "trace-1", "span-1");
    }

    @Test
    public void finishingAnOlderTraceLeavesTheNewestCurrent() {
        start("trace-1", "span-1");
        start("trace-2", "span-2");

        traces.traceFinished("trace-1");

        assertCurrent(traces.currentTraceAttributes(), "trace-2", "span-2");
    }

    @Test
    public void finishingTheLastTraceLeavesNoCurrentTrace() {
        start("trace-1", "span-1");

        traces.traceFinished("trace-1");

        Assert.assertNull(traces.currentTraceAttributes());
    }

    @Test
    public void finishingIsIdempotentAndToleratesUnknownIds() {
        start("trace-1", "span-1");

        traces.traceFinished("unknown");
        traces.traceFinished(null);
        traces.traceFinished("trace-1");
        traces.traceFinished("trace-1");

        Assert.assertNull(traces.currentTraceAttributes());
        Assert.assertEquals(0, traces.size());
    }

    @Test
    public void sizeIsCappedAndTheOldestIsEvicted() {
        for (int i = 0; i <= InFlightTraces.MAX_IN_FLIGHT_TRACES; i++) {
            start("trace-" + i, "span-" + i);
        }

        Assert.assertEquals(InFlightTraces.MAX_IN_FLIGHT_TRACES, traces.size());
        assertCurrent(traces.currentTraceAttributes(),
                "trace-" + InFlightTraces.MAX_IN_FLIGHT_TRACES,
                "span-" + InFlightTraces.MAX_IN_FLIGHT_TRACES);

        traces.traceFinished("trace-0");    // already evicted: nothing to remove
        Assert.assertEquals(InFlightTraces.MAX_IN_FLIGHT_TRACES, traces.size());

        traces.traceFinished("trace-1");    // oldest survivor
        Assert.assertEquals(InFlightTraces.MAX_IN_FLIGHT_TRACES - 1, traces.size());
    }

    @Test
    public void releasedOwnersAreNotCurrent() {
        start("trace-1", "span-1");

        final WeakReference<Object> released = new WeakReference<>(new Object());
        released.clear();
        traces.register(released, "trace-2", "span-2");

        assertCurrent(traces.currentTraceAttributes(), "trace-1", "span-1");
        Assert.assertEquals(1, traces.size());
    }

    @Test
    public void onSpanCreatedRegistersTheTraceFromTheRequestContext() {
        final Map<String, String> requestContext = new HashMap<>();
        requestContext.put(DistributedTracing.NR_TRACE_ID_ATTRIBUTE, "trace-1");
        requestContext.put(DistributedTracing.NR_SPAN_ID_ATTRIBUTE, "span-1");

        traces.onSpanCreated(requestContext);

        assertCurrent(traces.currentTraceAttributes(), "trace-1", "span-1");
        Reference.reachabilityFence(requestContext);
    }

    @Test
    public void onSpanCreatedIgnoresARequestContextWithoutBothIds() {
        final Map<String, String> missingSpan = new HashMap<>();
        missingSpan.put(DistributedTracing.NR_TRACE_ID_ATTRIBUTE, "trace-1");
        traces.onSpanCreated(missingSpan);

        final Map<String, String> missingTrace = new HashMap<>();
        missingTrace.put(DistributedTracing.NR_SPAN_ID_ATTRIBUTE, "span-1");
        traces.onSpanCreated(missingTrace);

        traces.onSpanCreated(null);

        Assert.assertNull(traces.currentTraceAttributes());
    }

    @Test
    public void onSpanCreatedIgnoredWhenDistributedTracingIsDisabled() {
        FeatureFlag.disableFeature(FeatureFlag.DistributedTracing);

        final Map<String, String> requestContext = new HashMap<>();
        requestContext.put(DistributedTracing.NR_TRACE_ID_ATTRIBUTE, "trace-1");
        requestContext.put(DistributedTracing.NR_SPAN_ID_ATTRIBUTE, "span-1");
        traces.onSpanCreated(requestContext);

        Assert.assertEquals(0, traces.size());
        Reference.reachabilityFence(requestContext);
    }

    @Test
    public void onTraceCreatedDoesNothing() {
        final Map<String, String> requestContext = new HashMap<>();
        requestContext.put(DistributedTracing.NR_TRACE_ID_ATTRIBUTE, "trace-1");

        traces.onTraceCreated(requestContext);

        Assert.assertEquals(0, traces.size());
    }
}
