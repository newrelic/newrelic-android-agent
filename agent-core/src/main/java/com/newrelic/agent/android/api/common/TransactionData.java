/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.api.common;

import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.distributedtracing.TraceContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TransactionData {
    private final Object errorCodeLock = new Object();

    private final long timestamp;
    private final String url;
    private final String httpMethod;
    private final String carrier;
    private final float time;
    private final int statusCode;
    private final long bytesSent;
    private final long bytesReceived;
    private final String appData;
    private final String wanType;
    private final TraceContext traceContext;
    private Map<String, Object> traceAttributes;


    // optionals
    private int errorCode;
    private String responseBody;
    private Map<String, String> params;


    public TransactionData(final String url, final String httpMethod, final String carrier, final float time,
                           final int statusCode, final int errorCode, final long bytesSent, final long bytesReceived,
                           final String appData, final String wanType, final TraceContext traceContext, final Map<String, Object> traceAttributes) {
        int endPos = url.indexOf('?');
        if (endPos < 0) {
            endPos = url.indexOf(';');
            if (endPos < 0) {
                endPos = url.length();
            }
        }

        this.url = url.substring(0, endPos);
        this.httpMethod = httpMethod;
        this.carrier = carrier;
        this.time = time;
        this.statusCode = statusCode;
        this.errorCode = errorCode;
        this.bytesSent = bytesSent;
        this.bytesReceived = bytesReceived;
        this.appData = appData;
        this.wanType = wanType;
        this.timestamp = System.currentTimeMillis();
        this.responseBody = null;
        this.params = new HashMap<>();
        this.traceContext = traceContext;
        this.traceAttributes = traceAttributes;
    }

    public TransactionData(final String url, final String httpMethod, final String carrier, final float time,
                           final int statusCode, final int errorCode, final long bytesSent, final long bytesReceived,
                           final String appData, final String wanType, final TraceContext traceContext,
                           final String responseBody, final Map<String, String> params, final Map<String, Object> traceAttributes) {
        this(url, httpMethod, carrier, time, statusCode, errorCode, bytesSent, bytesReceived, appData, wanType, traceContext, traceAttributes);
        this.responseBody = responseBody;
        this.params = params;
    }

    public String getUrl() {
        return url;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public String getCarrier() {
        return carrier;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public int getErrorCode() {
        synchronized (errorCodeLock) {
            return errorCode;
        }
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

    public String getWanType() {
        return wanType;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public float getTime() {
        return time;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public Map<String, String> getParams() {
        return params;
    }

    public void setErrorCode(int errorCode) {
        synchronized (errorCodeLock) {
            this.errorCode = errorCode;
        }
    }

    public void setResponseBody(String responseBody) {
        if (FeatureFlag.featureEnabled(FeatureFlag.HttpResponseBodyCapture)) {
            if (responseBody == null || responseBody.isEmpty()) {
                this.responseBody = null;
            } else {
                this.responseBody = responseBody;
            }
        }
    }

    public void setParams(Map<String, String> params) {
        this.params = params;
    }

    public TraceContext getTraceContext() {
        return traceContext;
    }

    public Map<String, Object> getTraceAttributes() {
        return traceAttributes;
    }

    public void setTraceAttributes(Map<String, Object> traceAttributes) {
        this.traceAttributes = traceAttributes;
    }

    public List<Object> asList() {
        final ArrayList<Object> r = new ArrayList<Object>();
        r.add(url);
        r.add(carrier);
        r.add(time);
        r.add(statusCode);
        r.add(errorCode);
        r.add(bytesSent);
        r.add(bytesReceived);
        r.add(appData);
        return r;
    }

    @Override
    public String toString() {
        return "TransactionData{" +
                "timestamp=" + timestamp +
                ", url='" + url + '\'' +
                ", httpMethod='" + httpMethod + '\'' +
                ", carrier='" + carrier + '\'' +
                ", time=" + time +
                ", statusCode=" + statusCode +
                ", errorCode=" + errorCode +
                ", errorCodeLock=" + errorCodeLock +
                ", bytesSent=" + bytesSent +
                ", bytesReceived=" + bytesReceived +
                ", appData='" + appData + '\'' +
                ", wanType='" + wanType + '\'' +
                '}';
    }

}
