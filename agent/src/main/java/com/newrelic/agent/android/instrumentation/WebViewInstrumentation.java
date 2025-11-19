package com.newrelic.agent.android.instrumentation;


import android.webkit.WebView;

import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.stats.StatsEngine;


import java.util.Map;

public class WebViewInstrumentation {

    public static void loadUrl(WebView webView,String url) {
        webView.loadUrl(url);
        StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_MOBILE_ANDROID_WEBVIEW_LOAD_URL);
    }

    public static void loadUrl(WebView webView, String url, Map<String,String> additionalHttpHeaders) {
        webView.loadUrl(url,additionalHttpHeaders);
        StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_MOBILE_ANDROID_WEBVIEW_LOAD_URL);
    }

    public static void postUrl(WebView webView, String url, byte[] postData) {
        webView.postUrl(url,postData);
        StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_MOBILE_ANDROID_WEBVIEW_POST_URL);
    }
}
