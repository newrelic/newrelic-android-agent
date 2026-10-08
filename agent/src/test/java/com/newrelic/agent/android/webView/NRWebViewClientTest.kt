package com.newrelic.agent.android.webView

import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

import com.newrelic.agent.android.metric.MetricNames
import com.newrelic.agent.android.stats.StatsEngine

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NRWebViewClientTest {

    private lateinit var webView: WebView
    private lateinit var delegate: WebViewClient

    @Before
    fun setUp() {
        webView = mock(WebView::class.java)
        delegate = mock(WebViewClient::class.java)
        StatsEngine.SUPPORTABILITY.statsMap.clear()
    }

    @After
    fun tearDown() {
        StatsEngine.SUPPORTABILITY.statsMap.clear()
    }

    @Test
    fun onPageFinished_forwardsToDelegateAndRecordsPageLoad() {
        NRWebViewClient(delegate).onPageFinished(webView, "https://example.com")

        verify(delegate, times(1)).onPageFinished(webView, "https://example.com")
        assertTrue(StatsEngine.SUPPORTABILITY.statsMap
                .containsKey(MetricNames.SUPPORTABILITY_MOBILE_ANDROID_WEBVIEW_PAGE_FINISHED))
        verify(webView, times(1)).evaluateJavascript(anyString(), isNull())
    }

    @Test
    fun shouldOverrideUrlLoading_returnsDelegateDecision() {
        val request = mock(WebResourceRequest::class.java)
        `when`(delegate.shouldOverrideUrlLoading(webView, request)).thenReturn(true)

        assertTrue(NRWebViewClient(delegate).shouldOverrideUrlLoading(webView, request))
    }

    @Test
    fun nullDelegate_behavesLikePlainWebViewClient() {
        val request = mock(WebResourceRequest::class.java)
        // WebViewClient's default shouldOverrideUrlLoading(View, Request) dereferences request.url,
        // so it must be stubbed even though the assertion is about the return value.
        `when`(request.url).thenReturn(Uri.parse("https://example.com"))

        val client = NRWebViewClient(null)

        assertFalse(client.shouldOverrideUrlLoading(webView, request))
        assertNull(client.shouldInterceptRequest(webView, request))
    }

    @Test
    fun delegate_isReparentable() {
        val client = NRWebViewClient(delegate)
        val replacement = mock(WebViewClient::class.java)

        client.delegate = replacement
        client.onPageFinished(webView, "https://example.com")

        assertSame(replacement, client.delegate)
        verify(replacement, times(1)).onPageFinished(webView, "https://example.com")
        verify(delegate, times(0)).onPageFinished(webView, "https://example.com")
    }
}
