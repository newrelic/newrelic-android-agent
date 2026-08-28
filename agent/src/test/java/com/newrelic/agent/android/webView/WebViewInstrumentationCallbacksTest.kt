package com.newrelic.agent.android.webView

import android.os.Build
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

import com.newrelic.agent.android.metric.MetricNames
import com.newrelic.agent.android.stats.StatsEngine

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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

    @Test
    fun pageFinished_incrementsMetricAndEvaluatesDetectionScript() {
        WebViewInstrumentationCallbacks.pageFinished(webView, "https://example.com")

        assertTrue(StatsEngine.SUPPORTABILITY.statsMap
                .containsKey(MetricNames.SUPPORTABILITY_MOBILE_ANDROID_WEBVIEW_PAGE_FINISHED))

        val scriptCaptor = ArgumentCaptor.forClass(String::class.java)
        verify(webView, times(1)).evaluateJavascript(scriptCaptor.capture(), isNull())
        assertTrue(scriptCaptor.value.contains(WebViewJSInterface.INTERFACE_NAME))
    }

    @Test
    fun setWebViewClientCalled_wrapsCustomerClient() {
        val customer = mock(WebViewClient::class.java)

        val result = WebViewInstrumentationCallbacks.setWebViewClientCalled(webView, customer)

        assertTrue("Should return an NR wrapper", result is NRWebViewClient)
        assertSame(customer, (result as NRWebViewClient).delegate)
    }

    @Test
    fun setWebViewClientCalled_doesNotDoubleWrap() {
        val alreadyOurs = NRWebViewClient(mock(WebViewClient::class.java))

        val result = WebViewInstrumentationCallbacks.setWebViewClientCalled(webView, alreadyOurs)

        assertSame(alreadyOurs, result)
    }

    @Test
    fun setWebViewClientCalled_reparentsExistingWrapper() {
        val first = mock(WebViewClient::class.java)
        val second = mock(WebViewClient::class.java)

        val wrapper = WebViewInstrumentationCallbacks.setWebViewClientCalled(webView, first)
        val again = WebViewInstrumentationCallbacks.setWebViewClientCalled(webView, second)

        assertSame("Should reuse the same wrapper instance", wrapper, again)
        assertSame(second, (again as NRWebViewClient).delegate)
    }

    @Test
    fun setWebViewClientCalled_nullClient_wrapsWithDefaultBehavior() {
        val result = WebViewInstrumentationCallbacks.setWebViewClientCalled(webView, null)

        assertTrue(result is NRWebViewClient)
        assertNotNull("Delegate must never be null", (result as NRWebViewClient).delegate)
    }

    @Test
    fun onPageFinishedCalled_isSuppressedWhenNRClientInstalled() {
        WebViewInstrumentationCallbacks.setWebViewClientCalled(webView, mock(WebViewClient::class.java))

        WebViewInstrumentationCallbacks.onPageFinishedCalled(
                mock(WebViewClient::class.java), webView, "https://example.com")

        assertFalse("Wrapper already counts this page load",
                StatsEngine.SUPPORTABILITY.statsMap
                        .containsKey(MetricNames.SUPPORTABILITY_MOBILE_ANDROID_WEBVIEW_PAGE_FINISHED))
        verify(webView, times(0)).evaluateJavascript(org.mockito.ArgumentMatchers.anyString(), isNull())
    }

    @Test
    fun loadUrlCalled_enablesJavaScriptWhenDisabled() {
        val settings = mock(WebSettings::class.java)
        `when`(webView.getSettings()).thenReturn(settings)
        `when`(settings.getJavaScriptEnabled()).thenReturn(false)

        WebViewInstrumentationCallbacks.loadUrlCalled(webView)

        verify(settings, times(1)).setJavaScriptEnabled(true)
    }

    @Test
    fun loadUrlCalled_leavesJavaScriptAloneWhenAlreadyEnabled() {
        val settings = mock(WebSettings::class.java)
        `when`(webView.getSettings()).thenReturn(settings)
        `when`(settings.getJavaScriptEnabled()).thenReturn(true)

        WebViewInstrumentationCallbacks.loadUrlCalled(webView)

        verify(settings, times(0)).setJavaScriptEnabled(anyBoolean())
    }

    @Test
    fun loadUrlCalled_installsWrapperPreservingExistingClient() {
        val customer = mock(WebViewClient::class.java)
        `when`(webView.getWebViewClient()).thenReturn(customer)

        WebViewInstrumentationCallbacks.loadUrlCalled(webView)

        val clientCaptor = ArgumentCaptor.forClass(WebViewClient::class.java)
        verify(webView, times(1)).setWebViewClient(clientCaptor.capture())
        val installed = clientCaptor.value
        assertTrue(installed is NRWebViewClient)
        assertSame("Existing client must be preserved as the delegate",
                customer, (installed as NRWebViewClient).delegate)
    }

    @Test
    fun loadUrlCalled_doesNotReinstallWhenSetWebViewClientAlreadyWrapped() {
        // Ordering case from spec 2.4: client set first, then load.
        WebViewInstrumentationCallbacks.setWebViewClientCalled(webView, mock(WebViewClient::class.java))

        WebViewInstrumentationCallbacks.loadUrlCalled(webView)

        verify(webView, times(0)).setWebViewClient(org.mockito.ArgumentMatchers.any())
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.N])
    fun loadUrlCalled_belowApi26_doesNotInstallWrapper() {
        WebViewInstrumentationCallbacks.loadUrlCalled(webView)

        // getWebViewClient() does not exist below API 26, so an existing client could not be
        // preserved; the safety rule is to skip installation rather than risk clobbering it.
        verify(webView, times(0)).setWebViewClient(org.mockito.ArgumentMatchers.any())
    }
}
