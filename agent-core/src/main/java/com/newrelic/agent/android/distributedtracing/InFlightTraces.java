/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.distributedtracing;

import com.newrelic.agent.android.FeatureFlag;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * The distributed traces of the instrumented requests currently in flight, so a log record
 * written while one is in flight can carry its trace.id and span.id.
 * <p>
 * Fed by {@link DistributedTracing} when a trace starts, independent of whichever
 * {@link TraceListener} the app registered, so replacing or removing the app's listener cannot
 * disable log correlation.
 * <p>
 * Requests can overlap and a log call has no tie to any one of them, so the most recently
 * started request still in flight is the best available answer. {@link #traceFinished} (called
 * when the transaction ends) is the primary way an entry leaves. The weak reference and the cap
 * of {@code MAX_IN_FLIGHT_TRACES} are only a backstop for a call that is created but never
 * completes: a finished request's trace context can stay reachable until harvest, so the weak
 * reference alone does not release it.
 */
public class InFlightTraces {
    // Far above the number of requests genuinely in flight at once
    static final int MAX_IN_FLIGHT_TRACES = 64;

    private static final InFlightTraces instance = new InFlightTraces();

    private static final class Entry {
        final WeakReference<TraceContext> owner;
        final String traceId;
        final String spanId;
        // Built once per request, not once per log record; immutable, so safe to hand to any thread
        final Map<String, String> attributes;

        Entry(WeakReference<Object> owner, String traceId, String spanId) {
            this.owner = owner;
            this.traceId = traceId;
            this.spanId = spanId;

            final Map<String, String> ids = new HashMap<>(4);
            ids.put(DistributedTracing.NR_TRACE_ID_ATTRIBUTE, traceId);
            ids.put(DistributedTracing.NR_SPAN_ID_ATTRIBUTE, spanId);
            this.attributes = Collections.unmodifiableMap(ids);
        }
    }

    // Ordered oldest to newest
    private final LinkedList<Entry> traces = new LinkedList<>();

    public static InFlightTraces getInstance() {
        return instance;
    }

    /**
     * Records a newly started trace. Takes the ids from the trace context itself, not from its
     * request context: that map is handed to the app's TraceListener, which may overwrite trace.id.
     * Ignored when distributed tracing is disabled.
     */
    void traceStarted(TraceContext traceContext) {
        if (traceContext == null || !FeatureFlag.featureEnabled(FeatureFlag.DistributedTracing)) {
            return;
        }

        // Held weakly: released once the trace context is unreachable, or earlier by traceFinished
        register(new WeakReference<Object>(traceContext),
                traceContext.getTraceId(),
                traceContext.getTracePayload().getSpanId());
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
     * @return trace.id and span.id of the most recently started request still in flight, as an
     * unmodifiable map shared by every call for that request, or null when none is
     */
    public Map<String, String> currentTraceAttributes() {
        synchronized (this) {
            pruneReleased();

            final Entry newest = traces.peekLast();
            return (newest == null) ? null : newest.attributes;
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
