/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.util

import com.newrelic.agent.android.ApplicationFramework
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlatformDetectorTest {

    private fun detect(manifest: AppClasses) = PlatformDetector(manifest).detect()

    private fun activities(vararg types: String) = FakeAppClasses(activityTypes = types.toSet())

    private fun application(vararg types: String) = FakeAppClasses(applicationTypes = types.toSet())

    @Test
    fun nothingDeclaredDetectsNothing() {
        assertNull(detect(FakeAppClasses()))
    }

    @Test
    fun unrelatedTypesDetectNothing() {
        assertNull(detect(activities("androidx.appcompat.app.AppCompatActivity")))
        assertNull(detect(application("android.app.Application")))
    }

    // --- activities ---

    @Test
    fun flutterActivityIsFlutter() {
        assertEquals(
            ApplicationFramework.Flutter,
            detect(activities("io.flutter.embedding.android.FlutterActivity"))
        )
    }

    @Test
    fun flutterFragmentActivityIsFlutter() {
        assertEquals(
            ApplicationFramework.Flutter,
            detect(activities("io.flutter.embedding.android.FlutterFragmentActivity"))
        )
    }

    @Test
    fun legacyFlutterActivityIsFlutter() {
        assertEquals(ApplicationFramework.Flutter, detect(activities("io.flutter.app.FlutterActivity")))
    }

    @Test
    fun reactActivityIsReactNative() {
        assertEquals(ApplicationFramework.ReactNative, detect(activities("com.facebook.react.ReactActivity")))
    }

    @Test
    fun cordovaActivityIsCordova() {
        assertEquals(ApplicationFramework.Cordova, detect(activities("org.apache.cordova.CordovaActivity")))
    }

    @Test
    fun bridgeActivityIsCapacitor() {
        assertEquals(ApplicationFramework.Capacitor, detect(activities("com.getcapacitor.BridgeActivity")))
    }

    @Test
    fun unityPlayerActivityIsUnity() {
        assertEquals(ApplicationFramework.Unity, detect(activities("com.unity3d.player.UnityPlayerActivity")))
    }

    @Test
    fun unityIsRankedAfterTheOtherFrameworks() {
        assertEquals(
            ApplicationFramework.Flutter,
            detect(activities("com.unity3d.player.UnityPlayerActivity", "io.flutter.embedding.android.FlutterActivity"))
        )
    }

    @Test
    fun capacitorWinsOverCordovaWhenBothMatch() {
        assertEquals(
            ApplicationFramework.Capacitor,
            detect(activities("com.getcapacitor.BridgeActivity", "org.apache.cordova.CordovaActivity"))
        )
    }

    // --- application class ---

    @Test
    fun reactApplicationIsReactNativeEvenWithoutAReactActivity() {
        assertEquals(
            ApplicationFramework.ReactNative,
            detect(application("com.facebook.react.ReactApplication"))
        )
    }

    @Test
    fun anActivityTypeDeclaredOnTheApplicationDoesNotCount() {
        assertNull(detect(application("com.facebook.react.ReactActivity")))
    }

    @Test
    fun anApplicationTypeDeclaredOnAnActivityDoesNotCount() {
        assertNull(detect(activities("com.facebook.react.ReactApplication")))
    }

    // --- New Relic hybrid plugin classes ---

    private fun classes(vararg names: String) = FakeAppClasses(classes = names.toSet())

    @Test
    fun reactNativePluginClassIsReactNative() {
        assertEquals(ApplicationFramework.ReactNative, detect(classes("com.NewRelic.NRMModularAgentPackage")))
    }

    @Test
    fun flutterPluginClassIsFlutter() {
        assertEquals(
            ApplicationFramework.Flutter,
            detect(classes("com.newrelic.newrelic_mobile.NewrelicMobilePlugin"))
        )
    }

    @Test
    fun cordovaPluginClassIsCordova() {
        assertEquals(
            ApplicationFramework.Cordova,
            detect(classes("com.newrelic.cordova.plugin.NewRelicCordovaPlugin"))
        )
    }

    @Test
    fun capacitorPluginClassIsCapacitor() {
        assertEquals(
            ApplicationFramework.Capacitor,
            detect(classes("com.newrelic.capacitor.plugin.NewRelicCapacitorPlugin"))
        )
    }

    @Test
    fun classesTheNativeAgentItselfShipsDoNotCount() {
        assertNull(
            detect(
                classes(
                    "com.newrelic.agent.android.NewRelic",
                    "com.newrelic.agent.android.unity.NewRelicUnity",
                    "com.newrelic.agent.android.hybrid.rninterface.IStack"
                )
            )
        )
    }

    @Test
    fun pluginClassesRankLikeTheOtherSignals() {
        assertEquals(
            ApplicationFramework.Capacitor,
            detect(
                classes(
                    "com.newrelic.cordova.plugin.NewRelicCordovaPlugin",
                    "com.newrelic.capacitor.plugin.NewRelicCapacitorPlugin"
                )
            )
        )
    }

    @Test
    fun aPluginClassStillCountsWhenTheActivitiesAreUnrelated() {
        val appClasses = FakeAppClasses(
            activityTypes = setOf("androidx.appcompat.app.AppCompatActivity"),
            classes = setOf("com.NewRelic.NRMModularAgentPackage")
        )

        assertEquals(ApplicationFramework.ReactNative, detect(appClasses))
    }

    // --- failures ---

    @Test
    fun failingLookupsDetectNothing() {
        assertNull(
            detect(
                FakeAppClasses(
                    failActivityLookupWith = SecurityException("denied"),
                    failApplicationLookupWith = SecurityException("denied"),
                    failClassLookupWith = SecurityException("denied")
                )
            )
        )
    }

    @Test
    fun aFailingClassLookupDoesNotHideAnActivityMatch() {
        val appClasses = FakeAppClasses(
            activityTypes = setOf("io.flutter.embedding.android.FlutterActivity"),
            failClassLookupWith = SecurityException("denied")
        )

        assertEquals(ApplicationFramework.Flutter, detect(appClasses))
    }

    @Test
    fun aFailingActivityLookupDoesNotHideAnApplicationMatch() {
        val manifest = FakeAppClasses(
            applicationTypes = setOf("com.facebook.react.ReactApplication"),
            failActivityLookupWith = SecurityException("denied")
        )

        assertEquals(ApplicationFramework.ReactNative, detect(manifest))
    }
}
