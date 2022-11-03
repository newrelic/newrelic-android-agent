/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.distributedtracing;

import java.util.Locale;

/**
 * The primary header that identifies the entire trace (trace ID) and the
 * calling service (span/parent id).
 */
public abstract class TraceParent implements TraceHeader {
    public static final String TRACE_PARENT_HEADER = "traceparent";
    static final int TRACE_PARENT_VERSION = 0;
    static final String TRACE_PARENT_HEADER_FMT = "%s-%s-%s-%s";

    final TraceContext traceContext;
    final String parentId;

    public static TraceParent createTraceParent(TraceContext traceContext) {
        return new W3CTraceParent(traceContext);
    }

    protected TraceParent(TraceContext traceContext) {
        this.traceContext = traceContext;
        this.parentId = DistributedTracing.generateSpanId();
    }

    @Override
    public String getHeaderName() {
        return TRACE_PARENT_HEADER;
    }

    /**
     * The parent ID (aka, span ID) represents the guid of the current span,
     * and is represented as a 8 bye (16-character) hex string.
     * All bytes as zero is considered an invalid value.
     *
     * @return String representation of guid.
     */
    public String getParentId() {
        return parentId;
    }

    /**
     * Returns trace context version
     *
     * @return version formatted to spec
     */
    public String getVersion() {
        return String.format(Locale.ROOT, "%02x", TRACE_PARENT_VERSION);
    }

    static class W3CTraceParent extends TraceParent {
        final static String TRACE_PARENT_HEADER_REGEX = "^(\\d+)-([A-Fa-f0-9]{32})-([A-Fa-f0-9]{16})?-(\\d+)$";

        W3CTraceParent(TraceContext traceContext) {
            super(traceContext);
        }

        /**
         * W3C Trace Parent format: version "-" trace-id "-" parent-id "-" trace-flags
         * <p>
         * Fields:
         * trace-id: 16 byte array identifier. All zeroes forbidden
         * parent-id: 8 byte array identifier. All zeroes forbidden
         * trace-flags: 8 bit (1 byte) flag. Currently, only one bit is used.
         * version: Assumes version 00. Version ff is forbidden
         * <p>
         * Example:
         * base16(version) = 00
         * base16(trace-id) = 4bf92f3577b34da6a3ce929d0e0e4736
         * base16(parent-id) = 00f067aa0ba902b7
         * base16(trace-flags) = 00  // not sampled
         * Value: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-00
         *
         **/
        @Override
        public String getHeaderValue() {
            return String.format(Locale.ROOT,
                    TRACE_PARENT_HEADER_FMT,
                    getVersion(),
                    traceContext.traceId,
                    parentId,
                    traceContext.getSampled());
        }
    }
}
