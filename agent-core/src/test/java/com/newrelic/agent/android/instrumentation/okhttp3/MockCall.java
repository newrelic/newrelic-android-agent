/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation.okhttp3;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.AsyncTimeout;
import okio.Timeout;

public class MockCall implements Call {
    private final OkHttpClient client;
    private final Request request;
    private final Response response;
    private final AsyncTimeout timeout;

    public MockCall(OkHttpClient client, Request request, Response response) {
        this.client = client;
        this.response = response;
        this.request = request;
        this.timeout = new AsyncTimeout();
    }

    @Override
    public Request request() {
        return request;
    }

    @Override
    public Response execute() throws IOException {
        return response;
    }

    @Override
    public void enqueue(Callback responseCallback) {
        try {
            responseCallback.onResponse(this, response);
        } catch (IOException e) {
        }
    }

    @Override
    public void cancel() {
    }

    @Override
    public boolean isExecuted() {
        return true;
    }

    @Override
    public boolean isCanceled() {
        return false;
    }

    @Override
    public Timeout timeout() {
        return timeout.timeout(5, TimeUnit.SECONDS);
    }

    @Override
    public Call clone() {
        return new MockCall(client, request, response);
    }
}
