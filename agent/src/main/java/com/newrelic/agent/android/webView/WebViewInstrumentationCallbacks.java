package com.newrelic.agent.android.webView;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.CompoundButton;
import android.widget.RadioGroup;

import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.stats.StatsEngine;

import java.util.Map;
import java.util.WeakHashMap;

public class WebViewInstrumentationCallbacks {
    private static final AgentLog log = AgentLogManager.getAgentLog();

    private static final Map<WebView, Boolean> jsInterfaceInjected = new WeakHashMap<>();

    // Polls for window.newrelic instead of checking once, since a Browser agent snippet loaded
    // via <script async>/defer, or injected dynamically, may not exist yet when onPageFinished fires.
    private static final int DETECTION_POLL_INTERVAL_MS = 250;
    private static final int DETECTION_MAX_POLL_ATTEMPTS = 8;

    private static final String DETECTION_SCRIPT =
            "(function() {" +
            "if (!window." + WebViewJSInterface.INTERFACE_NAME + ") { return; }" +
            "var attempts = 0;" +
            "var maxAttempts = " + DETECTION_MAX_POLL_ATTEMPTS + ";" +
            "var intervalMs = " + DETECTION_POLL_INTERVAL_MS + ";" +
            "var check = function() {" +
            "if (typeof window.newrelic !== 'undefined') {" +
            "window." + WebViewJSInterface.INTERFACE_NAME + ".reportBrowserAgentDetected(true);" +
            "return;" +
            "}" +
            "attempts++;" +
            "if (attempts >= maxAttempts) {" +
            "window." + WebViewJSInterface.INTERFACE_NAME + ".reportBrowserAgentDetected(false);" +
            "return;" +
            "}" +
            "setTimeout(check, intervalMs);" +
            "};" +
            "check();" +
            "})();";


    public static void ButtonClicked(View view) {
        try {
            // Do something
            String viewName = view.getResources().getResourceName(view.getId());
            log.debug("ViewCapture ButtonClicked: " + viewName);
        } catch (Exception e) {
            // Do something
        }

    }

    public static void ButtonLongClicked(View view) {
        try {
            // Do something
            String viewName = view.getResources().getResourceName(view.getId());
            log.debug("ViewCapture ButtonLongClicked: " + viewName);
        } catch (Exception e) {
            // Do something
        }
    }

    public static void OnListItemClicked(AdapterView adapterView,View view) {
        try {
            // Do something
            String viewName = view.getResources().getResourceName(view.getId());
            log.debug("ViewCapture ListItemClicked: " + viewName);
        } catch (Exception e) {
            // Do something
        }

    }

    public static void OnDialogButtonClicked(DialogInterface dialog, int which) {
        try {
            // Do something

            if(dialog instanceof AlertDialog) {
                AlertDialog alertDialog = (AlertDialog) dialog;
                String dialogTitle = alertDialog.getButton(which).getText().toString();
                log.debug("ViewCapture: Dialog Button Clicked: " + dialogTitle);
            }

            log.debug("ViewCapture: Dialog Button Clicked");
        } catch (Exception e) {
            // Do something
        }

    }

    public static void OnCheckedChange(CompoundButton buttonView, boolean isChecked) {
        try {
            // Do something
            log.debug("ViewCapture: " + buttonView.getText().toString() + " Checked: " + isChecked);
        } catch (Exception e) {
            // Do something
        }

    }

    public static void OnCheckedChange(RadioGroup radioGroup, int checkedId) {
        try {

            for (int i = 0; i < radioGroup.getChildCount(); i++) {
                View view = radioGroup.getChildAt(i);
                if (view instanceof CompoundButton) {
                    CompoundButton compoundButton = (CompoundButton) view;
                    if (compoundButton.getId() == checkedId) {
                        log.debug("ViewCapture: " + compoundButton.getText().toString() + " Checked: " + checkedId);
                    }
                }
            }
            // Do something
        } catch (Exception e) {
            // Do something
        }

    }

    static synchronized void ensureJsInterfaceInjected(WebView webView) {
        if (webView == null || jsInterfaceInjected.containsKey(webView)) {
            return;
        }
        jsInterfaceInjected.put(webView, Boolean.TRUE);
        try {
            webView.addJavascriptInterface(new WebViewJSInterface(), WebViewJSInterface.INTERFACE_NAME);
        } catch (Exception e) {
            log.error("Failed to inject NR WebView JS interface", e);
        }
    }

    public static void loadUrlCalled(WebView var0) {
        ensureJsInterfaceInjected(var0);
        StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_MOBILE_ANDROID_WEBVIEW_LOAD_URL);
    }

    public static void postUrlCalled(WebView var0) {
        ensureJsInterfaceInjected(var0);
        StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_MOBILE_ANDROID_WEBVIEW_POST_URL);
    }

    public static void onPageFinishedCalled(WebViewClient var0, WebView var1, String var2) {
        StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_MOBILE_ANDROID_WEBVIEW_PAGE_FINISHED);
        if (var1 != null) {
            try {
                var1.evaluateJavascript(DETECTION_SCRIPT, null);
            } catch (Exception e) {
                log.error("Failed to run NR browser agent detection script", e);
            }
        }
    }
}
