/*
 * Copyright (c) 2026-present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.webView

import android.webkit.JavascriptInterface

import com.newrelic.agent.android.metric.MetricNames
import com.newrelic.agent.android.stats.StatsEngine

/**
 * JS-callable bridge injected into instrumented WebViews to report whether the
 * currently-loaded page is running the New Relic Browser (JS) agent.
 */
class WebViewJSInterface {

    companion object {
        const val INTERFACE_NAME = "NRWebViewBridge"
    }

    @JavascriptInterface
    fun reportBrowserAgentDetected(detected: Boolean) {
        if (detected) {
            StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_MOBILE_ANDROID_WEBVIEW_BROWSER_AGENT_DETECTED)
        }
    }
}
