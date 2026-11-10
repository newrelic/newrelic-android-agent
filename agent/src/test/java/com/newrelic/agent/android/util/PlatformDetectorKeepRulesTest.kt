/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.util

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The detector compares class names, and R8 renames any class nothing keeps by name (a Flutter release
 * build turns FlutterActivity into "X.e"). The agent's consumer rules therefore have to keep every name
 * the detector asks about, or detection silently finds nothing in a minified app.
 */
class PlatformDetectorKeepRulesTest {

    /** Every class name the detector looks up, found by running it against an app that has none of them. */
    private fun namesTheDetectorChecks(): Set<String> {
        val asked = linkedSetOf<String>()
        PlatformDetector(object : AppClasses {
            override fun hasActivityOfType(typeName: String) = false.also { asked.add(typeName) }
            override fun hasApplicationOfType(typeName: String) = false.also { asked.add(typeName) }
            override fun hasClass(className: String) = false.also { asked.add(className) }
        }).detect()
        return asked
    }

    /** Class names kept by name (not renamed) by the agent's consumer rules. */
    private fun namesTheConsumerRulesKeep(): Set<String> {
        // unit tests run with the module directory as the working directory
        val rules = File("proguard-rules.pro").readText()
        return Regex("""-keepnames\s+(?:class|interface)\s+(\S+)""")
            .findAll(rules)
            .map { it.groupValues[1] }
            .toSet()
    }

    @Test
    fun theDetectorChecksSomething() {
        assertTrue(namesTheDetectorChecks().size >= 10)
    }

    @Test
    fun everyClassNameTheDetectorChecksIsKeptByTheConsumerRules() {
        val kept = namesTheConsumerRulesKeep()
        val unprotected = namesTheDetectorChecks().filterNot { it in kept }

        assertTrue("R8 may rename these, hiding the framework from the detector: $unprotected", unprotected.isEmpty())
    }
}
