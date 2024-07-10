/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation.okhttp2;

import com.newrelic.agent.android.TaskQueue;
import com.newrelic.agent.android.api.common.TransactionData;
import com.newrelic.agent.android.instrumentation.TransactionState;
import com.newrelic.agent.android.instrumentation.TransactionStateUtil;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.measurement.HttpTransactionMeasurement;
import com.squareup.okhttp.Call;
import com.squareup.okhttp.Callback;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.IOException;

public class CallExtension extends Call {
    private static final AgentLog log = AgentLogManager.getAgentLog();

    private TransactionState transactionState;
    private OkHttpClient client;
    Request request;
    Call impl;

    CallExtension(OkHttpClient client, Request request, Call impl, TransactionState transactionState) {
        super(client, request);
        this.client = client;
        this.request = request;
        this.impl = impl;
        this.transactionState = transactionState;
    }

    @Override
    public Response execute() throws IOException {
        getTransactionState();
        Response response = null;
        try {
            response = impl.execute();
        } catch (IOException e) {
            error(e);
            throw e;
        }

        return checkResponse(response);
    }

    @Override
    public void enqueue(Callback responseCallback) {
        getTransactionState();

        impl.enqueue(new CallbackExtension(responseCallback, transactionState));
    }

    @Override
    public void cancel() {
        impl.cancel();
    }

    @Override
    public boolean isCanceled() {
        return impl.isCanceled();
    }


    private Response checkResponse(Response response) {
        if (!getTransactionState().isComplete()) {
            response = OkHttp2TransactionStateUtil.inspectAndInstrumentResponse(getTransactionState(), response);
            if (response.request() != null) {
                OkHttp2TransactionStateUtil.inspectAndInstrument(transactionState, response.request());
            }
        }

        return response;
    }

    protected TransactionState getTransactionState() {
        if (transactionState == null) {
            transactionState = new TransactionState();
        }
        OkHttp2TransactionStateUtil.inspectAndInstrument(transactionState, request);
        return transactionState;
    }

    protected void error(final Exception e) {
        final TransactionState transactionState = getTransactionState();
        TransactionStateUtil.setErrorCodeFromException(transactionState, e);
        if (!transactionState.isComplete()) {
            final TransactionData transactionData = transactionState.end();

            // If no transaction data is available, don't record a transaction measurement.
            if (transactionData != null) {
                transactionData.setResponseBody(e.toString());
                TaskQueue.queue(new HttpTransactionMeasurement(transactionData));
            }
        }
    }
}
