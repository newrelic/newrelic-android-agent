/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation.okhttp2;

import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.HttpHeaders;
import com.newrelic.agent.android.distributedtracing.DistributedTracing;
import com.newrelic.agent.android.distributedtracing.TraceContext;
import com.newrelic.agent.android.instrumentation.HttpURLConnectionExtension;
import com.newrelic.agent.android.instrumentation.HttpsURLConnectionExtension;
import com.newrelic.agent.android.instrumentation.ReplaceCallSite;
import com.newrelic.agent.android.instrumentation.TransactionState;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.squareup.okhttp.Call;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.ResponseBody;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.net.ssl.HttpsURLConnection;

public class OkHttp2Instrumentation {
    private static final AgentLog log = AgentLogManager.getAgentLog();
    static final String CACHED_RESPONSE_CLASS = "com.squareup.okhttp.Cache$CacheResponseBody";

    private OkHttp2Instrumentation() {

    }

    @ReplaceCallSite
    public static Request build(Request.Builder builder) {
        return new RequestBuilderExtension(builder).build();
    }

    @ReplaceCallSite
    public static Call newCall(OkHttpClient client, Request request) {
        TransactionState transactionState = new TransactionState();
        addHeadersAsCustomAttribute(transactionState, request);
        if (FeatureFlag.featureEnabled(FeatureFlag.DistributedTracing)) {
            try {
                // start the trace with a new call
                TraceContext trace = DistributedTracing.getInstance().startTrace(transactionState);
                transactionState.setTrace(trace);
                Request instrumentedRequest = OkHttp2TransactionStateUtil.setDistributedTraceHeaders(transactionState, request);
                return new CallExtension(client, instrumentedRequest, client.newCall(instrumentedRequest), transactionState);
            } catch (Exception e) {
                log.error(e.getMessage());
            }
        }
        return new CallExtension(client, request, client.newCall(request), transactionState);
    }

    /**
     * Adds HTTP headers from the request as custom attributes to the transaction state.
     * Only headers configured via {@link HttpHeaders#addHttpHeaderAsAttribute(String)} are captured.
     *
     * <p><b>Thread Safety:</b> Creates a defensive copy of the header list to avoid
     * {@link java.util.ConcurrentModificationException} if headers are modified concurrently.</p>
     *
     * @param transactionState The transaction state to add attributes to
     * @param request The OkHttp2 request containing headers
     */
    private static void addHeadersAsCustomAttribute(TransactionState transactionState, Request request) {
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

    @ReplaceCallSite
    public static Response.Builder body(Response.Builder builder, ResponseBody body) {
        return new ResponseBuilderExtension(builder).body(body);
    }

    @ReplaceCallSite
    public static Response.Builder newBuilder(Response.Builder builder) {
        return new ResponseBuilderExtension(builder);
    }

    @ReplaceCallSite(isStatic = false, scope = "com.squareup.okhttp.OkUrlFactory")
    public static HttpURLConnection open(com.squareup.okhttp.OkUrlFactory factory, URL url) {
        HttpURLConnection conn = factory.open(url);
        String protocol = url.getProtocol();

        if (protocol.equals("http")) {
            return new HttpURLConnectionExtension(conn);
        }
        if (protocol.equals("https") && conn instanceof HttpsURLConnection) {
            return new HttpsURLConnectionExtension((HttpsURLConnection) conn);
        }

        return new HttpURLConnectionExtension(conn);
    }

    @ReplaceCallSite
    public static ResponseBody body(Response response) {
        final ResponseBody body = response.body();
        try {
            if ((body != null) && (body instanceof PrebufferedResponseBody)) {
                final PrebufferedResponseBody responseBody = (PrebufferedResponseBody) body;
                // Cache uses a private internal class for response body representation,
                // and assumes it will always be that type when casting
                if (responseBody.impl.getClass().getName().equalsIgnoreCase(CACHED_RESPONSE_CLASS)) {
                    return responseBody.impl;
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage());
        }

        return body;
    }

}
