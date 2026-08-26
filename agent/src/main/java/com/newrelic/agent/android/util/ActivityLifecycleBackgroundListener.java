/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.util;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import com.newrelic.agent.android.background.ApplicationStateMonitor;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;

import java.util.concurrent.atomic.AtomicBoolean;

public class ActivityLifecycleBackgroundListener extends UiBackgroundListener implements Application.ActivityLifecycleCallbacks {

    private static final AgentLog log = AgentLogManager.getAgentLog();
    private AtomicBoolean isInBackground = new AtomicBoolean(false);
    private final boolean isHybridFramework;

    public ActivityLifecycleBackgroundListener() {
        this(false);
    }

    public ActivityLifecycleBackgroundListener(boolean isHybridFramework) {
        this.isHybridFramework = isHybridFramework;
    }

    @Override
    public void onActivityResumed(Activity activity) {
        log.info("ActivityLifecycleBackgroundListener.onActivityResumed");
        if (isInBackground.getAndSet(false)) {
            Runnable runner = new Runnable() {
                @Override
                public void run() {
                    ApplicationStateMonitor.getInstance().activityStarted();
                }
            };
            executor.submit(runner);
        }
    }

    @Override
    public void onTrimMemory(int level) {
        log.info("ActivityLifecycleBackgroundListener.onTrimMemory level: " + level);
        // NR-262548: MAUI/Xamarin can fire TRIM_MEMORY_UI_HIDDEN while still in
        // the foreground, which would falsely background the agent and shut down
        // AnalyticsController. For these hosts, rely on Activity lifecycle only.
        if (isHybridFramework) {
            return;
        }
        if (TRIM_MEMORY_UI_HIDDEN == level)
            isInBackground.set(true);
        super.onTrimMemory(level);
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        log.info("ActivityLifecycleBackgroundListener.onActivityCreated");
        isInBackground.set(false);
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        log.info("ActivityLifecycleBackgroundListener.onActivityDestroyed");
        isInBackground.set(false);
    }

    // Necessary to detect Power button presses
    @Override
    public void onActivityStarted(Activity activity) {
        if (isInBackground.compareAndSet(true, false)) {
            Runnable runner = new Runnable() {
                @Override
                public void run() {
                    log.debug("ActivityLifecycleBackgroundListener.onActivityStarted - notifying ApplicationStateMonitor");
                    ApplicationStateMonitor.getInstance().activityStarted();
                }
            };
            executor.submit(runner);
        }
    }

    @Override
    public void onActivityPaused(Activity activity) {
        if (isChangingConfig(activity)) {
            // Configuration change (rotation, locale, etc.) - the activity will be recreated.
            // Do not signal backgrounding; isInBackground stays false so the recreated
            // activity's onResume/onStart won't spuriously fire applicationForegrounded either.
            return;
        }
        if (isInBackground.compareAndSet(false, true)) {
            Runnable runner = new Runnable() {
                @Override
                public void run() {
                    log.debug("ActivityLifecycleBackgroundListener.onActivityPaused - notifying ApplicationStateMonitor");
                    ApplicationStateMonitor.getInstance().uiHidden();
                }
            };
            executor.submit(runner);
        }
    }

    @Override
    public void onActivityStopped(Activity activity) {
        final boolean isConfigChange = isChangingConfig(activity);
        if (isInBackground.getAndSet(true)) {
            Runnable runner = new Runnable() {
                @Override
                public void run() {
                    log.debug("ActivityLifecycleBackgroundListener.onActivityStopped - notifying ApplicationStateMonitor (isConfigChange=" + isConfigChange + ")");
                    ApplicationStateMonitor.getInstance().activityStopped(isConfigChange);
                }
            };
            executor.submit(runner);
        }
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
    }

    private static boolean isChangingConfig(Activity activity) {
        return activity != null && activity.isChangingConfigurations();
    }

}
