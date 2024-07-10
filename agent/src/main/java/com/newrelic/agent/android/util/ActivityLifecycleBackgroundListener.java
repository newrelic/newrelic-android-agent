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

@TargetApi(14)
public class ActivityLifecycleBackgroundListener extends UiBackgroundListener implements Application.ActivityLifecycleCallbacks {

    private static final AgentLog log = AgentLogManager.getAgentLog();
    private AtomicBoolean isInBackground = new AtomicBoolean(false);

    @Override
    public void onActivityResumed(Activity activity) {
        log.info("ActivityLifecycleBackgroundListener.onActivityResumed");
        log.info("ActivityLifecycleBackgroundListener.onActivityResumed isInBackground: " + isInBackground.get());

        if(!isInBackground.get()) {
            log.info("ActivityLifecycleBackgroundListener.onActivityResumed isInBackground: " + !isInBackground.get());
            Runnable runner = new Runnable() {
                @Override
                public void run() {
                    log.info("ActivityLifecycleBackgroundListener.onActivityResumed - notifying ApplicationStateMonitor");
                    ApplicationStateMonitor.getInstance().activityStarted();
                }
            };
            executor.submit(runner);
        } else {

            if (isInBackground.getAndSet(false)) {
                Runnable runner = new Runnable() {
                    @Override
                    public void run() {
                        log.info("ActivityLifecycleBackgroundListener.onActivityResumed - notifying ApplicationStateMonitor");
                        ApplicationStateMonitor.getInstance().activityStarted();
                    }
                };
                executor.submit(runner);
            }
        }
    }

    @Override
    public void onTrimMemory(int level) {
        log.info("ActivityLifecycleBackgroundListener.onTrimMemory level: " + level);
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
        log.info("ActivityLifecycleBackgroundListener.onActivityStarted");
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
        if (isInBackground.getAndSet(true)) {
            Runnable runner = new Runnable() {
                @Override
                public void run() {
                    log.debug("ActivityLifecycleBackgroundListener.onActivityStopped - notifying ApplicationStateMonitor");
                    ApplicationStateMonitor.getInstance().activityStopped();
                }
            };
            executor.submit(runner);
        }
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
    }

}
