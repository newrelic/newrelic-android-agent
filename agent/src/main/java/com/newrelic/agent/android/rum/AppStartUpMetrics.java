/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.rum;

public class AppStartUpMetrics {
    private Long applicationOnCreateTime = 0L;
    private Long appOnCreateEndToFirstActivityCreate = 0L;
    private Long firstActivityCreateToResume = 0L;
    private Long coldStartTime = 0L;
    private Long hotStartTime = 0L;

    public AppStartUpMetrics() {
        AppTracer tracer = AppTracer.getInstance();
        long appOnCreateEnd = tracer.getAppOnCreateEndTime();
        long firstActivityStart = tracer.getFirstActivityStartTime();

        // Guard against appOnCreateEndTime never being set (Handler.post Runnable
        // didn't run before AppStartUpMetrics was constructed). Without the guard,
        // these would be -uptime / +uptime instead of zero.
        this.applicationOnCreateTime = appOnCreateEnd > 0
                ? appOnCreateEnd - tracer.getAppOnCreateTime()
                : 0L;
        this.appOnCreateEndToFirstActivityCreate = appOnCreateEnd > 0
                ? tracer.getFirstActivityCreatedTime() - appOnCreateEnd
                : 0L;
        this.firstActivityCreateToResume =
                tracer.getFirstActivityResumeTime() - tracer.getFirstActivityCreatedTime();
        this.coldStartTime =
                tracer.getFirstActivityResumeTime() - tracer.getContentProviderStartedTime();
        this.hotStartTime = firstActivityStart > 0
                ? tracer.getFirstActivityResumeTime() - firstActivityStart
                : 0L;
    }

    @Override
    public String toString() {
        return "NewRelicAppStartUpMetrics{" +
                "applicationOnCreateTime=" + applicationOnCreateTime / 1000.0 +
                ", appOnCreateEndToFirstActivityCreate=" + appOnCreateEndToFirstActivityCreate / 1000.0 +
                ", firstActivityCreateToResume=" + firstActivityCreateToResume / 1000.0 +
                ", coldStartTime=" + coldStartTime / 1000.0 +
                ", hotStartTime=" + hotStartTime / 1000.0 +
                '}';
    }

    public Long getApplicationOnCreateTime() {
        return applicationOnCreateTime;
    }

    public void setApplicationOnCreateTime(Long applicationOnCreateTime) {
        this.applicationOnCreateTime = applicationOnCreateTime;
    }

    public Long getAppOnCreateEndToFirstActivityCreate() {
        return appOnCreateEndToFirstActivityCreate;
    }

    public void setAppOnCreateEndToFirstActivityCreate(Long appOnCreateEndToFirstActivityCreate) {
        this.appOnCreateEndToFirstActivityCreate = appOnCreateEndToFirstActivityCreate;
    }

    public Long getFirstActivityCreateToResume() {
        return firstActivityCreateToResume;
    }

    public void setFirstActivityCreateToResume(Long firstActivityCreateToResume) {
        this.firstActivityCreateToResume = firstActivityCreateToResume;
    }

    public Long getColdStartTime() {
        return coldStartTime;
    }

    public void setColdStartTime(Long coldStartTime) {
        this.coldStartTime = coldStartTime;
    }

    public Long getHotStartTime() {
        return hotStartTime;
    }

    public void setHotStartTime(Long hotStartTime) {
        this.hotStartTime = hotStartTime;
    }
}