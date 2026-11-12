/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.util

import com.newrelic.agent.android.AgentConfiguration
import com.newrelic.agent.android.ApplicationFramework
import com.newrelic.agent.android.stats.StatsEngine
import org.junit.After
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
        StatsEngine.reset()
    }

    @After
    fun tearDown() {
        StatsEngine.reset()
    }

    /** The detection supportability metrics recorded so far. */
    private fun detectionMetrics(): List<String> =
        StatsEngine.SUPPORTABILITY.statsMap.keys.filter { it.contains("HybridPlatform/Detected/") }.sorted()

    /** Resolves for an app whose framework resource says [declared] (null: the app has no such resource). */
    private fun resolveWith(declared: ApplicationFramework?) =
        ApplicationFrameworkResolver.resolve(configuration) { declared }

    @Test
    fun detectedFrameworkFillsTheGapWhenNothingWasSet() {
        val versionBefore = configuration.applicationFrameworkVersion

        resolveWith(ApplicationFramework.Flutter)

        assertEquals(ApplicationFramework.Flutter, configuration.applicationFramework)
        assertEquals(versionBefore, configuration.applicationFrameworkVersion)
        assertFalse(configuration.isApplicationFrameworkExplicit)
    }

    @Test
    fun explicitFrameworkWinsOverDetection() {
        configuration.applicationFramework = ApplicationFramework.ReactNative

        resolveWith(ApplicationFramework.Flutter)

        assertEquals(ApplicationFramework.ReactNative, configuration.applicationFramework)
        assertTrue(configuration.isApplicationFrameworkExplicit)
    }

    @Test
    fun explicitNativeAlsoWinsOverDetection() {
        configuration.applicationFramework = ApplicationFramework.Native

        resolveWith(ApplicationFramework.Flutter)

        assertEquals(ApplicationFramework.Native, configuration.applicationFramework)
    }

    @Test
    fun nothingDetectedLeavesTheDefaultsAlone() {
        resolveWith(null)

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

    // --- supportability metrics ---

    private val detectedFlutter = "Supportability/Mobile/Android/HybridPlatform/Detected/Flutter"

    @Test
    fun aDetectedFrameworkThatIsUsedIsCounted() {
        ApplicationFrameworkResolver.resolve(configuration) { ApplicationFramework.Flutter }

        assertEquals(listOf(detectedFlutter), detectionMetrics())
        assertEquals(1, StatsEngine.SUPPORTABILITY.statsMap[detectedFlutter]!!.count)
    }

    @Test
    fun nothingDetectedCountsNothing() {
        ApplicationFrameworkResolver.resolve(configuration) { null }

        assertEquals(emptyList<String>(), detectionMetrics())
    }

    @Test
    fun anAppThatSetItsFrameworkExplicitlyIsNeverCounted() {
        configuration.applicationFramework = ApplicationFramework.Flutter

        ApplicationFrameworkResolver.resolve(configuration) { ApplicationFramework.Flutter }
        ApplicationFrameworkResolver.resolve(configuration) { ApplicationFramework.Capacitor }

        assertEquals(emptyList<String>(), detectionMetrics())
        assertEquals(ApplicationFramework.Flutter, configuration.applicationFramework)
    }

    @Test
    fun aFailingDetectorCountsNothingAndLeavesAnExplicitFrameworkAlone() {
        configuration.applicationFramework = ApplicationFramework.Cordova

        ApplicationFrameworkResolver.resolve(configuration) { throw IllegalStateException("boom") }

        assertEquals(emptyList<String>(), detectionMetrics())
        assertEquals(ApplicationFramework.Cordova, configuration.applicationFramework)
    }

    @Test
    fun aFailingDetectorOnANonExplicitAppCountsNothing() {
        ApplicationFrameworkResolver.resolve(configuration) { throw IllegalStateException("boom") }

        assertEquals(emptyList<String>(), detectionMetrics())
        assertEquals(ApplicationFramework.Native, configuration.applicationFramework)
    }

    // --- the framework version ---

    @Test
    fun aDeclaredVersionIsUsedWithTheFramework() {
        ApplicationFrameworkResolver.resolve(configuration, { ApplicationFramework.Flutter }) { "1.3.5" }

        assertEquals(ApplicationFramework.Flutter, configuration.applicationFramework)
        assertEquals("1.3.5", configuration.applicationFrameworkVersion)
        assertFalse(configuration.isApplicationFrameworkExplicit)
    }

    @Test
    fun noDeclaredVersionKeepsTheDefault() {
        val before = configuration.applicationFrameworkVersion

        ApplicationFrameworkResolver.resolve(configuration, { ApplicationFramework.Flutter }) { null }

        assertEquals(before, configuration.applicationFrameworkVersion)
    }

    @Test
    fun aVersionWithoutAFrameworkIsIgnored() {
        val before = configuration.applicationFrameworkVersion

        ApplicationFrameworkResolver.resolve(configuration, { null }) { "1.3.5" }

        assertEquals(before, configuration.applicationFrameworkVersion)
    }

    @Test
    fun theVersionIsNotReadWhenTheFrameworkIsExplicit() {
        configuration.applicationFramework = ApplicationFramework.Cordova
        configuration.applicationFrameworkVersion = "7.1.14"
        var consulted = false

        ApplicationFrameworkResolver.resolve(configuration, { ApplicationFramework.Flutter }) {
            consulted = true
            "1.3.5"
        }

        assertFalse(consulted)
        assertEquals("7.1.14", configuration.applicationFrameworkVersion)
    }

    @Test
    fun aFailingVersionReadKeepsTheDetectedFramework() {
        val before = configuration.applicationFrameworkVersion

        ApplicationFrameworkResolver.resolve(configuration, { ApplicationFramework.Flutter }) {
            throw IllegalStateException("boom")
        }

        assertEquals(ApplicationFramework.Flutter, configuration.applicationFramework)
        assertEquals(before, configuration.applicationFrameworkVersion)
        assertEquals(listOf(detectedFlutter), detectionMetrics())
    }
}
