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
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Kotlin companion to NativeReportingTest for tests that require mocking final
 * AgentNDK methods. mockito-inline conflicts with Robolectric's class loader;
 * MockK handles final Kotlin methods correctly in this environment.
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
        // AgentNDK.stop() is a final Kotlin method; use MockK to stub it.
        // Reproduces the runtime crash where the agent was compiled against an
        // AgentNDK.stop() with a different return-type descriptor than the agent-ndk
        // class loaded at runtime (void <-> boolean across releases). The JVM raises a
        // NoSuchMethodError (a LinkageError) at the call site. stop() must not propagate
        // it, otherwise backgrounding the app crashes the process.
        val mismatched = mockk<AgentNDK>(relaxed = true)
        every { mismatched.stop() } throws NoSuchMethodError("No virtual method stop()V in class AgentNDK")
        NativeReporting.agentNdk.set(mismatched)

        nativeReporter.stop()

        Assert.assertTrue(StatsEngine.SUPPORTABILITY.statsMap.containsKey(MetricNames.SUPPORTABILITY_NDK_STOP))
        Assert.assertNull(NativeReporting.agentNdk.get())
    }
}
