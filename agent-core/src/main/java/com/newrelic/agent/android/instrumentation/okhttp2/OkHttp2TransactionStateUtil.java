/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation.okhttp2;

import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.TaskQueue;
import com.newrelic.agent.android.api.common.TransactionData;
import com.newrelic.agent.android.distributedtracing.TraceContext;
import com.newrelic.agent.android.distributedtracing.TraceHeader;
import com.newrelic.agent.android.harvest.HarvestConfiguration;
import com.newrelic.agent.android.instrumentation.TransactionState;
import com.newrelic.agent.android.instrumentation.TransactionStateUtil;
import com.newrelic.agent.android.measurement.HttpTransactionMeasurement;
import com.newrelic.agent.android.util.Constants;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.ResponseBody;

import org.apache.http.HttpStatus;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.TreeMap;

import okio.Buffer;

public class OkHttp2TransactionStateUtil extends TransactionStateUtil {
    public static void inspectAndInstrument(final TransactionState transactionState, final Request request) {
        if (request == null) {
            log.debug("Missing request");
        } else {
            if (!transactionState.isSent()) {
                inspectAndInstrument(transactionState, request.urlString(), request.method());
            }
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

    public static Response inspectAndInstrumentResponse(final TransactionState transactionState, Response response) {
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
            if (request != null && request.url() != null) {
                String url = request.url().toString();
                if (!url.isEmpty()) {
                    inspectAndInstrument(transactionState, url, request.method());
                }
            }

            try {
                appData = response.header(Constants.Network.APP_DATA_HEADER);
                statusCode = response.code();
                contentLength = exhaustiveContentLength(response);
            } catch (Exception e) {
                log.debug("OkHttp2TransactionStateUtil: Missing body or content length");
            }
        }

        inspectAndInstrumentResponse(transactionState, appData, (int) contentLength, statusCode);

        return addTransactionAndErrorData(transactionState, response);
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

                // The response body may be read only once, so read it here and return a cloned response later.
                String responseBodyString = "";

                try {
                    // The response body may be read only once, so read it here and return a cloned response
                    if (response.body() != null) {
                        ResponseBody body = response.body();
                        ByteBuffer byteBuffer = ByteBuffer.wrap(body.bytes());
                        Buffer buffer = new Buffer().write(byteBuffer.array());
                        ResponseBody newBody = new PrebufferedResponseBody(body, buffer);
                        response = response.newBuilder().body(newBody).build();
                        responseBodyString = new String(byteBuffer.array());
                    } else {
                        if (response.message() != null) {
                            log.debug("Missing response body, using response message");
                            responseBodyString = response.message();
                        }
                    }

                } catch (Exception e) {
                    if (response.message() != null) {
                        log.debug("Missing response body, using response message");
                        responseBodyString = response.message();
                    }
                }

                transactionData.setResponseBody(responseBodyString);
                transactionData.getParams().putAll(params);


                response = setDistributedTraceHeaders(transactionState, response);
            }

            HttpTransactionMeasurement httpTransactionMeasurement = new HttpTransactionMeasurement(transactionData);
            TaskQueue.queue(httpTransactionMeasurement);
        }

        return response;
    }

    private static long exhaustiveContentLength(Response response) {
        // favor buffer length over header value
        long contentLength = CONTENTLENGTH_UNKNOWN;

        if (response != null) {
            if (response.body() != null) {
                try {
                    contentLength = response.body().contentLength();
                } catch (IOException e) {
                    log.debug("Failed to parse content length: " + e.toString());
                }
            }
            if (contentLength < 0L) {
                String responseBodyString = response.header(Constants.Network.CONTENT_LENGTH_HEADER);
                if (responseBodyString != null && responseBodyString.length() > 0) {
                    try {
                        contentLength = Long.parseLong(responseBodyString);
                    } catch (NumberFormatException e) {
                        log.debug("Failed to parse content length: " + e.toString());
                    }
                } else {
                    Response networkResponse = response.networkResponse();
                    if (networkResponse != null) {
                        responseBodyString = networkResponse.header(Constants.Network.CONTENT_LENGTH_HEADER);
                        if (responseBodyString != null && responseBodyString.length() > 0) {
                            try {
                                contentLength = Long.parseLong(responseBodyString);
                            } catch (NumberFormatException e) {
                                log.debug("Failed to parse content length: " + e.toString());
                            }
                        } else {
                            if (networkResponse.body() != null) {
                                try {
                                    contentLength = networkResponse.body().contentLength();
                                } catch (IOException e) {
                                    log.debug("Failed to parse network response content length: " + e.toString());
                                    e.printStackTrace();
                                }
                            }
                        }
                    }  // read the response? bad!

                }
            }
        }
        return contentLength;
    }

    public static Request setDistributedTraceHeaders(TransactionState transactionState, Request request) {
        if (FeatureFlag.featureEnabled(FeatureFlag.DistributedTracing) && !HarvestConfiguration.getDefaultHarvestConfiguration().getAccount_id().isEmpty() ) {
            try {
                Request.Builder builder = request.newBuilder();

                TraceContext traceContext = transactionState.getTrace();
                if (traceContext != null) {
                    for (TraceHeader traceHeader : traceContext.getHeaders()) {
                        builder = builder.header(traceHeader.getHeaderName(), traceHeader.getHeaderValue());
                    }
                }
                TraceContext.reportSupportabilityMetrics();

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
                    for (TraceHeader traceHeader : traceContext.getHeaders()) {
                        builder = builder.header(traceHeader.getHeaderName(), traceHeader.getHeaderValue());
                    }
                }
                TraceContext.reportSupportabilityMetrics();

                return builder.build();

            } catch (Exception e) {
                log.error("setDistributedTraceHeaders: Unable to add trace headers. ", e);
                TraceContext.reportSupportabilityExceptionMetric(e);
            }
        }

        return response;
    }

}
