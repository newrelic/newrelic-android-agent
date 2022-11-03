/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation.okhttp2;

import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.ResponseBody;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;

import okio.Buffer;

public class PrebufferedResponseBodyTest {

    private final String RESPONSE = "{" +
            "  'error': {" +
            "    'code': 400," +
            "    'message': 'The provided API key is invalid.'," +
            "    'errors': [" +
            "      {" +
            "        'message': 'The provided API key is invalid.'," +
            "        'domain': 'global'," +
            "        'reason': 'badRequest'" +
            "      }" +
            "    ]," +
            "    'status': 'INVALID_ARGUMENT'" +
            "  }" +
            "}";
    
    private final MediaType mediaType = MediaType.parse("application/json; charset=UTF-8");

    private PrebufferedResponseBody prebufferedResponseBody;
    private Buffer source = new Buffer();
    private ResponseBody responseBody;

    @Before
    public void setUp() throws Exception {
        source = new Buffer();
        source.writeUtf8(RESPONSE);
        responseBody = ResponseBody.create(mediaType, RESPONSE.length(), source);
        prebufferedResponseBody = new PrebufferedResponseBody(responseBody, source);
    }

    @Test
    public void contentType() {
        Assert.assertEquals("Content type:", prebufferedResponseBody.contentType(), mediaType);
    }

    @Test
    public void contentLength() {
        Assert.assertEquals("Content length:", prebufferedResponseBody.contentLength(), RESPONSE.length());
    }

    @Test
    public void mutatedContentLength() {
        Buffer buffer = new Buffer().writeUtf8(RESPONSE.substring(2));
        prebufferedResponseBody = new PrebufferedResponseBody(responseBody, buffer);
        Assert.assertNotEquals("Buffer size:", source.size(), buffer.size());
        Assert.assertEquals("Return length of impl buffer, not buffer:", prebufferedResponseBody.contentLength(), RESPONSE.length());
    }

    @Test
    public void unreadContentLength() throws IOException {
        Buffer buffer = new Buffer().writeUtf8(RESPONSE.substring(2));
        responseBody = Mockito.mock(ResponseBody.class);
        Mockito.when(responseBody.contentLength()).thenReturn(-1L);
        prebufferedResponseBody = Mockito.spy(new PrebufferedResponseBody(responseBody, buffer));
        Assert.assertEquals("Return length of impl buffer, not buffer:", -1, prebufferedResponseBody.contentLength());
    }

    @Test
    public void source() throws IOException {
        Assert.assertEquals("Impl:", prebufferedResponseBody.impl.source(), source);
        Assert.assertEquals("Gettor:", prebufferedResponseBody.source(), source);
        Buffer buffer = new Buffer().writeUtf8(RESPONSE.substring(2));
        prebufferedResponseBody = new PrebufferedResponseBody(responseBody, buffer);
        Assert.assertEquals("Impl replacement:", prebufferedResponseBody.source(), buffer);
    }

    @Test
    public void close() {
        try {
            prebufferedResponseBody.close();
            // Mockito.verify(prebufferedResponseBody.impl, Mockito.atLeastOnce()).close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}