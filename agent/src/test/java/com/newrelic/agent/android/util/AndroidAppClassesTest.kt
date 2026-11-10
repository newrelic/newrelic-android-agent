/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.util

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** An app's own activity: its superclass chain ends in android.app.Activity. */
class ExampleManifestActivity : Activity()

/** Two levels deep, like an app activity extending a base class that extends a framework one. */
open class ExampleBaseActivity : Activity()
class ExampleDerivedActivity : ExampleBaseActivity()

/** Stands in for a framework interface such as ReactApplication. */
interface ExampleFrameworkApp
interface ExampleSubFrameworkApp : ExampleFrameworkApp

/** The interface directly on the declared class. */
class ExampleDirectApp : Application(), ExampleFrameworkApp

/** The interface two superclass hops above the declared class, like a license-check wrapper. */
open class ExampleMainApp : Application(), ExampleFrameworkApp
open class ExampleMiddleApp : ExampleMainApp()
class ExampleWrapperApp : ExampleMiddleApp()

/** The interface reached through another interface. */
class ExampleSubInterfaceApp : Application(), ExampleSubFrameworkApp

class ExamplePlainApp : Application()

@RunWith(RobolectricTestRunner::class)
class AndroidAppClassesTest {

    private val packageName = "com.example.hybrid"
    private lateinit var packageManager: PackageManager
    private lateinit var context: Context

    @Before
    fun setUp() {
        packageManager = mockk(relaxed = true)
        context = mockk(relaxed = true)
        every { context.packageName } returns packageName
        every { context.packageManager } returns packageManager
        every { context.classLoader } returns javaClass.classLoader
    }

    private fun components() = AndroidAppClasses(context)

    private fun declareActivities(vararg names: String) {
        val info = PackageInfo().apply {
            activities = names.map { ActivityInfo().apply { name = it } }.toTypedArray()
        }
        @Suppress("DEPRECATION")
        every { packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES) } returns info
    }

    private fun declareApplication(className: String?) {
        every { context.applicationInfo } returns ApplicationInfo().apply { this.className = className }
    }

    // --- activities ---

    @Test
    fun activityExtendingTheTypeIsFound() {
        declareActivities(ExampleManifestActivity::class.java.name)
        assertTrue(components().hasActivityOfType("android.app.Activity"))
    }

    @Test
    fun activityTwoLevelsBelowTheTypeIsFound() {
        declareActivities(ExampleDerivedActivity::class.java.name)
        assertTrue(components().hasActivityOfType(ExampleBaseActivity::class.java.name))
    }

    @Test
    fun activityThatIsTheTypeIsFoundWithoutLoadingIt() {
        declareActivities("com.example.NotOnTheClasspath")
        assertTrue(components().hasActivityOfType("com.example.NotOnTheClasspath"))
    }

    @Test
    fun activityNotOfTheTypeIsNotFound() {
        declareActivities(ExampleManifestActivity::class.java.name)
        assertFalse(components().hasActivityOfType("com.nope.Base"))
    }

    @Test
    fun unloadableActivityClassIsSkipped() {
        declareActivities("com.example.NotOnTheClasspath")
        assertFalse(components().hasActivityOfType("com.nope.Base"))
    }

    @Test
    fun appWithNoActivitiesFindsNothing() {
        @Suppress("DEPRECATION")
        every { packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES) } returns PackageInfo()
        assertFalse(components().hasActivityOfType("android.app.Activity"))
    }

    @Test
    fun unknownPackageFindsNothing() {
        @Suppress("DEPRECATION")
        every { packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES) } throws
            PackageManager.NameNotFoundException(packageName)
        assertFalse(components().hasActivityOfType("android.app.Activity"))
    }

    // --- loadable classes ---

    @Test
    fun loadableClassIsFound() {
        assertTrue(components().hasClass("java.lang.String"))
    }

    @Test
    fun missingClassIsNotFound() {
        assertFalse(components().hasClass("com.nope.Missing"))
    }

    // --- application class ---

    @Test
    fun applicationImplementingTheInterfaceDirectlyIsFound() {
        declareApplication(ExampleDirectApp::class.java.name)
        assertTrue(components().hasApplicationOfType(ExampleFrameworkApp::class.java.name))
    }

    @Test
    fun applicationWhoseSuperclassesImplementTheInterfaceIsFound() {
        declareApplication(ExampleWrapperApp::class.java.name)
        assertTrue(components().hasApplicationOfType(ExampleFrameworkApp::class.java.name))
    }

    @Test
    fun applicationImplementingAnInterfaceThatExtendsTheTypeIsFound() {
        declareApplication(ExampleSubInterfaceApp::class.java.name)
        assertTrue(components().hasApplicationOfType(ExampleFrameworkApp::class.java.name))
    }

    @Test
    fun applicationNotOfTheTypeIsNotFound() {
        declareApplication(ExamplePlainApp::class.java.name)
        assertFalse(components().hasApplicationOfType(ExampleFrameworkApp::class.java.name))
    }

    @Test
    fun appWithNoApplicationClassFindsNothing() {
        declareApplication(null)
        assertFalse(components().hasApplicationOfType(ExampleFrameworkApp::class.java.name))
    }

    @Test
    fun unloadableApplicationClassIsSkipped() {
        declareApplication("com.example.NotOnTheClasspath")
        assertFalse(components().hasApplicationOfType(ExampleFrameworkApp::class.java.name))
    }
}
