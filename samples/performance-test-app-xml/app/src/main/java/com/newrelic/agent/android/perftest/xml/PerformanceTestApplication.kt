package com.newrelic.agent.android.perftest.xml

import android.app.Application
import android.util.Log
import com.newrelic.agent.android.AgentConfiguration
import com.newrelic.agent.android.ApplicationFramework
import com.newrelic.agent.android.FeatureFlag
import com.newrelic.agent.android.NewRelic
import com.newrelic.agent.android.logging.AgentLog

class PerformanceTestApplication : Application() {

    companion object {
        private const val TAG = "PerfTestApp"
    }

    override fun onCreate() {
        super.onCreate()

        Log.d(TAG, "Application onCreate - Agent Enabled: ${BuildConfig.AGENT_ENABLED}")
        Log.d(TAG, "Session Replay Enabled: ${BuildConfig.SESSION_REPLAY_ENABLED}")

        if (BuildConfig.AGENT_ENABLED && BuildConfig.NEWRELIC_TOKEN.isNotEmpty()) {
            initializeNewRelic()
        } else {
            Log.d(TAG, "New Relic Agent disabled for this build variant")
        }
    }

    private fun initializeNewRelic() {
        Log.d(TAG, "Initializing New Relic Agent with token: ${BuildConfig.NEWRELIC_TOKEN.take(10)}...")

        NewRelic.enableFeature(FeatureFlag.NativeReporting)
        NewRelic.enableFeature(FeatureFlag.LogReporting)
        NewRelic.enableFeature(FeatureFlag.OfflineStorage)
        NewRelic.enableFeature(FeatureFlag.ApplicationExitReporting)
        NewRelic.enableFeature(FeatureFlag.BackgroundReporting)

        NewRelic.withApplicationToken(BuildConfig.NEWRELIC_TOKEN)
            .usingCollectorAddress("staging-mobile-collector.newrelic.com")
            .usingCrashCollectorAddress("staging-mobile-crash.newrelic.com")
            .withLogLevel(AgentLog.DEBUG)
            .withLaunchActivityName("PerformanceTestApplication")
            .withApplicationFramework(ApplicationFramework.Native, "1.0")
            .start(this)

        Log.d(TAG, "New Relic Agent initialized successfully")
    }
}
