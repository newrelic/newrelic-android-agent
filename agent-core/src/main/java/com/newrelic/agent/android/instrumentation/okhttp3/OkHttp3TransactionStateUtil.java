/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation.okhttp3;

import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.HttpHeaders;
import com.newrelic.agent.android.TaskQueue;
import com.newrelic.agent.android.api.common.TransactionData;
import com.newrelic.agent.android.distributedtracing.TraceContext;
import com.newrelic.agent.android.distributedtracing.TraceHeader;
import com.newrelic.agent.android.harvest.HarvestConfiguration;
import com.newrelic.agent.android.instrumentation.TransactionState;
import com.newrelic.agent.android.instrumentation.TransactionStateUtil;
import com.newrelic.agent.android.measurement.HttpTransactionMeasurement;
import com.newrelic.agent.android.util.Constants;

import org.apache.http.HttpStatus;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import okhttp3.Headers;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class OkHttp3TransactionStateUtil extends TransactionStateUtil {

    public static void inspectAndInstrument(final TransactionState transactionState, final Request request) {
        if (request == null) {
            log.debug("Missing request");
        } else {
            inspectAndInstrument(transactionState, request.url().toString(), request.method());
            try {
                RequestBody body = request.body();
                if (body != null && body.contentLength() > 0) {
                    transactionState.setBytesSent(body.contentLength());
                }
            } catch (IOException e) {
                log.debug("Could not determine request length: " + e);
            }
        }
    }

    public static Response inspectAndInstrumentResponse(final TransactionState transactionState, final Response response) {
        String appData = "";
        int statusCode = -1;
        long contentLength = 0;

        // response has been seen to be null to protect
        if (response == null) {
            statusCode = HttpStatus.SC_INTERNAL_SERVER_ERROR;   // 500
            log.debug("Missing response");
        } else {
            // update the request state from response (perhaps changed by interceptor)
            Request request = response.request();
            // add request headers as custom attributes
            addHeadersAsCustomAttribute(transactionState, request);
            if (request != null && request.url() != null) {
                String url = request.url().toString();
                if (!url.isEmpty()) {
                    inspectAndInstrument(transactionState, url, request.method());
                }
            }

            appData = response.header(Constants.Network.APP_DATA_HEADER);
            statusCode = response.code();
            try {
                contentLength = exhaustiveContentLength(response);
            } catch (Exception e) {
            }
            if (contentLength < 0) {
                log.debug("OkHttp3TransactionStateUtil: Missing body or content length");
            }
        }

        inspectAndInstrumentResponse(transactionState, appData, (int) contentLength, statusCode);

        return addTransactionAndErrorData(transactionState, response);
    }

    private static long exhaustiveContentLength(Response response) {
        // favor buffer length over header value
        long contentLength = CONTENTLENGTH_UNKNOWN;

        if (response != null) {
            if (response.body() != null) {
                contentLength = response.body().contentLength();
            }
            if (contentLength < 0L) {
                String responseBodyString = response.header(Constants.Network.CONTENT_LENGTH_HEADER);
                if (responseBodyString != null && responseBodyString.length() > 0) {
                    try {
                        contentLength = Long.parseLong(responseBodyString);
                    } catch (NumberFormatException var10) {
                        log.debug("Failed to parse content length: " + var10);
                    }
                } else {
                    Response networkResponse = response.networkResponse();
                    if (networkResponse != null) {
                        responseBodyString = networkResponse.header(Constants.Network.CONTENT_LENGTH_HEADER);
                        if (responseBodyString != null && responseBodyString.length() > 0) {
                            try {
                                contentLength = Long.parseLong(responseBodyString);
                            } catch (NumberFormatException var5) {
                                log.debug("Failed to parse network response content length: " + var5);
                            }
                        } else {
                            if (networkResponse.body() != null) {
                                contentLength = networkResponse.body().contentLength();
                            }
                        }
                    } else {
                        // read the response? bad!
                    }
                }
            }
        }
        return contentLength;
    }

    protected static Response addTransactionAndErrorData(TransactionState transactionState, Response response) {
        final TransactionData transactionData = transactionState.end();

        //
        // This will happen if we capture insufficient state to report upon (e.g. no URL)
        //
        // Warning is logged inside TransactionState.end().
        //
        if (transactionData != null) {

            if (response != null && transactionState.isErrorOrFailure()) {
                // If there is a Content-Type header present, add it to the error param map.
                final String contentTypeHeader = response.header(Constants.Network.CONTENT_TYPE_HEADER);
                Map<String, String> params = new TreeMap<String, String>();

                if (contentTypeHeader != null && !contentTypeHeader.isEmpty()) {
                    params.put(Constants.Transactions.CONTENT_TYPE, contentTypeHeader);
                }

                /* Also add Content-Length. TransactionState bytesReceived is set directly
                 * from the Content-Length header in this error case.
                 */
                params.put(Constants.Transactions.CONTENT_LENGTH, transactionState.getBytesReceived() + "");

                String responseBodyString = "";
                try {
                    long contentLength = exhaustiveContentLength(response);
                    if (contentLength > 0) {
                        responseBodyString = response.peekBody(contentLength).string();
                    }
                } catch (Exception e) {
                    if (response.message() != null) {
                        log.debug("Missing response body, using response message");
                        responseBodyString = response.message();
                    }
                }

                transactionData.setResponseBody(responseBodyString);
                transactionData.getParams().putAll(params);


            }

            HttpTransactionMeasurement httpTransactionMeasurement = new HttpTransactionMeasurement(transactionData);
            TaskQueue.queue(httpTransactionMeasurement);

            setDistributedTraceHeaders(transactionState, response);
        }

        return response;
    }

    public static Request setDistributedTraceHeaders(TransactionState transactionState, Request request) {
        if (FeatureFlag.featureEnabled(FeatureFlag.DistributedTracing) && !HarvestConfiguration.getDefaultHarvestConfiguration().getAccount_id().isEmpty()) {
            try {
                Request.Builder builder = request.newBuilder();

                TraceContext traceContext = transactionState.getTrace();
                if (traceContext != null) {
                    for (TraceHeader traceHeader : traceContext.getHeaders()) {
                        builder = builder.header(traceHeader.getHeaderName(), traceHeader.getHeaderValue());
                    }

                    TraceContext.reportSupportabilityMetrics();
                }

                return builder.build();

            } catch (Exception e) {
                log.error("setDistributedTraceHeaders: Unable to add trace headers. ", e);
                TraceContext.reportSupportabilityExceptionMetric(e);
            }
        }

        return request;
    }

    public static Response setDistributedTraceHeaders(TransactionState transactionState, Response response) {
        if (FeatureFlag.featureEnabled(FeatureFlag.DistributedTracing) && !HarvestConfiguration.getDefaultHarvestConfiguration().getAccount_id().isEmpty()) {
            try {
                Response.Builder builder = response.newBuilder();
                TraceContext traceContext = transactionState.getTrace();
                if (traceContext != null) {
                    Headers headers = response.headers();
                    for (TraceHeader traceHeader : traceContext.getHeaders()) {
                        if (headers.get(traceHeader.getHeaderName()) != null) {

                        } else {
                            builder = builder.addHeader(traceHeader.getHeaderName(), traceHeader.getHeaderValue());
                        }
                    }
                }

                return builder.build();

            } catch (Exception e) {
                log.error("setDistributedTraceHeaders: Unable to add trace headers. ", e);
                TraceContext.reportSupportabilityExceptionMetric(e);
            }
        }

        return response;
    }

    public static void addHeadersAsCustomAttribute(TransactionState transactionState, Request request) {

        Map<String, String> headers = new HashMap<>();
        for (String s : HttpHeaders.getInstance().getHttpHeaders()) {
            if (request.headers().get(s) != null) {
                headers.put(HttpHeaders.translateApolloHeader(s), request.headers().get(s));
            }
        }
        transactionState.setParams(headers);
    }


}
