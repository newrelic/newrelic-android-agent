/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.mobileview;

import java.util.WeakHashMap;

/**
 * Tracks which Activity instances host a Compose {@code NavHost} wired up via
 * {@code withNewRelicNavigationListener()}. Written by the Compose producer at
 * {@code DisposableEffect}-attach time (synchronous within {@code onCreate}, and therefore
 * always before {@code Application.ActivityLifecycleCallbacks#onActivityResumed} fires for that
 * Activity), and read by {@link MobileViewActivityLifecycleCallbacks} to suppress the redundant
 * container-level Activity event for Activities whose content is Compose Navigation.
 */
public class ComposeNavHostRegistry {
    private static final ComposeNavHostRegistry instance = new ComposeNavHostRegistry();

    private final Object lock = new Object();
    private final WeakHashMap<Object, Boolean> hostingActivities = new WeakHashMap<>();

    private ComposeNavHostRegistry() {
    }

    public static ComposeNavHostRegistry getInstance() {
        return instance;
    }

    public void register(Object activity) {
        if (activity == null) {
            return;
        }
        synchronized (lock) {
            hostingActivities.put(activity, Boolean.TRUE);
        }
    }

    public void unregister(Object activity) {
        if (activity == null) {
            return;
        }
        synchronized (lock) {
            hostingActivities.remove(activity);
        }
    }

    public boolean isRegistered(Object activity) {
        if (activity == null) {
            return false;
        }
        synchronized (lock) {
            return hostingActivities.containsKey(activity);
        }
    }
}
