/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.distributedtracing;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;

public class TracePayloadTest {

    private TraceContext traceContext;
    private TracePayload tracePayload;

    @Before
    public void setUp() throws Exception {
        TraceConfiguration.setInstance(new TraceConfiguration("1", "22", "333"));
        traceContext = TraceContext.createTraceContext(new HashMap<String, String>() {{
            put("url", "https://httpbin.org/status/418");
            put("httpMethod", "GET");
        }});
        tracePayload = traceContext.tracePayload;
    }

    @Test
    public void testCreateTracePayload() {
        Assert.assertNotNull(traceContext.tracePayload);
    }

    @Test
    public void testAsJson() {
        Assert.assertTrue(isValidTraceHeaderValue(tracePayload.asBase64Json()));
    }

    @Test
    public void testAsBase64Json() {
    }

    @Test
    public void testGetTraceId() {
        Assert.assertEquals(traceContext.getTraceId(), tracePayload.getTraceId());
    }

    @Test
    public void testGetHeaderName() {
        Assert.assertEquals(TracePayload.TRACE_PAYLOAD_HEADER, tracePayload.getHeaderName());
    }

    @Test
    public void testGetHeaderValue() {
        Assert.assertTrue(isValidTraceHeaderValue(tracePayload.getHeaderValue()));
    }

    public boolean isValidTraceHeaderValue(String headerValue) {
        Assert.assertNotNull(headerValue);
        Assert.assertFalse(headerValue.isEmpty());

        try {
            JsonObject jsonObject = JsonParser.parseString(headerValue).getAsJsonObject();
            Assert.assertEquals(2, jsonObject.entrySet().size());

            Assert.assertTrue(jsonObject.has(TracePayload.VERSION_KEY));
            JsonArray version = jsonObject.get(TracePayload.VERSION_KEY).getAsJsonArray();
            Assert.assertNotNull(version);
            Assert.assertEquals(TracePayload.MAJOR_VERSION, version.get(0).getAsInt());
            Assert.assertEquals(TracePayload.MINOR_VERSION, version.get(1).getAsInt());

            Assert.assertTrue(jsonObject.has(TracePayload.DATA_KEY));
            JsonObject data = jsonObject.get(TracePayload.DATA_KEY).getAsJsonObject();
            Assert.assertEquals(7, data.entrySet().size());
            Assert.assertNotNull(data);
            Assert.assertEquals(TracePayload.CALLER_TYPE, data.get(TracePayload.PAYLOAD_TYPE_KEY).getAsString());
            Assert.assertEquals(traceContext.getTraceId(), data.get(TracePayload.TRACE_ID_KEY).getAsString());
            Assert.assertEquals(tracePayload.spanId, data.get(TracePayload.GUID_KEY).getAsString());
            Assert.assertEquals(tracePayload.timestampMs, data.get(TracePayload.TIMESTAMP_KEY).getAsLong());
            Assert.assertEquals(tracePayload.traceContext.getAccountId(), data.get(TracePayload.ACCOUNT_ID_KEY).getAsString());
            Assert.assertEquals(tracePayload.traceContext.getApplicationId(), data.get(TracePayload.APP_ID_KEY).getAsString());
            Assert.assertNull(data.get("d.app"));
            Assert.assertNull(data.get("d.ty"));
            Assert.assertNull(data.get("d.ac"));
            Assert.assertNull(data.get("d.tr"));
            Assert.assertNull(data.get("d.id"));
            Assert.assertNull(data.get("d.ti"));
            Assert.assertNull(data.get("d.tk"));

        } catch (Exception e) {
            Assert.fail("Not a valid Json string");
        }

        return true;
    }
}