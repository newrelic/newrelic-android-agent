/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation;

import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;

import java.net.HttpURLConnection;

import javax.net.ssl.HttpsURLConnection;

public class OkHttp3Instrumentation {
    private static final AgentLog log = AgentLogManager.getAgentLog();

    public OkHttp3Instrumentation() {}

    @WrapReturn(className="okhttp3/OkHttpClient", methodName="open", methodDesc="(Ljava/net/URL;)Ljava/net/HttpURLConnection;")
    public static HttpURLConnection open(final HttpURLConnection connection) {
        if (connection instanceof HttpsURLConnection) {
            return new HttpsURLConnectionExtension((HttpsURLConnection) connection);
        } else if (connection != null){
            return new HttpURLConnectionExtension(connection);
        } else {
            return null;
        }
    }

    @WrapReturn(className="okhttp3/OkHttpClient", methodName="open", methodDesc="(Ljava/net/URL;Ljava/net/Proxy)Ljava/net/HttpURLConnection;")
    public static HttpURLConnection openWithProxy(final HttpURLConnection connection) {
        if (connection instanceof HttpsURLConnection) {
            return new HttpsURLConnectionExtension((HttpsURLConnection) connection);
        } else if (connection != null){
            return new HttpURLConnectionExtension(connection);
        } else {
            return null;
        }
    }

    @WrapReturn(className="okhttp3/OkUrlFactory", methodName="open", methodDesc="(Ljava/net/URL;)Ljava/net/HttpURLConnection;")
    public static HttpURLConnection urlFactoryOpen(final HttpURLConnection connection) {
        log.debug("OkHttpInstrumentation - wrapping return of call to OkUrlFactory.open...");
        if (connection instanceof HttpsURLConnection) {
            return new HttpsURLConnectionExtension((HttpsURLConnection) connection);
        } else if (connection != null){
            return new HttpURLConnectionExtension(connection);
        } else {
            return null;
        }
    }

}
