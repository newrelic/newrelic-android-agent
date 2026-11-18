/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.util

import android.content.Context
import android.content.res.Resources
import android.content.res.Resources.NotFoundException
import com.newrelic.agent.android.ApplicationFramework
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ApplicationFrameworkResourceTest {

    private val packageName = "com.example.hybrid"
    private val resourceId = 0x7f0c000a
    private lateinit var resources: Resources
    private lateinit var context: Context

    @Before
    fun setUp() {
        resources = mockk(relaxed = true)
        context = mockk(relaxed = true)
        every { context.packageName } returns packageName
        every { context.resources } returns resources
        every { resources.getIdentifier("com_newrelic_application_framework", "string", packageName) } returns 0
    }

    private fun declare(value: String) {
        every { resources.getIdentifier("com_newrelic_application_framework", "string", packageName) } returns resourceId
        every { resources.getString(resourceId) } returns value
    }

    @Test
    fun resourceNameMatchesWhatHybridAgentsShip() {
        assertEquals("com_newrelic_application_framework", ApplicationFrameworkResource.NAME)
    }

    @Test
    fun appWithoutTheResourceHasNoFramework() {
        assertNull(ApplicationFrameworkResource.read(context))
        verify(exactly = 0) { resources.getString(any()) }
    }

    @Test
    fun everyHybridFrameworkNameIsRead() {
        listOf("Flutter", "ReactNative", "Cordova", "Capacitor", "Unity", "MAUI", "Xamarin", "Unreal").forEach {
            declare(it)
            assertEquals(ApplicationFramework.valueOf(it), ApplicationFrameworkResource.read(context))
        }
    }

    @Test
    fun surroundingWhitespaceAndCaseAreIgnored() {
        declare("  flutter\n")
        assertEquals(ApplicationFramework.Flutter, ApplicationFrameworkResource.read(context))
    }

    @Test
    fun aResourceSayingNativeIsNotADetection() {
        declare("Native")
        assertNull(ApplicationFrameworkResource.read(context))
    }

    @Test
    fun anUnknownNameIsIgnored() {
        declare("SomethingElse")
        assertNull(ApplicationFrameworkResource.read(context))
    }

    @Test
    fun anEmptyValueIsIgnored() {
        declare("")
        assertNull(ApplicationFrameworkResource.read(context))
    }

    @Test
    fun aResourceThatCannotBeReadIsIgnored() {
        declare("Flutter")
        every { resources.getString(resourceId) } throws NotFoundException("gone")
        assertNull(ApplicationFrameworkResource.read(context))
    }

    // --- the framework version ---

    private val versionId = 0x7f0c000b

    private fun declareVersion(value: String) {
        every { resources.getIdentifier("com_newrelic_application_framework_version", "string", packageName) } returns versionId
        every { resources.getString(versionId) } returns value
    }

    @Test
    fun versionResourceNameMatchesWhatHybridAgentsShip() {
        assertEquals("com_newrelic_application_framework_version", ApplicationFrameworkResource.VERSION_NAME)
    }

    @Test
    fun appWithoutTheVersionResourceHasNoVersion() {
        every { resources.getIdentifier("com_newrelic_application_framework_version", "string", packageName) } returns 0
        assertNull(ApplicationFrameworkResource.readVersion(context))
    }

    @Test
    fun theDeclaredVersionIsRead() {
        declareVersion("1.9.4")
        assertEquals("1.9.4", ApplicationFrameworkResource.readVersion(context))
    }

    @Test
    fun surroundingWhitespaceInTheVersionIsIgnored() {
        declareVersion("  1.9.4\n")
        assertEquals("1.9.4", ApplicationFrameworkResource.readVersion(context))
    }

    @Test
    fun aBlankVersionIsIgnored() {
        declareVersion("   ")
        assertNull(ApplicationFrameworkResource.readVersion(context))
    }

    @Test
    fun aVersionThatCannotBeReadIsIgnored() {
        declareVersion("1.9.4")
        every { resources.getString(versionId) } throws NotFoundException("gone")
        assertNull(ApplicationFrameworkResource.readVersion(context))
    }
}
