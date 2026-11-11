/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import com.newrelic.agent.android.stats.StatsEngine
import com.newrelic.agent.android.util.ApplicationFrameworkResource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.spy
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidAgentImplPlatformDetectionTest {

    private lateinit var spyContext: SpyContext
    private lateinit var agentConfiguration: AgentConfiguration

    @Before
    fun setUp() {
        Agent.setImpl(null)
        StatsEngine.reset()
        spyContext = SpyContext()
        agentConfiguration = AgentConfiguration().apply {
            applicationToken = "ab20dfe5-96d2-4c6d-b975-3fe9d8778dfc"
        }
    }

    @After
    fun tearDown() {
        Agent.stop()
        Agent.setImpl(null)
        StatsEngine.reset()
    }

    private fun metricNames(): Set<String> = StatsEngine.SUPPORTABILITY.statsMap.keys

    /** Gives the app the framework string resource that a hybrid SDK ships. */
    private fun declareFrameworkResource(value: String, version: String? = null) {
        val context = spyContext.context
        val resourceId = 0x7f0c00aa
        val versionId = 0x7f0c00ab
        // read these before stubbing: calling the spy context inside the stubbing chain is rejected
        val packageName = context.packageName
        val resources = spy(context.resources)
        doReturn(resourceId).`when`(resources).getIdentifier(ApplicationFrameworkResource.NAME, "string", packageName)
        doReturn(value).`when`(resources).getString(resourceId)
        if (version != null) {
            doReturn(versionId).`when`(resources).getIdentifier(ApplicationFrameworkResource.VERSION_NAME, "string", packageName)
            doReturn(version).`when`(resources).getString(versionId)
        }
        doReturn(resources).`when`(context).resources
    }

    @Test
    fun theFrameworkResourceIsUsedWhenNothingWasSet() {
        declareFrameworkResource("ReactNative")

        AndroidAgentImpl.init(spyContext.context, agentConfiguration)

        val impl = Agent.getImpl() as AndroidAgentImpl
        assertEquals(ApplicationFramework.ReactNative, impl.deviceInformation.applicationFramework)
    }

    @Test
    fun detectedFrameworkIsInDeviceInformationFromTheFirstRead() {
        declareFrameworkResource("Flutter")

        AndroidAgentImpl.init(spyContext.context, agentConfiguration)

        val impl = Agent.getImpl() as AndroidAgentImpl
        assertEquals(ApplicationFramework.Flutter, impl.deviceInformation.applicationFramework)
        assertEquals(ApplicationFramework.Flutter, agentConfiguration.applicationFramework)
        assertFalse(agentConfiguration.isApplicationFrameworkExplicit)
        // the framework is a literal path segment, so it is not rewritten by the agent still in use at this point
        assertTrue(metricNames().contains("Supportability/Mobile/Android/HybridPlatform/Detected/Flutter"))
    }

    @Test
    fun theDeclaredVersionIsInDeviceInformationFromTheFirstRead() {
        declareFrameworkResource("Flutter", "1.3.5")

        AndroidAgentImpl.init(spyContext.context, agentConfiguration)

        val impl = Agent.getImpl() as AndroidAgentImpl
        assertEquals(ApplicationFramework.Flutter, impl.deviceInformation.applicationFramework)
        assertEquals("1.3.5", impl.deviceInformation.applicationFrameworkVersion)
    }

    @Test
    fun withoutADeclaredVersionTheAgentVersionIsKept() {
        declareFrameworkResource("Flutter")

        AndroidAgentImpl.init(spyContext.context, agentConfiguration)

        val impl = Agent.getImpl() as AndroidAgentImpl
        assertEquals(Agent.getVersion(), impl.deviceInformation.applicationFrameworkVersion)
    }

    @Test
    fun explicitFrameworkIsNotOverriddenByTheResource() {
        declareFrameworkResource("Flutter")
        agentConfiguration.applicationFramework = ApplicationFramework.ReactNative

        AndroidAgentImpl.init(spyContext.context, agentConfiguration)

        val impl = Agent.getImpl() as AndroidAgentImpl
        assertEquals(ApplicationFramework.ReactNative, impl.deviceInformation.applicationFramework)
        // an app that names its own framework is not one of the hybrid apps being counted
        assertFalse(metricNames().any { it.contains("HybridPlatform/Detected/") })
    }

    @Test
    fun anAppWithoutTheResourceStaysNative() {
        AndroidAgentImpl.init(spyContext.context, agentConfiguration)

        val impl = Agent.getImpl() as AndroidAgentImpl
        assertEquals(ApplicationFramework.Native, impl.deviceInformation.applicationFramework)
        assertFalse(metricNames().any { it.contains("HybridPlatform/Detected/") })
    }
}
