package com.newrelic.agent.android.testapp

import android.app.Application
import com.newrelic.agent.android.FeatureFlag
import com.newrelic.agent.android.NewRelic
import com.newrelic.agent.android.logging.AgentLog

class TestApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Add NewRelic
        NewRelic.enableFeature(FeatureFlag.NativeReporting)
        NewRelic.enableFeature(FeatureFlag.LogReporting)
        NewRelic.enableFeature(FeatureFlag.OfflineStorage)
        NewRelic.enableFeature(FeatureFlag.ApplicationExitReporting)
        NewRelic.enableFeature(FeatureFlag.BackgroundReporting)

        NewRelic.withApplicationToken("<APP-ID>")    // ## Provide application ID to test map uploads
            .withLogLevel(AgentLog.DEBUG)
            .withLaunchActivityName("agent-test-app")
            .start(this)
    }
}
