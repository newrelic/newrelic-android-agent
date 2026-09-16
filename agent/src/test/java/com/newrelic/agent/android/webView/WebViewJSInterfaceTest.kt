package com.newrelic.agent.android.webView

import com.newrelic.agent.android.metric.MetricNames
import com.newrelic.agent.android.stats.StatsEngine

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WebViewJSInterfaceTest {

    private lateinit var jsInterface: WebViewJSInterface

    @Before
    fun setUp() {
        jsInterface = WebViewJSInterface()
        StatsEngine.SUPPORTABILITY.statsMap.clear()
    }

    @After
    fun tearDown() {
        StatsEngine.SUPPORTABILITY.statsMap.clear()
    }

    @Test
    fun reportBrowserAgentDetected_true_incrementsMetric() {
        jsInterface.reportBrowserAgentDetected(true)

        assertTrue(StatsEngine.SUPPORTABILITY.statsMap
                .containsKey(MetricNames.SUPPORTABILITY_MOBILE_ANDROID_WEBVIEW_BROWSER_AGENT_DETECTED))
    }

    @Test
    fun reportBrowserAgentDetected_false_doesNotIncrementMetric() {
        jsInterface.reportBrowserAgentDetected(false)

        assertFalse(StatsEngine.SUPPORTABILITY.statsMap
                .containsKey(MetricNames.SUPPORTABILITY_MOBILE_ANDROID_WEBVIEW_BROWSER_AGENT_DETECTED))
    }
}
