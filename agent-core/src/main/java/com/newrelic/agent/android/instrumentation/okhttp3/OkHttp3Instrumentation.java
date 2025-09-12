
/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation.okhttp3;

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

import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.internal.Internal;

public class OkHttp3Instrumentation {
    private static final AgentLog log = AgentLogManager.getAgentLog();
    static NewRelicWebSocketListener webSocketListener = new NewRelicWebSocketListener();

    private OkHttp3Instrumentation() {
    }

    @ReplaceCallSite
    public static Request build(Request.Builder builder) {
        return new RequestBuilderExtension(builder).build();
    }

    @ReplaceCallSite
    public static Call newCall(OkHttpClient client, Request request) {
        TransactionState transactionState = new TransactionState();
        addHeadersAsCustomAttribute(transactionState, request);
        
        // Create a new client with New Relic interceptor added after existing interceptors
        OkHttpClient instrumentedClient = addNewRelicInterceptor(client, transactionState);
        
        if (FeatureFlag.featureEnabled(FeatureFlag.DistributedTracing)) {
            try {
                // start the trace with a new call
                TraceContext trace = DistributedTracing.getInstance().startTrace(transactionState);
                transactionState.setTrace(trace);

                Request instrumentedRequest = OkHttp3TransactionStateUtil.setDistributedTraceHeaders(transactionState, request);
                return new CallExtension(instrumentedClient, instrumentedRequest, instrumentedClient.newCall(instrumentedRequest), transactionState);

            } catch (Exception e) {
                log.error(e.getMessage());
            }
        }
        return new CallExtension(instrumentedClient, request, instrumentedClient.newCall(request), transactionState);
    }

    private static void setWebSocketListener(OkHttpClient client,Request request) {
        client.newWebSocket(request,webSocketListener) ;
    }

    private static void addHeadersAsCustomAttribute(TransactionState transactionState, Request request) {

        Map<String, String> headers = new HashMap<>();
        for (String s : HttpHeaders.getInstance().getHttpHeaders()) {
            if (request.headers().get(s) != null) {
                headers.put(HttpHeaders.translateApolloHeader(s), request.headers().get(s));
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

    @SuppressWarnings("deprecation")
    @ReplaceCallSite(isStatic = false, scope = "okhttp3.OkUrlFactory")
    public static HttpURLConnection open(okhttp3.OkUrlFactory factory, URL url) {
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


    public static class OkHttp35 {

        @ReplaceCallSite
        public static void setCallWebSocket(Internal internal, Call call) {
            try {
                if (call instanceof CallExtension) {
                    call = ((CallExtension) call).getImpl();
                }
                //  OkHttp 3.4.x : internal.setCallWebSocket(call);
                Method setCallWebSocket = Internal.class.getMethod("setCallWebSocket", Call.class);
                if (setCallWebSocket != null) {
                    setCallWebSocket.invoke(internal, call);
                } else {
                    logReflectionError("setCallWebSocket(Lokhttp3/Call;)V");
                }

            } catch (Exception e) {
                log.error("OkHttp3Instrumentation: " + e.getMessage());
            }
        }

        @ReplaceCallSite
        public static okhttp3.internal.connection.StreamAllocation callEngineGetStreamAllocation(Internal internal, Call call) {
            okhttp3.internal.connection.StreamAllocation streamAllocation = null;

            try {
                if (call instanceof CallExtension) {
                    call = ((CallExtension) call).getImpl();
                }
                // OkHttp 3.4.x : okhttp3.internal.connection.streamAllocation = internal.callEngineGetStreamAllocation(call);
                Method callEngineGetStreamAllocation = Internal.class.getMethod("callEngineGetStreamAllocation", Call.class);
                if (callEngineGetStreamAllocation != null) {
                    streamAllocation = (okhttp3.internal.connection.StreamAllocation) callEngineGetStreamAllocation.invoke(internal, call);
                } else {
                    logReflectionError("callEngineGetStreamAllocation(Lokhttp3/Call;)Lokhttp3/internal/connection/StreamAllocation;");
                }

            } catch (Exception e) {
                log.error("OkHttp3Instrumentation: " + e.getMessage());
            }

            return streamAllocation;
        }

        @ReplaceCallSite
        public static Call newWebSocketCall(Internal internal, OkHttpClient client, Request request) {
            Call call = null;
            //WebSocket Listner
            setWebSocketListener(client,request);
            try {
                // OkHttp 3.5.x : call = internal.newWebSocketCall(client, request);
                Method newWebSocketCall = Internal.class.getMethod("newWebSocketCall", OkHttpClient.class, Request.class);
                if (newWebSocketCall != null) {
                    TransactionState transactionState = new TransactionState();
                    if (FeatureFlag.featureEnabled(FeatureFlag.DistributedTracing)) {
                        // start the trace with a new call
                        TraceContext trace = DistributedTracing.getInstance().startTrace(transactionState);
                        transactionState.setTrace(trace);

                        Request instrumentedRequest = OkHttp3TransactionStateUtil.setDistributedTraceHeaders(transactionState, request);
                        Call impl = (Call) newWebSocketCall.invoke(internal, client, instrumentedRequest);
                        call = new CallExtension(client, instrumentedRequest, impl, transactionState);
                    } else {
                        Call impl = (Call) newWebSocketCall.invoke(internal, client, request);
                        call = new CallExtension(client, request, impl, transactionState);
                    }
                } else {
                    logReflectionError("newWebSocketCall(Lokhttp3/OkHttpClient;Lokhttp3/Request;)Lokhttp3/Call;");
                }
            } catch (Exception e) {
                log.error("OkHttp3Instrumentation: " + e.getMessage());
            }

            return call;
        }
    }

    private static void logReflectionError(String signature) {
        String crlf = System.getProperty("line.separator");
        log.error("Unable to resolve method \"" + signature + "\"." + crlf +
                "This is usually due to building the app with unsupported OkHttp versions." + crlf +
                "Check your build configuration for compatibility.");
    }
    
    private static OkHttpClient addNewRelicInterceptor(OkHttpClient originalClient, TransactionState transactionState) {
        try {
            // Check if our interceptor is already added to avoid duplicates
            for (okhttp3.Interceptor interceptor : originalClient.interceptors()) {
                if (interceptor instanceof NewRelicInterceptor) {
                    log.debug("New Relic interceptor already present");
                    return originalClient;
                }
            }
            
            // Create new client builder from existing client
            OkHttpClient.Builder builder = originalClient.newBuilder();
            
            // Add New Relic interceptor after existing application interceptors  and only if there are existing interceptors
            if(!builder.interceptors().isEmpty()) {
                builder.addInterceptor(new NewRelicInterceptor(transactionState));
                return builder.build();
            }
            
            log.debug("Added New Relic interceptor after " + originalClient.interceptors().size() + " existing interceptors");
            
            return originalClient;
        } catch (Exception e) {
            log.error("Failed to add New Relic interceptor: " + e.getMessage());
            return originalClient;
        }
    }

    // New Relic interceptor class to capture URL changes and in future any such requests modifications
    private static class NewRelicInterceptor implements okhttp3.Interceptor {
        private final TransactionState transactionState;
        
        public NewRelicInterceptor(TransactionState transactionState) {
            this.transactionState = transactionState;
        }
        
        @Override
        public Response intercept(Chain chain) throws java.io.IOException {
            Request request = chain.request();
            String finalUrl = request.url().toString();
            
            log.debug("New Relic interceptor capturing final URL: " + finalUrl);
            
            // Update transaction state with the final URL after all customer interceptors
            if (transactionState != null) {
                transactionState.setUrl(finalUrl);
            }

            return chain.proceed(request);
        }
    }
}
