/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation;

import static com.newrelic.agent.android.instrumentation.TransactionStateUtil.setTrace;

import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.TaskQueue;
import com.newrelic.agent.android.api.common.TransactionData;
import com.newrelic.agent.android.distributedtracing.TraceContext;
import com.newrelic.agent.android.distributedtracing.TraceHeader;
import com.newrelic.agent.android.instrumentation.io.CountingInputStream;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.measurement.http.HttpTransactionMeasurement;
import com.newrelic.agent.android.tracing.TraceMachine;
import com.newrelic.agent.android.util.Constants;

import org.apache.http.Header;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.RequestLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.protocol.HttpContext;

import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.Map;
import java.util.TreeMap;

@Deprecated
@SuppressWarnings("deprecation")
public final class ApacheInstrumentation {
    protected static final AgentLog log = AgentLogManager.getAgentLog();

    private ApacheInstrumentation() {
    }

    @ReplaceCallSite
    public static HttpResponse execute(HttpClient httpClient, HttpHost target, HttpRequest request,
                                       HttpContext context) throws IOException {
        final TransactionState transactionState = new TransactionState();

        setCrossProcessHeader(request);

        if (FeatureFlag.featureEnabled(FeatureFlag.DistributedTracing)) {
            setTrace(transactionState);
            setDistributedTraceHeaders(transactionState, request);
        }

        try {
            return delegate(httpClient.execute(target, delegate(target, request, transactionState), context), transactionState);
        } catch (IOException e) {
            httpClientError(transactionState, e);
            throw e;
        }
    }

    @ReplaceCallSite
    public static <T> T execute(HttpClient httpClient, HttpHost target, HttpRequest request,
                                ResponseHandler<? extends T> responseHandler, HttpContext context)
            throws IOException {
        final TransactionState transactionState = new TransactionState();

        setCrossProcessHeader(request);

        if (FeatureFlag.featureEnabled(FeatureFlag.DistributedTracing)) {
            setTrace(transactionState);
            setDistributedTraceHeaders(transactionState, request);
        }

        try {
            return httpClient.execute(target, delegate(target, request, transactionState), delegate(responseHandler, transactionState), context);
        } catch (ClientProtocolException e) {
            httpClientError(transactionState, e);
            throw e;
        } catch (IOException e) {
            httpClientError(transactionState, e);
            throw e;
        }
    }

    @ReplaceCallSite
    public static <T> T execute(HttpClient httpClient, HttpHost target, HttpRequest request,
                                ResponseHandler<? extends T> responseHandler) throws IOException {
        final TransactionState transactionState = new TransactionState();

        setCrossProcessHeader(request);

        if (FeatureFlag.featureEnabled(FeatureFlag.DistributedTracing)) {
            setTrace(transactionState);
            setDistributedTraceHeaders(transactionState, request);
        }

        try {
            return httpClient.execute(target, delegate(target, request, transactionState), delegate(responseHandler, transactionState));
        } catch (ClientProtocolException e) {
            httpClientError(transactionState, e);
            throw e;
        } catch (IOException e) {
            httpClientError(transactionState, e);
            throw e;
        }
    }

    @ReplaceCallSite
    public static HttpResponse execute(HttpClient httpClient, HttpHost target, HttpRequest request)
            throws IOException {
        final TransactionState transactionState = new TransactionState();

        setCrossProcessHeader(request);

        if (FeatureFlag.featureEnabled(FeatureFlag.DistributedTracing)) {
            setTrace(transactionState);
            setDistributedTraceHeaders(transactionState, request);
        }

        try {
            return delegate(httpClient.execute(target, delegate(target, request, transactionState)), transactionState);
        } catch (IOException e) {
            httpClientError(transactionState, e);
            throw e;
        }
    }

    @ReplaceCallSite
    public static HttpResponse execute(HttpClient httpClient, HttpUriRequest request, HttpContext context)
            throws IOException {
        final TransactionState transactionState = new TransactionState();

        setCrossProcessHeader(request);

        if (FeatureFlag.featureEnabled(FeatureFlag.DistributedTracing)) {
            setTrace(transactionState);
            setDistributedTraceHeaders(transactionState, request);
        }

        try {
            return delegate(httpClient.execute(delegate(request, transactionState), context), transactionState);
        } catch (IOException e) {
            httpClientError(transactionState, e);
            throw e;
        }
    }

    @ReplaceCallSite
    public static <T> T execute(HttpClient httpClient, HttpUriRequest request,
                                ResponseHandler<? extends T> responseHandler, HttpContext context)
            throws IOException {
        final TransactionState transactionState = new TransactionState();

        setCrossProcessHeader(request);

        if (FeatureFlag.featureEnabled(FeatureFlag.DistributedTracing)) {
            setTrace(transactionState);
            setDistributedTraceHeaders(transactionState, request);
        }

        try {
            return httpClient.execute(delegate(request, transactionState), delegate(responseHandler, transactionState), context);
        } catch (ClientProtocolException e) {
            httpClientError(transactionState, e);
            throw e;
        } catch (IOException e) {
            httpClientError(transactionState, e);
            throw e;
        }
    }

    @ReplaceCallSite
    public static <T> T execute(HttpClient httpClient, HttpUriRequest request,
                                ResponseHandler<? extends T> responseHandler) throws IOException {
        final TransactionState transactionState = new TransactionState();

        setCrossProcessHeader(request);

        if (FeatureFlag.featureEnabled(FeatureFlag.DistributedTracing)) {
            setTrace(transactionState);
            setDistributedTraceHeaders(transactionState, request);
        }

        try {
            return httpClient.execute(delegate(request, transactionState), delegate(responseHandler, transactionState));
        } catch (ClientProtocolException e) {
            httpClientError(transactionState, e);
            throw e;
        } catch (IOException e) {
            httpClientError(transactionState, e);
            throw e;
        }
    }

    @ReplaceCallSite
    public static HttpResponse execute(HttpClient httpClient, HttpUriRequest request) throws IOException {
        final TransactionState transactionState = new TransactionState();

        setCrossProcessHeader(request);

        if (FeatureFlag.featureEnabled(FeatureFlag.DistributedTracing)) {
            setTrace(transactionState);
            setDistributedTraceHeaders(transactionState, request);
        }

        try {
            return delegate(httpClient.execute(delegate(request, transactionState)), transactionState);
        } catch (IOException e) {
            httpClientError(transactionState, e);
            throw e;
        }
    }

    protected static void httpClientError(TransactionState transactionState, Exception e) {
        if (!transactionState.isComplete()) {
            TransactionStateUtil.setErrorCodeFromException(transactionState, e);
            TransactionData transactionData = transactionState.end();

            // If no transaction data is available, don't record a transaction measurement.
            if (transactionData != null) {
                transactionData.setResponseBody(e.toString());

                TaskQueue.queue(new HttpTransactionMeasurement(transactionData));
            }
        }
    }

    private static HttpUriRequest delegate(final HttpUriRequest request, TransactionState transactionState) {
        //
        // It's tempting to call transactionState.setUrl(...) here, but leave that to the HttpRequest version
        // of this function below.
        //
        return inspectAndInstrument(transactionState, request);
    }

    private static HttpRequest delegate(final HttpHost host, final HttpRequest request, TransactionState transactionState) {
        return inspectAndInstrument(transactionState, host, request);
    }

    private static HttpResponse delegate(final HttpResponse response, TransactionState transactionState) {
        return inspectAndInstrument(transactionState, response);
    }

    private static <T> ResponseHandler<? extends T> delegate(final ResponseHandler<? extends T> handler, TransactionState transactionState) {
        return com.newrelic.agent.android.instrumentation.httpclient.ResponseHandlerImpl.wrap(handler, transactionState);
    }

    private static void setCrossProcessHeader(final HttpRequest request) {
        final String crossProcessId = Agent.getCrossProcessId();
        if (crossProcessId != null) {
            TraceMachine.setCurrentTraceParam("cross_process_data", crossProcessId);
            request.setHeader(Constants.Network.CROSS_PROCESS_ID_HEADER, crossProcessId);
        }
    }

    static void setDistributedTraceHeaders(final TransactionState transactionState, final HttpRequest request) {
        if (transactionState.getTrace() != null) {
            try {
                TraceContext traceContext = transactionState.getTrace();

                if (traceContext != null) {
                    for (TraceHeader traceHeader : traceContext.getHeaders()) {
                        request.setHeader(traceHeader.getHeaderName(), traceHeader.getHeaderValue());
                    }
                    TraceContext.reportSupportabilityMetrics();
                }


            } catch (Exception e) {
                log.error("setDistributedTraceHeaders: Unable to add trace headers. ", e);
                TraceContext.reportSupportabilityExceptionMetric(e);
            }
        }
    }

    static void setDistributedTraceHeaders(final TransactionState transactionState, final HttpResponse response) {
        if (transactionState.getTrace() != null) {
            try {
                TraceContext traceContext = transactionState.getTrace();
                if (traceContext != null) {
                    for (TraceHeader traceHeader : traceContext.getHeaders()) {
                        if (response.containsHeader(traceHeader.getHeaderName())) {

                        } else {
                            response.setHeader(traceHeader.getHeaderName(), traceHeader.getHeaderValue());
                        }
                    }
                }

            } catch (Exception e) {
                log.error("setDistributedTraceHeaders: Unable to add trace headers. ", e);
                TraceContext.reportSupportabilityExceptionMetric(e);
            }
        }
    }

    public static HttpRequest inspectAndInstrument(final TransactionState transactionState, final HttpHost host, final HttpRequest request) {
        String url = null;

        //
        // We need to reconstruct the full URL from the host & request.
        //
        final RequestLine requestLine = request.getRequestLine();
        if (requestLine != null) {
            final String uri = requestLine.getUri();
            final boolean isAbsoluteUri = uri != null && uri.length() >= 10 && uri.substring(0, 10).indexOf("://") >= 0;
            if (!isAbsoluteUri && uri != null && host != null) {
                final String prefix = host.toURI();
                url = prefix + ((prefix.endsWith("/") || uri.startsWith("/")) ? "" : "/") + uri;
            } else if (isAbsoluteUri) {
                url = uri;
            }

            TransactionStateUtil.inspectAndInstrument(transactionState, url, requestLine.getMethod());
        }

        setCrossProcessHeader(request);
        if (FeatureFlag.featureEnabled(FeatureFlag.DistributedTracing)) {
            setTrace(transactionState);
            setDistributedTraceHeaders(transactionState, request);
        }

        //
        // We *need* a URL & HTTP method to provide the collector with meaningful info.
        //
        if (transactionState.getUrl() == null || transactionState.getHttpMethod() == null) {
            try {
                //
                // Capture a stack trace to get some idea of what caused this.
                //
                throw new Exception("TransactionData constructor was not provided with a valid URL, host or HTTP method");
            } catch (Exception e) {
                log.error(MessageFormat.format(
                        "TransactionStateUtil.inspectAndInstrument(...) for {0} could not determine request URL or HTTP method [host={1}, requestLine={2}]", request.getClass().getCanonicalName(), host, requestLine), e);
                return request;
            }
        }

        wrapRequestEntity(transactionState, request);
        return request;
    }

    public static HttpUriRequest inspectAndInstrument(final TransactionState transactionState, final HttpUriRequest request) {
        setCrossProcessHeader(request);
        if (FeatureFlag.featureEnabled(FeatureFlag.DistributedTracing)) {
            setTrace(transactionState);
            setDistributedTraceHeaders(transactionState, request);
        }
        TransactionStateUtil.inspectAndInstrument(transactionState, request.getURI().toString(), request.getMethod());
        wrapRequestEntity(transactionState, request);
        return request;
    }

    private static void wrapRequestEntity(final TransactionState transactionState, final HttpRequest request) {
        if (request instanceof HttpEntityEnclosingRequest) {
            final HttpEntityEnclosingRequest entityEnclosingRequest = (HttpEntityEnclosingRequest) request;
            if (entityEnclosingRequest.getEntity() != null) {
                entityEnclosingRequest.setEntity(new com.newrelic.agent.android.instrumentation.httpclient.HttpRequestEntityImpl(entityEnclosingRequest.getEntity(), transactionState));
            }
        }
    }

    public static HttpResponse inspectAndInstrument(final TransactionState transactionState, final HttpResponse response) {
        transactionState.setStatusCode(response.getStatusLine().getStatusCode());

        final Header[] appDataHeader = response.getHeaders(Constants.Network.APP_DATA_HEADER);
        if (appDataHeader != null && appDataHeader.length > 0 && !"".equals(appDataHeader[0].getValue())) {
            transactionState.setAppData(appDataHeader[0].getValue());
        }

        final Header[] contentLengthHeader = response.getHeaders(Constants.Network.CONTENT_LENGTH_HEADER);
        long contentLengthFromHeader = TransactionStateUtil.CONTENTLENGTH_UNKNOWN;
        if (contentLengthHeader != null && contentLengthHeader.length > 0) {
            try {
                contentLengthFromHeader = Long.parseLong(contentLengthHeader[0].getValue());
                transactionState.setBytesReceived(contentLengthFromHeader);

                addTransactionAndErrorData(transactionState, response);
            } catch (NumberFormatException e) {
                log.warn("Failed to parse content length: " + e);
            }
        } else if (response.getEntity() != null) {
            response.setEntity(new com.newrelic.agent.android.instrumentation.httpclient.HttpResponseEntityImpl(response.getEntity(), transactionState, contentLengthFromHeader));
        } else {
            //
            // No content-length header and no HttpEntity? Not much else we can do for 'em.
            //
            transactionState.setBytesReceived(0);
            addTransactionAndErrorData(transactionState, null);
        }

        setDistributedTraceHeaders(transactionState, response);

        return response;
    }

    protected static void addTransactionAndErrorData(TransactionState transactionState, HttpResponse response) {
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
            String responseBody = "";
            Map<String, String> params = new TreeMap<String, String>();

            if (response != null) {

                try {
                    if (response.getEntity() != null) {
                        if (!(response.getEntity() instanceof com.newrelic.agent.android.instrumentation.httpclient.HttpRequestEntityImpl)) {
                            // We need to wrap the entity in order to get to the response body stream.
                            response.setEntity(new com.newrelic.agent.android.instrumentation.httpclient.ContentBufferingResponseEntityImpl(response.getEntity()));
                        }
                        final InputStream content = response.getEntity().getContent();
                        if (content instanceof CountingInputStream) {
                            responseBody = ((CountingInputStream) content).getBufferAsString();
                        } else {
                            log.error("Unable to wrap content stream for entity");
                        }
                    } else {
                        log.debug("null response entity. response-body will be reported empty");
                    }
                } catch (IllegalStateException e) {
                    log.error(e.toString());
                } catch (IOException e) {
                    log.error(e.toString());
                }

                // If there is a Content-Type header present, add it to the error param map.
                final Header[] contentTypeHeader = response.getHeaders(Constants.Network.CONTENT_TYPE_HEADER);
                String contentType = null;

                if (contentTypeHeader != null && contentTypeHeader.length > 0 && !"".equals(contentTypeHeader[0].getValue())) {
                    contentType = contentTypeHeader[0].getValue();
                }

                if (contentType != null && contentType.length() > 0) {
                    params.put(Constants.Transactions.CONTENT_TYPE, contentType);
                }
            }

            /* Also add Content-Length. TransactionState bytesReceived is set directly
             * from the Content-Length header in this error case.
             */
            params.put(Constants.Transactions.CONTENT_LENGTH, transactionState.getBytesReceived() + "");

            transactionData.setResponseBody(responseBody);
            transactionData.setParams(params);
        }

        TaskQueue.queue(new HttpTransactionMeasurement(transactionData));
    }

}
