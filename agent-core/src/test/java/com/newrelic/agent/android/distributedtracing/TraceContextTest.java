/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.distributedtracing;

import com.newrelic.agent.android.instrumentation.TransactionState;
import com.newrelic.agent.android.metric.Metric;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.test.mock.Providers;

import junit.framework.TestCase;

import org.junit.Assert;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TraceContextTest extends TestCase {

    TraceContext traceContext;
    TraceConfiguration traceConfiguration;
    private TransactionState transactionState;
    private Map<String, String> requestContext;

    @Override
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

    public void testCreateTraceContext() {
        TraceContext traceContext = TraceContext.createTraceContext(null);
        Assert.assertNotNull(traceContext);
        Assert.assertTrue(traceContext instanceof TraceContext.W3CTraceContext);
    }

    public void testGetHeaders() {
        Assert.assertNotNull(traceContext.tracePayload);
        Assert.assertFalse(traceContext.getHeaders().isEmpty());

        traceContext.legacyHeadersEnabled = false;
        Assert.assertFalse(traceContext.getHeaders().isEmpty());
    }

    public void testGetSpanId() {
        Assert.assertNotNull(traceContext.tracePayload);
        Assert.assertFalse(traceContext.getTracePayload().getSpanId().isEmpty());
        Assert.assertEquals(traceContext.getParentId(), traceContext.getTracePayload().getSpanId());
    }

    public void testHeadersContainsSpanId() {
        Assert.assertFalse(traceContext.getHeaders().isEmpty());
        for (Object tc : traceContext.getHeaders()) {
            if (tc instanceof TraceState) {
                Map<String, String> entries = ((TraceState) tc).entries;
                Assert.assertTrue(entries.get("@nr").contains(traceContext.getTracePayload().getSpanId()));
            }
        }
    }


    public void testGetTraceId() {
        Assert.assertNotNull(traceContext.getTraceId());
        Assert.assertFalse(traceContext.getTraceId().isEmpty());
        Assert.assertEquals(32, traceContext.getTraceId().length());
    }

    public void testGetParentTraceId() {
        Assert.assertNotNull(traceContext.getParentId());
        Assert.assertFalse(traceContext.getParentId().isEmpty());
        Assert.assertTrue(traceContext.getParentId().matches(TraceContext.SPAN_ID_REGEX));
    }

    public void testIsSampled() {
        Assert.assertFalse(traceContext.traceConfiguration.isSampled());
    }

    public void testGetVendor() {
        Assert.assertEquals("@nr", traceContext.getVendor());
        TraceConfiguration.getInstance().trustedAccountId = "trustme";
        Assert.assertEquals("trustme@nr", traceContext.getVendor());
    }

    public void testGetAccountId() {
        Assert.assertEquals(traceConfiguration.accountId, traceContext.getAccountId());
    }

    public void testGetApplicationId() {
        Assert.assertEquals(traceConfiguration.applicationId, traceContext.getApplicationId());
    }

    public void testGetTracePayload() {
        Assert.assertNotNull(traceContext.getTracePayload());
    }

    public void testAsTraceAttributes() {
        Map<String, Object> traceAttrs = traceContext.asTraceAttributes();
        Assert.assertFalse(traceAttrs.isEmpty());
        Assert.assertEquals(3, traceAttrs.size());
        Assert.assertTrue(traceAttrs.containsKey(DistributedTracing.NR_ID_ATTRIBUTE));
        Assert.assertTrue(traceAttrs.containsKey(DistributedTracing.NR_GUID_ATTRIBUTE));
        Assert.assertTrue(traceAttrs.containsKey(DistributedTracing.NR_TRACE_ID_ATTRIBUTE));
    }

    public void testGetPayloadTraceHeader() {
        TraceHeader traceHeader = traceContext.getTracePayload();
        Assert.assertEquals(TracePayload.TRACE_PAYLOAD_HEADER, traceHeader.getHeaderName());
        Assert.assertNotNull(traceHeader.getHeaderValue());
    }

    public void testReportSupportabilityMetrics() {
        TraceContext.reportSupportabilityMetrics();
        ConcurrentHashMap<String, Metric> statsMap = StatsEngine.get().getStatsMap();
        Assert.assertNotNull(statsMap.entrySet().contains(TraceContext.SUPPORTABILITY_TRACE_CONTEXT_CREATED));
    }

    public void testReportSupportabilityExceptionMetric() {
        TraceContext.reportSupportabilityExceptionMetric(new RuntimeException("tenet"));
        ConcurrentHashMap<String, Metric> statsMap = StatsEngine.get().getStatsMap();
        Assert.assertNotNull(statsMap.entrySet().contains(TraceContext.SUPPORTABILITY_TRACE_CONTEXT_CREATED));
    }
}