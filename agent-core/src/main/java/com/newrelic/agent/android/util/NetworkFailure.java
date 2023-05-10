/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.util;

import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpResponseException;
import org.apache.http.conn.ConnectTimeoutException;

import javax.net.ssl.SSLException;

import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

public enum NetworkFailure {

    Unknown(-1),
    BadURL(-1000),
    TimedOut(-1001),
    CannotConnectToHost(-1004),
    DNSLookupFailed(-1006),
    BadServerResponse(-1011),
    SecureConnectionFailed(-1200);

    private int errorCode;
    private static final AgentLog log = AgentLogManager.getAgentLog();

    NetworkFailure(int errorCode) {
        this.errorCode = errorCode;
    }

    public static NetworkFailure exceptionToNetworkFailure(final Exception e) {
        log.error("NetworkFailure.exceptionToNetworkFailure: Attempting to convert network exception " + e.getClass().getName() + " to error code.");
        NetworkFailure error = Unknown;

        try {
            // Apache exceptions must be first
            if (e instanceof ConnectTimeoutException) {
                error = TimedOut;
            } else if (e instanceof HttpResponseException || e instanceof ClientProtocolException) {
                error = BadServerResponse;
            }
        } catch (NoClassDefFoundError e1) {
            // Android 9: no default Apache libs available

        } finally {
            if (e instanceof SocketTimeoutException) {
                error = TimedOut;
            } else if (e instanceof UnknownHostException) {
                error = DNSLookupFailed;
            } else if (e instanceof ConnectException) {
                error = CannotConnectToHost;
            } else if (e instanceof MalformedURLException) {
                error = BadURL;
            } else if (e instanceof SSLException) {
                error = SecureConnectionFailed;
            }
        }

        return error;
    }

    public static int exceptionToErrorCode(final Exception e) {
        return exceptionToNetworkFailure(e).getErrorCode();
    }

    public static NetworkFailure fromErrorCode(final int errorCode) {
        log.debug("fromErrorCode invoked with errorCode: " + errorCode);
        for (NetworkFailure failure : NetworkFailure.values()) {
            if (failure.getErrorCode() == errorCode) {
                log.debug("fromErrorCode found matching failure: " + failure);
                return failure;
            }
        }
        return Unknown;
    }

    public int getErrorCode() {
        return errorCode;
    }
}
