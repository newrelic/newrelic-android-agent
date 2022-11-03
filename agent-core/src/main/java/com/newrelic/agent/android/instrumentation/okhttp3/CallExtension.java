/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation.okhttp3;

import com.newrelic.agent.android.TaskQueue;
import com.newrelic.agent.android.api.common.TransactionData;
import com.newrelic.agent.android.instrumentation.TransactionState;
import com.newrelic.agent.android.instrumentation.TransactionStateUtil;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.measurement.http.HttpTransactionMeasurement;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.Timeout;

public class CallExtension implements Call {
    private static final AgentLog log = AgentLogManager.getAgentLog();

    private TransactionState transactionState;
    private OkHttpClient client;
    Request request;
    Call impl;

    CallExtension(OkHttpClient client, Request request, Call impl, TransactionState transactionState) {
        this.client = client;
        this.request = request;
        this.impl = impl;
        this.transactionState = transactionState;
    }

    @Override
    public Request request() {
        return impl.request();
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
        impl.enqueue(new CallbackExtension(responseCallback, transactionState, this));
    }

    @Override
    public void cancel() {
        impl.cancel();
    }

    @Override
    public boolean isExecuted() {
        return false;
    }

    @Override
    public boolean isCanceled() {
        return impl.isCanceled();
    }

    @Override
    public Timeout timeout() {
        return impl.timeout();
    }

    @Override
    public Call clone() {
        // Does this need to be instrumented as well?
        //   "Create a new, identical call to this one which can be enqueued or executed even if this call has already been."
        // return new CallExtension(client, request, client.newCall(request), new TransactionState());
        return impl.clone();
    }


    private Response checkResponse(Response response) {
        if (!getTransactionState().isComplete()) {
            if (response.request() != null) {
                OkHttp3TransactionStateUtil.inspectAndInstrument(transactionState, response.request());
            }
            response = OkHttp3TransactionStateUtil.inspectAndInstrumentResponse(getTransactionState(), response);
        }

        return response;
    }

    protected TransactionState getTransactionState() {
        if (transactionState == null) {
            transactionState = new TransactionState();
        }
        OkHttp3TransactionStateUtil.inspectAndInstrument(transactionState, request);

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

    public Call getImpl() {
        return impl;
    }

}
