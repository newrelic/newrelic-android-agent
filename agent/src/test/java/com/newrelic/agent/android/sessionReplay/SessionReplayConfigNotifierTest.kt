/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class SessionReplayConfigNotifierTest {

    /** Holds posted work until the test drains it, standing in for the main looper. */
    private class QueueExecutor : Executor {
        private val queue = java.util.ArrayDeque<Runnable>()

        @Synchronized
        override fun execute(command: Runnable) {
            queue.add(command)
        }

        @Synchronized
        fun size(): Int = queue.size

        fun drain() {
            while (true) {
                val next = synchronized(this) { queue.poll() } ?: return
                next.run()
            }
        }
    }

    private class Call(val configuration: SessionReplayConfiguration, val mode: SessionReplayMode)

    private class Recorder : SessionReplayConfigListener {
        val calls = mutableListOf<Call>()

        override fun onSessionReplayConfigChanged(configuration: SessionReplayConfiguration, mode: SessionReplayMode) {
            calls.add(Call(configuration, mode))
        }
    }

    private lateinit var executor: QueueExecutor
    private lateinit var notifier: SessionReplayConfigNotifier

    @Volatile
    private var mode = SessionReplayMode.OFF

    private fun config(enabled: Boolean = true, maskAllImages: Boolean = true) =
        SessionReplayConfiguration().apply {
            setEnabled(enabled)
            setMaskAllImages(maskAllImages)
        }

    @Before
    fun setUp() {
        executor = QueueExecutor()
        mode = SessionReplayMode.OFF
        notifier = SessionReplayConfigNotifier(SessionReplayModeSource { mode }, executor)
    }

    @Test
    fun publishDeliversTheConfigurationAndModeToEveryListener() {
        val first = Recorder()
        val second = Recorder()
        notifier.add(first)
        notifier.add(second)
        mode = SessionReplayMode.FULL

        notifier.publish(config(enabled = true))
        executor.drain()

        for (recorder in listOf(first, second)) {
            assertEquals(1, recorder.calls.size)
            assertTrue(recorder.calls[0].configuration.isEnabled)
            assertEquals(SessionReplayMode.FULL, recorder.calls[0].mode)
        }
    }

    @Test
    fun publishDoesNotCallListenersOnThePublishingThread() {
        val recorder = Recorder()
        notifier.add(recorder)

        notifier.publish(config())

        assertTrue("nothing runs until the executor runs it", recorder.calls.isEmpty())
        assertEquals(1, executor.size())
    }

    @Test
    fun theListenerSeesTheConfigurationAsItWasWhenPublished() {
        val recorder = Recorder()
        notifier.add(recorder)
        val live = config(maskAllImages = true)

        notifier.publish(live)
        live.setMaskAllImages(false) // the harvest thread keeps updating the live object
        executor.drain()

        assertTrue(recorder.calls[0].configuration.isMaskAllImages)
        assertNotSame(live, recorder.calls[0].configuration)
    }

    @Test
    fun theListenerSeesTheModeAsItWasWhenPublished() {
        val recorder = Recorder()
        notifier.add(recorder)
        mode = SessionReplayMode.ERROR

        notifier.publish(config())
        mode = SessionReplayMode.FULL
        executor.drain()

        assertEquals(SessionReplayMode.ERROR, recorder.calls[0].mode)
    }

    @Test
    fun notificationsArriveInTheOrderTheyWerePublished() {
        val recorder = Recorder()
        notifier.add(recorder)

        mode = SessionReplayMode.ERROR
        notifier.publish(config())
        mode = SessionReplayMode.FULL
        notifier.publish(config())
        mode = SessionReplayMode.OFF
        notifier.publish(config())
        executor.drain()

        assertEquals(
            listOf(SessionReplayMode.ERROR, SessionReplayMode.FULL, SessionReplayMode.OFF),
            recorder.calls.map { it.mode }
        )
    }

    @Test
    fun theLastNotificationCarriesTheFinalStateWhenChangesRace() {
        val recorder = Recorder()
        notifier.add(recorder)
        val modes = SessionReplayMode.values()

        val workers = (0 until 8).map { worker ->
            thread(start = false) {
                repeat(200) { step ->
                    mode = modes[(worker + step) % modes.size]
                    notifier.publish(config())
                }
            }
        }
        workers.forEach { it.start() }
        workers.forEach { it.join() }
        val finalMode = mode
        executor.drain()

        assertEquals(8 * 200, recorder.calls.size)
        assertEquals(finalMode, recorder.calls.last().mode)
    }

    @Test
    fun aThrowingListenerDoesNotStopTheOthersOrReachTheCaller() {
        val before = Recorder()
        val after = Recorder()
        notifier.add(before)
        notifier.add(SessionReplayConfigListener { _, _ -> throw IllegalStateException("listener bug") })
        notifier.add(SessionReplayConfigListener { _, _ -> throw NoSuchMethodError("linked against an older agent") })
        notifier.add(after)

        notifier.publish(config())
        executor.drain() // must not throw

        assertEquals(1, before.calls.size)
        assertEquals(1, after.calls.size)
    }

    @Test
    fun aListenerThatRegistersLateGetsTheLatestStateOnce() {
        mode = SessionReplayMode.FULL
        notifier.publish(config(enabled = true))
        executor.drain()
        val late = Recorder()

        notifier.add(late)
        assertTrue("the replay is queued, not run inside add()", late.calls.isEmpty())
        executor.drain()

        assertEquals(1, late.calls.size)
        assertTrue(late.calls[0].configuration.isEnabled)
        assertEquals(SessionReplayMode.FULL, late.calls[0].mode)
    }

    @Test
    fun aListenerThatRegistersBeforeAnythingWasPublishedIsNotCalled() {
        val recorder = Recorder()

        notifier.add(recorder)
        executor.drain()

        assertTrue(recorder.calls.isEmpty())
    }

    @Test
    fun theReplayCarriesTheLatestStateNotAnEarlierOne() {
        mode = SessionReplayMode.ERROR
        notifier.publish(config())
        mode = SessionReplayMode.FULL
        notifier.publish(config())
        executor.drain()
        val late = Recorder()

        notifier.add(late)
        executor.drain()

        assertEquals(listOf(SessionReplayMode.FULL), late.calls.map { it.mode })
    }

    @Test
    fun aListenerAddedBetweenAPublishAndItsDeliveryHearsThatStateOnce() {
        val early = Recorder()
        notifier.add(early)
        mode = SessionReplayMode.FULL
        notifier.publish(config()) // queued, not yet delivered
        val late = Recorder()

        notifier.add(late) // queues a replay of the state above
        executor.drain()

        assertEquals(1, early.calls.size)
        assertEquals(1, late.calls.size)
        assertEquals(SessionReplayMode.FULL, late.calls[0].mode)
    }

    @Test
    fun aRemovedListenerIsNotCalledEvenForWorkAlreadyQueued() {
        val removed = Recorder()
        val kept = Recorder()
        notifier.add(removed)
        notifier.add(kept)

        notifier.publish(config())
        assertTrue(notifier.remove(removed))
        executor.drain()

        assertTrue(removed.calls.isEmpty())
        assertEquals(1, kept.calls.size)
    }

    @Test
    fun addingTheSameListenerTwiceIsANoOp() {
        val recorder = Recorder()

        assertTrue(notifier.add(recorder))
        assertFalse(notifier.add(recorder))
        notifier.publish(config())
        executor.drain()

        assertEquals(1, recorder.calls.size)
    }

    @Test
    fun nullAndUnknownListenersAreIgnored() {
        assertFalse(notifier.add(null))
        assertFalse(notifier.remove(null))
        assertFalse(notifier.remove(Recorder()))
    }

    @Test
    fun publishWithoutListenersPostsNothingButRemembersTheState() {
        mode = SessionReplayMode.ERROR

        notifier.publish(config())
        assertEquals(0, executor.size())
        val late = Recorder()
        notifier.add(late)
        executor.drain()

        assertEquals(listOf(SessionReplayMode.ERROR), late.calls.map { it.mode })
    }

    @Test
    fun resetForgetsListenersAndTheLatestState() {
        val recorder = Recorder()
        notifier.add(recorder)
        notifier.publish(config())
        executor.drain()

        notifier.reset()
        val late = Recorder()
        notifier.add(late)
        notifier.publish(config())
        executor.drain()

        assertEquals(1, recorder.calls.size)
        assertEquals(1, late.calls.size)
    }

    @Test
    fun theSnapshotKeepsTheDerivedMaskingSets() {
        val rule = SessionReplayConfiguration.CustomMaskingRule().apply {
            setIdentifier("class")
            setOperator("equals")
            setType("mask")
            setName(listOf("com.example.SecretView"))
        }
        val tagRule = SessionReplayConfiguration.CustomMaskingRule().apply {
            setIdentifier("tag")
            setOperator("equals")
            setType("mask")
            setName(listOf("secret-tag"))
        }
        val live = config().apply {
            setCustomMaskingRules(listOf(rule, tagRule))
            processCustomMaskingRules()
        }
        val recorder = Recorder()
        notifier.add(recorder)

        notifier.publish(live)
        executor.drain()

        val delivered = recorder.calls[0].configuration
        assertTrue(delivered.maskedViewClasses.contains("com.example.SecretView"))
        assertTrue(delivered.maskedViewTags.contains("secret-tag"))
        assertEquals(live.maskedViewClasses, delivered.maskedViewClasses)
        assertEquals(live.maskedViewTags, delivered.maskedViewTags)
        assertEquals(live.unmaskedViewClasses, delivered.unmaskedViewClasses)
        assertEquals(live.unmaskedViewTags, delivered.unmaskedViewTags)
    }

    @Test
    fun publishNeverThrowsEvenWhenTheModeSourceFails() {
        var failing = true
        notifier = SessionReplayConfigNotifier(
            SessionReplayModeSource {
                if (failing) throw IllegalStateException("mode source bug")
                mode
            },
            executor
        )
        val recorder = Recorder()
        notifier.add(recorder)

        notifier.publish(config()) // must not throw
        executor.drain()
        assertTrue(recorder.calls.isEmpty())

        failing = false
        mode = SessionReplayMode.FULL
        notifier.publish(config())
        executor.drain()

        assertEquals(listOf(SessionReplayMode.FULL), recorder.calls.map { it.mode })
    }

    @Test
    fun aReplayToAThrowingListenerDoesNotThrowOrBlockOthers() {
        notifier.publish(config())
        executor.drain()
        val recorder = Recorder()

        notifier.add(SessionReplayConfigListener { _, _ -> throw IllegalStateException("listener bug") })
        notifier.add(recorder)
        executor.drain() // must not throw

        assertEquals(1, recorder.calls.size)
    }

    @Test
    fun aSecondPublishWaitsUntilTheFirstHasReadTheModeAndPosted() {
        val calls = AtomicInteger()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        notifier = SessionReplayConfigNotifier(
            SessionReplayModeSource {
                if (calls.getAndIncrement() == 0) {
                    entered.countDown()
                    release.await(5, TimeUnit.SECONDS)
                }
                mode
            },
            executor
        )
        notifier.add(Recorder())

        val first = thread { notifier.publish(config()) }
        assertTrue("the first publish reads the mode", entered.await(5, TimeUnit.SECONDS))
        val second = thread { notifier.publish(config()) }
        second.join(200)

        assertTrue("the second publish is still blocked on the lock", second.isAlive)
        assertEquals(1, calls.get())
        assertEquals(0, executor.size())

        release.countDown()
        first.join(5000)
        second.join(5000)

        assertEquals(2, executor.size())
    }
}
