/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation.retrofit;

import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.TaskQueue;
import com.newrelic.agent.android.api.common.TransactionData;
import com.newrelic.agent.android.distributedtracing.DistributedTracing;
import com.newrelic.agent.android.distributedtracing.TraceContext;
import com.newrelic.agent.android.instrumentation.TransactionState;
import com.newrelic.agent.android.instrumentation.TransactionStateUtil;
import com.newrelic.agent.android.measurement.HttpTransactionMeasurement;
import com.newrelic.agent.android.util.Constants;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import retrofit.client.Client;
import retrofit.client.Header;
import retrofit.client.Request;
import retrofit.client.Response;

public class ClientExtension implements Client {
    private Client impl;
    private TransactionState transactionState;
    private Request request;

    public ClientExtension(Client impl) {
        this.impl = impl;
    }

    @Override
    public Response execute(Request request) throws IOException {
        this.request = request;

        transactionState = getTransactionState();

        request = setCrossProcessHeaders(request);

        if (FeatureFlag.featureEnabled(FeatureFlag.DistributedTracing)) {
            TraceContext trace = DistributedTracing.getInstance().startTrace(transactionState);
            transactionState.setTrace(trace);
            request = RetrofitTransactionStateUtil.setDistributedTraceHeaders(transactionState, request);
        }

        Response response;
        try {
            response = impl.execute(request);
            response = new Response(response.getUrl(), response.getStatus(), response.getReason(), response.getHeaders(), new ContentBufferingTypedInput(response.getBody()));
        } catch (IOException ex) {
            error(ex);
            throw ex;
        }
        checkResponse(response);
        return response;
    }

    private Request setCrossProcessHeaders(Request request) {
        final String crossProcessId = Agent.getCrossProcessId();
        List<Header> headers = new ArrayList<Header>(request.getHeaders());
        if (crossProcessId != null) {
            headers.add(new Header(Constants.Network.CROSS_PROCESS_ID_HEADER, crossProcessId));
        }
        return new Request(request.getMethod(), request.getUrl(), headers, request.getBody());
    }

    private void checkResponse(Response response) {
        if (!getTransactionState().isComplete()) {
            RetrofitTransactionStateUtil.inspectAndInstrumentResponse(getTransactionState(), response);
        }
    }

    protected TransactionState getTransactionState() {
        if (transactionState == null) {
            transactionState = new TransactionState();
        }
        RetrofitTransactionStateUtil.inspectAndInstrument(transactionState, request);
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

    void setRequest(Request request) {
        this.request = request;
    }

}
