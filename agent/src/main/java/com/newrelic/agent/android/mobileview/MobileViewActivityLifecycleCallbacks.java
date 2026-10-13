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
import androidx.fragment.app.FragmentActivity;
import androidx.navigation.fragment.NavHostFragment;

import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
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

    // Whether a given Activity instance was treated as a NavHostFragment container at the time
    // it appeared. Computed once in onActivityResumed and reused in onActivityPaused so the
    // appear/disappear calls stay symmetric even if the activity adds/removes a NavHostFragment
    // at runtime between those two calls - otherwise an activity tracked as "appeared" could be
    // skipped on "disappeared" (or vice versa), permanently corrupting MobileViewContext's state.
    private final Map<Activity, Boolean> navHostContainerAtAppear = new WeakHashMap<>();

    // Wall-clock time of onActivityCreated, per Activity instance, consumed (removed) by the
    // first onActivityResumed that follows. Only that first resume represents actual load time;
    // a later resume (e.g. backgrounding then foregrounding the same instance) has nothing to
    // measure, so it correctly gets no loadTime once the entry has been consumed.
    private final Map<Activity, Long> createdAtMs = new WeakHashMap<>();

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        // Bookkeeping (config-change suppression, container-check caching) always runs, even if
        // the flag is currently disabled - otherwise toggling the flag off/on between an
        // activity's resume and pause could desync it from onActivityPaused's own bookkeeping,
        // leaking MobileViewContext.currentView. Only the actual event emission is flag-gated.
        //
        // The container-check cache is populated BEFORE the config-change suppression check
        // below, not after, because a config-change recreates the Activity as a brand-new
        // instance: if this resume is suppressed (config-change) we still return early, but the
        // new instance must already have its own navHostContainerAtAppear entry so a later real
        // pause (post-rotation) can look it up - otherwise that lookup misses, falls through, and
        // risks emitting a disappear event for an activity that was never tracked as appeared.
        Class<?> clazz = activity.getClass();
        boolean isContainer = isNavHostContainer(activity);
        navHostContainerAtAppear.put(activity, isContainer);
        Long createdAt = createdAtMs.remove(activity);
        if (changingConfigurations.remove(clazz.getName())) {
            return;
        }
        if (isContainer) {
            // Content is driven by a NavHostFragment: the fragment producer will emit its own
            // MobileView event for the current destination, so tracking the host Activity too
            // would just add a redundant container-level event to the referrer chain.
            return;
        }
        if (!FeatureFlag.featureEnabled(FeatureFlag.AutomaticMobileViewTracing)) {
            return;
        }
        try {
            Long loadTime = createdAt != null ? (System.currentTimeMillis() - createdAt) : null;
            MobileViewContext.getInstance().onViewAppeared(
                    new MobileViewAppearance(clazz.getSimpleName(), UiPlatform.ANDROID)
                            .viewClass(clazz.getName())
                            .loadTimeMs(loadTime));
        } catch (Exception e) {
            log.error("MobileViewActivityLifecycleCallbacks.onActivityResumed: ", e);
        }
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
        if (activity.isChangingConfigurations()) {
            changingConfigurations.add(activity.getClass().getName());
            return;
        }
        Boolean wasContainer = navHostContainerAtAppear.remove(activity);
        if (Boolean.TRUE.equals(wasContainer)) {
            return;
        }
        if (!FeatureFlag.featureEnabled(FeatureFlag.AutomaticMobileViewTracing)) {
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
        createdAtMs.put(activity, System.currentTimeMillis());
        if (!FeatureFlag.featureEnabled(FeatureFlag.AutomaticMobileViewTracing)) {
            return;
        }
        if (activity instanceof FragmentActivity) {
            try {
                ((FragmentActivity) activity).getSupportFragmentManager()
                        .registerFragmentLifecycleCallbacks(new MobileViewFragmentLifecycleCallbacks(activity), true);
            } catch (Exception e) {
                log.error("MobileViewActivityLifecycleCallbacks.onActivityCreated: ", e);
            }
        }
    }

    /**
     * @return true if the activity's primary content is a {@link NavHostFragment} or a Compose
     * {@code NavHost} (i.e. it's a pure navigation container whose destinations are tracked by
     * the Fragment or Compose producer instead).
     */
    private static boolean isNavHostContainer(@NonNull Activity activity) {
        try {
            if (ComposeNavHostRegistry.getInstance().isRegistered(activity)) {
                return true;
            }
        } catch (Exception e) {
            log.error("MobileViewActivityLifecycleCallbacks.isNavHostContainer: ", e);
        }
        if (!(activity instanceof FragmentActivity)) {
            return false;
        }
        for (androidx.fragment.app.Fragment fragment : ((FragmentActivity) activity).getSupportFragmentManager().getFragments()) {
            if (fragment instanceof NavHostFragment) {
                return true;
            }
        }
        return false;
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
