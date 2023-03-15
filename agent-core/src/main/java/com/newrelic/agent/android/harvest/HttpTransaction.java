/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.harvest;

import com.google.gson.JsonArray;
import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.distributedtracing.TraceContext;
import com.newrelic.agent.android.harvest.type.HarvestableArray;
import com.newrelic.agent.android.util.SafeJsonPrimitive;

import java.util.Map;

/**
 * A {@link HarvestableArray} representation of an HTTP transaction.
 */
public class HttpTransaction extends HarvestableArray {
    private String url;
    private String httpMethod;
    private String carrier;
    private String wanType;
    private double totalTime;
    private int statusCode;
    private int errorCode;
    private long bytesSent;
    private long bytesReceived;
    private String appData;
    private TraceContext traceContext;
    private Map<String, Object> traceAttributes;

    // New field for Activity Traces. Keep null for legacy.
    private Long timestamp;

    // optional members
    private String responseBody;
    private Map<String, String> params;

    @Override
    public JsonArray asJsonArray() {
        JsonArray array = new JsonArray();
        array.add(SafeJsonPrimitive.factory(url));
        array.add(SafeJsonPrimitive.factory(carrier));
        array.add(SafeJsonPrimitive.factory(totalTime));
        array.add(SafeJsonPrimitive.factory(statusCode));
        array.add(SafeJsonPrimitive.factory(errorCode));
        array.add(SafeJsonPrimitive.factory(bytesSent));
        array.add(SafeJsonPrimitive.factory(bytesReceived));
        array.add(appData == null ? null : SafeJsonPrimitive.factory(appData));
        array.add(SafeJsonPrimitive.factory(wanType));
        array.add(SafeJsonPrimitive.factory(httpMethod));
        return array;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public void setCarrier(String carrier) {
        this.carrier = carrier;
    }

    public void setWanType(String wanType) {
        this.wanType = wanType;
    }

    public void setTotalTime(double totalTime) {
        this.totalTime = totalTime;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public void setErrorCode(int errorCode) {
        this.errorCode = errorCode;
    }

    public void setBytesSent(long bytesSent) {
        this.bytesSent = bytesSent;
    }

    public void setBytesReceived(long bytesReceived) {
        this.bytesReceived = bytesReceived;
    }

    public void setAppData(String appData) {
        this.appData = appData;
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

    public Map<String, Object> getTraceAttributes() {
        return traceAttributes;
    }

    public void setTraceAttributes(Map<String, Object> traceAttributes) {
        this.traceAttributes = traceAttributes;
    }

    public void setTraceContext(TraceContext traceContext) {
        this.traceContext = traceContext;
    }

    public TraceContext getTraceContext() {
        return traceContext;
    }

    public void setParams(Map<String, String> params) {
        this.params = params;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
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

    public String getWanType() {
        return wanType;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public Map<String, String> getParams() {
        return params;
    }

    @Override
    public String toString() {
        return "HttpTransaction{" +
                "url='" + url + '\'' +
                ", carrier='" + carrier + '\'' +
                ", wanType='" + wanType + '\'' +
                ", httpMethod='" + httpMethod + '\'' +
                ", totalTime=" + totalTime +
                ", statusCode=" + statusCode +
                ", errorCode=" + errorCode +
                ", bytesSent=" + bytesSent +
                ", bytesReceived=" + bytesReceived +
                ", appData='" + appData + '\'' +
                ", responseBody='" + responseBody + '\'' +
                ", params='" + params + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }

}
