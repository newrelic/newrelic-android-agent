/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation.okhttp2;

import com.squareup.okhttp.*;

import java.io.IOException;

public class MockCall extends Call {
    private Response response;

    public MockCall(OkHttpClient client, Request request, Response response) {
        super(client, request);
        this.response = response;
    }

    @Override
    public Response execute() throws IOException {
        return response;
    }

    @Override
    public void enqueue(Callback responseCallback) {
        try {
            responseCallback.onResponse(response);
        } catch (IOException e) {
        }
    }

    @Override
    public void cancel() {
        super.cancel();
    }

    @Override
    public boolean isCanceled() {
        return super.isCanceled();
    }
}