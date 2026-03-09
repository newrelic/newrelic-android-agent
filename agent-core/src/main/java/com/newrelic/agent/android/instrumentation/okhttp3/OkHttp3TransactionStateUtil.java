/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation.okhttp3;

import static com.newrelic.agent.android.analytics.AnalyticsAttribute.ATTRIBUTE_VALUE_MAX_LENGTH;
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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
                // Silently handle - content length is optional
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

            // If still unknown, try Content-Length header
            if (contentLength < 0L) {
                String contentLengthHeader = response.header(Constants.Network.CONTENT_LENGTH_HEADER);
                if (contentLengthHeader != null && contentLengthHeader.length() > 0) {
                    try {
                        contentLength = Long.parseLong(contentLengthHeader);
                    } catch (NumberFormatException var10) {
                        log.debug("Failed to parse content length: " + var10);
                    }
                } else {
                    Response networkResponse = response.networkResponse();
                    if (networkResponse != null) {
                        contentLengthHeader = networkResponse.header(Constants.Network.CONTENT_LENGTH_HEADER);
                        if (contentLengthHeader != null && contentLengthHeader.length() > 0) {
                            try {
                                contentLength = Long.parseLong(contentLengthHeader);
                            } catch (NumberFormatException var5) {
                                log.debug("Failed to parse network response content length: " + var5);
                            }
                        } else {
                            if (networkResponse.body() != null) {
                                contentLength = networkResponse.body().contentLength();
                            }
                        }
                    } else {
                        // Response likely from cache with no content length
                        log.debug("No network response available, content length unknown");
                    }
                }
            }
        }

        // NOTE: Do NOT use peekBody() to determine content length for responses with unknown length.
        // peekBody() can block indefinitely for streaming responses (SSE, chunked transfers, etc.)
        // causing ~100 second delays. It's acceptable for content length to remain unknown (-1).

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
                Map<String, String> params = new TreeMap<>();

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
                    // Only capture body if content length is known and positive.
                    // Streaming responses (SSE, chunked) have contentLength = -1,
                    // so peekBody() won't be called and won't block.
                    if (contentLength > 0) {
                        // Limit response body capture to New Relic's attribute value limit (4096 bytes)
                        // to prevent memory issues and respect platform constraints
                        long bytesToRead = Math.min(contentLength, ATTRIBUTE_VALUE_MAX_LENGTH);
                        responseBodyString = response.peekBody(bytesToRead).string();

                        // Truncate if the string exceeds the limit (multi-byte characters can cause this)
                        if (responseBodyString.length() > ATTRIBUTE_VALUE_MAX_LENGTH) {
                            responseBodyString = responseBodyString.substring(0, ATTRIBUTE_VALUE_MAX_LENGTH);
                            log.debug("Response body truncated to " + ATTRIBUTE_VALUE_MAX_LENGTH + " characters");
                        }

                        if (contentLength > bytesToRead) {
                            log.debug("Error response body truncated from " + contentLength + " to " + bytesToRead + " bytes");
                        }
                    }
                } catch (Exception e) {
                    log.debug("Failed to read error response body: " + e.getMessage());
                    if (response.message() != null) {
                        log.debug("Using response message as fallback");
                        responseBodyString = response.message();
                        // Ensure response message also respects the limit
                        if (responseBodyString.length() > ATTRIBUTE_VALUE_MAX_LENGTH) {
                            responseBodyString = responseBodyString.substring(0, ATTRIBUTE_VALUE_MAX_LENGTH);
                        }
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

    /**
     * Adds HTTP headers from the request as custom attributes to the transaction state.
     * Only headers configured via {@link HttpHeaders#addHttpHeaderAsAttribute(String)} are captured.
     *
     * <p><b>Thread Safety:</b> Creates a defensive copy of the header list to avoid
     * {@link java.util.ConcurrentModificationException} if headers are modified concurrently.</p>
     *
     * @param transactionState The transaction state to add attributes to
     * @param request The OkHttp request containing headers
     */
    public static void addHeadersAsCustomAttribute(TransactionState transactionState, Request request) {
        Map<String, String> headers = new HashMap<>();

        // Defensive copy to prevent ConcurrentModificationException
        Set<String> headersCopy = new HashSet<>(HttpHeaders.getInstance().getHttpHeaders());

        for (String headerName : headersCopy) {
            String headerValue = request.headers().get(headerName);
            if (headerValue != null) {
                headers.put(HttpHeaders.translateApolloHeader(headerName), headerValue);
            }
        }
        transactionState.setParams(headers);
    }


}
