/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.distributedtracing;

import com.newrelic.agent.android.instrumentation.TransactionState;
import com.newrelic.agent.android.metric.Metric;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.test.mock.Providers;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TraceContextTest  {

    TraceContext traceContext;
    TraceConfiguration traceConfiguration;
    private TransactionState transactionState;
    private Map<String, String> requestContext;

    @Before
    public void setUp() throws Exception {
        traceConfiguration = new TraceConfiguration("1", "11", "");
        traceConfiguration = TraceConfiguration.setInstance(traceConfiguration);
        transactionState = Providers.provideTransactionState();
        requestContext = new HashMap<String, String>() {{
            put("url", transactionState.getUrl());
            put("httpMethod", transactionState.getHttpMethod());
            put("threadId", String.valueOf(Thread.currentThread().getId()));
        }};

        traceContext = Mockito.spy(TraceContext.createTraceContext(requestContext));
    }

    @Test
    public void testCreateTraceContext() {
        TraceContext traceContext = TraceContext.createTraceContext(null);
        Assert.assertNotNull(traceContext);
        Assert.assertTrue(traceContext instanceof TraceContext.W3CTraceContext);
    }

    @Test
    public void testGetHeaders() {
        Assert.assertNotNull(traceContext.tracePayload);
        Assert.assertFalse(traceContext.getHeaders().isEmpty());

        traceContext.legacyHeadersEnabled = false;
        Assert.assertFalse(traceContext.getHeaders().isEmpty());
    }

    @Test
    public void testGetSpanId() {
        Assert.assertNotNull(traceContext.tracePayload);
        Assert.assertFalse(traceContext.getTracePayload().getSpanId().isEmpty());
        Assert.assertEquals(traceContext.getParentId(), traceContext.getTracePayload().getSpanId());
    }

    @Test
    public void testHeadersContainsSpanId() {
        Assert.assertFalse(traceContext.getHeaders().isEmpty());
        for (Object tc : traceContext.getHeaders()) {
            if (tc instanceof TraceState) {
                Map<String, String> entries = ((TraceState) tc).entries;
                Assert.assertTrue(entries.get("@nr").contains(traceContext.getTracePayload().getSpanId()));
            }
        }
    }

    @Test
    public void testGetTraceId() {
        Assert.assertNotNull(traceContext.getTraceId());
        Assert.assertFalse(traceContext.getTraceId().isEmpty());
        Assert.assertEquals(32, traceContext.getTraceId().length());
    }

    @Test
    public void testGetParentTraceId() {
        Assert.assertNotNull(traceContext.getParentId());
        Assert.assertFalse(traceContext.getParentId().isEmpty());
        Assert.assertTrue(traceContext.getParentId().matches(TraceContext.SPAN_ID_REGEX));
    }

    @Test
    public void testIsSampled() {
        Assert.assertTrue(traceContext.traceConfiguration.isSampled());
    }

    @Test
    public void testGetVendor() {
        Assert.assertEquals("@nr", traceContext.getVendor());
        TraceConfiguration.getInstance().trustedAccountId = "trustme";
        Assert.assertEquals("trustme@nr", traceContext.getVendor());
    }

    @Test
    public void testGetAccountId() {
        Assert.assertEquals(traceConfiguration.accountId, traceContext.getAccountId());
    }

    @Test
    public void testGetApplicationId() {
        Assert.assertEquals(traceConfiguration.applicationId, traceContext.getApplicationId());
    }

    @Test
    public void testGetTracePayload() {
        Assert.assertNotNull(traceContext.getTracePayload());
    }

    @Test
    public void testAsTraceAttributes() {
        Map<String, Object> traceAttrs = traceContext.asTraceAttributes();
        Assert.assertFalse(traceAttrs.isEmpty());
        Assert.assertEquals(3, traceAttrs.size());
        Assert.assertTrue(traceAttrs.containsKey(DistributedTracing.NR_ID_ATTRIBUTE));
        Assert.assertTrue(traceAttrs.containsKey(DistributedTracing.NR_GUID_ATTRIBUTE));
        Assert.assertTrue(traceAttrs.containsKey(DistributedTracing.NR_TRACE_ID_ATTRIBUTE));
    }

    @Test
    public void testGetPayloadTraceHeader() {
        TraceHeader traceHeader = traceContext.getTracePayload();
        Assert.assertEquals(TracePayload.TRACE_PAYLOAD_HEADER, traceHeader.getHeaderName());
        Assert.assertNotNull(traceHeader.getHeaderValue());
    }

    @Test
    public void testReportSupportabilityMetrics() {
        TraceContext.reportSupportabilityMetrics();
        ConcurrentHashMap<String, Metric> statsMap = StatsEngine.get().getStatsMap();
        Assert.assertNotNull(statsMap.entrySet().contains(TraceContext.SUPPORTABILITY_TRACE_CONTEXT_CREATED));
    }

    @Test
    public void testReportSupportabilityExceptionMetric() {
        TraceContext.reportSupportabilityExceptionMetric(new RuntimeException("tenet"));
        ConcurrentHashMap<String, Metric> statsMap = StatsEngine.get().getStatsMap();
        Assert.assertNotNull(statsMap.entrySet().contains(TraceContext.SUPPORTABILITY_TRACE_CONTEXT_CREATED));
    }
}