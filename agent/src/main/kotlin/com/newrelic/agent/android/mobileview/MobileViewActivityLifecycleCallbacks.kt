/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.mobileview

import android.app.Activity
import android.app.Application
import android.os.Bundle

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.navigation.fragment.NavHostFragment

import com.newrelic.agent.android.FeatureFlag
import com.newrelic.agent.android.logging.AgentLog
import com.newrelic.agent.android.logging.AgentLogManager

import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

/**
 * Automatic MobileView tracking for Activities. Emits MobileView events on
 * `onResume`/`onPause`, gated by [FeatureFlag.AutomaticMobileViewTracing].
 */
class MobileViewActivityLifecycleCallbacks : Application.ActivityLifecycleCallbacks {

    // Identities of activities currently being torn down and recreated for a config change
    // (e.g. rotation), keyed by "taskId:className" rather than class name alone. A config change
    // destroys and recreates the Activity as a brand-new instance, so there's no Activity
    // identity (no stable hashCode/instance reference) that survives from the old instance to
    // the new one - task ID is the one thing that does, since the recreated Activity lands back
    // in the same task. Two DIFFERENT concurrent instances of the same Activity class (e.g.
    // multi-window/split-screen, or the same screen opened in two separate tasks) run in
    // different tasks, so keying on taskId too (not class name alone) stops them from
    // cross-contaminating each other's suppression flag.
    private val changingConfigurations: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())

    // Whether a given Activity instance was treated as a NavHostFragment container at the time
    // it appeared. Computed once in onActivityResumed and reused in onActivityPaused so the
    // appear/disappear calls stay symmetric even if the activity adds/removes a NavHostFragment
    // at runtime between those two calls - otherwise an activity tracked as "appeared" could be
    // skipped on "disappeared" (or vice versa), permanently corrupting MobileViewContext's state.
    private val navHostContainerAtAppear = WeakHashMap<Activity, Boolean>()

    // Wall-clock time of onActivityCreated, per Activity instance, consumed (removed) by the
    // first onActivityResumed that follows. Only that first resume represents actual load time;
    // a later resume (e.g. backgrounding then foregrounding the same instance) has nothing to
    // measure, so it correctly gets no loadTime once the entry has been consumed.
    private val createdAtMs = WeakHashMap<Activity, Long>()

    override fun onActivityResumed(activity: Activity) {
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
        val clazz = activity.javaClass
        val isContainer = isNavHostContainer(activity)
        navHostContainerAtAppear[activity] = isContainer
        val createdAt = createdAtMs.remove(activity)
        if (changingConfigurations.remove(configChangeKey(activity))) {
            return
        }
        if (isContainer) {
            // Content is driven by a NavHostFragment: the fragment producer will emit its own
            // MobileView event for the current destination, so tracking the host Activity too
            // would just add a redundant container-level event to the referrer chain.
            return
        }
        if (!FeatureFlag.featureEnabled(FeatureFlag.AutomaticMobileViewTracing)) {
            return
        }
        try {
            val loadTime = createdAt?.let { System.currentTimeMillis() - it }
            MobileViewContext.getInstance().onViewAppeared(
                MobileViewAppearance(clazz.simpleName, UiPlatform.ANDROID)
                    .viewClass(clazz.name)
                    .loadTimeMs(loadTime)
            )
        } catch (e: Exception) {
            log.error("MobileViewActivityLifecycleCallbacks.onActivityResumed: ", e)
        }
    }

    override fun onActivityPaused(activity: Activity) {
        if (activity.isChangingConfigurations) {
            changingConfigurations.add(configChangeKey(activity))
            return
        }
        val wasContainer = navHostContainerAtAppear.remove(activity)
        if (wasContainer == true) {
            return
        }
        if (!FeatureFlag.featureEnabled(FeatureFlag.AutomaticMobileViewTracing)) {
            return
        }
        try {
            MobileViewContext.getInstance().onViewDisappeared(activity.javaClass.simpleName)
        } catch (e: Exception) {
            log.error("MobileViewActivityLifecycleCallbacks.onActivityPaused: ", e)
        }
    }

    override fun onActivityCreated(activity: Activity, bundle: Bundle?) {
        createdAtMs[activity] = System.currentTimeMillis()
        if (!FeatureFlag.featureEnabled(FeatureFlag.AutomaticMobileViewTracing)) {
            return
        }
        if (activity is FragmentActivity) {
            try {
                activity.supportFragmentManager
                    .registerFragmentLifecycleCallbacks(MobileViewFragmentLifecycleCallbacks(activity), true)
            } catch (e: Exception) {
                log.error("MobileViewActivityLifecycleCallbacks.onActivityCreated: ", e)
            }
        }
    }

    override fun onActivityStarted(activity: Activity) {
    }

    override fun onActivityStopped(activity: Activity) {
    }

    override fun onActivitySaveInstanceState(activity: Activity, bundle: Bundle) {
    }

    override fun onActivityDestroyed(activity: Activity) {
    }

    companion object {
        private val log: AgentLog = AgentLogManager.getAgentLog()

        private fun configChangeKey(activity: Activity): String {
            return activity.taskId.toString() + ":" + activity.javaClass.name
        }

        /**
         * @return true if the activity's primary content is a [NavHostFragment] or a Compose
         * `NavHost` (i.e. it's a pure navigation container whose destinations are tracked by
         * the Fragment or Compose producer instead).
         */
        private fun isNavHostContainer(activity: Activity): Boolean {
            try {
                if (ComposeNavHostRegistry.getInstance().isRegistered(activity)) {
                    return true
                }
                if (activity !is FragmentActivity) {
                    return false
                }
                // Recurse into child fragment managers, not just the activity's top-level fragments: the
                // Fragment producer below registers its callbacks recursively (registerFragmentLifecycleCallbacks(...,
                // true)), so it fires for a NavHostFragment's destinations no matter how deeply that
                // NavHostFragment is nested under other fragments. Only scanning top-level fragments here
                // would miss a nested NavHostFragment, leaving this check returning false while the nested
                // NavHostFragment's destinations still fire their own MobileView events - producing a
                // redundant Activity-level event alongside them.
                //
                // This walk (and the registry check above) runs on every Activity resume, including
                // when the feature flag is disabled, so it must never let an exception escape: a
                // FragmentManager mutated concurrently by an in-flight transaction (e.g.
                // getChildFragmentManager() on a fragment not yet attached) would otherwise crash the
                // host app from inside Application.ActivityLifecycleCallbacks.
                return containsNavHostFragment(activity.supportFragmentManager)
            } catch (e: Exception) {
                log.error("MobileViewActivityLifecycleCallbacks.isNavHostContainer: ", e)
                return false
            }
        }

        private fun containsNavHostFragment(fragmentManager: FragmentManager): Boolean {
            for (fragment: Fragment in fragmentManager.fragments) {
                if (fragment is NavHostFragment) {
                    return true
                }
                if (containsNavHostFragment(fragment.childFragmentManager)) {
                    return true
                }
            }
            return false
        }
    }
}
