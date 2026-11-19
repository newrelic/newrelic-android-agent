/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.newrelic.agent.android.AgentConfiguration
import com.newrelic.agent.android.NewRelic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The ingest gate, read through the real [SessionReplay] and the public [NewRelic] entry. */
@RunWith(RobolectricTestRunner::class)
class SessionReplayRecordEventsTest {

    private lateinit var application: Application

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        SessionReplay.deInitialize()
    }

    @After
    fun tearDown() {
        SessionReplay.deInitialize()
    }

    private fun initialize(mode: SessionReplayMode) {
        SessionReplay.initialize(application, Handler(Looper.getMainLooper()), AgentConfiguration(), mode)
    }

    @Test
    fun beforeSessionReplayStartsTheStatusIsDisabled() {
        assertEquals(SessionReplayIngestStatus.DROPPED_DISABLED, SessionReplay.recordSessionReplayEvents("[]", "2.0.0"))
    }

    @Test
    fun fullModeAcceptsAndRejectsInvalidBatches() {
        initialize(SessionReplayMode.FULL)

        assertEquals(SessionReplayIngestStatus.ACCEPTED, SessionReplay.recordSessionReplayEvents("[]", "2.0.0"))
        assertEquals(SessionReplayIngestStatus.DROPPED_INVALID, SessionReplay.recordSessionReplayEvents("", "2.0.0"))
    }

    @Test
    fun errorModeIsDisabledUntilErrorModeShips() {
        initialize(SessionReplayMode.ERROR)

        assertEquals(SessionReplayIngestStatus.DROPPED_DISABLED, SessionReplay.recordSessionReplayEvents("[]", "2.0.0"))
    }

    @Test
    fun pausingTheReplayClosesTheGateAndRecordingReopensIt() {
        initialize(SessionReplayMode.FULL)

        assertTrue(SessionReplay.pauseReplay())
        assertEquals(SessionReplayIngestStatus.DROPPED_DISABLED, SessionReplay.recordSessionReplayEvents("[]", "2.0.0"))

        assertTrue(SessionReplay.transitionToMode(SessionReplayMode.FULL, "test"))
        assertEquals(SessionReplayIngestStatus.ACCEPTED, SessionReplay.recordSessionReplayEvents("[]", "2.0.0"))
    }

    @Test
    fun afterTheSessionEndsTheModeIsNullAndTheStatusIsDisabled() {
        initialize(SessionReplayMode.FULL)
        SessionReplay.deInitialize()

        assertEquals(SessionReplayIngestStatus.DROPPED_DISABLED, SessionReplay.recordSessionReplayEvents("[]", "2.0.0"))
    }

    @Test
    fun theNewRelicEntryReturnsTheSameStatus() {
        assertEquals(SessionReplayIngestStatus.DROPPED_DISABLED, NewRelic.recordSessionReplayEvents("[]", "2.0.0"))

        initialize(SessionReplayMode.FULL)

        assertEquals(SessionReplayIngestStatus.ACCEPTED, NewRelic.recordSessionReplayEvents("[]", "2.0.0"))
        assertEquals(SessionReplayIngestStatus.DROPPED_INVALID, NewRelic.recordSessionReplayEvents(null, "2.0.0"))
    }
}
