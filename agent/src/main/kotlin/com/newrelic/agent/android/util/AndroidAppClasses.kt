/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.util

import android.content.Context
import android.content.pm.PackageManager

/**
 * The classes an app contains, looked up by name: the activities and application class it declares
 * in its manifest, and any class its class loader can load. The manifest only names the app's own
 * classes (for example MainActivity), so a framework type is found by walking each class's
 * superclass chain, and the interfaces along it, by name. All of this needs the framework's class
 * names to survive R8.
 */
class AndroidAppClasses(private val context: Context) : AppClasses {

    private val activityNames: List<String> by lazy {
        try {
            @Suppress("DEPRECATION")
            val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_ACTIVITIES)
            info.activities?.map { it.name } ?: emptyList()
        } catch (e: PackageManager.NameNotFoundException) {
            emptyList()
        }
    }

    override fun hasActivityOfType(typeName: String): Boolean =
        activityNames.any { isOfType(it, typeName) }

    override fun hasApplicationOfType(typeName: String): Boolean {
        val applicationName = context.applicationInfo?.className ?: return false
        return isOfType(applicationName, typeName)
    }

    override fun hasClass(className: String): Boolean = loadClass(className) != null

    private fun isOfType(className: String, typeName: String): Boolean {
        if (className == typeName) {
            return true
        }
        return try {
            generateSequence(loadClass(className)) { it.superclass }
                .any { it.name == typeName || implements(it, typeName) }
        } catch (t: Throwable) {
            // an unresolvable superclass or interface (linkage error): no signal
            false
        }
    }

    private fun implements(type: Class<*>, typeName: String): Boolean =
        type.interfaces.any { it.name == typeName || implements(it, typeName) }

    /** Loads without initializing; returns null on any failure, including linkage errors. */
    private fun loadClass(name: String): Class<*>? =
        try {
            Class.forName(name, false, context.classLoader)
        } catch (t: Throwable) {
            null
        }
}
