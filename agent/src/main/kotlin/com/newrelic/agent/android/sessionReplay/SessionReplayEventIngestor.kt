/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay

import com.google.gson.JsonParser
import com.newrelic.agent.android.logging.AgentLogManager
import com.newrelic.agent.android.util.Constants
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/** The recording mode the ingest gate decides on. Null means session replay is not running. */
internal fun interface SessionReplayIngestModeSource {
    fun currentMode(): SessionReplayMode?
}

/** Where the event lines go, in order. Returns false if they could not be written. */
internal fun interface SessionReplayIngestSink {
    fun append(lines: List<String>): Boolean
}

/**
 * Whether a harvest is reading the working file right now. This is the same flag native frames
 * use: anything written between the harvest's read and its clear would be wiped by the clear.
 */
internal fun interface SessionReplayIngestHarvestGuard {
    fun isHarvesting(): Boolean
}

/**
 * Takes rrweb events that were captured and encoded outside the agent (the Flutter plugin) and
 * stores them in the session replay buffer.
 *
 * A batch is a JSON array of rrweb events in play order. The events are kept as Dart sent them;
 * they are not decoded into the agent's own event types.
 *
 * [record] runs on the caller's thread and does only the cheap checks: the gate, the empty check
 * and the UTF-8 check. There is no size cap: the reporter's 1 MB limit applies after gzip, when
 * the upload is built. The batch is parsed and written on [executor]. That executor must run tasks
 * one at a time in submission order, because Dart's order has to survive: each Meta is
 * immediately followed by its FullSnapshot.
 *
 * While a harvest is running ([harvestGuard]), parsed batches are held in memory instead of being
 * written, as native frames are. They are written in order by [flushAfterHarvest]. The holding
 * happens on the ingest thread, and so does the flush, so a batch that arrives after the harvest
 * can never be written ahead of one that was held.
 */
internal class SessionReplayEventIngestor(
    private val modeSource: SessionReplayIngestModeSource,
    private val executor: Executor,
    private val sink: SessionReplayIngestSink,
    private val harvestGuard: SessionReplayIngestHarvestGuard,
    private val onParseFailed: Runnable,
) {

    // Only touched on the ingest thread.
    private val held = ArrayList<List<String>>()
    private var holding = false

    /** @return one of the [Constants.SessionReplay] `INGEST_STATUS_*` values */
    fun record(events: String?): String {
        val mode = modeSource.currentMode()
        if (mode == null || mode == SessionReplayMode.OFF || mode == SessionReplayMode.ERROR) {
            return Constants.SessionReplay.INGEST_STATUS_DROPPED_DISABLED
        }
        if (events == null || events.isBlank()) {
            return Constants.SessionReplay.INGEST_STATUS_DROPPED_INVALID
        }
        if (!canEncodeAsUtf8(events)) {
            return Constants.SessionReplay.INGEST_STATUS_DROPPED_INVALID
        }
        return try {
            executor.execute { append(events) }
            Constants.SessionReplay.INGEST_STATUS_ACCEPTED
        } catch (e: Exception) {
            // this is a public API called from the plugin; nothing may escape into the host app
            log("could not queue a batch: $e")
            Constants.SessionReplay.INGEST_STATUS_DROPPED_DISABLED
        }
    }

    private fun append(events: String) {
        try {
            val lines = parseLines(events)
            if (lines == null) {
                // a batch that does not parse would be a bug in our encoder, so it gets no status
                log("dropped a batch that is not a JSON array of objects")
                onParseFailed.run()
                return
            }
            if (lines.isEmpty()) return
            write(lines)
        } catch (e: Exception) {
            log("could not store a batch: $e")
        }
    }

    /** Writes [lines], or holds them if a harvest is running or earlier batches are still held. */
    private fun write(lines: List<String>) {
        if (holding || harvestGuard.isHarvesting()) {
            holding = true
            held.add(lines)
            return
        }
        writeNow(lines)
    }

    private fun writeNow(lines: List<String>) {
        if (!sink.append(lines)) {
            AgentLogManager.getAgentLog().debug("SessionReplay: ingested events were not written, there is no working file")
        }
    }

    /**
     * Call when a harvest has finished reading the file. Writes the batches that were held, in the
     * order they arrived, on the ingest thread. Batches already queued behind this call are still
     * held until it runs, so they stay behind the held ones.
     */
    fun flushAfterHarvest() {
        try {
            executor.execute { flushHeld() }
        } catch (e: Exception) {
            log("could not queue the flush after a harvest: $e")
        }
    }

    private fun flushHeld() {
        try {
            for (lines in held) writeNow(lines)
        } catch (e: Exception) {
            log("could not write held batches: $e")
        } finally {
            held.clear()
            holding = false
        }
    }

    /**
     * The events as Dart sent them, one compact JSON object each. Returns null if the text is not a
     * JSON array whose elements are all objects, so a bad batch is dropped whole.
     */
    private fun parseLines(events: String): List<String>? {
        val root = try {
            JsonParser.parseString(events)
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
        /** One daemon thread, so batches are parsed and appended in the order they were recorded. */
        @JvmStatic
        fun singleThreadExecutor(): Executor =
            Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "SessionReplayIngest").apply { isDaemon = true }
            }

        /**
         * Whether [text] can be encoded as UTF-8: every high surrogate is followed by a low one, and
         * no low surrogate stands alone. Walks the chars without allocating, because [record] runs
         * on the main thread and the text can be large.
         */
        @JvmStatic
        fun canEncodeAsUtf8(text: CharSequence): Boolean {
            var i = 0
            while (i < text.length) {
                val c = text[i]
                if (Character.isHighSurrogate(c)) {
                    if (i + 1 >= text.length || !Character.isLowSurrogate(text[i + 1])) return false
                    i++
                } else if (Character.isLowSurrogate(c)) {
                    return false
                }
                i++
            }
            return true
        }
    }
}
