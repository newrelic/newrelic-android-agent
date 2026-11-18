/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.newrelic.agent.android.sessionReplay.SessionReplay
import com.newrelic.agent.android.sessionReplay.SessionReplayConfigListenerTestSupport
import com.newrelic.agent.android.sessionReplay.SessionReplayConfiguration
import com.newrelic.agent.android.sessionReplay.SessionReplayMode
import com.newrelic.agent.android.stats.StatsEngine
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NewRelicSessionReplayConfigurationTest {

    private val offShape = mapOf<String, Any>("enabled" to false, "recordingMode" to "off")

    // NewRelic reads the process-wide AgentConfiguration, so its Session Replay part is saved and restored
    private val configuration = AgentConfiguration.getInstance()
    private val savedSessionReplayConfiguration = SessionReplayConfiguration()
    private var wasStarted = false

    @Before
    fun setUp() {
        SessionReplayConfigListenerTestSupport.reset()
        wasStarted = NewRelic.started
        savedSessionReplayConfiguration.setConfiguration(configuration.sessionReplayConfiguration)
        NewRelic.started = false
        configuration.sessionReplayConfiguration.apply {
            isEnabled = false
            mode = "custom"
            // differs from the defaults (true, true, true, false) and between most key pairs
            isMaskApplicationText = true
            isMaskUserInputText = false
            isMaskAllImages = false
            isMaskAllUserTouches = true
        }
    }

    @After
    fun tearDown() {
        SessionReplay.deInitialize()
        SessionReplayConfigListenerTestSupport.reset()
        configuration.sessionReplayConfiguration.setConfiguration(savedSessionReplayConfiguration)
        NewRelic.started = wasStarted
        StatsEngine.reset()
    }

    private fun startRecording(mode: SessionReplayMode) {
        configuration.sessionReplayConfiguration.isEnabled = true
        val application = ApplicationProvider.getApplicationContext<Application>()
        SessionReplay.initialize(application, Handler(Looper.getMainLooper()), configuration, mode)
    }

    @Test
    fun beforeTheAgentStartsTheMapIsTheOffShape() {
        configuration.sessionReplayConfiguration.isEnabled = true

        assertEquals(offShape, NewRelic.getSessionReplayConfiguration())
    }

    @Test
    fun aDisabledConfigurationIsTheOffShapeEvenWhileSessionReplayHoldsFull() {
        NewRelic.started = true
        startRecording(SessionReplayMode.FULL)
        configuration.sessionReplayConfiguration.isEnabled = false

        assertEquals(SessionReplayMode.FULL, SessionReplay.getCurrentMode())
        assertEquals(offShape, NewRelic.getSessionReplayConfiguration())
    }

    @Test
    fun anEnabledConfigurationRecordingFullGivesTheSevenKeys() {
        NewRelic.started = true
        startRecording(SessionReplayMode.FULL)

        val map = NewRelic.getSessionReplayConfiguration()

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
    }

    @Test
    fun recordingInErrorModeIsReportedAsError() {
        NewRelic.started = true
        startRecording(SessionReplayMode.ERROR)

        assertEquals("error", NewRelic.getSessionReplayConfiguration()["recordingMode"])
    }

    @Test
    fun anEnabledConfigurationWithSessionReplayNeverInitializedIsReportedAsOffWithTheSevenKeys() {
        NewRelic.started = true
        configuration.sessionReplayConfiguration.isEnabled = true
        assertNull(SessionReplay.getCurrentMode())

        val map = NewRelic.getSessionReplayConfiguration()

        assertEquals("off", map["recordingMode"])
        assertEquals(true, map["enabled"])
        assertEquals(7, map.size)
    }

    @Test
    fun theMapIsUnmodifiable() {
        NewRelic.started = true
        startRecording(SessionReplayMode.FULL)

        @Suppress("UNCHECKED_CAST")
        val enabled = NewRelic.getSessionReplayConfiguration() as MutableMap<String, Any>
        NewRelic.started = false
        @Suppress("UNCHECKED_CAST")
        val beforeStart = NewRelic.getSessionReplayConfiguration() as MutableMap<String, Any>

        assertThrows(UnsupportedOperationException::class.java) { enabled["enabled"] = false }
        assertThrows(UnsupportedOperationException::class.java) { beforeStart["enabled"] = true }
    }
}
