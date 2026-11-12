/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.ndk

import com.newrelic.agent.android.AgentConfiguration
import com.newrelic.agent.android.ApplicationFramework
import com.newrelic.agent.android.agentdata.AgentDataController
import com.newrelic.agent.android.agentdata.AgentDataReporter
import com.newrelic.agent.android.analytics.AnalyticsAttribute
import com.newrelic.agent.android.analytics.AnalyticsControllerImpl
import com.newrelic.agent.android.crash.Crash
import com.newrelic.agent.android.crash.CrashReporter
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * NDK crashes, exceptions and ANRs report the app's configured framework as their platform, spelled
 * like the session's platform attribute (the ApplicationFramework name, "Native" for a native app).
 */
@RunWith(RobolectricTestRunner::class)
class NativeReportingPlatformTest {

    private val listener = NativeReporting.NativeReportListener()
    private val configuration: AgentConfiguration = AgentConfiguration.getInstance()
    private lateinit var frameworkBefore: ApplicationFramework

    private val reportedAttributes = slot<MutableMap<String, Any>>()
    private val reportedCrash = slot<Crash>()

    @Before
    fun setUp() {
        frameworkBefore = configuration.applicationFramework

        mockkStatic(AgentDataController::class)
        mockkStatic(AgentDataReporter::class)
        mockkStatic(CrashReporter::class)
        every { AgentDataReporter.getInstance() } returns mockk(relaxed = true)
        every { AgentDataController.sendAgentData(any(), capture(reportedAttributes)) } returns true
        every { CrashReporter.getInstance() } returns mockk<CrashReporter>(relaxed = true).also {
            every { it.storeAndReportCrash(capture(reportedCrash), any()) } returns Unit
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
        // restore without marking the shared configuration as explicitly set
        configuration.applyDetectedApplicationFramework(frameworkBefore)
    }

    private fun sample(name: String): String =
        NativeReporting::class.java.getResourceAsStream("/nativeReporting/$name")!!.bufferedReader().use { it.readText() }

    private fun configure(framework: ApplicationFramework) = configuration.applyDetectedApplicationFramework(framework)

    private fun crashPlatform(): String? =
        reportedCrash.captured.sessionAttributes
            .firstOrNull { it.name == AnalyticsAttribute.APPLICATION_PLATFORM_ATTRIBUTE }
            ?.stringValue

    // --- the value ---

    @Test
    fun everyFrameworkReportsItsName() {
        ApplicationFramework.values().forEach {
            configure(it)
            assertEquals(it.name, NativeReporting.platformAttributeValue())
        }
    }

    @Test
    fun aNativeAppReportsTheNativeEnumValue() {
        configure(ApplicationFramework.Native)
        assertEquals(ApplicationFramework.Native.toString(), NativeReporting.platformAttributeValue())
    }

    // --- native exceptions ---

    @Test
    fun aNativeExceptionFromANativeAppReportsTheNativeEnumValue() {
        configure(ApplicationFramework.Native)

        listener.onNativeException(sample("exception-2024.sample"))

        assertEquals("Native", reportedAttributes.captured[AnalyticsAttribute.APPLICATION_PLATFORM_ATTRIBUTE])
    }

    @Test
    fun aNativeExceptionFromAHybridAppReportsTheFramework() {
        configure(ApplicationFramework.ReactNative)

        listener.onNativeException(sample("exception-2024.sample"))

        assertEquals("ReactNative", reportedAttributes.captured[AnalyticsAttribute.APPLICATION_PLATFORM_ATTRIBUTE])
    }

    // --- ANRs ---

    @Test
    fun anAnrFromANativeAppReportsTheNativeEnumValue() {
        configure(ApplicationFramework.Native)

        listener.onApplicationNotResponding(sample("anr-2024.sample"))

        assertEquals("Native", reportedAttributes.captured[AnalyticsAttribute.APPLICATION_PLATFORM_ATTRIBUTE])
    }

    @Test
    fun anAnrFromAHybridAppReportsTheFramework() {
        configure(ApplicationFramework.Flutter)

        listener.onApplicationNotResponding(sample("anr-2024.sample"))

        assertEquals("Flutter", reportedAttributes.captured[AnalyticsAttribute.APPLICATION_PLATFORM_ATTRIBUTE])
    }

    // --- native crashes ---

    @Test
    fun aNativeCrashFromANativeAppReportsTheNativeEnumValue() {
        configure(ApplicationFramework.Native)

        listener.onNativeCrash(sample("crash-2024.sample"))

        assertEquals("Native", crashPlatform())
    }

    @Test
    fun aNativeCrashFromAHybridAppReportsTheFramework() {
        configure(ApplicationFramework.Cordova)

        listener.onNativeCrash(sample("crash-2024.sample"))

        assertEquals("Cordova", crashPlatform())
    }

    @Test
    fun aPlatformTheCrashedSessionAlreadyRecordedWinsOverTheConfiguredFramework() {
        // the configured framework is only a fallback: a crash flushed on a later launch keeps the platform
        // of the session that crashed (AnalyticsAttribute equality is by name, and a Set keeps the first)
        configure(ApplicationFramework.Cordova)
        mockkStatic(AnalyticsControllerImpl::class)
        val controller = mockk<AnalyticsControllerImpl>(relaxed = true)
        every { AnalyticsControllerImpl.getInstance() } returns controller
        every { controller.sessionAttributes } returns
            mutableSetOf(AnalyticsAttribute(AnalyticsAttribute.APPLICATION_PLATFORM_ATTRIBUTE, "Flutter"))

        listener.onNativeCrash(sample("crash-2024.sample"))

        assertEquals("Flutter", crashPlatform())
    }
}
