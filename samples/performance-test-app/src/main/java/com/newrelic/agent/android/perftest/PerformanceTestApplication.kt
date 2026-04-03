/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.perftest

import android.app.Application
import android.util.Log
import com.newrelic.agent.android.FeatureFlag
import com.newrelic.agent.android.NewRelic
import com.newrelic.agent.android.logging.AgentLog
import com.newrelic.agent.android.perftest.metrics.PerformanceMetricsCollector

class PerformanceTestApplication : Application() {

    companion object {
        private const val TAG = "PerfTestApp"
        private var appStartTime: Long = 0
    }

    override fun onCreate() {
        super.onCreate()

        // Record app start time for metrics
        appStartTime = System.currentTimeMillis()
        Log.d(TAG, "Application starting...")
        Log.d(TAG, "Agent enabled: ${BuildConfig.AGENT_ENABLED}")
        Log.d(TAG, "Session Replay enabled: ${BuildConfig.SESSION_REPLAY_ENABLED}")

        // Conditionally initialize New Relic based on build variant
        if (BuildConfig.AGENT_ENABLED) {
            initializeNewRelic()
        } else {
            Log.d(TAG, "New Relic Agent disabled for this build variant (noAgent)")
        }

        // Initialize performance metrics collection
        initializeMetricsCollection()
    }

    private fun initializeNewRelic() {
        Log.d(TAG, "Initializing New Relic Agent with token: ${BuildConfig.NEWRELIC_TOKEN.take(10)}...")

        // Enable New Relic features
        NewRelic.enableFeature(FeatureFlag.NativeReporting)
        NewRelic.enableFeature(FeatureFlag.LogReporting)
        NewRelic.enableFeature(FeatureFlag.OfflineStorage)
        NewRelic.enableFeature(FeatureFlag.ApplicationExitReporting)
        NewRelic.enableFeature(FeatureFlag.BackgroundReporting)

        // Session Replay is controlled by the application token on the server side
        // Token ending in ...c03514cb3e764ea = No Session Replay
        // Token ending in ...830940e0de85081 = With Session Replay
        Log.d(TAG, "Session Replay variant: ${if (BuildConfig.SESSION_REPLAY_ENABLED) "ENABLED" else "DISABLED"}")

        // Initialize New Relic with variant-specific token
        NewRelic.withApplicationToken(BuildConfig.NEWRELIC_TOKEN)
            .usingCollectorAddress("staging-mobile-collector.newrelic.com")
            .usingCrashCollectorAddress("staging-mobile-crash.newrelic.com")
            .withLogLevel(AgentLog.DEBUG)
            .withLaunchActivityName("PerformanceTestApplication")
            .start(this)

        Log.d(TAG, "New Relic Agent initialized successfully")
    }

    private fun initializeMetricsCollection() {
        try {
            val metricsCollector = PerformanceMetricsCollector.getInstance(this)

            // Record startup time
            metricsCollector.recordStartupTime(appStartTime)

            // Start collecting metrics every 2 seconds
            metricsCollector.startCollecting()

            Log.d(TAG, "Performance metrics collection started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize metrics collection", e)
        }
    }
}
