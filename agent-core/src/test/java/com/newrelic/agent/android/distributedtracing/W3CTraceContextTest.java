/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.distributedtracing;

import junit.framework.TestCase;

import org.junit.Assert;
import org.junit.Test;

public class W3CTraceContextTest extends TestCase {

    private TraceContext.W3CTraceContext traceContext;

    @Override
    public void setUp() throws Exception {
        traceContext = new TraceContext.W3CTraceContext(null);
    }

    @Test
    public void testGetParentHeader() {
        Assert.assertEquals("Should return W3C trace parent header", TraceParent.TRACE_PARENT_HEADER, traceContext.traceParent.getHeaderName());
        Assert.assertFalse("Should return W3C trace parent value", traceContext.traceParent.getHeaderValue().isEmpty());
        Assert.assertTrue(traceContext.traceParent.getHeaderValue().matches(TraceParent.W3CTraceParent.TRACE_PARENT_HEADER_REGEX));
    }

    @Test
    public void testGetStateHeader() {
        Assert.assertEquals("Should return W3C trace state header", TraceState.TRACE_STATE_HEADER, traceContext.traceState.getHeaderName());
        Assert.assertFalse("Should return W3C trace state value", traceContext.traceState.getHeaderValue().isEmpty());
    }

    public void testShouldProvideValidTraceId() {
        String traceId = traceContext.getTraceId();
        Assert.assertEquals("Should create 32 char guid", 32, traceId.length());
        Assert.assertTrue(traceId.matches(TraceContext.TRACE_ID_REGEX));
    }

    public void testShouldProvideValidTraceParentId() {
        Assert.assertFalse(traceContext.getParentId().isEmpty());
    }

}