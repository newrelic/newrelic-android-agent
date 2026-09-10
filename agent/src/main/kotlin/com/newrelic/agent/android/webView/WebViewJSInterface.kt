/*
 * Copyright (c) 2026-present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.webView

import android.webkit.JavascriptInterface
import android.webkit.WebView

import com.newrelic.agent.android.logging.AgentLog
import com.newrelic.agent.android.logging.AgentLogManager
import com.newrelic.agent.android.metric.MetricNames
import com.newrelic.agent.android.stats.StatsEngine

/**
 * JS-callable bridge injected into instrumented WebViews. Reports whether the currently-loaded
 * page is running the New Relic Browser (JS) agent, and — for the NR-489843 POC — receives
 * session replay harvest payloads siphoned from the injected browser agent's beforeHarvest hook.
 *
 * Every method here runs on the WebView's JS thread, not the UI thread. So none of them may touch
 * the WebView, and none may do real work: the replay path hands its payload straight to the merge
 * executor, and the rest only log.
 */
class WebViewJSInterface @JvmOverloads constructor(webView: WebView? = null) {

    /**
     * Merge state for the WebView this bridge was injected into, or null when the bridge was built
     * without one (unit tests, and the detection-only path). The bridge being per-WebView is what
     * makes this work without a registry: the instance *is* the key.
     */
    val replayState: WebViewReplayState? = webView?.let { WebViewReplayState(it) }

    companion object {
        const val INTERFACE_NAME = "NRWebViewBridge"

        private const val LOG_TAG = "[NR-WV-SR]"

        /** Skip reason that represents correct behavior rather than a failure. */
        private const val SKIP_REASON_EXISTING_AGENT = "existing-agent"

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
        // 'existing-agent' is a designed-correct outcome, not a failure: the page runs its own
        // browser agent and we deliberately stay out. Warning on it would cry wolf on every page
        // load of a legitimately-instrumented customer page. The other two reasons are real
        // failures worth a warning.
        if (SKIP_REASON_EXISTING_AGENT == reason) {
            log.info("$LOG_TAG injection skipped: $reason")
        } else {
            log.warn("$LOG_TAG injection skipped: $reason")
        }
    }

    /**
     * One-shot proof-of-life from the injected hook, reported on its first invocation for any
     * feature before the session_replay filter is applied.
     *
     * This exists to separate two failure modes that are otherwise indistinguishable by silence:
     * session replay never harvesting at all (no RUM response to carry an entitlement/sampling
     * decision), versus the hook receiving a wrapper whose shape is not `{feature, payload}`.
     */
    @JavascriptInterface
    fun reportHarvestObserved(detail: String?) {
        log.info("$LOG_TAG $detail")
    }

    /**
     * Receives one session replay harvest's rrweb event array from the injected browser agent and
     * merges it into the native replay stream. The only path that carries replay data.
     *
     * A second method used to accept the same payload wrapped in a descriptor envelope, to answer
     * what shape observation mode hands over. It is gone: the hook's one-shot
     * `shape=… bodyShape=…` line answers that on every first replay harvest, without a second
     * `@JavascriptInterface` method exposed to whatever else the page is running.
     *
     * @param payload the events as plain JSON text. The browser agent build hands over an
     *                uncompressed body, so there is no decode step; the injected hook refuses to
     *                forward a binary body rather than mangling one into this parameter.
     */
    @JavascriptInterface
    fun reportSessionReplayEvents(payload: String?) {
        try {
            val state = replayState
            if (state == null) {
                log.debug("$LOG_TAG no replay state on this bridge; ignoring an event batch")
                return
            }
            if (payload.isNullOrEmpty()) {
                log.warn("$LOG_TAG received an empty event batch from the WebView")
                return
            }
            // Handed straight off: nothing beyond this point may run on the WebView's JS thread.
            // The merge thread is shared with the document re-attach path, which keeps both in a
            // single total order per WebView.
            WebViewReplayState.post { mergeBatch(state, payload) }
        } catch (t: Throwable) {
            // Log and swallow: nothing may escape into the WebView's JS thread.
            log.error("$LOG_TAG failed to accept a WebView session replay event batch", t)
        }
    }

    /** Merge executor thread. */
    private fun mergeBatch(state: WebViewReplayState, jsonText: String) {
        try {
            val events = WebViewReplayMerger.extractEvents(jsonText)
            if (events == null) {
                StatsEngine.SUPPORTABILITY.inc(
                    MetricNames.SUPPORTABILITY_MOBILE_ANDROID_WEBVIEW_REPLAY_DECODE_FAILED
                )
                log.warn(
                    "$LOG_TAG batch is not an rrweb event array " +
                        "(chars=${jsonText.length}, head=${jsonText.take(120)})"
                )
                return
            }

            // INFO with the type histogram: this is the line that says whether the browser agent is
            // producing full snapshots at all. A batch that is all type 3 means there is nothing to
            // graft, which renders as an empty iframe for reasons that have nothing to do with the
            // merge logic.
            val types = sortedMapOf<Int, Int>()
            for (i in 0 until events.size()) {
                val e = events.get(i)
                if (e != null && e.isJsonObject) {
                    val t = WebViewReplayMerger.typeOf(e.asJsonObject)
                    types[t] = (types[t] ?: 0) + 1
                }
            }
            log.info(
                "$LOG_TAG BATCH events=${events.size()} types=$types chars=${jsonText.length}"
            )

            for (i in 0 until events.size()) {
                val element = events.get(i)
                if (element == null || !element.isJsonObject) {
                    // One malformed event must not cost the whole harvest.
                    continue
                }
                state.processEvent(element.asJsonObject)
            }
        } catch (t: Throwable) {
            log.error("$LOG_TAG failed to merge a WebView session replay event batch", t)
        }
    }
}
