/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.distributedtracing;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TraceParentTest {
    static final String TRACE_FIELD_DELIMITER = "-";

    private TraceContext traceContext;
    private TraceParent traceParent;

    @Before
    public void setUp() throws Exception {
        TraceConfiguration.setInstance(new TraceConfiguration("1", "22", null));
        traceContext = TraceContext.createTraceContext(null);
        traceParent = traceContext.traceParent;
    }

    @Test
    public void testGetHeaderName() {
        Assert.assertEquals(TraceParent.TRACE_PARENT_HEADER, traceContext.traceParent.getHeaderName());
    }

    @Test
    public void testGetHeaderValue() {
        String headerValue = traceParent.getHeaderValue();
        Assert.assertTrue(isValidTraceHeaderValue(headerValue));
    }

    @Test
    public void testGetParentTraceId() {
        Assert.assertNotNull(traceParent.getParentId());
    }

    @Test
    public void testGetVersion() {
        Assert.assertEquals("00", traceParent.getVersion());
    }

    public boolean isValidTraceHeaderValue(String headerValue) {
        Assert.assertNotNull(headerValue);
        Assert.assertFalse(headerValue.isEmpty());
        Assert.assertTrue(headerValue.matches(TraceParent.W3CTraceParent.TRACE_PARENT_HEADER_REGEX));
        String[] fields = headerValue.split(TRACE_FIELD_DELIMITER);
        Assert.assertEquals(4, fields.length);
        Assert.assertEquals(fields[0], traceParent.getVersion());
        Assert.assertEquals(fields[1], traceContext.getTraceId());
        Assert.assertEquals(fields[2], traceParent.getParentId());
        Assert.assertEquals(fields[3], traceContext.getSampled());

        return true;
    }

}