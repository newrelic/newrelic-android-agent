/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.distributedtracing;

import com.newrelic.agent.android.FeatureFlag;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * The distributed traces of the instrumented requests currently in flight, so a log record
 * written while one is in flight can carry its trace.id and span.id.
 * <p>
 * Registered through the {@link TraceListener} hook. {@link DistributedTracing} always notifies
 * this listener, independent of whichever listener the app registered, so replacing or
 * removing the app's listener cannot disable log correlation.
 * <p>
 * Requests can overlap and a log call has no tie to any one of them, so the most recently
 * started request still in flight is the best available answer. Entries are held weakly and the
 * list is capped, so a request that never reports back (a call that is created but never
 * executed) cannot tag every later log record.
 */
public class InFlightTraces implements TraceListener {
    // Far above the number of requests genuinely in flight at once
    static final int MAX_IN_FLIGHT_TRACES = 64;

    private static final InFlightTraces instance = new InFlightTraces();

    private static final class Entry {
        final WeakReference<Object> owner;
        final String traceId;
        final String spanId;

        Entry(WeakReference<Object> owner, String traceId, String spanId) {
            this.owner = owner;
            this.traceId = traceId;
            this.spanId = spanId;
        }
    }

    // Ordered oldest to newest
    private final LinkedList<Entry> traces = new LinkedList<>();

    public static InFlightTraces getInstance() {
        return instance;
    }

    @Override
    public void onTraceCreated(Map<String, String> requestContext) {
        // The span id is not in the request context yet: register in onSpanCreated
    }

    @Override
    public void onSpanCreated(Map<String, String> requestContext) {
        if (requestContext == null || !FeatureFlag.featureEnabled(FeatureFlag.DistributedTracing)) {
            return;
        }

        // The request context lives exactly as long as its TraceContext, so it stands in as the owner
        register(new WeakReference<Object>(requestContext),
                requestContext.get(DistributedTracing.NR_TRACE_ID_ATTRIBUTE),
                requestContext.get(DistributedTracing.NR_SPAN_ID_ATTRIBUTE));
    }

    void register(WeakReference<Object> owner, String traceId, String spanId) {
        if (owner == null || isEmpty(traceId) || isEmpty(spanId)) {
            return;
        }

        synchronized (this) {
            pruneReleased();
            traces.addLast(new Entry(owner, traceId, spanId));
            if (traces.size() > MAX_IN_FLIGHT_TRACES) {
                traces.removeFirst();
            }
        }
    }

    /**
     * Marks the trace as no longer in flight. Safe to call more than once, or for an unknown id.
     */
    public void traceFinished(String traceId) {
        if (traceId == null) {
            return;
        }

        synchronized (this) {
            traces.removeIf(entry -> entry.owner.get() == null || entry.traceId.equals(traceId));
        }
    }

    /**
     * @return trace.id and span.id of the most recently started request still in flight,
     * or null when none is
     */
    public Map<String, String> currentTraceAttributes() {
        synchronized (this) {
            pruneReleased();

            final Entry newest = traces.peekLast();
            if (newest == null) {
                return null;
            }

            final Map<String, String> attributes = new HashMap<>(4);
            attributes.put(DistributedTracing.NR_TRACE_ID_ATTRIBUTE, newest.traceId);
            attributes.put(DistributedTracing.NR_SPAN_ID_ATTRIBUTE, newest.spanId);
            return attributes;
        }
    }

    public synchronized void reset() {
        traces.clear();
    }

    // Package-visible for tests
    synchronized int size() {
        return traces.size();
    }

    // Callers hold the lock
    private void pruneReleased() {
        traces.removeIf(entry -> entry.owner.get() == null);
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }
}
