/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.mobileview;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Automatic MobileView tracking for Activities. Emits MobileView events on
 * {@code onResume}/{@code onPause}, gated by {@link FeatureFlag#AutomaticMobileViewTracing}.
 */
public class MobileViewActivityLifecycleCallbacks implements Application.ActivityLifecycleCallbacks {
    private static final AgentLog log = AgentLogManager.getAgentLog();

    // Class names of activities currently being torn down and recreated for a config change
    // (e.g. rotation). Keyed by class name, not a single flag, so that multiple activities
    // rotating concurrently (multi-window/split-screen) don't desync each other's suppression.
    private final Set<String> changingConfigurations = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        if (!FeatureFlag.featureEnabled(FeatureFlag.AutomaticMobileViewTracing)) {
            return;
        }
        Class<?> clazz = activity.getClass();
        if (changingConfigurations.remove(clazz.getName())) {
            return;
        }
        try {
            MobileViewContext.getInstance().onViewAppeared(clazz.getSimpleName(), clazz.getName(), null);
        } catch (Exception e) {
            log.error("MobileViewActivityLifecycleCallbacks.onActivityResumed: ", e);
        }
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
        if (!FeatureFlag.featureEnabled(FeatureFlag.AutomaticMobileViewTracing)) {
            return;
        }
        if (activity.isChangingConfigurations()) {
            changingConfigurations.add(activity.getClass().getName());
            return;
        }
        try {
            MobileViewContext.getInstance().onViewDisappeared(activity.getClass().getSimpleName());
        } catch (Exception e) {
            log.error("MobileViewActivityLifecycleCallbacks.onActivityPaused: ", e);
        }
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle bundle) {
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle bundle) {
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
    }
}
