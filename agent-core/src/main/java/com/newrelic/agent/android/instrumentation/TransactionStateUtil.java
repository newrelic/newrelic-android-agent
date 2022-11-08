/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation;

import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.distributedtracing.DistributedTracing;
import com.newrelic.agent.android.distributedtracing.TraceContext;
import com.newrelic.agent.android.distributedtracing.TraceHeader;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.util.Constants;
import com.newrelic.agent.android.util.ExceptionHelper;

import java.io.IOException;
import java.net.HttpURLConnection;

@SuppressWarnings("deprecation")
public class TransactionStateUtil {
    protected static final AgentLog log = AgentLogManager.getAgentLog();
    protected static final long CONTENTLENGTH_UNKNOWN = -1L;

    public static void inspectAndInstrument(final TransactionState transactionState, final String url, final String httpMethod) {
        transactionState.setUrl(url);
        transactionState.setHttpMethod(httpMethod);
        transactionState.setCarrier(Agent.getActiveNetworkCarrier());
        transactionState.setWanType(Agent.getActiveNetworkWanType());
    }

    public static void inspectAndInstrument(final TransactionState transactionState, final HttpURLConnection conn) {
        inspectAndInstrument(transactionState, conn.getURL().toString(), conn.getRequestMethod());
    }

    public static void inspectAndInstrumentResponse(final TransactionState transactionState, final String appData, final int contentLength, final int statusCode) {
        if (appData != null && !appData.equals("")) {
            transactionState.setAppData(appData);
        }
        if (contentLength >= 0) {
            transactionState.setBytesReceived(contentLength);
        }
        transactionState.setStatusCode(statusCode);
    }

    public static void inspectAndInstrumentResponse(final TransactionState transactionState, final HttpURLConnection conn) {
        String appData = null;
        int contentLength = -1;
        int statusCode = 0;
        try {
            contentLength = conn.getContentLength();
            statusCode = conn.getResponseCode();
            appData = conn.getHeaderField(Constants.Network.APP_DATA_HEADER);
        } catch (IllegalStateException e) {
            // connection has closed and will not give up any further info
            log.debug("Failed to retrieve response data on a closed connection: " + e.getLocalizedMessage());
        } catch (IOException e) {
            log.debug("Failed to retrieve response data due to an I/O exception: " + e.getLocalizedMessage());
        } catch (NullPointerException e) {
            //
            // [MOBILE-27] if a request fails in such a way that Harmony doesn't get a response header object,
            //             conn.getResponseCode() will throw a NullPointerException.
            //
            log.error("Failed to retrieve response code due to underlying (Harmony?) NPE" + e.getLocalizedMessage());
        }

        inspectAndInstrumentResponse(transactionState, appData, contentLength, statusCode);
    }


    public static void setErrorCodeFromException(final TransactionState transactionState, final Exception e) {
        final int exceptionAsErrorCode = ExceptionHelper.exceptionToErrorCode(e);

        log.error("TransactionStateUtil: Attempting to convert network exception " + e.getClass().getName() + " to error code.");
        transactionState.setErrorCode(exceptionAsErrorCode);
    }

    public static void setTrace(final TransactionState transactionState) {
        if (transactionState.getTrace() == null) {
            TraceContext trace = DistributedTracing.getInstance().startTrace(transactionState);
            transactionState.setTrace(trace);
        }
    }

    /**
     * Must only be called prior to connect
     *
     * @param transactionState
     * @param conn
     */
    static void setDistributedTraceHeaders(TransactionState transactionState, HttpURLConnection conn) {
        if (FeatureFlag.featureEnabled(FeatureFlag.DistributedTracing)) {
            try {
                TraceContext traceContext = transactionState.getTrace();
                if (traceContext != null) {
                    for (TraceHeader traceHeader : traceContext.getHeaders()) {
                        conn.addRequestProperty(traceHeader.getHeaderName(), traceHeader.getHeaderValue());
                    }
                    TraceContext.reportSupportabilityMetrics();
                }

            } catch (Exception e) {
                log.error("setDistributedTraceHeaders: Unable to add trace headers. ", e);
                TraceContext.reportSupportabilityExceptionMetric(e);
            }
        }
    }

    public static void setCrossProcessHeader(final HttpURLConnection conn) {
        try {
            final String crossProcessId = Agent.getCrossProcessId();
            if (crossProcessId != null) {
                conn.setRequestProperty(Constants.Network.CROSS_PROCESS_ID_HEADER, crossProcessId);
            }
        } catch (Exception e) {
            log.error("setCrossProcessHeader: " + e.getLocalizedMessage());
        }
    }

}
