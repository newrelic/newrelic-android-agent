/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.mobileview;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.navigation.fragment.NavHostFragment;

import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Automatic MobileView tracking for Fragments. Emits MobileView events on
 * {@code onFragmentResumed}/{@code onFragmentPaused}, gated by {@link FeatureFlag#AutomaticMobileViewTracing}.
 * One instance is registered per host Activity instance (see {@link MobileViewActivityLifecycleCallbacks}).
 */
public class MobileViewFragmentLifecycleCallbacks extends FragmentManager.FragmentLifecycleCallbacks {
    private static final AgentLog log = AgentLogManager.getAgentLog();

    // Class names of fragments currently being torn down and recreated because their host
    // Activity is changing configurations (e.g. rotation) - mirrors the suppression in
    // MobileViewActivityLifecycleCallbacks. Static (not an instance field) because a NEW
    // MobileViewFragmentLifecycleCallbacks instance is registered on each recreated Activity
    // instance's FragmentManager - the entry added in onFragmentPaused (on the dying activity's
    // instance) must still be visible to onFragmentResumed on the recreated activity's instance.
    private static final Set<String> changingConfigurations = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Activity hostActivity;

    public MobileViewFragmentLifecycleCallbacks(@NonNull Activity hostActivity) {
        this.hostActivity = hostActivity;
    }

    @Override
    public void onFragmentResumed(@NonNull FragmentManager fm, @NonNull Fragment fragment) {
        if (fragment instanceof NavHostFragment) {
            return;
        }
        // Bookkeeping runs regardless of flag state - see the matching comment in
        // MobileViewActivityLifecycleCallbacks.onActivityResumed for why.
        Class<?> clazz = fragment.getClass();
        if (changingConfigurations.remove(clazz.getName())) {
            return;
        }
        if (!FeatureFlag.featureEnabled(FeatureFlag.AutomaticMobileViewTracing)) {
            return;
        }
        try {
            MobileViewContext.getInstance().onViewAppeared(clazz.getSimpleName(), clazz.getName(), null);
        } catch (Exception e) {
            log.error("MobileViewFragmentLifecycleCallbacks.onFragmentResumed: ", e);
        }
    }

    @Override
    public void onFragmentPaused(@NonNull FragmentManager fm, @NonNull Fragment fragment) {
        if (fragment instanceof NavHostFragment) {
            return;
        }
        if (hostActivity.isChangingConfigurations()) {
            changingConfigurations.add(fragment.getClass().getName());
            return;
        }
        if (!FeatureFlag.featureEnabled(FeatureFlag.AutomaticMobileViewTracing)) {
            return;
        }
        try {
            MobileViewContext.getInstance().onViewDisappeared(fragment.getClass().getSimpleName());
        } catch (Exception e) {
            log.error("MobileViewFragmentLifecycleCallbacks.onFragmentPaused: ", e);
        }
    }
}
