package com.newrelic.agent.android.webView

import android.webkit.WebView
import android.webkit.WebViewClient

import com.newrelic.agent.android.metric.MetricNames
import com.newrelic.agent.android.stats.StatsEngine

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WebViewInstrumentationCallbacksTest {

    private lateinit var webView: WebView

    @Before
    fun setUp() {
        webView = mock(WebView::class.java)
        StatsEngine.SUPPORTABILITY.statsMap.clear()
    }

    @After
    fun tearDown() {
        StatsEngine.SUPPORTABILITY.statsMap.clear()
    }

    @Test
    fun loadUrlCalled_incrementsMetricAndInjectsJsInterfaceOnce() {
        WebViewInstrumentationCallbacks.loadUrlCalled(webView)
        WebViewInstrumentationCallbacks.loadUrlCalled(webView)

        assertTrue(StatsEngine.SUPPORTABILITY.statsMap
                .containsKey(MetricNames.SUPPORTABILITY_MOBILE_ANDROID_WEBVIEW_LOAD_URL))
        assertEquals(2L, StatsEngine.SUPPORTABILITY.statsMap
                .get(MetricNames.SUPPORTABILITY_MOBILE_ANDROID_WEBVIEW_LOAD_URL)!!.count)
        verify(webView, times(1)).addJavascriptInterface(any(WebViewJSInterface::class.java), eq(WebViewJSInterface.INTERFACE_NAME))
    }

    @Test
    fun postUrlCalled_doesNotReinjectJsInterfaceIfLoadUrlAlreadyDid() {
        WebViewInstrumentationCallbacks.loadUrlCalled(webView)
        WebViewInstrumentationCallbacks.postUrlCalled(webView)

        assertTrue(StatsEngine.SUPPORTABILITY.statsMap
                .containsKey(MetricNames.SUPPORTABILITY_MOBILE_ANDROID_WEBVIEW_POST_URL))
        verify(webView, times(1)).addJavascriptInterface(any(WebViewJSInterface::class.java), eq(WebViewJSInterface.INTERFACE_NAME))
    }

    @Test
    fun onPageFinishedCalled_incrementsMetricAndEvaluatesDetectionScript() {
        val client = mock(WebViewClient::class.java)

        WebViewInstrumentationCallbacks.onPageFinishedCalled(client, webView, "https://example.com")

        assertTrue(StatsEngine.SUPPORTABILITY.statsMap
                .containsKey(MetricNames.SUPPORTABILITY_MOBILE_ANDROID_WEBVIEW_PAGE_FINISHED))

        val scriptCaptor = ArgumentCaptor.forClass(String::class.java)
        verify(webView, times(1)).evaluateJavascript(scriptCaptor.capture(), isNull())
        val script = scriptCaptor.value
        assertTrue("Script should reference the bridge", script.contains(WebViewJSInterface.INTERFACE_NAME))
        assertTrue("Script should retry via setTimeout instead of a single synchronous check",
                script.contains("setTimeout"))
    }
}
