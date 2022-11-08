/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation.okhttp3;

import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;

import okhttp3.Handshake;
import okhttp3.Headers;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;


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
