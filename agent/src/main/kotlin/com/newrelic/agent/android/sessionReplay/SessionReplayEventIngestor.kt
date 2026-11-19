/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay

import com.google.gson.JsonParser
import com.newrelic.agent.android.logging.AgentLogManager
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/** The recording mode the ingest gate decides on. Null means session replay is not running. */
internal fun interface SessionReplayIngestModeSource {
    fun currentMode(): SessionReplayMode?
}

/** Where parsed events go: one JSON object per line, in order. Returns false if they could not be written. */
internal fun interface SessionReplayIngestSink {
    fun append(lines: List<String>): Boolean
}

/**
 * Takes rrweb events that were captured and encoded outside the agent (the Flutter plugin) and
 * stores them in the session replay buffer.
 *
 * [record] runs on the caller's thread and does only the cheap checks: the gate, the empty check
 * and the UTF-8 size. The batch is parsed and written on [executor]. That executor must run tasks
 * one at a time in submission order, because Dart's order has to survive: each Meta is
 * immediately followed by its FullSnapshot.
 */
internal class SessionReplayEventIngestor(
    private val modeSource: SessionReplayIngestModeSource,
    private val executor: Executor,
    private val sink: SessionReplayIngestSink,
    private val onParseFailed: Runnable,
) {

    /** @return one of the [SessionReplayIngestStatus] values */
    fun record(eventsJSON: String?): String {
        val mode = modeSource.currentMode()
        if (mode == null || mode == SessionReplayMode.OFF || mode == SessionReplayMode.ERROR) {
            return SessionReplayIngestStatus.DROPPED_DISABLED
        }
        if (eventsJSON == null || eventsJSON.isBlank()) {
            return SessionReplayIngestStatus.DROPPED_INVALID
        }
        if (utf8Length(eventsJSON) > MAX_BATCH_BYTES) {
            return SessionReplayIngestStatus.DROPPED_INVALID
        }
        return try {
            executor.execute { append(eventsJSON) }
            SessionReplayIngestStatus.ACCEPTED
        } catch (e: Exception) {
            // this is a public API called from the plugin; nothing may escape into the host app
            log("could not queue a batch: $e")
            SessionReplayIngestStatus.DROPPED_DISABLED
        }
    }

    private fun append(eventsJSON: String) {
        try {
            val lines = parseLines(eventsJSON)
            if (lines == null) {
                // a batch that does not parse would be a bug in our encoder, so it gets no status
                log("dropped a batch that is not a JSON array of objects")
                onParseFailed.run()
                return
            }
            if (lines.isEmpty()) return
            if (!sink.append(lines)) {
                AgentLogManager.getAgentLog().debug("SessionReplay: ingested events were not written, there is no working file")
            }
        } catch (e: Exception) {
            log("could not store a batch: $e")
        }
    }

    /**
     * The events as Dart sent them, one compact JSON object each. They are not decoded into the
     * agent's own event types. Returns null if the text is not a JSON array whose elements are all objects.
     */
    private fun parseLines(eventsJSON: String): List<String>? {
        val root = try {
            JsonParser.parseString(eventsJSON)
        } catch (e: Exception) {
            return null
        }
        if (!root.isJsonArray) return null
        val elements = root.asJsonArray
        val lines = ArrayList<String>(elements.size())
        for (element in elements) {
            if (!element.isJsonObject) return null
            lines.add(element.toString())
        }
        return lines
    }

    private fun log(message: String) {
        AgentLogManager.getAgentLog().error("SessionReplay: $message")
    }

    companion object {
        /** The most UTF-8 bytes one call may carry. */
        const val MAX_BATCH_BYTES = 1_000_000

        /** One daemon thread, so batches are parsed and appended in the order they were recorded. */
        @JvmStatic
        fun singleThreadExecutor(): Executor =
            Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "SessionReplayIngest").apply { isDaemon = true }
            }

        /**
         * The UTF-8 size of [text], counted without allocating the byte array, because [record]
         * runs on the main thread and the text can be 1 MB. An unpaired surrogate is counted as 3
         * bytes, which over-estimates by 2 (the encoder writes one byte for it).
         */
        @JvmStatic
        fun utf8Length(text: CharSequence): Int {
            var bytes = 0
            var i = 0
            while (i < text.length) {
                val c = text[i]
                when {
                    c.code < 0x80 -> bytes += 1
                    c.code < 0x800 -> bytes += 2
                    Character.isHighSurrogate(c) && i + 1 < text.length && Character.isLowSurrogate(text[i + 1]) -> {
                        bytes += 4
                        i++
                    }
                    else -> bytes += 3
                }
                i++
            }
            return bytes
        }
    }
}
