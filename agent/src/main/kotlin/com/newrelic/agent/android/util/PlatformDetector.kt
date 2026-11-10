/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.util

import android.content.Context
import com.newrelic.agent.android.ApplicationFramework

/** What the app contains, looked up by class name. */
interface AppClasses {
    /** Whether a declared activity is, or extends or implements, [typeName]. */
    fun hasActivityOfType(typeName: String): Boolean

    /** Whether the declared application class is, or extends or implements, [typeName]. */
    fun hasApplicationOfType(typeName: String): Boolean

    /** Whether [className] can be loaded by the app's class loader. */
    fun hasClass(className: String): Boolean
}

/**
 * Decides which hybrid framework, if any, an app is built with. A hybrid app shows it in three
 * ways: its activity extends the framework's activity class, its application class implements the
 * framework's application interface, or it ships New Relic's own plugin for that framework.
 *
 * The plugin classes are the entry points of the hybrid SDK (not the native agent, which contains
 * no hybrid-only classes), so finding one means the app integrated that SDK.
 *
 * When more than one framework matches, the first entry below wins, so the order is the ranking.
 */
class PlatformDetector(private val appClasses: AppClasses) {

    private class Rule(
        val framework: ApplicationFramework,
        val activityTypes: List<String> = emptyList(),
        val applicationTypes: List<String> = emptyList(),
        val pluginClasses: List<String> = emptyList()
    )

    private val rules: List<Rule> = listOf(
        Rule(
            ApplicationFramework.Capacitor,
            activityTypes = listOf("com.getcapacitor.BridgeActivity"),
            pluginClasses = listOf("com.newrelic.capacitor.plugin.NewRelicCapacitorPlugin")
        ),
        Rule(
            ApplicationFramework.Cordova,
            activityTypes = listOf("org.apache.cordova.CordovaActivity"),
            pluginClasses = listOf("com.newrelic.cordova.plugin.NewRelicCordovaPlugin")
        ),
        Rule(
            ApplicationFramework.ReactNative,
            activityTypes = listOf("com.facebook.react.ReactActivity"),
            // Apps that drive React themselves (custom activities, React Native Navigation) still
            // have an application class implementing ReactApplication.
            applicationTypes = listOf("com.facebook.react.ReactApplication"),
            pluginClasses = listOf("com.NewRelic.NRMModularAgentPackage")
        ),
        Rule(
            ApplicationFramework.Flutter,
            activityTypes = listOf(
                "io.flutter.embedding.android.FlutterActivity",
                "io.flutter.embedding.android.FlutterFragmentActivity",
                "io.flutter.app.FlutterActivity"
            ),
            pluginClasses = listOf("com.newrelic.newrelic_mobile.NewrelicMobilePlugin")
        ),
        Rule(
            ApplicationFramework.Unity,
            activityTypes = listOf("com.unity3d.player.UnityPlayerActivity")
        )
    )

    /** The detected framework, or null when nothing matches. Never throws. */
    fun detect(): ApplicationFramework? = rules.firstOrNull { matches(it) }?.framework

    private fun matches(rule: Rule): Boolean =
        rule.activityTypes.any { holds { appClasses.hasActivityOfType(it) } } ||
            rule.applicationTypes.any { holds { appClasses.hasApplicationOfType(it) } } ||
            rule.pluginClasses.any { holds { appClasses.hasClass(it) } }

    private fun holds(lookup: () -> Boolean): Boolean =
        try {
            lookup()
        } catch (t: Throwable) {
            false
        }

    companion object {
        @JvmStatic
        fun forContext(context: Context): PlatformDetector = PlatformDetector(AndroidAppClasses(context))
    }
}
