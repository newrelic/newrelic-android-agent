package com.newrelic.agent.android.webView;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Build;
import android.view.View;
import android.webkit.WebSettings;
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

    private static final Map<WebView, WebViewState> states = new WeakHashMap<>();

    /**
     * Per-WebView instrumentation state. Held in a WeakHashMap so tracking a WebView never
     * keeps it (and its Activity) alive.
     */
    static final class WebViewState {
        boolean jsInterfaceInjected;
        NRWebViewClient nrClient;
    }

    private static synchronized WebViewState stateFor(WebView webView) {
        WebViewState state = states.get(webView);
        if (state == null) {
            state = new WebViewState();
            states.put(webView, state);
        }
        return state;
    }

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
        if (webView == null) {
            return;
        }
        WebViewState state = stateFor(webView);
        if (state.jsInterfaceInjected) {
            return;
        }
        try {
            webView.addJavascriptInterface(new WebViewJSInterface(), WebViewJSInterface.INTERFACE_NAME);
            state.jsInterfaceInjected = true;
        } catch (Exception e) {
            // Flag deliberately left unset so the next navigation retries. Marking it before the
            // call would let one transient failure disable detection for this WebView permanently.
            log.error("Failed to inject NR WebView JS interface", e);
        }
    }

    /**
     * Enables JavaScript so the detection script can run. This deliberately overrides a host-app
     * setting — see section 5 of the design spec. The warning is the audit trail for support cases.
     */
    @SuppressLint("SetJavaScriptEnabled")
    static void enableJavaScript(WebView webView) {
        if (webView == null) {
            return;
        }
        try {
            WebSettings settings = webView.getSettings();
            if (settings != null && !settings.getJavaScriptEnabled()) {
                settings.setJavaScriptEnabled(true);
                log.warn("New Relic enabled JavaScript on a WebView for browser agent detection");
            }
        } catch (Throwable t) {
            log.error("Failed to enable JavaScript for NR browser agent detection", t);
        }
    }

    /**
     * Installs an {@link NRWebViewClient} on this WebView, preserving any client already set.
     *
     * Skipped below API 26: {@code getWebViewClient()} does not exist there, so an existing client
     * could neither be read nor distinguished from "no client set" — and installing blind would
     * silently drop the host app's callbacks. On those API levels the wrapper arrives only via
     * {@link #setWebViewClientCalled}, which carries the delegate by construction.
     */
    @TargetApi(Build.VERSION_CODES.O)
    static synchronized void ensureNRClientInstalled(WebView webView) {
        if (webView == null) {
            return;
        }
        WebViewState state = stateFor(webView);
        if (state.nrClient != null) {
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            log.debug("Skipping NR WebViewClient install below API 26: existing client cannot be preserved");
            return;
        }
        try {
            WebViewClient existing = webView.getWebViewClient();
            if (existing instanceof NRWebViewClient) {
                state.nrClient = (NRWebViewClient) existing;
                return;
            }
            NRWebViewClient wrapper = new NRWebViewClient(existing);
            webView.setWebViewClient(wrapper);
            // Recorded only after the install actually succeeded. Recording first would make
            // hasNRClient() claim a wrapper that isn't there, suppressing the fallback path and
            // losing the page load from both paths at once.
            state.nrClient = wrapper;
        } catch (Throwable t) {
            log.error("Failed to install NR WebViewClient", t);
        }
    }

    /**
     * Pre-navigation setup, run inline on the caller's thread from the loadUrl/postUrl call sites.
     * Never posted to the WebView's handler: a posted install can land after onPageFinished has
     * already fired for the page we wanted to inspect.
     */
    static void prepare(WebView webView) {
        ensureJsInterfaceInjected(webView);
        enableJavaScript(webView);
        ensureNRClientInstalled(webView);
    }

    public static void loadUrlCalled(WebView var0) {
        prepare(var0);
        StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_MOBILE_ANDROID_WEBVIEW_LOAD_URL);
    }

    public static void postUrlCalled(WebView var0) {
        prepare(var0);
        StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_MOBILE_ANDROID_WEBVIEW_POST_URL);
    }

    /**
     * Records a completed page load and runs browser-agent detection. Called by
     * {@link NRWebViewClient} and, on WebViews with no NR client installed, by the
     * bytecode-instrumented {@code onPageFinished} override in the host app.
     */
    public static void pageFinished(WebView webView, String url) {
        StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_MOBILE_ANDROID_WEBVIEW_PAGE_FINISHED);
        if (webView == null) {
            return;
        }
        try {
            webView.evaluateJavascript(DETECTION_SCRIPT, null);
        } catch (Exception e) {
            log.error("Failed to run NR browser agent detection script", e);
        }
    }

    public static void onPageFinishedCalled(WebViewClient var0, WebView var1, String var2) {
        if (hasNRClient(var1)) {
            return;     // NRWebViewClient.onPageFinished already reported this page load
        }
        pageFinished(var1, var2);
    }

    /**
     * Bytecode entry point for {@code WebView.setWebViewClient(WebViewClient)}. Returns the client
     * that should actually be set — an {@link NRWebViewClient} wrapping the caller's client, so
     * every callback the host app registered still fires.
     *
     * Fails open: any problem returns the original client unchanged rather than breaking the
     * host app's call.
     */
    public static synchronized WebViewClient setWebViewClientCalled(WebView webView, WebViewClient client) {
        if (client instanceof NRWebViewClient) {
            return client;              // already ours (also covers the agent's own install call)
        }
        if (webView == null) {
            return client;              // nothing to key state on; don't alter behavior
        }
        try {
            WebViewState state = stateFor(webView);
            if (state.nrClient != null) {
                state.nrClient.setDelegate(client != null ? client : new WebViewClient());
                return state.nrClient;  // re-parent rather than stacking a second wrapper
            }
            NRWebViewClient wrapper = new NRWebViewClient(client);
            state.nrClient = wrapper;
            return wrapper;
        } catch (Throwable t) {
            log.error("Failed to wrap WebViewClient; using the original", t);
            return client;
        }
    }

    /**
     * Whether an {@link NRWebViewClient} is currently this WebView's client — meaning
     * {@link NRWebViewClient#onPageFinished} already reported the page load.
     *
     * Prefers the live client over tracked state. The host app can replace our wrapper through a
     * call site we did not intercept (see the residual gap in the design spec), and trusting stale
     * bookkeeping there would suppress the fallback path and lose the page load entirely.
     */
    @TargetApi(Build.VERSION_CODES.O)
    private static synchronized boolean hasNRClient(WebView webView) {
        if (webView == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                return webView.getWebViewClient() instanceof NRWebViewClient;
            } catch (Throwable t) {
                log.debug("Could not read the current WebViewClient; falling back to tracked state");
            }
        }
        WebViewState state = states.get(webView);   // deliberately not stateFor(): no entry created
        return state != null && state.nrClient != null;
    }
}
