/*
 * Copyright (c) 2021-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.ndk

import com.newrelic.agent.android.AgentConfiguration
import com.newrelic.agent.android.FeatureFlag
import com.newrelic.agent.android.NewRelic
import com.newrelic.agent.android.SpyContext
import com.newrelic.agent.android.logging.AgentLog
import com.newrelic.agent.android.logging.AgentLogManager
import com.newrelic.agent.android.logging.ConsoleAgentLog
import com.newrelic.agent.android.metric.MetricNames
import com.newrelic.agent.android.stats.StatsEngine
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Kotlin companion to NativeReportingTest for tests that exercise LinkageError handling
 * in NativeReporting.stop(). AgentNDK.stop() is a final Kotlin method that calls the
 * native nativeStop(); in the test environment the native library is unavailable, so
 * UnsatisfiedLinkError (a LinkageError) is thrown naturally — no mocking required.
 */
@RunWith(RobolectricTestRunner::class)
class NativeReportingKotlinTest {

    private lateinit var nativeReporter: NativeReporting

    @Before
    fun setup() {
        AgentLogManager.setAgentLog(ConsoleAgentLog())
        AgentLogManager.getAgentLog().setLevel(AgentLog.DEBUG)
        NewRelic.enableFeature(FeatureFlag.NativeReporting)
        val context = SpyContext()
        StatsEngine.reset()
        nativeReporter = NativeReporting.initialize(context.context, AgentConfiguration.getInstance())
        NativeReporting.instance.set(nativeReporter)
    }

    @After
    fun tearDown() {
        NativeReporting.agentNdk.set(null)
        NativeReporting.instance.set(null)
    }

    @Test
    fun stopSurvivesNdkAbiMismatch() {
        // agentNdk.get().stop() calls the native nativeStop(), which throws
        // UnsatisfiedLinkError (a LinkageError) because the native library is
        // unavailable in the test environment. NativeReporting.stop() must catch
        // the LinkageError and not propagate it — otherwise backgrounding the app
        // crashes the process when the agent-ndk version at runtime differs from
        // the one compiled against.
        nativeReporter.stop()

        Assert.assertTrue(StatsEngine.SUPPORTABILITY.statsMap.containsKey(MetricNames.SUPPORTABILITY_NDK_STOP))
        Assert.assertNull(NativeReporting.agentNdk.get())
    }
}
