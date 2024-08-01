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
import com.newrelic.agent.android.distributedtracing.TraceHeader;
import com.newrelic.agent.android.instrumentation.TransactionState;
import com.newrelic.agent.android.instrumentation.TransactionStateUtil;
import com.newrelic.agent.android.measurement.HttpTransactionMeasurement;
import com.newrelic.agent.android.util.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import retrofit.client.Header;
import retrofit.client.Request;
import retrofit.client.Response;

public class RetrofitTransactionStateUtil extends TransactionStateUtil {

    public static void inspectAndInstrument(final TransactionState transactionState, final Request request) {
        transactionState.setUrl(request.getUrl());
        transactionState.setHttpMethod(request.getMethod());
        transactionState.setCarrier(Agent.getActiveNetworkCarrier());
        transactionState.setWanType(Agent.getActiveNetworkWanType());
    }

    public static void inspectAndInstrumentResponse(final TransactionState transactionState, final Response response) {
        final String appData = getAppDataHeader(response.getHeaders(), Constants.Network.APP_DATA_HEADER);
        if (appData != null && !"".equals(appData)) {
            transactionState.setAppData(appData);
        }

        int statusCode = response.getStatus();
        transactionState.setStatusCode(statusCode);

        final long contentLength = response.getBody().length();
        if (contentLength >= 0) {
            transactionState.setBytesReceived(contentLength);
        }
        addTransactionAndErrorData(transactionState, response);
    }

    private static String getAppDataHeader(List<Header> headers, String headerName) {
        if (headers != null) {
            for (Header header : headers) {
                if (header.getName() != null && header.getName().equalsIgnoreCase(headerName)) {
                    return header.getValue();
                }
            }
        }
        return null;
    }

    protected static void addTransactionAndErrorData(TransactionState transactionState, Response response) {
        final TransactionData transactionData = transactionState.end();

        //
        // This will happen if we capture insufficient state to report upon (e.g. no URL)
        //
        // Warning is logged inside TransactionState.end().
        //
        if (transactionData == null) {
            return;
        }

        if (transactionState.isErrorOrFailure()) {
            // If there is a Content-Type header present, add it to the error param map.
            final String contentTypeHeader = getAppDataHeader(response.getHeaders(), Constants.Network.CONTENT_TYPE_HEADER);
            Map<String, String> params = new TreeMap<String, String>();

            if (contentTypeHeader != null && contentTypeHeader.length() > 0 && !"".equals(contentTypeHeader)) {
                params.put(Constants.Transactions.CONTENT_TYPE, contentTypeHeader);
            }

			/* Also add Content-Length. TransactionState bytesReceived is set directly
             * from the Content-Length header in this error case.
			*/
            params.put(Constants.Transactions.CONTENT_LENGTH, transactionState.getBytesReceived() + "");

            transactionData.setParams(params);
        }

        HttpTransactionMeasurement httpTransactionMeasurement = new HttpTransactionMeasurement(transactionData);
        TaskQueue.queue(httpTransactionMeasurement);

        response = setDistributedTraceHeaders(transactionState, response);
    }

    static Request setDistributedTraceHeaders(TransactionState transactionState, Request request) {
        List<Header> headers = new ArrayList<Header>(request.getHeaders());

        if (FeatureFlag.featureEnabled(FeatureFlag.DistributedTracing)) {
            TraceContext traceContext = DistributedTracing.getInstance().startTrace(transactionState);
            transactionState.setTrace(traceContext);

            try {
                if (traceContext != null) {
                    for (TraceHeader traceHeader : traceContext.getHeaders()) {
                        headers.add(new Header(traceHeader.getHeaderName(), traceHeader.getHeaderValue()));
                    }
                }

                TraceContext.reportSupportabilityMetrics();

                return new Request(request.getMethod(), request.getUrl(), headers, request.getBody());

            } catch (Exception e) {
                TraceContext.reportSupportabilityExceptionMetric(e);
            }
        }

        return request;
    }

    static Response setDistributedTraceHeaders(TransactionState transactionState, Response response) {
        if (FeatureFlag.featureEnabled(FeatureFlag.DistributedTracing)) {
            List<Header> headers = new ArrayList<Header>(response.getHeaders());

            TraceContext traceContext = DistributedTracing.getInstance().startTrace(transactionState);
            transactionState.setTrace(traceContext);

            try {
                if (traceContext != null) {
                    for (TraceHeader traceHeader : traceContext.getHeaders()) {
                        headers.add(new Header(traceHeader.getHeaderName(), traceHeader.getHeaderValue()));
                    }
                }

                return new Response(response.getUrl(),
                        response.getStatus(),
                        response.getReason(),
                        headers,
                        response.getBody());

            } catch (Exception e) {
                TraceContext.reportSupportabilityExceptionMetric(e);
            }
        }

        return response;
    }

}
