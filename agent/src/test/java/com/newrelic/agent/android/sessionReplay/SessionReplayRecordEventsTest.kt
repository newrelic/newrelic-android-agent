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
import com.newrelic.agent.android.ApplicationFramework
import com.newrelic.agent.android.NewRelic
import com.newrelic.agent.android.sessionReplay.capture.SessionReplayFileManager
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

    /** The Flutter plugin sets the framework before the agent starts, so the default here is Flutter. */
    private fun initialize(mode: SessionReplayMode, framework: ApplicationFramework = ApplicationFramework.Flutter) {
        val configuration = AgentConfiguration().apply { applicationFramework = framework }
        SessionReplay.initialize(application, Handler(Looper.getMainLooper()), configuration, mode)
    }

    @Test
    fun aNativeFrameworkIsDisabledEvenWhenRecordingInFullMode() {
        // add-to-app: the host app starts the agent itself, so the framework stays Native
        initialize(SessionReplayMode.FULL, ApplicationFramework.Native)

        assertEquals(SessionReplayIngestStatus.DROPPED_DISABLED, SessionReplay.recordSessionReplayEvents("[]"))
        assertEquals(SessionReplayIngestStatus.DROPPED_DISABLED, NewRelic.recordSessionReplayEvents("[]"))
    }

    @Test
    fun otherHybridFrameworksAreDisabledToo() {
        initialize(SessionReplayMode.FULL, ApplicationFramework.ReactNative)

        assertEquals(SessionReplayIngestStatus.DROPPED_DISABLED, SessionReplay.recordSessionReplayEvents("[]"))
    }

    @Test
    fun theFrameworkGateWinsOverAnInvalidBatch() {
        initialize(SessionReplayMode.FULL, ApplicationFramework.Native)

        assertEquals(SessionReplayIngestStatus.DROPPED_DISABLED, SessionReplay.recordSessionReplayEvents(""))
        assertEquals(SessionReplayIngestStatus.DROPPED_DISABLED, SessionReplay.recordSessionReplayEvents(null))
    }

    private fun singleton(): SessionReplay {
        val field = SessionReplay::class.java.getDeclaredField("instance")
        field.isAccessible = true
        return field.get(null) as SessionReplay
    }

    /** The events are written by the ingest thread, so poll the working file instead of assuming a time. */
    private fun awaitEventsInFile(expected: Int) {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            if (SessionReplayFileManager.readEventsAsJsonArray().size() == expected) return
            Thread.sleep(25)
        }
        assertEquals("events in the working file", expected, SessionReplayFileManager.readEventsAsJsonArray().size())
    }

    @Test
    fun eventsAreWrittenToTheWorkingFileWhenNoHarvestIsRunning() {
        initialize(SessionReplayMode.FULL)
        SessionReplay.initSessionReplay(SessionReplayMode.FULL)

        assertEquals(
            SessionReplayIngestStatus.ACCEPTED,
            SessionReplay.recordSessionReplayEvents("""[{"type":4,"timestamp":1},{"type":2,"timestamp":2}]"""),
        )

        awaitEventsInFile(2)
    }

    @Test
    fun eventsRecordedDuringAHarvestAreWrittenInOrderOnceItFinalizes() {
        initialize(SessionReplayMode.FULL)
        SessionReplay.initSessionReplay(SessionReplayMode.FULL)
        val session = singleton()

        session.onHarvestBefore()
        assertEquals(SessionReplayIngestStatus.ACCEPTED, SessionReplay.recordSessionReplayEvents("""[{"type":4,"timestamp":1}]"""))
        assertEquals(SessionReplayIngestStatus.ACCEPTED, SessionReplay.recordSessionReplayEvents("""[{"type":2,"timestamp":2}]"""))
        session.onHarvestFinalize()
        assertEquals(SessionReplayIngestStatus.ACCEPTED, SessionReplay.recordSessionReplayEvents("""[{"type":3,"timestamp":3}]"""))

        awaitEventsInFile(3)
        val events = SessionReplayFileManager.readEventsAsJsonArray()
        assertEquals(listOf(4, 2, 3), (0 until events.size()).map { events[it].asJsonObject["type"].asInt })
    }

    @Test
    fun beforeSessionReplayStartsTheStatusIsDisabled() {
        assertEquals(SessionReplayIngestStatus.DROPPED_DISABLED, SessionReplay.recordSessionReplayEvents("[]"))
    }

    @Test
    fun fullModeAcceptsAndRejectsInvalidBatches() {
        initialize(SessionReplayMode.FULL)

        assertEquals(SessionReplayIngestStatus.ACCEPTED, SessionReplay.recordSessionReplayEvents("[]"))
        assertEquals(SessionReplayIngestStatus.DROPPED_INVALID, SessionReplay.recordSessionReplayEvents(""))
    }

    @Test
    fun errorModeIsDisabledUntilErrorModeShips() {
        initialize(SessionReplayMode.ERROR)

        assertEquals(SessionReplayIngestStatus.DROPPED_DISABLED, SessionReplay.recordSessionReplayEvents("[]"))
    }

    @Test
    fun pausingTheReplayClosesTheGateAndRecordingReopensIt() {
        initialize(SessionReplayMode.FULL)

        assertTrue(SessionReplay.pauseReplay())
        assertEquals(SessionReplayIngestStatus.DROPPED_DISABLED, SessionReplay.recordSessionReplayEvents("[]"))

        assertTrue(SessionReplay.transitionToMode(SessionReplayMode.FULL, "test"))
        assertEquals(SessionReplayIngestStatus.ACCEPTED, SessionReplay.recordSessionReplayEvents("[]"))
    }

    @Test
    fun afterTheSessionEndsTheModeIsNullAndTheStatusIsDisabled() {
        initialize(SessionReplayMode.FULL)
        SessionReplay.deInitialize()

        assertEquals(SessionReplayIngestStatus.DROPPED_DISABLED, SessionReplay.recordSessionReplayEvents("[]"))
    }

    @Test
    fun theNewRelicEntryReturnsTheSameStatus() {
        assertEquals(SessionReplayIngestStatus.DROPPED_DISABLED, NewRelic.recordSessionReplayEvents("[]"))

        initialize(SessionReplayMode.FULL)

        assertEquals(SessionReplayIngestStatus.ACCEPTED, NewRelic.recordSessionReplayEvents("[]"))
        assertEquals(SessionReplayIngestStatus.DROPPED_INVALID, NewRelic.recordSessionReplayEvents(null))
    }
}
