/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.rum;

import android.app.Application;
import android.content.Intent;
import android.os.SystemClock;

import java.util.concurrent.atomic.AtomicReference;

public final class AppTracer {

    private static final AtomicReference<AppTracer> instance = new AtomicReference<>(new AppTracer());

    private static Long contentProviderStartedTime = 0L;
    private static Long appOnCreateTime = 0L;
    private static Long appOnCreateEndTime = 0L;
    private static Long firstDrawTime = 0L;
    private static Long firstActivityCreatedTime = 0L;
    private static Long firstActivityStartTime = 0L;
    private static Long firstActivityResumeTime = 0L;
    private static Long lastAppPauseTime = 0L;

    private static String firstActivityName = null;
    private static String firstActivityReferrer = null;
    private static Intent firstActivityIntent = null;

    private static Boolean isColdStart = null;
    private static Boolean currentAppLaunchProcessed = true;
    private static Boolean isFirstPostExecuted = false;

    public static AppTracer getInstance() {
        return instance.get();
    }

    public void start() {
        appOnCreateTime = SystemClock.uptimeMillis();
    }

    public void onAppLaunchListener(Application context) {
        appOnCreateEndTime = SystemClock.uptimeMillis();
    }

    public Long getContentProviderStartedTime() {
        return contentProviderStartedTime;
    }

    public void setContentProviderStartedTime(Long contentProviderStartedTime) {
        this.contentProviderStartedTime = contentProviderStartedTime;
    }

    public Long getAppOnCreateTime() {
        return appOnCreateTime;
    }

    public void setAppOnCreateTime(Long appOnCreateTime) {
        this.appOnCreateTime = appOnCreateTime;
    }

    public Long getAppOnCreateEndTime() {
        return appOnCreateEndTime;
    }

    public void setAppOnCreateEndTime(Long appOnCreateEndTime) {
        this.appOnCreateEndTime = appOnCreateEndTime;
    }

    public Long getFirstDrawTime() {
        return firstDrawTime;
    }

    public void setFirstDrawTime(Long firstDrawTime) {
        this.firstDrawTime = firstDrawTime;
    }

    public Long getFirstActivityCreatedTime() {
        return firstActivityCreatedTime;
    }

    public void setFirstActivityCreatedTime(Long firstActivityCreatedTime) {
        this.firstActivityCreatedTime = firstActivityCreatedTime;
    }

    public String getFirstActivityName() {
        return firstActivityName;
    }

    public void setFirstActivityName(String firstActivityName) {
        this.firstActivityName = firstActivityName;
    }

    public Long getFirstActivityStartTime() {
        return firstActivityStartTime;
    }

    public void setFirstActivityStartTime(Long firstActivityStartTime) {
        this.firstActivityStartTime = firstActivityStartTime;
    }

    public Long getFirstActivityResumeTime() {
        return firstActivityResumeTime;
    }

    public void setFirstActivityResumeTime(Long firstActivityResumeTime) {
        this.firstActivityResumeTime = firstActivityResumeTime;
    }

    public Long getLastAppPauseTime() {
        return lastAppPauseTime;
    }

    public void setLastAppPauseTime(Long lastAppPauseTime) {
        this.lastAppPauseTime = lastAppPauseTime;
    }

    public boolean isColdStart() {
        return isColdStart;
    }

    public void setIsColdStart(boolean isColdStart) {
        this.isColdStart = isColdStart;
    }

    public Boolean getCurrentAppLaunchProcessed() {
        return currentAppLaunchProcessed;
    }

    public void setCurrentAppLaunchProcessed(Boolean currentAppLaunchProcessed) {
        this.currentAppLaunchProcessed = currentAppLaunchProcessed;
    }

    public Boolean getFirstPostExecuted() {
        return isFirstPostExecuted;
    }

    public void setFirstPostExecuted(Boolean firstPostExecuted) {
        isFirstPostExecuted = firstPostExecuted;
    }

    public String getFirstActivityReferrer() {
        return firstActivityReferrer;
    }

    public void setFirstActivityReferrer(String firstActivityReferrer) {
        this.firstActivityReferrer = firstActivityReferrer;
    }

    public Intent getFirstActivityIntent() {
        return firstActivityIntent;
    }

    public void setFirstActivityIntent(Intent firstActivityIntent) {
        this.firstActivityIntent = firstActivityIntent;
    }
}
