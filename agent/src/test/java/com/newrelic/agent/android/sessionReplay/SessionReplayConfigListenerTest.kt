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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class SessionReplayConfigListenerTest {

    private lateinit var application: Application
    private lateinit var agentConfiguration: AgentConfiguration
    private val calls = mutableListOf<Pair<SessionReplayConfiguration, SessionReplayMode>>()
    private val listener = SessionReplayConfigListener { configuration, mode -> calls.add(configuration to mode) }

    @Before
    fun setUp() {
        SessionReplayConfigListenerTestSupport.reset()
        application = ApplicationProvider.getApplicationContext()
        agentConfiguration = AgentConfiguration()
        agentConfiguration.sessionReplayConfiguration.isEnabled = true
    }

    @After
    fun tearDown() {
        SessionReplay.deInitialize()
        SessionReplayConfigListenerTestSupport.reset()
    }

    private fun start(mode: SessionReplayMode) {
        SessionReplay.initialize(application, Handler(Looper.getMainLooper()), agentConfiguration, mode)
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun modes() = calls.map { it.second }

    @Test
    fun errorSwitchingToFullNotifiesOnceWithTheFullMode() {
        SessionReplay.addConfigListener(listener)
        start(SessionReplayMode.ERROR)

        assertTrue(SessionReplay.switchModeOnError())
        idle()

        assertEquals(listOf(SessionReplayMode.FULL), modes())
        assertTrue(calls[0].first.isEnabled)
    }

    @Test
    fun anErrorThatChangesNothingDoesNotNotify() {
        start(SessionReplayMode.FULL)
        SessionReplay.addConfigListener(listener)

        SessionReplay.onError()
        idle()

        assertTrue(calls.isEmpty())
    }

    @Test
    fun anErrorBeforeSessionReplayStartsDoesNotNotify() {
        SessionReplay.addConfigListener(listener)

        SessionReplay.onError()
        idle()

        assertTrue(calls.isEmpty())
    }

    @Test
    fun pausingNotifiesOffOnce() {
        start(SessionReplayMode.FULL)
        SessionReplay.addConfigListener(listener)

        assertTrue(SessionReplay.pauseReplay())
        idle()

        assertEquals(listOf(SessionReplayMode.OFF), modes())
    }

    @Test
    fun pausingWhenAlreadyOffDoesNotNotify() {
        start(SessionReplayMode.FULL)
        SessionReplay.pauseReplay()
        idle()
        SessionReplay.addConfigListener(listener)
        idle()
        calls.clear() // the replay of the state above is not what this test is about

        assertFalse(SessionReplay.pauseReplay())
        idle()

        assertTrue(calls.isEmpty())
    }

    @Test
    fun pausingBeforeSessionReplayStartsDoesNotNotify() {
        SessionReplay.addConfigListener(listener)

        assertFalse(SessionReplay.pauseReplay())
        idle()

        assertTrue(calls.isEmpty())
    }

    @Test
    fun transitioningToAnotherModeNotifiesAndToTheSameModeDoesNot() {
        start(SessionReplayMode.ERROR)
        SessionReplay.addConfigListener(listener)

        assertTrue(SessionReplay.transitionToMode(SessionReplayMode.FULL, "test"))
        assertFalse(SessionReplay.transitionToMode(SessionReplayMode.FULL, "test"))
        idle()

        assertEquals(listOf(SessionReplayMode.FULL), modes())
    }

    @Test
    fun endingTheSessionDoesNotNotifyAndTheModeIsNull() {
        start(SessionReplayMode.FULL)
        SessionReplay.addConfigListener(listener)

        SessionReplay.deInitialize()
        idle()

        assertTrue(calls.isEmpty())
        assertNull(SessionReplay.getCurrentMode())
    }

    @Test
    fun theNextNotificationAfterTheSessionEndedReportsOff() {
        start(SessionReplayMode.FULL)
        SessionReplay.deInitialize()
        SessionReplay.addConfigListener(listener)

        SessionReplay.notifyConfigListeners(agentConfiguration.sessionReplayConfiguration)
        idle()

        assertEquals(listOf(SessionReplayMode.OFF), modes())
    }

    @Test
    fun listenersStayRegisteredAcrossSessions() {
        start(SessionReplayMode.FULL)
        SessionReplay.addConfigListener(listener)
        SessionReplay.deInitialize()
        start(SessionReplayMode.ERROR)

        SessionReplay.switchModeOnError()
        idle()

        assertEquals(listOf(SessionReplayMode.FULL), modes())
    }

    @Test
    fun registeringBeforeSessionReplayStartsWorks() {
        SessionReplay.addConfigListener(listener)
        start(SessionReplayMode.FULL)

        SessionReplay.pauseReplay()
        idle()

        assertEquals(listOf(SessionReplayMode.OFF), modes())
    }

    @Test
    fun aRemovedListenerIsNotCalled() {
        start(SessionReplayMode.FULL)
        SessionReplay.addConfigListener(listener)
        SessionReplay.removeConfigListener(listener)

        SessionReplay.pauseReplay()
        idle()

        assertTrue(calls.isEmpty())
    }

    @Test
    fun aListenerAddedAfterAChangeGetsTheLatestStateOnce() {
        start(SessionReplayMode.ERROR)
        SessionReplay.switchModeOnError()
        idle()

        SessionReplay.addConfigListener(listener)
        idle()

        assertEquals(listOf(SessionReplayMode.FULL), modes())
    }

    @Test
    fun theListenerGetsTheConfigurationAsItWasAtTheChange() {
        val live = agentConfiguration.sessionReplayConfiguration
        live.setMaskAllImages(false) // maskAllImages defaults to true, so false is the non-default value
        start(SessionReplayMode.ERROR)
        SessionReplay.addConfigListener(listener)

        SessionReplay.switchModeOnError()
        live.setMaskAllImages(true) // a later harvest changes the live object
        idle()

        assertFalse(calls[0].first.isMaskAllImages)
    }

    @Test
    fun nullListenersAreIgnored() {
        SessionReplay.addConfigListener(null)
        SessionReplay.removeConfigListener(null)
        start(SessionReplayMode.ERROR)
        SessionReplay.addConfigListener(listener)

        SessionReplay.switchModeOnError()
        idle()

        assertEquals(listOf(SessionReplayMode.FULL), modes())
    }

    @Test
    fun anErrorWhileInErrorModeNotifiesFullExactlyOnce() {
        start(SessionReplayMode.ERROR)
        SessionReplay.addConfigListener(listener)

        SessionReplay.onError()
        idle()

        assertEquals(listOf(SessionReplayMode.FULL), modes())
    }

    @Test
    fun pausingFromErrorModeNotifiesOffOnce() {
        start(SessionReplayMode.ERROR)
        SessionReplay.addConfigListener(listener)

        assertTrue(SessionReplay.pauseReplay())
        idle()

        assertEquals(listOf(SessionReplayMode.OFF), modes())
    }

    @Test
    fun transitioningToOffNotifiesOnce() {
        start(SessionReplayMode.FULL)
        SessionReplay.addConfigListener(listener)

        assertTrue(SessionReplay.transitionToMode(SessionReplayMode.OFF, "test"))
        idle()

        assertEquals(listOf(SessionReplayMode.OFF), modes())
    }

    @Test
    fun nothingIsDeliveredBeforeTheMainLooperRuns() {
        start(SessionReplayMode.ERROR)
        SessionReplay.addConfigListener(listener)

        SessionReplay.switchModeOnError()
        assertTrue(calls.isEmpty())

        idle()
        assertEquals(listOf(SessionReplayMode.FULL), modes())
    }
}
