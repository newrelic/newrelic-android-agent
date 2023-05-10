/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.distributedtracing;

import junit.framework.TestCase;

import org.junit.Assert;
import org.junit.Test;

import java.util.Locale;

public class TraceStateTest extends TestCase {
    static final String TRACE_STATE_DELIMITER = "=";
    static final String TRACE_STATE_ENTRY_DELIMITER = ",";
    static final String TRACE_FIELD_DELIMITER = "-";

    private TraceContext traceContext;
    private TraceState traceState;

    @Override
    public void setUp() throws Exception {
        TraceConfiguration.setInstance(new TraceConfiguration("1", "22", "333"));
        traceContext = TraceContext.createTraceContext(null);
        traceState = traceContext.traceState;
    }

    @Test
    public void testGetHeaderValue() {
        String headerValue = traceState.getHeaderValue();
        Assert.assertTrue(isValidTraceHeaderValue(headerValue));
    }

    @Test
    public void testShouldProvideVendorInTraceState() {
        String[] entries = traceState.getHeaderValue().split(TRACE_STATE_ENTRY_DELIMITER);
        for (String entry : entries) {
            if (entry.matches(TraceState.W3CTraceState.TRACE_STATE_VENDOR_REGEX)) {
                String[] vendorParts = entry.split(TRACE_STATE_DELIMITER);
                Assert.assertEquals(2, vendorParts.length);
                Assert.assertEquals(traceContext.getVendor(), vendorParts[0]);
            }
        }
    }

    @Test
    public void testShouldIgnoreOtherVendors() {
        Assert.assertFalse("koko@4026".matches(TraceState.W3CTraceState.TRACE_STATE_VENDOR_REGEX));
        Assert.assertFalse("1234567890abcdef".matches(TraceState.W3CTraceState.TRACE_STATE_VENDOR_REGEX));
        Assert.assertTrue(traceContext.getVendor().matches(TraceState.W3CTraceState.TRACE_STATE_VENDOR_REGEX));
    }

    boolean isValidTraceHeaderValue(String headerValue) {
        Assert.assertNotNull(headerValue);
        Assert.assertFalse(headerValue.isEmpty());

        String[] headerEntries = headerValue.split(TRACE_STATE_ENTRY_DELIMITER);
        for (String entry : headerEntries) {
            if (!entry.matches(TraceState.W3CTraceState.TRACE_STATE_VENDOR_REGEX)) {
                continue;
            }

            Assert.assertTrue(entry.matches(TraceState.W3CTraceState.TRACE_STATE_HEADER_REGEX));

            String[] keyValue = entry.split(TRACE_STATE_DELIMITER);
            Assert.assertEquals(keyValue[0], traceContext.getVendor());
            Assert.assertTrue(keyValue[1].matches(TraceState.W3CTraceState.TRACE_STATE_ENTRY_REGEX));

            String[] fields = keyValue[1].split(TRACE_FIELD_DELIMITER);
            Assert.assertEquals(9, fields.length);
            Assert.assertEquals(fields[0], String.format(Locale.ROOT, "%1d", TraceState.TRACE_STATE_VERSION));
            Assert.assertEquals(fields[1], String.valueOf(TraceState.TRACE_STATE_PARENT_TYPE));
            Assert.assertEquals(fields[2], traceContext.getAccountId());
            Assert.assertEquals(fields[3], traceContext.getApplicationId());
            Assert.assertEquals(fields[4], traceContext.getParentId());
            Assert.assertEquals(fields[5], TraceContext.TRACE_FIELD_UNUSED);
            Assert.assertEquals(fields[6], TraceContext.TRACE_FIELD_UNUSED);
            Assert.assertEquals(fields[7], TraceContext.TRACE_FIELD_UNUSED);
            Assert.assertEquals(fields[8], String.valueOf(traceState.timestampMs));
        }

        return true;
    }

}