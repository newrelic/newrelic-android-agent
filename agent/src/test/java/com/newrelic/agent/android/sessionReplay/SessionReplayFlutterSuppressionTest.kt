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
import curtains.Curtains
import curtains.OnRootViewsChangedListener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.`when` as whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowLooper

/**
 * Flutter draws its own surface and hands session replay the rrweb events, so the agent must not
 * capture natively: no activity callbacks and no draw or touch interceptors. Everything else
 * (mode management, pause and record) keeps working.
 */
@RunWith(RobolectricTestRunner::class)
class SessionReplayFlutterSuppressionTest {

    private lateinit var application: Application
    private lateinit var shadowLooper: ShadowLooper

    @Before
    fun setUp() {
        val real = ApplicationProvider.getApplicationContext<Application>()
        // a mock, so the test can see whether the agent registered activity lifecycle callbacks
        application = Mockito.mock(Application::class.java)
        whenever(application.applicationContext).thenReturn(application)
        whenever(application.resources).thenReturn(real.resources)
        whenever(application.cacheDir).thenReturn(real.cacheDir)

        shadowLooper = Shadows.shadowOf(Looper.getMainLooper())
        shadowLooper.pause()
        Curtains.onRootViewsChangedListeners.clear()
        SessionReplay.deInitialize()
    }

    @After
    fun tearDown() {
        SessionReplay.deInitialize()
        shadowLooper.idle()
        Curtains.onRootViewsChangedListeners.clear()
    }

    private fun configuration(framework: ApplicationFramework) = AgentConfiguration().apply {
        applicationFramework = framework
    }

    private fun initialize(framework: ApplicationFramework, mode: SessionReplayMode = SessionReplayMode.FULL) {
        SessionReplay.initialize(application, Handler(Looper.getMainLooper()), configuration(framework), mode)
    }

    @Test
    fun flutter_doesNotRegisterActivityLifecycleCallbacks() {
        initialize(ApplicationFramework.Flutter)

        Mockito.verify(application, never()).registerActivityLifecycleCallbacks(any())
    }

    @Test
    fun native_registersActivityLifecycleCallbacks() {
        initialize(ApplicationFramework.Native)

        Mockito.verify(application, times(1)).registerActivityLifecycleCallbacks(any())
    }

    @Test
    fun otherHybridFrameworksStillCaptureNatively() {
        initialize(ApplicationFramework.ReactNative)

        Mockito.verify(application, times(1)).registerActivityLifecycleCallbacks(any())
    }

    @Test
    fun flutter_startRecordingAttachesNoViewInterceptors() {
        initialize(ApplicationFramework.Flutter)

        SessionReplay.startRecording(SessionReplayMode.FULL)
        shadowLooper.idle()

        assertEquals(
            "no Curtains listener means no draw or touch interceptor is attached",
            0,
            Curtains.onRootViewsChangedListeners.size,
        )
    }

    @Test
    fun native_startRecordingAttachesTheCurtainsListener() {
        initialize(ApplicationFramework.Native)

        SessionReplay.startRecording(SessionReplayMode.FULL)
        shadowLooper.idle()

        assertEquals(1, Curtains.onRootViewsChangedListeners.size)
    }

    @Test
    fun flutter_pauseAndRecordStillChangeTheMode() {
        initialize(ApplicationFramework.Flutter)
        assertEquals(SessionReplayMode.FULL, SessionReplay.getCurrentMode())

        assertTrue(SessionReplay.pauseReplay())
        assertEquals(SessionReplayMode.OFF, SessionReplay.getCurrentMode())

        assertTrue(SessionReplay.transitionToMode(SessionReplayMode.FULL, "test"))
        assertEquals(SessionReplayMode.FULL, SessionReplay.getCurrentMode())
    }

    @Test
    fun theRuleIsReEvaluatedOnEverySessionStart() {
        initialize(ApplicationFramework.Flutter)
        SessionReplay.deInitialize()
        initialize(ApplicationFramework.Native)

        Mockito.verify(application, times(1)).registerActivityLifecycleCallbacks(any())
    }

    @Test
    fun flutter_stopRecordingLeavesOtherCurtainsListenersAlone() {
        // Curtains is shared with the host app and its libraries; Flutter never added a listener, so
        // pausing must not clear theirs
        val hostListener = OnRootViewsChangedListener { _, _ -> }
        Curtains.onRootViewsChangedListeners.add(hostListener)
        initialize(ApplicationFramework.Flutter)

        assertTrue(SessionReplay.pauseReplay())
        shadowLooper.idle()

        assertTrue(Curtains.onRootViewsChangedListeners.contains(hostListener))
    }

    @Test
    fun native_stopRecordingClearsTheCurtainsListeners() {
        val listener = OnRootViewsChangedListener { _, _ -> }
        Curtains.onRootViewsChangedListeners.add(listener)
        initialize(ApplicationFramework.Native)

        assertTrue(SessionReplay.pauseReplay())
        shadowLooper.idle()

        assertEquals(0, Curtains.onRootViewsChangedListeners.size)
    }

    @Test
    fun flutter_doesNotAllocateTheNativeCaptureObjects() {
        initialize(ApplicationFramework.Flutter)
        SessionReplay.initSessionReplay(SessionReplayMode.FULL)

        assertNull(staticField("sessionReplayActivityLifecycleCallbacks"))
        assertNull(staticField("viewDrawInterceptor"))
    }

    @Test
    fun native_allocatesTheNativeCaptureObjects() {
        initialize(ApplicationFramework.Native)
        SessionReplay.initSessionReplay(SessionReplayMode.FULL)

        assertNotNull(staticField("sessionReplayActivityLifecycleCallbacks"))
        assertNotNull(staticField("viewDrawInterceptor"))
    }

    private fun staticField(name: String): Any? =
        SessionReplay::class.java.getDeclaredField(name).apply { isAccessible = true }.get(null)
}
