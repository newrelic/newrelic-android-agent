/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation.okhttp2;

import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.util.Constants;
import com.squareup.okhttp.CacheControl;
import com.squareup.okhttp.Headers;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;

import java.net.URL;

public class RequestBuilderExtension extends Request.Builder {
    private Request.Builder impl;

    public RequestBuilderExtension(Request.Builder impl) {
        this.impl = impl;
        setCrossProcessHeader();
    }

    @Override
    public Request.Builder url(String url) {
        return impl.url(url);
    }

    @Override
    public Request.Builder url(URL url) {
        return impl.url(url);
    }

    @Override
    public Request.Builder header(String name, String value) {
        return impl.header(name, value);
    }

    @Override
    public Request.Builder addHeader(String name, String value) {
        return impl.addHeader(name, value);
    }

    @Override
    public Request.Builder removeHeader(String name) {
        return impl.removeHeader(name);
    }

    @Override
    public Request.Builder headers(Headers headers) {
        return impl.headers(headers);
    }

    @Override
    public Request.Builder cacheControl(CacheControl cacheControl) {
        return impl.cacheControl(cacheControl);
    }

    @Override
    public Request.Builder get() {
        return impl.get();
    }

    @Override
    public Request.Builder head() {
        return impl.head();
    }

    @Override
    public Request.Builder post(RequestBody body) {
        return impl.post(body);
    }

    @Override
    public Request.Builder delete() {
        return impl.delete();
    }

    @Override
    public Request.Builder put(RequestBody body) {
        return impl.put(body);
    }

    @Override
    public Request.Builder patch(RequestBody body) {
        return impl.patch(body);
    }

    @Override
    public Request.Builder method(String method, RequestBody body) {
        return impl.method(method, body);
    }

    @Override
    public Request.Builder tag(Object tag) {
        return impl.tag(tag);
    }

    @Override
    public Request build() {
        return impl.build();
    }

    void setCrossProcessHeader() {
        final String crossProcessId = Agent.getCrossProcessId();
        if (crossProcessId != null) {
            impl.removeHeader(Constants.Network.CROSS_PROCESS_ID_HEADER);
            impl.addHeader(Constants.Network.CROSS_PROCESS_ID_HEADER, crossProcessId);
        }
    }

}
