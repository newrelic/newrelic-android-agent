/*
 * Copyright (c) 2022-2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.measurement;

import com.newrelic.agent.android.api.common.TransactionData;
import com.newrelic.agent.android.distributedtracing.TraceContext;
import com.newrelic.agent.android.measurement.BaseMeasurement;
import com.newrelic.agent.android.measurement.MeasurementType;
import com.newrelic.agent.android.tracing.TraceMachine;
import com.newrelic.agent.android.util.Util;

import java.util.Map;

/**
 * A {@link com.newrelic.agent.android.measurement.Measurement} which represents a single HTTP Transaction.
 * <p>
 * This is a work in progress
 */
public class HttpTransactionMeasurement extends BaseMeasurement {
    private String url;
    private final String httpMethod;
    private final double totalTime;
    private final int statusCode;
    private final int errorCode;
    private final long bytesSent;
    private final long bytesReceived;
    private final String appData;
    private String responseBody;
    private Map<String, String> params;
    private TraceContext traceContext;
    private Map<String, Object> traceAttributes;


    public HttpTransactionMeasurement(String url, String httpMethod, int statusCode, int errorCode, long startTime, double totalTime, long bytesSent, long bytesReceived, String appData) {
        super(MeasurementType.Network);

        url = Util.sanitizeUrl(url);

        setName(url);
        setScope(TraceMachine.getCurrentScope());
        setStartTime(startTime);
        setEndTime(startTime + (int) totalTime);
        setExclusiveTime((int) (totalTime * 1000.0f));

        this.url = url;
        this.httpMethod = httpMethod;
        this.statusCode = statusCode;
        this.bytesSent = bytesSent;
        this.bytesReceived = bytesReceived;
        this.totalTime = totalTime;
        this.appData = appData;
        this.errorCode = errorCode;
        this.responseBody = null;
        this.params = null;
        this.traceContext = null;
    }

    public HttpTransactionMeasurement(String url, String httpMethod, int statusCode, int errorCode, long startTime, double totalTime, long bytesSent, long bytesReceived, String appData, String responseBody) {
        this(url, httpMethod, statusCode, errorCode, startTime, totalTime, bytesSent, bytesReceived, appData);
        this.responseBody = responseBody;
    }

    public HttpTransactionMeasurement(TransactionData transactionData) {
        this(transactionData.getUrl(),
                transactionData.getHttpMethod(),
                transactionData.getStatusCode(),
                transactionData.getErrorCode(),
                transactionData.getTimestamp(),
                transactionData.getTime(),
                transactionData.getBytesSent(),
                transactionData.getBytesReceived(),
                transactionData.getAppData());

        this.responseBody = transactionData.getResponseBody();
        this.params = transactionData.getParams();
        this.traceContext = transactionData.getTraceContext();
        this.traceAttributes = transactionData.getTraceAttributes();
    }

    @Override
    public double asDouble() {
        return this.totalTime;
    }

    public String getUrl() {
        return url;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public double getTotalTime() {
        return totalTime;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public long getBytesSent() {
        return bytesSent;
    }

    public long getBytesReceived() {
        return bytesReceived;
    }

    public String getAppData() {
        return appData;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public Map<String, String> getParams() {
        return params;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public TraceContext getTraceContext() {
        return traceContext;
    }

    public Map<String, Object> getTraceAttributes() {
        return traceAttributes;
    }

    @Override
    public String toString() {
        return "HttpTransactionMeasurement{" +
                "url='" + url + '\'' +
                ", httpMethod='" + httpMethod + '\'' +
                ", totalTime=" + totalTime +
                ", statusCode=" + statusCode +
                ", errorCode=" + errorCode +
                ", bytesSent=" + bytesSent +
                ", bytesReceived=" + bytesReceived +
                ", appData='" + appData + '\'' +
                ", responseBody='" + responseBody + '\'' +
                ", params='" + params + '\'' +
                '}';
    }


}
