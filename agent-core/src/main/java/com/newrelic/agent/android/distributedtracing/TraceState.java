/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.distributedtracing;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A required header that carries vendor-specific information and tracks where a trace has been.
 *
 * The tracestate header may contain many entries. Some of these may be from other APM vendors,
 * and some may be from other New Relic customers. Only trace state entries with a matching
 * trusted_account_key and the '@nr' key should be used.The existence of multiple New Relic entries
 * allows traces to cross trusted account boundaries within New Relic without breaking.
 */
public abstract class TraceState implements TraceHeader {
    public static final String TRACE_STATE_HEADER = "tracestate";
    static final int TRACE_STATE_VERSION = 0;
    static final int TRACE_STATE_PARENT_TYPE = 2;    // Mobile

    final TraceContext traceContext;
    final long timestampMs;
    final Map<String, String> entries;

    public static TraceState createTraceState(TraceContext traceContext) {
        return new W3CTraceState(traceContext);
    }

    TraceState(TraceContext traceContext) {
        this.traceContext = traceContext;
        this.timestampMs = DistributedTracing.generateNormalizedTimestamp();
        this.entries = new HashMap<>();
    }

    @Override
    public String getHeaderName() {
        return TRACE_STATE_HEADER;
    }

    static class W3CTraceState extends TraceState {
        /**
         * TraceState format: vendor "=" parent-id [,vendor=parent-id,...]
         * list: contains a key/value pair
         *
         * Fields:
         * vendor: 16 byte array identifier. All zeroes forbidden
         * parent-id: 8 byte array identifier. All zeroes forbidden
         *
         * key MUST begin with a lowercase letter or a digit and contain up to 256 characters
         * including [a-z, 0-9, _, -, *, /, and @].
         *
         * value is an opaque string containing up to 256 printable ASCII [RFC0020]
         * characters (i.e., the range 0x20 to 0x7E) except comma (,) and (=).
         * The string must end with a character which is not a space (0x20).
         *
         * Example:
         * Single tracing system (generic format): trustedAccountId@nr=00f067aa0ba902b7
         * Multiple tracing systems (with different formatting): trustedAccountId@nr=00f067aa0ba902b7,bongo=t61rcWkgMzE
         *
         **/
        static final String TRACE_STATE_HEADER_FMT = "%1d-%1d-%s-%s-%s-%s-%s-%s-%d";
        final static String TRACE_STATE_HEADER_REGEX = "^(\\d+)?@nr=(\\d)-(\\d)-(\\d+)?-(\\d+)?-(\\w+)?-(\\w)?-(\\d{1,2})?-(\\w)?-(\\d+)$";
        final static String TRACE_STATE_VENDOR_REGEX = "^(\\d+?)(@nr)(=.*)?";
        final static String TRACE_STATE_ENTRY_REGEX = "(\\d)-(\\d+)-(\\d+)?-(\\d+)?-(\\w*)?-(\\w+)?-(\\d{1,2})?-(\\w)?-(\\d+)$";

        W3CTraceState(TraceContext traceContext) {
            super(traceContext);
            entries.put(traceContext.getVendor(), getVendorState());
        }

        /**
         * The trusted account key MUST be followed by @nr as the vendor key. The nr key has
         * been assigned to NewRelic by the W3C; we may not use any other key.
         *
         * The value is a single string containing 9 fields, delimited with a single dash (-).
         * When a field is not applicable to a trace, an empty string must be used in its place.
         *
         * Example "190@nr=0-2-332029-2827902-5f474d64b9cc9b2a-7d3efb1b173fecfa---1518469636035"
         *
         * @return Formatted, validated trace state header value.
         */
        @Override
        public String getHeaderValue() {
            StringBuilder builder = new StringBuilder();
            for (String key : entries.keySet()) {
                builder.append(String.format("%s=%s,", key, entries.get(key)));
            }
            builder.deleteCharAt(builder.length() - 1);
            return builder.toString();
        }

        String getVendorState() {
            return String.format(Locale.ROOT,
                    TRACE_STATE_HEADER_FMT,
                    TraceState.TRACE_STATE_VERSION,         // trace state version
                    TraceState.TRACE_STATE_PARENT_TYPE,     // trace parent type (always Mobile)
                    traceContext.getAccountId(),            // account identifier of the application
                    traceContext.getApplicationId(),        // the app identifier
                    traceContext.getParentId(),             // guid of the calling span (parent)
                    TraceContext.TRACE_FIELD_UNUSED,        // transactionId
                    TraceContext.TRACE_FIELD_UNUSED,        // sampled
                    TraceContext.TRACE_FIELD_UNUSED,        // priority (omitted for Mobile)
                    timestampMs);
        }
    }
}
