/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation;

import com.newrelic.agent.android.api.common.CarrierType;
import com.newrelic.agent.android.api.common.TransactionData;
import com.newrelic.agent.android.api.common.WanType;
import com.newrelic.agent.android.api.v1.Defaults;
import com.newrelic.agent.android.distributedtracing.TraceContext;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.tracing.TraceMachine;
import com.newrelic.agent.android.util.Util;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class TransactionState {

    enum State {
        READY,
        SENT,
        COMPLETE
    }

    private static final AgentLog log = AgentLogManager.getAgentLog();

    private String url;
    private String httpMethod;
    private int statusCode = 0;
    private int errorCode = 0;
    private long bytesSent = 0;
    private long bytesReceived = 0;
    private long startTime;
    private long endTime = 0;
    private String appData;
    private String carrier = CarrierType.UNKNOWN;
    private String wanType = WanType.UNKNOWN;
    private State state = State.READY;
    private String contentType;
    private TransactionData transactionData;
    private TraceContext trace;

    private Map<String, String> params;

    public Map<String, String> getParams() {
        return params;
    }

    public void setParams(Map<String, String> params) {
        this.params = params;
    }

    public TransactionState() {
        this.startTime = System.currentTimeMillis();
        this.params = new HashMap<>();
        TraceMachine.enterNetworkSegment("External/unknownhost");
    }

    public TraceContext getTrace() {
        return trace;
    }

    public void setTrace(TraceContext traceContext) {
        if (!isSent()) {
            this.trace = traceContext;
        } else {
            log.debug("setCatPayload(...) called on TransactionState in " + state.toString() + " state");
        }
    }

    public void setCarrier(final String carrier) {
        if (!isSent()) {
            this.carrier = carrier;
            TraceMachine.setCurrentTraceParam("carrier", carrier);
        } else {
            log.debug("setCarrier(...) called on TransactionState in " + state.toString() + " state");
        }
    }

    public void setWanType(final String wanType) {
        if (!isSent()) {
            this.wanType = wanType;
            TraceMachine.setCurrentTraceParam("wan_type", wanType);
        } else {
            log.debug("setWanType(...) called on TransactionState in " + state.toString() + " state");
        }
    }

    public void setAppData(final String appData) {
        if (!isComplete()) {
            this.appData = appData;
            TraceMachine.setCurrentTraceParam("encoded_app_data", appData);
        } else {
            log.debug("setAppData(...) called on TransactionState in " + state.toString() + " state");
        }
    }

    public void setUrl(final String urlString) {
        final String url = Util.sanitizeUrl(urlString);
        if (url == null)
            return;

        if (!isSent()) {
            this.url = url;

            try {
                TraceMachine.setCurrentDisplayName("External/" + new URL(url).getHost());
            } catch (MalformedURLException e) {
                log.error("unable to parse host name from " + url);
            }
            TraceMachine.setCurrentTraceParam("uri", url);
        } else {
            log.debug("setUrl(...) called on TransactionState in " + state.toString() + " state");
        }
    }

    public void setHttpMethod(final String httpMethod) {
        if (!isSent()) {
            this.httpMethod = httpMethod;
            TraceMachine.setCurrentTraceParam("http_method", httpMethod);
        } else {
            log.debug("setHttpMethod(...) called on TransactionState in " + state.toString() + " state");
        }
    }

    public String getUrl() {
        return this.url;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public boolean isSent() {
        return (state == State.SENT) || (state == State.COMPLETE);
    }

    public boolean isComplete() {
        return (state == State.COMPLETE);
    }

    public void setStatusCode(int statusCode) {
        if (!isComplete()) {
            this.statusCode = statusCode;
            TraceMachine.setCurrentTraceParam("status_code", statusCode);
        } else {
            log.debug("setStatusCode(...) called on TransactionState in " + state.toString() + " state");
        }
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setErrorCode(int errorCode) {
        if (!isComplete()) {
            this.errorCode = errorCode;
            TraceMachine.setCurrentTraceParam("error_code", errorCode);
        } else {
            if (transactionData != null) {
                transactionData.setErrorCode(errorCode);
            }
            log.debug("setErrorCode(...) called on TransactionState in " + state.toString() + " state");
        }
    }

    public int getErrorCode() {
        return errorCode;
    }

    public void setBytesSent(long bytesSent) {
        if (!isComplete()) {
            this.bytesSent = bytesSent;
            TraceMachine.setCurrentTraceParam("bytes_sent", bytesSent);
        } else {
            log.debug("setBytesSent(...) called on TransactionState in " + state.toString() + " state");
        }
    }

    public long getBytesSent() {
        return bytesSent;
    }

    public void setBytesReceived(long bytesReceived) {
        if (!isComplete()) {
            this.bytesReceived = bytesReceived;
            TraceMachine.setCurrentTraceParam("bytes_received", bytesReceived);
        } else {
            log.debug("setBytesReceived(...) called on TransactionState in " + state.toString() + " state");
        }
    }

    public long getBytesReceived() {
        return bytesReceived;
    }

    public TransactionData end() {
        if (!isComplete()) {
            state = State.COMPLETE;
            endTime = System.currentTimeMillis();
            TraceMachine.exitMethod();
        }
        return toTransactionData();
    }

    TransactionData toTransactionData() {
        if (!isComplete()) {
            log.debug("toTransactionData() called on incomplete TransactionState");
        }

        if (url == null) {
            log.error("Attempted to convert a TransactionState instance with no URL into a TransactionData");
            return null;
        }

        float totalTimeAsSeconds = (endTime - startTime) / 1000.0f;

        // startTime is set when the instance is created, so should always be good
        // so negative if endtime < starttime
        if (totalTimeAsSeconds < 0) {
            log.error("Invalid response duration detected: start[" + startTime + "] end[" + endTime + "]");
            StatsEngine.get().inc(MetricNames.SUPPORTABILITY_RESPONSE_TIME_INVALID_DURATION);
            // sanitize duration (total) time:
            totalTimeAsSeconds = 0f;
        }

        if (transactionData == null) {
            transactionData = new TransactionData(url,
                    httpMethod,
                    carrier,
                    totalTimeAsSeconds,
                    statusCode,
                    errorCode,
                    bytesSent,
                    bytesReceived,
                    appData,
                    wanType, trace, "", params, null);
        }

        return transactionData;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public boolean isErrorOrFailure() {
        return isRequestError() || isRequestFailure();
    }

    public boolean isRequestFailure() {
        return isRequestFailure(errorCode);
    }

    public boolean isRequestError() {
        return isRequestError(statusCode);
    }

    public static boolean isRequestFailure(int errorCode) {
        return errorCode != 0;
    }

    public static boolean isRequestError(int statusCode) {
        return statusCode >= Defaults.MIN_HTTP_ERROR_STATUS_CODE;
    }

    @Override
    public String toString() {
        return "TransactionState{" +
                "url='" + url + '\'' +
                ", httpMethod='" + httpMethod + '\'' +
                ", statusCode=" + statusCode +
                ", errorCode=" + errorCode +
                ", bytesSent=" + bytesSent +
                ", bytesReceived=" + bytesReceived +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", appData='" + appData + '\'' +
                ", carrier='" + carrier + '\'' +
                ", wanType='" + wanType + '\'' +
                ", state=" + state +
                ", contentType='" + contentType + '\'' +
                ", transactionData=" + transactionData +
                '}';
    }

    State setState(State state) {
        this.state = state;
        return this.state;
    }
}
