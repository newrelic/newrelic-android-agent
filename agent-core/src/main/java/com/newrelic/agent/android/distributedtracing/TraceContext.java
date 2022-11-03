/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.distributedtracing;

import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.stats.StatsEngine;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * New Relic agents MUST be capable of generating, reading, and propagating well-formed
 * values for the two Trace Context headers: traceparent and tracestate, and must behave
 * according to the W3C spec with regard to malformed values.
 * <p>
 * https://source.datanerd.us/agents/agent-specs/blob/master/distributed_tracing/Trace-Context-Payload.md
 */

public abstract class TraceContext {
    protected static final AgentLog log = AgentLogManager.getAgentLog();

    static final String TRACE_FIELD_UNUSED = "";

    public static final String INVALID_TRACE_ID = "00000000000000000000000000000000";
    public static final String INVALID_SPAN_ID = "0000000000000000";

    final static String TRACE_ID_REGEX = "^[A-Fa-f0-9]{32}";
    final static String SPAN_ID_REGEX = "^([A-Fa-f0-9]{16})?";

    public static final String SUPPORTABILITY_TRACE_CONTEXT_CREATED = "Supportability/TraceContext/Create/Success";
    public static final String SUPPORTABILITY_TRACE_CONTEXT_EXCEPTION = "Supportability/TraceContext/Create/Exception/%s";

    final TraceConfiguration traceConfiguration;
    final String traceId;               // unique id (guid) for this trace
    final TraceParent traceParent;
    final TraceState traceState;
    final TracePayload tracePayload;    // legacy NewRelic trace payload
    final Map<String, String> requestContext;

    boolean legacyHeadersEnabled = true;

    /**
     * Trace context factory
     *
     * @param requestContext
     * @return Allocated trace context
     */
    public static TraceContext createTraceContext(Map<String, String> requestContext) {
        W3CTraceContext traceContext = new W3CTraceContext(requestContext);
        return traceContext;
    }

    public TraceContext(Map<String, String> requestContext) {
        this.traceConfiguration = TraceConfiguration.getInstance();
        this.requestContext = (requestContext == null ? new HashMap<String, String>() : requestContext);
        this.traceId = DistributedTracing.generateTraceId();
        this.traceParent = TraceParent.createTraceParent(this);
        this.traceState = TraceState.createTraceState(this);
        this.tracePayload = new TracePayload(this);

        this.requestContext.put("thread.id", String.valueOf(Thread.currentThread().getId()));
    }

    /**
     * A trace ID is represented as a 16-byte (32 char) array.
     * All bytes as zero is considered an invalid value.
     * This identifier is constant for the entire trace.
     *
     * @return String representation of guid.
     */
    public String getTraceId() {
        return traceId;
    }

    /**
     * The parent ID (aka, span ID) represents the guid of the current span,
     * and is represented as a 8 bye (16-character) hex string.
     * All bytes as zero is considered an invalid value.
     * Mobile spans are *always* root spans, so parent should always be empty
     *
     * @return String representation of guid.
     */
    public String getParentId() {
        return traceParent.getParentId();
    }

    /**
     * Returns the sampled field per the spec. Conforming agents MUST pass an
     * empty string if they do not sample traces.
     *
     * @return sampled state formatted to spec
     */
    public String getSampled() {
        return String.format(Locale.ROOT, "%02x", traceConfiguration.isSampled() ? 1 : 0);
    }

    /**
     * Returns trace vendor
     * @return vendor formatted to spec
     */
    public String getVendor() {
        return String.format(Locale.ROOT, "%s@nr", traceConfiguration.trustedAccountId);
    }

    /**
     * @return Account ID formatted to spec
     */
    public String getAccountId() {
        return String.format(Locale.ROOT, "%s", traceConfiguration.accountId);
    }

    /**
     * @return Application ID formatted to spec
     */
    public String getApplicationId() {
        return String.format(Locale.ROOT, "%s", traceConfiguration.applicationId);
    }

    /**
     * Returns all headers included this trace context.
     *
     * @return Collection (set) of all headers for this instance.
     */
    public Set<TraceHeader> getHeaders() {
        return new HashSet<TraceHeader>() {{
            if (legacyHeadersEnabled) {
                add(tracePayload);
            }
        }};
    }

    public TracePayload getTracePayload() {
        return tracePayload;
    }

    public static void reportSupportabilityMetrics() {
        StatsEngine.get().inc(SUPPORTABILITY_TRACE_CONTEXT_CREATED);
    }

    public static void reportSupportabilityExceptionMetric(Exception e) {
        log.error("setDistributedTraceHeaders: Unable to add trace headers. ", e);
        StatsEngine.get().inc(String.format(Locale.ROOT,
                TraceContext.SUPPORTABILITY_TRACE_CONTEXT_EXCEPTION, e.getClass().getSimpleName()));
    }

    public Map<String, Object> asTraceAttributes() {
        final Map<String, Object> attributeMap = new HashMap<String, Object>() {{
            put(DistributedTracing.NR_ID_ATTRIBUTE, tracePayload.spanId);
            put(DistributedTracing.NR_GUID_ATTRIBUTE, tracePayload.spanId);  // deprecated, but include for now
            put(DistributedTracing.NR_TRACE_ID_ATTRIBUTE, traceId);
        }};

        return attributeMap;
    }

    public void putRequestContext(Map<String, String> requestContextAsMap) {
        if (requestContextAsMap != null) {
            requestContext.putAll(requestContextAsMap);
        }
    }

    public Map<String, String> getRequestContext() {
        return requestContext;
    }

    /**
     * W3C trace context is split into two individual propagation fields supporting
     * interoperability and vendor-specific extensibility:
     *
     * traceparent describes the position of the incoming request in its trace graph in a portable,
     * fixed-length format. Its design focuses on fast parsing. Every tracing tool MUST properly
     * set traceparent even when it only relies on vendor-specific information in tracestate
     *
     * tracestate extends traceparent with vendor-specific data represented by a set of name/value
     * pairs. Storing information in tracestate is optional.
     *
     * Specs:
     * https://www.w3.org/TR/trace-context/
     * https://w3c.github.io/trace-context/
     * https://w3c.github.io/trace-context/#trace-context-http-request-headers-format
     */
    static class W3CTraceContext extends TraceContext {

        public W3CTraceContext(Map<String, String> requestContext) {
            super(requestContext);
        }

        @Override
        public String getParentId() {
            return traceParent.getParentId();
        }

        public Set<TraceHeader> getHeaders() {
            Set<TraceHeader> headers = super.getHeaders();

            headers.add(traceParent);
            headers.add(traceState);

            return headers;
        }

    }
}
