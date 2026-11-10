/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.util

import com.newrelic.agent.android.AgentConfiguration
import com.newrelic.agent.android.ApplicationFramework
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApplicationFrameworkResolverTest {

    private lateinit var configuration: AgentConfiguration

    @Before
    fun setUp() {
        configuration = AgentConfiguration()
    }

    private val flutterActivity = "io.flutter.embedding.android.FlutterActivity"

    /** Resolves for an app whose activities are of exactly the [declared] types. */
    private fun resolveWith(vararg declared: String) =
        ApplicationFrameworkResolver.resolve(configuration) {
            PlatformDetector(FakeAppClasses(activityTypes = declared.toSet())).detect()
        }

    @Test
    fun detectedFrameworkFillsTheGapWhenNothingWasSet() {
        val versionBefore = configuration.applicationFrameworkVersion

        resolveWith(flutterActivity)

        assertEquals(ApplicationFramework.Flutter, configuration.applicationFramework)
        assertEquals(versionBefore, configuration.applicationFrameworkVersion)
        assertFalse(configuration.isApplicationFrameworkExplicit)
    }

    @Test
    fun explicitFrameworkWinsOverDetection() {
        configuration.applicationFramework = ApplicationFramework.ReactNative

        resolveWith(flutterActivity)

        assertEquals(ApplicationFramework.ReactNative, configuration.applicationFramework)
        assertTrue(configuration.isApplicationFrameworkExplicit)
    }

    @Test
    fun explicitNativeAlsoWinsOverDetection() {
        configuration.applicationFramework = ApplicationFramework.Native

        resolveWith(flutterActivity)

        assertEquals(ApplicationFramework.Native, configuration.applicationFramework)
    }

    @Test
    fun nothingDetectedLeavesTheDefaultsAlone() {
        resolveWith()

        assertEquals(ApplicationFramework.Native, configuration.applicationFramework)
        assertFalse(configuration.isApplicationFrameworkExplicit)
    }

    @Test
    fun aFailingDetectorLeavesTheDefaultsAlone() {
        ApplicationFrameworkResolver.resolve(configuration) { throw IllegalStateException("boom") }

        assertEquals(ApplicationFramework.Native, configuration.applicationFramework)
        assertFalse(configuration.isApplicationFrameworkExplicit)
    }

    @Test
    fun aFailingDetectorResetsAnEarlierDetectionToNative() {
        // e.g. the agent was stopped and started again in the same process
        configuration.applyDetectedApplicationFramework(ApplicationFramework.Flutter)

        ApplicationFrameworkResolver.resolve(configuration) { throw IllegalStateException("boom") }

        assertEquals(ApplicationFramework.Native, configuration.applicationFramework)
        assertFalse(configuration.isApplicationFrameworkExplicit)
    }

    @Test
    fun aFailingDetectorLinkageErrorAlsoFallsBackToNative() {
        configuration.applyDetectedApplicationFramework(ApplicationFramework.ReactNative)

        ApplicationFrameworkResolver.resolve(configuration) { throw NoClassDefFoundError("kotlin/Unit") }

        assertEquals(ApplicationFramework.Native, configuration.applicationFramework)
    }

    @Test
    fun aFailureNeverOverridesAnExplicitFramework() {
        configuration.applicationFramework = ApplicationFramework.Cordova

        // not consulted at all for an explicit framework, but a throwing detector must be harmless
        ApplicationFrameworkResolver.resolve(configuration) { throw IllegalStateException("boom") }

        assertEquals(ApplicationFramework.Cordova, configuration.applicationFramework)
        assertTrue(configuration.isApplicationFrameworkExplicit)
    }

    @Test
    fun detectorIsNotConsultedWhenTheFrameworkIsExplicit() {
        configuration.applicationFramework = ApplicationFramework.Cordova
        var consulted = false

        ApplicationFrameworkResolver.resolve(configuration) {
            consulted = true
            null
        }

        assertFalse(consulted)
    }
}
