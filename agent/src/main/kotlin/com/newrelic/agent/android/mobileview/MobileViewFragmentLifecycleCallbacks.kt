/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.mobileview

import android.app.Activity
import android.os.Bundle

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.navigation.fragment.NavHostFragment

import com.newrelic.agent.android.FeatureFlag
import com.newrelic.agent.android.logging.AgentLog
import com.newrelic.agent.android.logging.AgentLogManager

import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

import kotlin.jvm.JvmStatic

/**
 * Automatic MobileView tracking for Fragments. Emits MobileView events on
 * `onFragmentResumed`/`onFragmentPaused`, gated by [FeatureFlag.AutomaticMobileViewTracing].
 * One instance is registered per host Activity instance (see [MobileViewActivityLifecycleCallbacks]).
 */
class MobileViewFragmentLifecycleCallbacks(
    private val hostActivity: Activity
) : FragmentManager.FragmentLifecycleCallbacks() {

    // Wall-clock time of onFragmentCreated, per Fragment instance, consumed (removed) by the
    // first onFragmentResumed that follows - mirrors MobileViewActivityLifecycleCallbacks'
    // createdAtMs. A later resume without an intervening create (e.g. backgrounding) correctly
    // gets no loadTime once the entry has been consumed.
    private val createdAtMs = WeakHashMap<Fragment, Long>()

    private fun configChangeKey(fragment: Fragment): String {
        return hostActivity.taskId.toString() + ":" + fragment.javaClass.name
    }

    override fun onFragmentCreated(fm: FragmentManager, fragment: Fragment, savedInstanceState: Bundle?) {
        if (fragment is NavHostFragment) {
            return
        }
        createdAtMs[fragment] = System.currentTimeMillis()
    }

    override fun onFragmentResumed(fm: FragmentManager, fragment: Fragment) {
        if (fragment is NavHostFragment) {
            return
        }
        // Bookkeeping runs regardless of flag state - see the matching comment in
        // MobileViewActivityLifecycleCallbacks.onActivityResumed for why.
        val clazz = fragment.javaClass
        val createdAt = createdAtMs.remove(fragment)
        if (changingConfigurations.remove(configChangeKey(fragment))) {
            return
        }
        if (!FeatureFlag.featureEnabled(FeatureFlag.AutomaticMobileViewTracing)) {
            return
        }
        try {
            val loadTime = createdAt?.let { System.currentTimeMillis() - it }
            MobileViewContext.getInstance().onViewAppeared(
                MobileViewAppearance(clazz.simpleName, UiPlatform.ANDROID_FRAGMENT)
                    .viewClass(clazz.name)
                    .loadTimeMs(loadTime)
            )
        } catch (e: Exception) {
            log.error("MobileViewFragmentLifecycleCallbacks.onFragmentResumed: ", e)
        }
    }

    override fun onFragmentPaused(fm: FragmentManager, fragment: Fragment) {
        if (fragment is NavHostFragment) {
            return
        }
        if (hostActivity.isChangingConfigurations) {
            changingConfigurations.add(configChangeKey(fragment))
            return
        }
        if (!FeatureFlag.featureEnabled(FeatureFlag.AutomaticMobileViewTracing)) {
            return
        }
        try {
            MobileViewContext.getInstance().onViewDisappeared(fragment.javaClass.simpleName)
        } catch (e: Exception) {
            log.error("MobileViewFragmentLifecycleCallbacks.onFragmentPaused: ", e)
        }
    }

    companion object {
        private val log: AgentLog = AgentLogManager.getAgentLog()

        // Identities of fragments currently being torn down and recreated because their host
        // Activity is changing configurations (e.g. rotation) - mirrors the suppression in
        // MobileViewActivityLifecycleCallbacks, including keying by "taskId:className" rather than
        // class name alone (see configChangeKey() there for why). Static (not an instance field)
        // because a NEW MobileViewFragmentLifecycleCallbacks instance is registered on each
        // recreated Activity instance's FragmentManager - the entry added in onFragmentPaused (on
        // the dying activity's instance) must still be visible to onFragmentResumed on the recreated
        // activity's instance. Keying by the host Activity's taskId (stable across that
        // recreation) rather than class name alone stops two concurrent instances of the same
        // Fragment class under different host Activity instances (e.g. the same screen open in two
        // multi-window tasks) from cross-contaminating each other's suppression flag.
        private val changingConfigurations: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())

        /**
         * Clears the static config-change suppression state. Test use only - production code
         * relies on this state surviving across the old/new Activity instance boundary during
         * a real rotation, so it must never be called outside of test teardown.
         */
        @JvmStatic
        fun clearChangingConfigurationsForTest() {
            changingConfigurations.clear()
        }
    }
}
