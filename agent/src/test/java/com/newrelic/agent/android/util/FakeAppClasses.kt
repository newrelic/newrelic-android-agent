/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.util

/**
 * In-memory [AppClasses] for an app whose activities, application and loadable classes are exactly
 * the given ones. [failActivityLookupWith], [failApplicationLookupWith] and [failClassLookupWith]
 * make that lookup throw.
 */
class FakeAppClasses(
    private val activityTypes: Set<String> = emptySet(),
    private val applicationTypes: Set<String> = emptySet(),
    private val classes: Set<String> = emptySet(),
    private val failActivityLookupWith: Throwable? = null,
    private val failApplicationLookupWith: Throwable? = null,
    private val failClassLookupWith: Throwable? = null
) : AppClasses {

    override fun hasActivityOfType(typeName: String): Boolean {
        failActivityLookupWith?.let { throw it }
        return activityTypes.contains(typeName)
    }

    override fun hasApplicationOfType(typeName: String): Boolean {
        failApplicationLookupWith?.let { throw it }
        return applicationTypes.contains(typeName)
    }

    override fun hasClass(className: String): Boolean {
        failClassLookupWith?.let { throw it }
        return classes.contains(className)
    }
}
