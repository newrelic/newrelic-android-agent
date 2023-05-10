/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation.okhttp2;

import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.squareup.okhttp.Handshake;
import com.squareup.okhttp.Headers;
import com.squareup.okhttp.Protocol;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.ResponseBody;

import java.io.IOException;

import okio.Buffer;
import okio.BufferedSource;

import static com.newrelic.agent.android.instrumentation.okhttp2.OkHttp2Instrumentation.CACHED_RESPONSE_CLASS;

public class ResponseBuilderExtension extends Response.Builder {
    private static final AgentLog log = AgentLogManager.getAgentLog();
    private Response.Builder impl;

    public ResponseBuilderExtension(Response.Builder impl) {
        this.impl = impl;
    }

    @Override
    public Response.Builder request(Request request) {
        return impl.request(request);
    }

    @Override
    public Response.Builder protocol(Protocol protocol) {
        return impl.protocol(protocol);
    }

    @Override
    public Response.Builder code(int code) {
        return impl.code(code);
    }

    @Override
    public Response.Builder message(String message) {
        return impl.message(message);
    }

    @Override
    public Response.Builder handshake(Handshake handshake) {
        return impl.handshake(handshake);
    }

    @Override
    public Response.Builder header(String name, String value) {
        return impl.header(name, value);
    }

    @Override
    public Response.Builder addHeader(String name, String value) {
        return impl.addHeader(name, value);
    }

    @Override
    public Response.Builder removeHeader(String name) {
        return impl.removeHeader(name);
    }

    @Override
    public Response.Builder headers(Headers headers) {
        return impl.headers(headers);
    }

    @Override
    public Response.Builder body(ResponseBody body) {
        try {
            // Pre-fetch the BODY contents so that the content length is known
            if (body != null) {
                // Unless the response body was derived from the cache.
                // Cache uses a private internal class for response body
                // representation, so no need to wrap it.
                if (!body.getClass().getName().equalsIgnoreCase(CACHED_RESPONSE_CLASS)) {
                    final BufferedSource source = body.source();
                    if (source != null) {
                        Buffer buffer = new Buffer();
                        source.readAll(buffer);
                        return impl.body(new PrebufferedResponseBody(body, buffer));
                    }
                }
            }
        }
        catch (IOException e) {
            log.error("IOException reading from source: ", e);
        }
        catch (IllegalStateException ex) {
            // Retrofit 2.0 (retrofit2.OkHttpCall$NoContentResponseBody) produced this exception
            // if the response body has already been consumed during deserialization.
            // In this case, the length is already known. Simply consume the exception,
            // insert the body and move on.
        }

        return impl.body(body);
    }

    @Override
    public Response.Builder networkResponse(Response networkResponse) {
        return impl.networkResponse(networkResponse);
    }

    @Override
    public Response.Builder cacheResponse(Response cacheResponse) {
        return impl.cacheResponse(cacheResponse);
    }

    @Override
    public Response.Builder priorResponse(Response priorResponse) {
        return impl.priorResponse(priorResponse);
    }

    @Override
    public Response build() {
        return impl.build();
    }
}
