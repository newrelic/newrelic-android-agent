/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation;

import com.newrelic.agent.android.TaskQueue;
import com.newrelic.agent.android.api.common.TransactionData;
import com.newrelic.agent.android.measurement.HttpTransactionMeasurement;

import java.net.HttpURLConnection;
import java.net.URLConnection;

import javax.net.ssl.HttpsURLConnection;

public final class URLConnectionInstrumentation {
    private URLConnectionInstrumentation() {
    }

    @WrapReturn(className = "java/net/URL", methodName = "openConnection", methodDesc = "()Ljava/net/URLConnection;")
    public static URLConnection openConnection(final URLConnection connection) {
        if (connection instanceof HttpsURLConnection) {
            return new HttpsURLConnectionExtension((HttpsURLConnection) connection);
        } else if (connection instanceof HttpURLConnection) {
            return new HttpURLConnectionExtension((HttpURLConnection) connection);
        } else {
            return connection;
        }
    }

    @WrapReturn(className = "java/net/URL", methodName = "openConnection", methodDesc = "(Ljava/net/Proxy;)Ljava/net/URLConnection;")
    public static URLConnection openConnectionWithProxy(final URLConnection connection) {
        if (connection instanceof HttpsURLConnection) {
            return new HttpsURLConnectionExtension((HttpsURLConnection) connection);
        } else if (connection instanceof HttpURLConnection) {
            return new HttpURLConnectionExtension((HttpURLConnection) connection);
        } else {
            return connection;
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

}
