/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.newrelic.agent.android.background.ApplicationStateMonitor
import com.newrelic.agent.android.harvest.HarvestConfiguration
import com.newrelic.agent.android.sessionReplay.SessionReplay
import com.newrelic.agent.android.sessionReplay.SessionReplayConfigListener
import com.newrelic.agent.android.sessionReplay.SessionReplayConfigListenerTestSupport
import com.newrelic.agent.android.sessionReplay.SessionReplayConfiguration
import com.newrelic.agent.android.sessionReplay.SessionReplayMode
import com.newrelic.agent.android.stats.StatsEngine
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
class AndroidAgentImplSessionReplayConfigListenerTest {

    private lateinit var spyContext: SpyContext
    private lateinit var agentConfiguration: AgentConfiguration
    private var agent: AndroidAgentImpl? = null
    private val calls = mutableListOf<Pair<SessionReplayConfiguration, SessionReplayMode>>()
    private val listener = SessionReplayConfigListener { configuration, mode -> calls.add(configuration to mode) }
    private var exitReportingWasEnabled = false

    @Before
    fun setUp() {
        Agent.setImpl(null)
        StatsEngine.reset()
        SessionReplayConfigListenerTestSupport.reset()
        spyContext = SpyContext()
        agentConfiguration = AgentConfiguration().apply {
            applicationToken = "ab20dfe5-96d2-4c6d-b975-3fe9d8778dfc"
        }
        // keeps onHarvestConnected() from reading the app's exit reasons, which this test doesn't need
        exitReportingWasEnabled = FeatureFlag.featureEnabled(FeatureFlag.ApplicationExitReporting)
        FeatureFlag.disableFeature(FeatureFlag.ApplicationExitReporting)
    }

    @After
    fun tearDown() {
        // stop(false) skips the final harvest that stop() would run against the collector
        agent?.stop(false)
        Agent.setImpl(null)
        SessionReplay.deInitialize()
        AgentConfiguration.getInstance().sessionReplayConfiguration.isEnabled = false
        SessionReplayConfigListenerTestSupport.reset()
        if (exitReportingWasEnabled) FeatureFlag.enableFeature(FeatureFlag.ApplicationExitReporting)
        StatsEngine.reset()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun modes() = calls.map { it.second }

    /** Saves a Session Replay configuration the way an earlier connect would have. */
    private fun saveSessionReplayConfiguration(configure: SessionReplayConfiguration.() -> Unit) {
        val harvestConfiguration = HarvestConfiguration.getDefaultHarvestConfiguration()
        harvestConfiguration.remote_configuration.sessionReplayConfiguration.configure()
        SavedState(spyContext.context).saveHarvestConfiguration(harvestConfiguration)
    }

    /**
     * What Agent.start() does, minus Harvest.start(). A live harvest timer would connect on its
     * own thread and publish at arbitrary points, so the tests call the harvest callbacks by hand.
     */
    private fun startAgent(): AndroidAgentImpl {
        val impl = AndroidAgentImpl(spyContext.context, agentConfiguration)
        agent = impl
        Agent.setImpl(impl)
        impl.initialize()
        return impl
    }

    /** Starts the agent, then forgets what boot reported so a test only sees its own trigger. */
    private fun startedAgent(): AndroidAgentImpl {
        val impl = startAgent()
        SessionReplay.addConfigListener(listener)
        idle()
        calls.clear()
        return impl
    }

    private fun savedConfiguration(impl: AndroidAgentImpl): SessionReplayConfiguration =
        impl.savedState.harvestConfiguration.remote_configuration.sessionReplayConfiguration

    /** A sampling rate of 0 makes the session sample to OFF, so no recorder has to start. */
    private fun SessionReplayConfiguration.sampleToOff() {
        isEnabled = true
        samplingRate = 0.0
        errorSamplingRate = 0.0
    }

    private fun SessionReplayConfiguration.sampleToFull() {
        isEnabled = true
        samplingRate = 100.0
        errorSamplingRate = 100.0
    }

    /** Applies a saved configuration that samples every session to FULL, then forgets the notification. */
    private fun AndroidAgentImpl.configureToSampleFull() {
        savedConfiguration(this).sampleToFull()
        onHarvestConfigurationChanged()
        idle()
        calls.clear()
    }

    private fun restartSessionInTheBackground(impl: AndroidAgentImpl) {
        val original = ApplicationStateMonitor.getInstance()
        ApplicationStateMonitor.setInstance(object : ApplicationStateMonitor() {
            init {
                foregrounded.set(false)
            }
        })
        try {
            impl.onSessionRestarted()
            idle()
        } finally {
            ApplicationStateMonitor.setInstance(original)
        }
    }

    @Test
    fun aSavedConfigurationIsReportedAtBootToAListenerRegisteredBeforeStart() {
        saveSessionReplayConfiguration { sampleToOff() }
        SessionReplay.addConfigListener(listener)

        startAgent()
        idle()

        assertEquals(listOf(SessionReplayMode.OFF), modes())
        assertTrue(calls[0].first.isEnabled)
    }

    @Test
    fun aSavedConfigurationIsReplayedToAListenerRegisteredAfterStart() {
        saveSessionReplayConfiguration { sampleToOff() }
        startAgent()
        idle()

        SessionReplay.addConfigListener(listener)
        idle()

        assertEquals(listOf(SessionReplayMode.OFF), modes())
        assertTrue(calls[0].first.isEnabled)
    }

    @Test
    fun aSavedDisabledConfigurationIsReportedAtBoot() {
        saveSessionReplayConfiguration { isEnabled = false }
        SessionReplay.addConfigListener(listener)

        startAgent()
        idle()

        assertEquals(listOf(SessionReplayMode.OFF), modes())
        assertFalse(calls[0].first.isEnabled)
    }

    @Test
    fun noSavedConfigurationMeansNothingIsReportedAtBoot() {
        SessionReplay.addConfigListener(listener)

        startAgent()
        idle()

        assertTrue(calls.isEmpty())
    }

    @Test
    fun harvestConnectReportsTheConfigurationAfterItWasUpdatedEvenWhenDisabled() {
        val impl = startedAgent()
        savedConfiguration(impl).setMaskAllImages(false) // what the connect response resolved
        assertFalse(savedConfiguration(impl).isEnabled)

        impl.onHarvestConnected()
        idle()

        assertEquals(listOf(SessionReplayMode.OFF), modes())
        assertFalse(calls[0].first.isEnabled)
        assertFalse("must be the configuration after updateConfiguration ran", calls[0].first.isMaskAllImages)
        assertNull("a disabled configuration does not initialize session replay", SessionReplay.getCurrentMode())
    }

    @Test
    fun aConfigurationChangeIsReported() {
        val impl = startedAgent()
        savedConfiguration(impl).setMaskAllUserTouches(true)

        impl.onHarvestConfigurationChanged()
        idle()

        assertEquals(listOf(SessionReplayMode.OFF), modes())
        assertTrue(calls[0].first.isMaskAllUserTouches)
    }

    @Test
    fun aNewSessionReportsItsSamplingResult() {
        val impl = startedAgent()
        savedConfiguration(impl).sampleToOff()
        impl.onHarvestConfigurationChanged() // applies it to the agent configuration
        idle()
        calls.clear()

        impl.onSessionRestarted()
        idle()

        assertEquals(listOf(SessionReplayMode.OFF), modes())
        assertTrue(calls[0].first.isEnabled)
    }

    @Test
    fun aNewSessionIsReportedEvenWhenSessionReplayIsDisabled() {
        val impl = startedAgent()

        impl.onSessionRestarted()
        idle()

        assertEquals(listOf(SessionReplayMode.OFF), modes())
        assertFalse(calls[0].first.isEnabled)
    }

    @Test
    fun aNewSessionStartedInTheForegroundReportsTheSampledMode() {
        val impl = startedAgent()
        impl.configureToSampleFull()

        impl.onSessionRestarted()
        idle()

        assertEquals(listOf(SessionReplayMode.FULL), modes())
        assertTrue(calls[0].first.isEnabled)
    }

    @Test
    fun aNewSessionStartedInTheBackgroundIsReportedAsOff() {
        val impl = startedAgent()
        impl.configureToSampleFull()

        restartSessionInTheBackground(impl)

        assertEquals("the recorder was skipped, so nothing is recording", listOf(SessionReplayMode.OFF), modes())
    }

    private fun startRecording(mode: SessionReplayMode) {
        val configuration = AgentConfiguration.getInstance()
        configuration.sessionReplayConfiguration.isEnabled = true
        val application = ApplicationProvider.getApplicationContext<Application>()
        SessionReplay.initialize(application, Handler(Looper.getMainLooper()), configuration, mode)
    }

    @Test
    fun pausingThroughTheAgentNotifiesOnceAndPausingAgainDoesNot() {
        startedAgent()
        startRecording(SessionReplayMode.FULL)

        assertTrue(AndroidAgentImpl.pauseReplay())
        assertFalse(AndroidAgentImpl.pauseReplay())
        idle()

        assertEquals(listOf(SessionReplayMode.OFF), modes())
    }

    @Test
    fun recordingFromErrorModeNotifiesFullOnce() {
        startedAgent()
        startRecording(SessionReplayMode.ERROR)

        assertTrue(AndroidAgentImpl.recordReplay())
        idle()

        assertEquals(listOf(SessionReplayMode.FULL), modes())
    }

    @Test
    fun recordingWhenAlreadyFullDoesNotNotify() {
        startedAgent()
        startRecording(SessionReplayMode.FULL)

        assertTrue(AndroidAgentImpl.recordReplay())
        idle()

        assertTrue(calls.isEmpty())
    }

    @Test
    fun recordingWhileDisabledDoesNotNotify() {
        startedAgent()

        assertFalse(AndroidAgentImpl.recordReplay())
        idle()

        assertTrue(calls.isEmpty())
    }

    @Test
    fun recordingFromOffNotifiesFullOnce() {
        startedAgent()
        startRecording(SessionReplayMode.OFF)

        assertTrue(AndroidAgentImpl.recordReplay())
        idle()

        assertEquals(listOf(SessionReplayMode.FULL), modes())
    }

    @Test
    fun recordingFromABackgroundSkippedSessionReportsWhatIsActuallyRecording() {
        // The process's only session was background-skipped, so no mode manager was ever created.
        val impl = startedAgent()
        impl.configureToSampleFull()
        restartSessionInTheBackground(impl)
        calls.clear()
        AgentConfiguration.getInstance().sessionReplayConfiguration.isEnabled = true

        val started = AndroidAgentImpl.recordReplay()
        idle()

        // Documents the known gap; expected to change if the gap is closed.
        assertFalse("observed: recordReplay() fails without a mode manager", started)
        assertTrue("observed: a failed recordReplay() does not notify", calls.isEmpty())
    }

    @Test
    fun recordingAfterABackgroundSkippedSessionThatFollowedAForegroundOneReportsOff() {
        val impl = startedAgent()
        impl.configureToSampleFull()
        impl.onSessionRestarted() // a foreground session leaves the mode manager singleton behind
        restartSessionInTheBackground(impl)
        calls.clear()
        AgentConfiguration.getInstance().sessionReplayConfiguration.isEnabled = true

        val started = AndroidAgentImpl.recordReplay()
        idle()

        // Documents the known gap; expected to change if the gap is closed.
        assertTrue("observed: recordReplay() reports success", started)
        assertEquals("observed: reported as off, since SessionReplay.initialize() never ran", listOf(SessionReplayMode.OFF), modes())
    }
}
