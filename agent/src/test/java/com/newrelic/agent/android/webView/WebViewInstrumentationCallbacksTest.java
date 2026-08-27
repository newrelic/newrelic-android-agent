package com.newrelic.agent.android.webView;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.stats.StatsEngine;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class WebViewInstrumentationCallbacksTest {

    private WebView webView;

    @Before
    public void setUp() {
        webView = mock(WebView.class);
        StatsEngine.SUPPORTABILITY.getStatsMap().clear();
    }

    @After
    public void tearDown() {
        StatsEngine.SUPPORTABILITY.getStatsMap().clear();
    }

    @Test
    public void loadUrlCalled_incrementsMetricAndInjectsJsInterfaceOnce() {
        WebViewInstrumentationCallbacks.loadUrlCalled(webView);
        WebViewInstrumentationCallbacks.loadUrlCalled(webView);

        assertTrue(StatsEngine.SUPPORTABILITY.getStatsMap()
                .containsKey(MetricNames.SUPPORTABILITY_MOBILE_ANDROID_WEBVIEW_LOAD_URL));
        assertEquals(2, StatsEngine.SUPPORTABILITY.getStatsMap()
                .get(MetricNames.SUPPORTABILITY_MOBILE_ANDROID_WEBVIEW_LOAD_URL).getCount());
        verify(webView, times(1)).addJavascriptInterface(any(WebViewJSInterface.class), eq(WebViewJSInterface.INTERFACE_NAME));
    }

    @Test
    public void postUrlCalled_doesNotReinjectJsInterfaceIfLoadUrlAlreadyDid() {
        WebViewInstrumentationCallbacks.loadUrlCalled(webView);
        WebViewInstrumentationCallbacks.postUrlCalled(webView);

        assertTrue(StatsEngine.SUPPORTABILITY.getStatsMap()
                .containsKey(MetricNames.SUPPORTABILITY_MOBILE_ANDROID_WEBVIEW_POST_URL));
        verify(webView, times(1)).addJavascriptInterface(any(WebViewJSInterface.class), eq(WebViewJSInterface.INTERFACE_NAME));
    }

    @Test
    public void onPageFinishedCalled_incrementsMetricAndEvaluatesDetectionScript() {
        WebViewClient client = mock(WebViewClient.class);

        WebViewInstrumentationCallbacks.onPageFinishedCalled(client, webView, "https://example.com");

        assertTrue(StatsEngine.SUPPORTABILITY.getStatsMap()
                .containsKey(MetricNames.SUPPORTABILITY_MOBILE_ANDROID_WEBVIEW_PAGE_FINISHED));
        verify(webView, times(1)).evaluateJavascript(contains(WebViewJSInterface.INTERFACE_NAME), isNull());
    }
}
