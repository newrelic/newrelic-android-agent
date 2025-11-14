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
import com.squareup.okhttp.Callback;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.IOException;

public class CallbackExtension implements Callback {
    private static final AgentLog log = AgentLogManager.getAgentLog();

    private TransactionState transactionState;
    private Callback impl;

    public CallbackExtension(Callback impl, TransactionState transactionState) {
        this.impl = impl;
        this.transactionState = transactionState;
    }

    @Override
    public void onFailure(Request request, IOException e) {
        error(e);
        try {
            impl.onFailure(request, e);
        } catch (Exception callbackException) {
            // Protect against exceptions thrown by the application's callback implementation
            log.error("Exception in application callback onFailure: " + callbackException.getMessage(), callbackException);
        }
    }

    @Override
    public void onResponse(Response response) throws IOException {
        response = checkResponse(response);
        try {
            impl.onResponse(response);
        } catch (IOException ioException) {
            // Re-throw IOException as per the method signature contract
            throw ioException;
        } catch (Exception callbackException) {
            // Protect against unexpected exceptions thrown by the application's callback implementation
            log.error("Exception in application callback onResponse: " + callbackException.getMessage(), callbackException);
        }
    }

    private Response checkResponse(Response response) {
        if (!getTransactionState().isComplete()) {
            log.verbose("CallbackExtension.checkResponse() - transaction is not complete.  Inspecting and instrumenting response.");
            response = OkHttp2TransactionStateUtil.inspectAndInstrumentResponse(getTransactionState(), response);
        }

        return response;
    }

    protected TransactionState getTransactionState() {
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
