/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.util

import android.content.Context
import com.newrelic.agent.android.ApplicationFramework

/**
 * The framework a hybrid New Relic SDK declares for the app it is built into: a string resource
 * named [NAME], shipped in the SDK's own library and merged into the app at build time, whose value
 * is an [ApplicationFramework] name (for example "Flutter"). It is there however the agent was started,
 * so it works even when the native agent starts before any hybrid code runs.
 *
 * Next to it a second string, [VERSION_NAME], carries the hybrid SDK's own version (for example
 * "1.9.4"), so the platform version is right from the first session too and does not wait for the
 * SDK's own start call. The SDK generates both from its build, so neither is edited by hand.
 *
 * An app with no hybrid SDK has no such resource, and the lookup is a single in-process miss: no
 * binder call, no class loading. The agent's resource keep file (res/raw/newrelic_keep.xml) keeps the
 * string from being removed by resource shrinking, which cannot see a lookup by name.
 */
object ApplicationFrameworkResource {

    const val NAME = "com_newrelic_application_framework"
    const val VERSION_NAME = "com_newrelic_application_framework_version"

    /** The declared framework, or null when the app declares none or declares something unusable. */
    @JvmStatic
    fun read(context: Context): ApplicationFramework? {
        val declared = readString(context, NAME) ?: return null

        return ApplicationFramework.values()
            .firstOrNull { it != ApplicationFramework.Native && it.name.equals(declared, ignoreCase = true) }
    }

    /** The declared framework version, or null when the app declares none or it is blank. */
    @JvmStatic
    fun readVersion(context: Context): String? = readString(context, VERSION_NAME)

    private fun readString(context: Context, name: String): String? {
        val resources = context.resources
        val id = resources.getIdentifier(name, "string", context.packageName)
        if (id == 0) {
            return null
        }

        val value = try {
            resources.getString(id)?.trim()
        } catch (e: android.content.res.Resources.NotFoundException) {
            null
        }
        return if (value.isNullOrEmpty()) null else value
    }
}
