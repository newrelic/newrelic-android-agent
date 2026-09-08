/*
 * Copyright (c) 2026-present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.webView

import android.webkit.JavascriptInterface

import com.newrelic.agent.android.logging.AgentLog
import com.newrelic.agent.android.logging.AgentLogManager
import com.newrelic.agent.android.metric.MetricNames
import com.newrelic.agent.android.stats.StatsEngine

import org.json.JSONArray
import org.json.JSONObject

import java.util.TreeMap

/**
 * JS-callable bridge injected into instrumented WebViews. Reports whether the currently-loaded
 * page is running the New Relic Browser (JS) agent, and — for the NR-489843 POC — receives
 * session replay harvest payloads siphoned from the injected browser agent's beforeHarvest hook.
 *
 * Every method here runs on the WebView's JS thread, not the UI thread. They log only; none of
 * them may touch the WebView.
 */
class WebViewJSInterface {

    companion object {
        const val INTERFACE_NAME = "NRWebViewBridge"

        private const val LOG_TAG = "[NR-WV-SR]"

        /** Kept under logcat's ~4KB per-line limit, with room for the prefix. */
        private const val CHUNK_SIZE = 3500

        private val log: AgentLog = AgentLogManager.getAgentLog()
    }

    @JavascriptInterface
    fun reportBrowserAgentDetected(detected: Boolean) {
        if (detected) {
            StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_MOBILE_ANDROID_WEBVIEW_BROWSER_AGENT_DETECTED)
        }
    }

    /**
     * Reports why the POC did not inject a browser agent into this page. Logged rather than
     * recorded as a supportability metric: phase 1 validation reads logcat, and a new metric
     * would mean a new constant in agent-core's MetricNames for no phase-1 benefit.
     *
     * Expected reasons: 'existing-agent', 'loader-load-failed', 'beforeHarvest-unavailable'.
     */
    @JavascriptInterface
    fun reportInjectionSkipped(reason: String?) {
        log.warn("$LOG_TAG injection skipped: $reason")
    }

    /**
     * Receives one session replay harvest payload, wrapped in a descriptor envelope built by the
     * injected hook. The envelope carries shape information alongside the serialized payload
     * because the payload's runtime type is not known in advance — the browser agent compresses
     * replay payloads in normal operation, and whether observation mode hands over the
     * pre-compression object or a binary blob is exactly what this POC is measuring.
     */
    @JavascriptInterface
    fun reportSessionReplayPayload(envelopeJson: String?) {
        try {
            if (envelopeJson.isNullOrEmpty()) {
                log.warn("$LOG_TAG received an empty envelope from the WebView")
                return
            }

            val envelope = JSONObject(envelopeJson)
            val serialized = envelope.optString("serialized", "")

            log.info(buildSummary(envelope, serialized))

            if (log.level >= AgentLog.DEBUG && serialized.isNotEmpty()) {
                logChunked(serialized)
            }
        } catch (t: Throwable) {
            // Log and swallow: nothing may escape into the WebView's JS thread.
            log.error("$LOG_TAG failed to handle a WebView session replay payload", t)
        }
    }

    /**
     * One-line summary of a harvest. Shape fields are always present; event statistics are
     * best-effort and omitted rather than faked when the body is not an rrweb event array —
     * in that case `shape` and `bodyShape` explain why.
     */
    private fun buildSummary(envelope: JSONObject, serialized: String): String {
        val sb = StringBuilder(LOG_TAG)
        sb.append(' ').append(envelope.optString("feature", "?"))
        sb.append(" url=").append(envelope.optString("url", "?"))
        sb.append(" shape=").append(envelope.optString("shape", "?"))
        sb.append(" keys=").append(envelope.opt("keys")?.toString() ?: "null")
        sb.append(" bodyShape=").append(envelope.optString("bodyShape", "?"))
        sb.append(" chars=").append(serialized.length)
        appendEventStats(sb, serialized)
        return sb.toString()
    }

    /**
     * Appends `events`, `types` and `ts` when the payload body turns out to be an array of
     * rrweb-shaped events (either a real JSON array or a JSON string containing one). Any other
     * shape leaves the summary untouched.
     */
    private fun appendEventStats(sb: StringBuilder, serialized: String) {
        try {
            if (serialized.isEmpty()) {
                return
            }

            val body = JSONObject(serialized).opt("body")
            val events: JSONArray = when (body) {
                is JSONArray -> body
                is String -> try {
                    JSONArray(body)
                } catch (e: Throwable) {
                    return
                }
                else -> return
            }

            val types = TreeMap<Int, Int>()
            var first = Long.MAX_VALUE
            var last = Long.MIN_VALUE

            for (i in 0 until events.length()) {
                val event = events.optJSONObject(i) ?: continue
                val type = event.optInt("type", -1)
                types[type] = (types[type] ?: 0) + 1

                val ts = event.optLong("timestamp", 0L)
                if (ts > 0L) {
                    if (ts < first) first = ts
                    if (ts > last) last = ts
                }
            }

            sb.append(" events=").append(events.length())
            sb.append(" types=").append(types)
            if (first != Long.MAX_VALUE) {
                sb.append(" ts=").append(first).append("->").append(last)
            }
        } catch (ignored: Throwable) {
            // Not an rrweb event array. shape/bodyShape in the summary already say so.
        }
    }

    /** Splits the payload across logcat lines, each tagged with its index so a capture reassembles in order. */
    private fun logChunked(serialized: String) {
        val total = (serialized.length + CHUNK_SIZE - 1) / CHUNK_SIZE
        var index = 0
        var offset = 0
        while (offset < serialized.length) {
            val end = minOf(offset + CHUNK_SIZE, serialized.length)
            index++
            log.debug("$LOG_TAG chunk $index/$total ${serialized.substring(offset, end)}")
            offset = end
        }
    }
}
