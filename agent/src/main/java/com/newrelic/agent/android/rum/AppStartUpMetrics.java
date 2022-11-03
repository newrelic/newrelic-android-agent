/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.rum;

public class AppStartUpMetrics {
    private Long contentProviderToAppStart = 0L;
    private Long applicationOnCreateTime = 0L;
    private Long appOnCreateEndToFirstActivityCreate = 0L;
    private Long firstActivityCreateToResume = 0L;
    private Long coldStartTime = 0L;
    private Long hotStartTime = 0L;
    private Long warmStartTime = 0L;

    public AppStartUpMetrics() {
        AppTracer tracer = AppTracer.getInstance();
        this.contentProviderToAppStart = tracer.getAppOnCreateTime() - tracer.getContentProviderStartedTime();
        this.applicationOnCreateTime = tracer.getAppOnCreateEndTime() - tracer.getAppOnCreateTime();
        this.appOnCreateEndToFirstActivityCreate = tracer.getFirstActivityCreatedTime() - tracer.getAppOnCreateEndTime();
        this.firstActivityCreateToResume = tracer.getFirstActivityResumeTime() - tracer.getFirstActivityCreatedTime();
        this.coldStartTime = tracer.getFirstActivityResumeTime() - tracer.getContentProviderStartedTime();
        this.hotStartTime = tracer.getFirstActivityResumeTime() - tracer.getFirstActivityStartTime();
        this.warmStartTime = tracer.getFirstActivityResumeTime() - tracer.getContentProviderStartedTime();
    }

    @Override
    public String toString() {
        return "NewRelicAppStartUpMetrics{" +
                "contentProviderToAppStart=" + contentProviderToAppStart / 1000.0 +
                ", applicationOnCreateTime=" + applicationOnCreateTime / 1000.0 +
                ", appOnCreateEndToFirstActivityCreate=" + appOnCreateEndToFirstActivityCreate / 1000.0 +
                ", firstActivityCreateToResume=" + firstActivityCreateToResume / 1000.0 +
                ", coldStartTime=" + coldStartTime / 1000.0 +
                ", hotStartTime=" + hotStartTime / 1000.0 +
                ", warmStartTime=" + warmStartTime / 1000.0 +
                '}';
    }

    public Long getContentProviderToAppStart() {
        return contentProviderToAppStart;
    }

    public void setContentProviderToAppStart(Long contentProviderToAppStart) {
        this.contentProviderToAppStart = contentProviderToAppStart;
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

    public Long getWarmStartTime() {
        return warmStartTime;
    }

    public void setWarmStartTime(Long warmStartTime) {
        this.warmStartTime = warmStartTime;
    }
}
