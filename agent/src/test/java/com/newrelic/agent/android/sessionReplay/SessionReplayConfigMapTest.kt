/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionReplayConfigMapTest {

    private val off = mapOf<String, Any>("enabled" to false, "recordingMode" to "off")

    // Four booleans cannot all differ; this pattern makes four of the six key pairs differ, and
    // eachMaskFlagChangesOnlyItsOwnKey covers the rest.
    private fun enabledConfig() = SessionReplayConfiguration().apply {
        isEnabled = true
        mode = "custom"
        isMaskApplicationText = true
        isMaskUserInputText = false
        isMaskAllImages = false
        isMaskAllUserTouches = true
    }

    private val maskFlags: List<Pair<String, SessionReplayConfiguration.(Boolean) -> Unit>> = listOf(
        "maskApplicationText" to { isMaskApplicationText = it },
        "maskUserInputText" to { isMaskUserInputText = it },
        "maskAllImages" to { isMaskAllImages = it },
        "maskAllUserTouches" to { isMaskAllUserTouches = it },
    )

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any>.putAnything() {
        (this as MutableMap<String, Any>)["enabled"] = true
    }

    @Test
    fun anEnabledConfigurationGivesTheSevenKeys() {
        val configuration = enabledConfig()

        val map = SessionReplayConfigMap.create(configuration, SessionReplayMode.FULL)

        assertEquals(
            mapOf<String, Any>(
                "enabled" to true,
                "mode" to "custom",
                "recordingMode" to "full",
                "maskApplicationText" to true,
                "maskUserInputText" to false,
                "maskAllImages" to false,
                "maskAllUserTouches" to true,
            ),
            map
        )
        assertEquals(configuration.mode, map["mode"])
        for (key in listOf("mode", "recordingMode")) {
            assertTrue(key, map[key] is String)
        }
        for (key in listOf("enabled", "maskApplicationText", "maskUserInputText", "maskAllImages", "maskAllUserTouches")) {
            assertTrue(key, map[key] is Boolean)
        }
    }

    @Test
    fun eachMaskFlagChangesOnlyItsOwnKey() {
        val base = SessionReplayConfigMap.create(enabledConfig(), SessionReplayMode.FULL)

        for ((key, set) in maskFlags) {
            val flipped = enabledConfig().apply { set(base[key] != true) }

            val map = SessionReplayConfigMap.create(flipped, SessionReplayMode.FULL)

            assertEquals(key, base[key] != true, map[key])
            assertEquals(key, base.filterKeys { it != key }, map.filterKeys { it != key })
        }
    }

    @Test
    fun theDefaultMasksAreReported() {
        val configuration = SessionReplayConfiguration().apply { isEnabled = true }

        val map = SessionReplayConfigMap.create(configuration, SessionReplayMode.FULL)

        assertEquals("default", map["mode"])
        assertEquals(configuration.isMaskApplicationText, map["maskApplicationText"])
        assertEquals(configuration.isMaskUserInputText, map["maskUserInputText"])
        assertEquals(configuration.isMaskAllImages, map["maskAllImages"])
        assertEquals(configuration.isMaskAllUserTouches, map["maskAllUserTouches"])
    }

    @Test
    fun errorModeIsReportedAsError() {
        val map = SessionReplayConfigMap.create(enabledConfig(), SessionReplayMode.ERROR)

        assertEquals("error", map["recordingMode"])
        assertEquals(7, map.size)
    }

    @Test
    fun aNullModeIsReportedAsOffWithTheSevenKeys() {
        val map = SessionReplayConfigMap.create(enabledConfig(), null)

        assertEquals("off", map["recordingMode"])
        assertEquals(true, map["enabled"])
        assertEquals(7, map.size)
    }

    @Test
    fun aDisabledConfigurationGivesTheOffShapeWhateverTheModeSays() {
        val configuration = enabledConfig().apply { isEnabled = false }

        for (mode in listOf(SessionReplayMode.FULL, SessionReplayMode.ERROR, SessionReplayMode.OFF, null)) {
            assertEquals(off, SessionReplayConfigMap.create(configuration, mode))
        }
    }

    @Test
    fun aNullConfigurationGivesTheOffShape() {
        assertEquals(off, SessionReplayConfigMap.create(null, SessionReplayMode.FULL))
        assertEquals(off, SessionReplayConfigMap.create(null, null))
        assertEquals(off, SessionReplayConfigMap.off())
    }

    @Test
    fun bothShapesAreUnmodifiable() {
        val enabled = SessionReplayConfigMap.create(enabledConfig(), SessionReplayMode.FULL)
        val disabled = SessionReplayConfigMap.create(null, null)

        assertThrows(UnsupportedOperationException::class.java) { enabled.putAnything() }
        assertThrows(UnsupportedOperationException::class.java) { disabled.putAnything() }
        assertThrows(UnsupportedOperationException::class.java) { SessionReplayConfigMap.off().putAnything() }
    }

    @Test
    fun changingTheConfigurationAfterwardsDoesNotChangeTheMap() {
        val configuration = enabledConfig()
        val map = SessionReplayConfigMap.create(configuration, SessionReplayMode.FULL)

        configuration.isMaskAllImages = true
        configuration.mode = "default"
        configuration.isEnabled = false

        assertEquals(false, map["maskAllImages"])
        assertEquals("custom", map["mode"])
        assertEquals(true, map["enabled"])
    }

    @Test
    fun aMissingModeFallsBackToDefault() {
        val configuration = enabledConfig().apply { mode = null }

        assertEquals("default", SessionReplayConfigMap.create(configuration, SessionReplayMode.FULL)["mode"])
    }
}
