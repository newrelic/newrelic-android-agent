/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.util;

import android.annotation.TargetApi;
import android.content.ComponentCallbacks2;
import android.content.res.Configuration;

import com.newrelic.agent.android.background.ApplicationStateMonitor;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@TargetApi(14)
public class UiBackgroundListener implements ComponentCallbacks2 {

    protected final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("UiBackgroundListener"));

    @Override
    public void onConfigurationChanged(final Configuration newConfig) {
    }

    @Override
    public void onLowMemory() {
    }

    // (called when application is backgrounded for API >= 14 (Ice Cream Sandwich))
    @Override
    public void onTrimMemory(final int level) {
        switch (level) {
            case TRIM_MEMORY_UI_HIDDEN:
                Runnable runner = new Runnable() {
                    @Override
                    public void run() {
                        ApplicationStateMonitor.getInstance().uiHidden();
                    }
                };
                executor.submit(runner);
                break;
            default:
                break;
        }
    }
}
