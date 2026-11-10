/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import android.app.Application
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.facebook.react.ReactApplication
import com.newrelic.agent.android.stats.StatsEngine
import io.flutter.embedding.android.FlutterActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.doReturn
import org.robolectric.RobolectricTestRunner

/** An app's own launcher activity, extending the framework's activity. */
class ExampleFlutterAppActivity : FlutterActivity()

/** An app whose own activities are plain, but whose Application implements ReactApplication. */
class ExampleReactApp : Application(), ReactApplication

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

    private fun declareLauncherActivity(className: String) {
        // read these before stubbing: calling the spy context inside the stubbing chain is rejected
        val packageManager = spyContext.context.packageManager
        val packageName = spyContext.context.packageName
        val info = PackageInfo().apply {
            activities = arrayOf(ActivityInfo().apply { name = className })
        }
        @Suppress("DEPRECATION")
        doReturn(info).`when`(packageManager).getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
    }

    private fun declareApplicationClass(className: String) {
        val context = spyContext.context
        val info = ApplicationInfo(context.applicationInfo).apply { this.className = className }
        doReturn(info).`when`(context).applicationInfo
    }

    @Test
    fun reactApplicationIsDetectedWithoutAReactActivity() {
        declareApplicationClass(ExampleReactApp::class.java.name)

        AndroidAgentImpl.init(spyContext.context, agentConfiguration)

        val impl = Agent.getImpl() as AndroidAgentImpl
        assertEquals(ApplicationFramework.ReactNative, impl.deviceInformation.applicationFramework)
    }

    @Test
    fun detectedFrameworkIsInDeviceInformationFromTheFirstRead() {
        declareLauncherActivity(ExampleFlutterAppActivity::class.java.name)

        AndroidAgentImpl.init(spyContext.context, agentConfiguration)

        val impl = Agent.getImpl() as AndroidAgentImpl
        assertEquals(ApplicationFramework.Flutter, impl.deviceInformation.applicationFramework)
        assertEquals(ApplicationFramework.Flutter, agentConfiguration.applicationFramework)
        assertFalse(agentConfiguration.isApplicationFrameworkExplicit)
        // the framework is a literal path segment, so it is not rewritten by the agent still in use at this point
        assertTrue(metricNames().contains("Supportability/Mobile/Android/HybridPlatform/Detected/Flutter"))
    }

    @Test
    fun explicitFrameworkIsNotOverriddenByDetection() {
        declareLauncherActivity(ExampleFlutterAppActivity::class.java.name)
        agentConfiguration.applicationFramework = ApplicationFramework.ReactNative

        AndroidAgentImpl.init(spyContext.context, agentConfiguration)

        val impl = Agent.getImpl() as AndroidAgentImpl
        assertEquals(ApplicationFramework.ReactNative, impl.deviceInformation.applicationFramework)
        // an app that names its own framework is not one of the hybrid apps being counted
        assertFalse(metricNames().any { it.contains("HybridPlatform/Detected/") })
    }

    @Test
    fun anAppWithNoFrameworkActivityStaysNative() {
        AndroidAgentImpl.init(spyContext.context, agentConfiguration)

        val impl = Agent.getImpl() as AndroidAgentImpl
        assertEquals(ApplicationFramework.Native, impl.deviceInformation.applicationFramework)
        assertFalse(metricNames().any { it.contains("HybridPlatform/Detected/") })
    }
}
