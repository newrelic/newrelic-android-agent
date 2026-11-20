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
import com.newrelic.agent.android.util.Constants
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

        assertEquals(Constants.SessionReplay.INGEST_STATUS_DROPPED_DISABLED, SessionReplay.recordSessionReplayEvents("[]"))
        assertEquals(Constants.SessionReplay.INGEST_STATUS_DROPPED_DISABLED, NewRelic.recordSessionReplayEvents("[]"))
    }

    @Test
    fun otherHybridFrameworksAreDisabledToo() {
        initialize(SessionReplayMode.FULL, ApplicationFramework.ReactNative)

        assertEquals(Constants.SessionReplay.INGEST_STATUS_DROPPED_DISABLED, SessionReplay.recordSessionReplayEvents("[]"))
    }

    @Test
    fun theFrameworkGateWinsOverAnInvalidBatch() {
        initialize(SessionReplayMode.FULL, ApplicationFramework.Native)

        assertEquals(Constants.SessionReplay.INGEST_STATUS_DROPPED_DISABLED, SessionReplay.recordSessionReplayEvents(""))
        assertEquals(Constants.SessionReplay.INGEST_STATUS_DROPPED_DISABLED, SessionReplay.recordSessionReplayEvents(null))
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
            Constants.SessionReplay.INGEST_STATUS_ACCEPTED,
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
        assertEquals(Constants.SessionReplay.INGEST_STATUS_ACCEPTED, SessionReplay.recordSessionReplayEvents("""[{"type":4,"timestamp":1}]"""))
        assertEquals(Constants.SessionReplay.INGEST_STATUS_ACCEPTED, SessionReplay.recordSessionReplayEvents("""[{"type":2,"timestamp":2}]"""))
        session.onHarvestFinalize()
        assertEquals(Constants.SessionReplay.INGEST_STATUS_ACCEPTED, SessionReplay.recordSessionReplayEvents("""[{"type":3,"timestamp":3}]"""))

        awaitEventsInFile(3)
        val events = SessionReplayFileManager.readEventsAsJsonArray()
        assertEquals(listOf(4, 2, 3), (0 until events.size()).map { events[it].asJsonObject["type"].asInt })
    }

    @Test
    fun batchesHeldDuringAHarvestSurviveTheFileClearOnHarvest() {
        initialize(SessionReplayMode.FULL)
        SessionReplay.initSessionReplay(SessionReplayMode.FULL)
        val session = singleton()
        assertEquals(Constants.SessionReplay.INGEST_STATUS_ACCEPTED, SessionReplay.recordSessionReplayEvents("""[{"type":4,"timestamp":1}]"""))
        awaitEventsInFile(1)

        // a real harvest over a non-empty file: onHarvest() reads it and clears it
        session.onHarvestBefore()
        assertEquals(Constants.SessionReplay.INGEST_STATUS_ACCEPTED, SessionReplay.recordSessionReplayEvents("""[{"type":4,"timestamp":2},{"type":2,"timestamp":3}]"""))
        session.onHarvest()
        session.onHarvestFinalize()

        awaitEventsInFile(2)
        val events = SessionReplayFileManager.readEventsAsJsonArray()
        assertEquals(listOf(4, 2), (0 until events.size()).map { events[it].asJsonObject["type"].asInt })
        assertEquals(listOf(2L, 3L), (0 until events.size()).map { events[it].asJsonObject["timestamp"].asLong })
    }

    @Test
    fun beforeSessionReplayStartsTheStatusIsDisabled() {
        assertEquals(Constants.SessionReplay.INGEST_STATUS_DROPPED_DISABLED, SessionReplay.recordSessionReplayEvents("[]"))
    }

    @Test
    fun fullModeAcceptsAndRejectsInvalidBatches() {
        initialize(SessionReplayMode.FULL)

        assertEquals(Constants.SessionReplay.INGEST_STATUS_ACCEPTED, SessionReplay.recordSessionReplayEvents("[]"))
        assertEquals(Constants.SessionReplay.INGEST_STATUS_DROPPED_INVALID, SessionReplay.recordSessionReplayEvents(""))
    }

    @Test
    fun errorModeIsDisabledUntilErrorModeShips() {
        initialize(SessionReplayMode.ERROR)

        assertEquals(Constants.SessionReplay.INGEST_STATUS_DROPPED_DISABLED, SessionReplay.recordSessionReplayEvents("[]"))
    }

    @Test
    fun pausingTheReplayClosesTheGateAndRecordingReopensIt() {
        initialize(SessionReplayMode.FULL)

        assertTrue(SessionReplay.pauseReplay())
        assertEquals(Constants.SessionReplay.INGEST_STATUS_DROPPED_DISABLED, SessionReplay.recordSessionReplayEvents("[]"))

        assertTrue(SessionReplay.transitionToMode(SessionReplayMode.FULL, "test"))
        assertEquals(Constants.SessionReplay.INGEST_STATUS_ACCEPTED, SessionReplay.recordSessionReplayEvents("[]"))
    }

    @Test
    fun afterTheSessionEndsTheModeIsNullAndTheStatusIsDisabled() {
        initialize(SessionReplayMode.FULL)
        SessionReplay.deInitialize()

        assertEquals(Constants.SessionReplay.INGEST_STATUS_DROPPED_DISABLED, SessionReplay.recordSessionReplayEvents("[]"))
    }

    @Test
    fun theNewRelicEntryReturnsTheSameStatus() {
        assertEquals(Constants.SessionReplay.INGEST_STATUS_DROPPED_DISABLED, NewRelic.recordSessionReplayEvents("[]"))

        initialize(SessionReplayMode.FULL)

        assertEquals(Constants.SessionReplay.INGEST_STATUS_ACCEPTED, NewRelic.recordSessionReplayEvents("[]"))
        assertEquals(Constants.SessionReplay.INGEST_STATUS_DROPPED_INVALID, NewRelic.recordSessionReplayEvents(null))
    }
}
