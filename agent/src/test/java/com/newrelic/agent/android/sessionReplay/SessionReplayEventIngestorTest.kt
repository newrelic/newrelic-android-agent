/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

class SessionReplayEventIngestorTest {

    /** Holds posted work until the test drains it, standing in for the ingest thread. */
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

    private class Sink : SessionReplayIngestSink {
        val appended = mutableListOf<List<String>>()
        var result = true

        override fun append(lines: List<String>): Boolean {
            appended.add(lines)
            return result
        }
    }

    private lateinit var executor: QueueExecutor
    private lateinit var sink: Sink
    private var parseFailures = 0
    private var mode: SessionReplayMode? = SessionReplayMode.FULL
    private lateinit var ingestor: SessionReplayEventIngestor

    @Before
    fun setUp() {
        executor = QueueExecutor()
        sink = Sink()
        parseFailures = 0
        mode = SessionReplayMode.FULL
        ingestor = SessionReplayEventIngestor(
            { mode },
            executor,
            sink,
            { parseFailures++ },
        )
    }

    // ---- gate ----

    @Test
    fun gate_nullOffAndErrorModesAreDisabled() {
        for (closed in listOf(null, SessionReplayMode.OFF, SessionReplayMode.ERROR)) {
            mode = closed
            assertEquals("mode=$closed", SessionReplayIngestStatus.DROPPED_DISABLED, ingestor.record("[]"))
        }
        assertEquals("nothing is queued for a disabled gate", 0, executor.size())
    }

    @Test
    fun gate_fullModeIsAccepted() {
        mode = SessionReplayMode.FULL
        assertEquals(SessionReplayIngestStatus.ACCEPTED, ingestor.record("[]"))
    }

    @Test
    fun gate_disabledWinsOverEmptyAndUnencodable() {
        mode = SessionReplayMode.OFF
        assertEquals(SessionReplayIngestStatus.DROPPED_DISABLED, ingestor.record(""))
        assertEquals(SessionReplayIngestStatus.DROPPED_DISABLED, ingestor.record(null))
        assertEquals(SessionReplayIngestStatus.DROPPED_DISABLED, ingestor.record("[\uD800]"))
    }

    // ---- validation ----

    @Test
    fun emptyNullAndBlankAreInvalid() {
        assertEquals(SessionReplayIngestStatus.DROPPED_INVALID, ingestor.record(null))
        assertEquals(SessionReplayIngestStatus.DROPPED_INVALID, ingestor.record(""))
        assertEquals(SessionReplayIngestStatus.DROPPED_INVALID, ingestor.record("   \n\t"))
        assertEquals(0, executor.size())
    }

    @Test
    fun emptyArrayIsAcceptedAndAppendsNothing() {
        assertEquals(SessionReplayIngestStatus.ACCEPTED, ingestor.record("[]"))
        executor.drain()
        assertTrue(sink.appended.isEmpty())
        assertEquals(0, parseFailures)
    }

    @Test
    fun thereIsNoSizeCap() {
        // the reporter's 1 MB limit applies after gzip, at upload time; nothing is capped when a batch comes in
        assertEquals(SessionReplayIngestStatus.ACCEPTED, ingestor.record("a".repeat(2_000_000)))
        assertEquals(SessionReplayIngestStatus.ACCEPTED, ingestor.record("中".repeat(1_000_000)))
    }

    @Test
    fun textThatCannotBeEncodedAsUtf8IsInvalid() {
        for (bad in listOf("[\uD800]", "[\uDC00]", "a\uD83D", "\uDE00\uD83D", "[{\"x\":\"\uD83D\"}]")) {
            assertEquals("bad=${bad.map { it.code.toString(16) }}", SessionReplayIngestStatus.DROPPED_INVALID, ingestor.record(bad))
        }
        assertEquals("nothing is queued for an unencodable batch", 0, executor.size())
    }

    @Test
    fun wellFormedTextIsAcceptedIncludingSurrogatePairs() {
        assertEquals(SessionReplayIngestStatus.ACCEPTED, ingestor.record("""[{"t":"aé中"}]"""))
        assertEquals(SessionReplayIngestStatus.ACCEPTED, ingestor.record("[{\"t\":\"😀\"}]"))
    }

    @Test
    fun canEncodeAsUtf8_followsSurrogateRules() {
        for (good in listOf("", "abc", "é", "中文", "😀", "a😀b")) {
            assertTrue("good=$good", SessionReplayEventIngestor.canEncodeAsUtf8(good))
        }
        for (bad in listOf("\uD800", "\uDC00", "\uDC00\uD800", "a\uD800b", "\uD800𐀀", "x\uD83D")) {
            assertEquals("bad=${bad.map { it.code.toString(16) }}", false, SessionReplayEventIngestor.canEncodeAsUtf8(bad))
        }
    }

    // ---- off the calling thread ----

    @Test
    fun record_doesNotParseOrWriteOnTheCallingThread() {
        assertEquals(SessionReplayIngestStatus.ACCEPTED, ingestor.record("not json"))
        assertEquals("a malformed batch is not parsed until the worker runs", 0, parseFailures)

        val status = ingestor.record("""[{"type":4}]""")

        assertEquals(SessionReplayIngestStatus.ACCEPTED, status)
        assertTrue("nothing is written until the worker runs", sink.appended.isEmpty())
        assertEquals(2, executor.size())

        executor.drain()
        assertEquals(1, parseFailures)
        assertEquals(1, sink.appended.size)
    }

    @Test
    fun batchesAreAppendedInTheOrderTheyWereRecorded() {
        ingestor.record("""[{"type":4,"n":1}]""")
        ingestor.record("""[{"type":2,"n":2},{"type":3,"n":3}]""")
        executor.drain()

        assertEquals(
            listOf(listOf("""{"type":4,"n":1}"""), listOf("""{"type":2,"n":2}""", """{"type":3,"n":3}""")),
            sink.appended,
        )
    }

    // ---- what is stored ----

    @Test
    fun eventsAreWrittenCompactWithTheirValuesIntact() {
        val meta = """{"type":4,"data":{"href":"https://x","width":1080,"height":1920},"timestamp":1700000000000}"""
        val diff = """{"type":3,"data":{"source":0,"x":1.50},"timestamp":1700000000001}"""
        val spaced = """{ "type" : 3 , "data" : { "source" : 0 } }"""
        val spacedCompact = """{"type":3,"data":{"source":0}}"""
        ingestor.record("[ $meta , $diff , $spaced ]")
        executor.drain()

        assertEquals(listOf(listOf(meta, diff, spacedCompact)), sink.appended)
    }

    @Test
    fun batchesThatAreNotAnArrayOfObjectsAreDroppedWholeAndCounted() {
        for (bad in listOf("not json", """{"a":1}""", "[1,2]", """[{"type":4}, 5]""")) {
            assertEquals("bad=$bad", SessionReplayIngestStatus.ACCEPTED, ingestor.record(bad))
        }
        executor.drain()

        assertTrue("a bad batch writes nothing, not even its good events", sink.appended.isEmpty())
        assertEquals(4, parseFailures)

        ingestor.record("""[{"type":4}]""")
        executor.drain()
        assertEquals("the next good batch still lands", listOf(listOf("""{"type":4}""")), sink.appended)
    }

    @Test
    fun aSinkThatCannotWriteDoesNotBreakTheIngestor() {
        sink.result = false
        ingestor.record("""[{"type":4}]""")
        executor.drain()

        sink.result = true
        ingestor.record("""[{"type":2}]""")
        executor.drain()

        assertEquals(2, sink.appended.size)
        assertEquals(0, parseFailures)
    }

    @Test
    fun anExecutorThatRejectsWorkGivesDisabledAndDoesNotThrow() {
        val rejecting = SessionReplayEventIngestor(
            { SessionReplayMode.FULL },
            Executor { throw java.util.concurrent.RejectedExecutionException("shut down") },
            sink,
            { parseFailures++ },
        )

        assertEquals(SessionReplayIngestStatus.DROPPED_DISABLED, rejecting.record("[]"))
    }

    // ---- the production executor ----

    @Test
    fun singleThreadExecutor_runsTasksInOrderOnTheIngestThread() {
        val executor = SessionReplayEventIngestor.singleThreadExecutor()
        val order = java.util.Collections.synchronizedList(mutableListOf<Int>())
        val threadNames = java.util.Collections.synchronizedSet(mutableSetOf<String>())
        val done = CountDownLatch(3)

        for (i in 1..3) {
            executor.execute {
                order.add(i)
                threadNames.add(Thread.currentThread().name)
                done.countDown()
            }
        }

        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertEquals(listOf(1, 2, 3), order)
        assertEquals(setOf("SessionReplayIngest"), threadNames)
    }
}
