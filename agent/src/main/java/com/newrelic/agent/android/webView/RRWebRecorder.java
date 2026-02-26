package com.newrelic.agent.android.webView;

import android.content.Context;
import android.os.Build;
import android.os.Looper;
import android.util.Log;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.sessionReplay.SessionReplay;

import org.json.JSONObject;

public class RRWebRecorder implements RRWebJavaScriptInterface.RRWebEventListener {
    private static final String TAG = "RRWebRecorder";
    // CDN URL for rrweb library
    private static final String RRWEB_CDN_URL = "https://cdn.jsdelivr.net/npm/rrweb@2.0.0-alpha.4/dist/rrweb.min.js";
    private WebView webView;
    private RRWebJavaScriptInterface jsInterface;
    private Context context;
    private boolean isRecording = false;
    private AgentConfiguration agentConfiguration;

    public RRWebRecorder(WebView webView) {
        this.webView = webView;
        this.context = webView.getContext();
        this.jsInterface = new RRWebJavaScriptInterface(this);
        this.agentConfiguration = AgentConfiguration.getInstance();
        setupWebView();
    }

    private void setupWebView() {
        // Enable JavaScript
        webView.getSettings().setJavaScriptEnabled(true);

        // Enable DOM storage (required for rrweb)
        webView.getSettings().setDomStorageEnabled(true);

        // Enable database storage
        webView.getSettings().setDatabaseEnabled(true);

        // Enable mixed content mode for Android 5.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webView.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        // Add JavaScript interface
        webView.addJavascriptInterface(jsInterface, "RRWebAndroid");

        // Set WebViewClient to inject script at the right time
        WebViewClient existingClient = null;
        try {
            // Try to preserve existing WebViewClient if possible
            // This is a best-effort approach
        } catch (Exception e) {
            Log.d(TAG, "No existing WebViewClient to preserve");
        }

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.d(TAG, "Page finished loading: " + url);

                // Inject rrweb script and start recording if recording is enabled
                if (isRecording) {
                    Log.d(TAG, "Auto-injecting rrweb script after page load");
                    injectScript();
                }
            }
        });
    }

    public void startRecording() {
        Log.d(TAG, "Starting RRWeb recording");
        isRecording = true;

        // Inject script immediately if page is already loaded
        injectScript();

        // Note: The WebViewClient will also inject on subsequent page loads
    }

    private void injectScript() {
        String script = getInjectionScript();

        // Ensure execution on main thread
        if (Looper.myLooper() == Looper.getMainLooper()) {
            evaluateScript(script);
        } else {
            webView.post(() -> evaluateScript(script));
        }
    }

    private void evaluateScript(String script) {
        webView.evaluateJavascript(script, result -> {
            if (result != null && !result.equals("null")) {
                Log.d(TAG, "RRWeb initialization result: " + result);
            } else {
                Log.w(TAG, "RRWeb script evaluation returned null or undefined");
            }
        });
    }

    private String getInjectionScript() {
        return "console.log('WebView Started - Loading rrweb from CDN');\n" +
                "(function() {" +
                "  \n// Check if rrweb is already loaded\n" +
                "  if (typeof rrweb !== 'undefined') {" +
                "    console.log('rrweb already loaded, starting recording...');" +
                "    initRRWebRecording();" +
                "    return;" +
                "  }" +
                "  \n" +
                "  // Load rrweb from CDN\n" +
                "  console.log('Loading rrweb from CDN...');" +
                "  var script = document.createElement('script');" +
                "  script.src = '" + RRWEB_CDN_URL + "';" +
                "  script.type = 'text/javascript';" +
                "  " +
                "  script.onload = function() {" +
                "    console.log('rrweb script loaded successfully from CDN');" +
                "    initRRWebRecording();" +
                "  };" +
                "  " +
                "  script.onerror = function() {" +
                "    console.error('Failed to load rrweb script from CDN');" +
                "    if (typeof RRWebAndroid !== 'undefined' && RRWebAndroid.logError) {" +
                "      RRWebAndroid.logError('Failed to load rrweb from CDN: " + RRWEB_CDN_URL + "');" +
                "    }" +
                "  };" +
                "  " +
                "  document.head.appendChild(script);" +
                "  " +
                " \n // Function to initialize rrweb recording \n" +
                "  function initRRWebRecording() {" +
                "    if (window.rrwebRecorder) {" +
                "      console.log('RRWeb already initialized');" +
                "      if (typeof RRWebAndroid !== 'undefined' && RRWebAndroid.logError) {" +
                "        RRWebAndroid.logError('RRWeb already initialized');" +
                "      }" +
                "      return;" +
                "    }" +
                "    " +
                "    if (typeof rrweb === 'undefined') {" +
                "      console.error('rrweb is not defined after load');" +
                "      if (typeof RRWebAndroid !== 'undefined' && RRWebAndroid.logError) {" +
                "        RRWebAndroid.logError('rrweb is not defined after load');" +
                "      }" +
                "      return;" +
                "    }" +
                "    " +
                "    if (typeof rrweb.record !== 'function') {" +
                "      console.error('rrweb.record is not a function');" +
                "      if (typeof RRWebAndroid !== 'undefined' && RRWebAndroid.logError) {" +
                "        RRWebAndroid.logError('rrweb.record is not a function');" +
                "      }" +
                "      return;" +
                "    }" +
                "    " +
                "    try {" +
                "      console.log('Calling rrweb.record...');" +
                "      window.rrwebRecorder = rrweb.record({" +
                "        emit: function(event) {" +
                "          try {" +
                "            console.log('RRWeb sending Events:', event.type);" +
                "            if (typeof RRWebAndroid !== 'undefined' && RRWebAndroid.sendEvent) {" +
                "              RRWebAndroid.sendEvent(JSON.stringify(event));" +
                "            }" +
                "          } catch(e) {" +
                "            console.error('Failed to send event:', e);" +
                "            if (typeof RRWebAndroid !== 'undefined' && RRWebAndroid.logError) {" +
                "              RRWebAndroid.logError('Failed to send event: ' + e.message);" +
                "            }" +
                "          }" +
                "        }," +
                "        checkoutEveryNms: 10000," +
                "        maskAllInputs: " + agentConfiguration.getSessionReplayConfiguration().isMaskUserInputText() + "," +
                "        maskTextClass: 'rr-mask'," +
                "        inlineStylesheet: true," +
                "        inlineImages: false," +
                "        sampling: {" +
                "          mousemove: true," +
                "          mouseInteraction: true," +
                "          scroll: 150," +
                "          input: 'last'" +
                "        }" +
                "      });" +
                "      console.log('RRWeb recording started successfully');" +
                "      if (typeof RRWebAndroid !== 'undefined' && RRWebAndroid.logError) {" +
                "        RRWebAndroid.logError('RRWeb recording started successfully');" +
                "      }" +
                "    } catch(e) {" +
                "      console.error('Failed to start recording:', e);" +
                "      if (typeof RRWebAndroid !== 'undefined' && RRWebAndroid.logError) {" +
                "        RRWebAndroid.logError('Failed to start recording: ' + e.message + ', Stack: ' + e.stack);" +
                "      }" +
                "    }" +
                "  }" +
                "})();";
    }

    public void stopRecording() {
        Log.d(TAG, "Stopping RRWeb recording");
        isRecording = false;

        String script = "(function() {" +
                "  if (window.rrwebRecorder) {" +
                "    window.rrwebRecorder();" +
                "    window.rrwebRecorder = null;" +
                "    console.log('RRWeb recording stopped');" +
                "    RRWebAndroid.logError('RRWeb recording stopped');" +
                "  }" +
                "})();";

        if (Looper.myLooper() == Looper.getMainLooper()) {
            evaluateScript(script);
        } else {
            webView.post(() -> evaluateScript(script));
        }
    }

    @Override
    public void onRRWebEvent(JSONObject event) {
        try {
            int eventType = event.getInt("type");
            long timestamp = event.getLong("timestamp");

            Log.d(TAG, "========== RRWeb Event ==========");
            Log.d(TAG, "Type: " + getEventTypeName(eventType));
            Log.d(TAG, "Timestamp: " + timestamp);
            Log.d(TAG, "Full Event: " + event.toString(2));
            Log.d(TAG, "================================");

            // Process or store events here
            processEvent(event);
        } catch (Exception e) {
            Log.e(TAG, "Error processing event", e);
        }
    }

    private String getEventTypeName(int type) {
        switch (type) {
            case 0: return "DomContentLoaded";
            case 1: return "Load";
            case 2: return "FullSnapshot";
            case 3: return "IncrementalSnapshot";
            case 4: return "Meta";
            case 5: return "Custom";
            default: return "Unknown";
        }
    }

    private void processEvent(JSONObject event) {
        // Implement your event processing logic here
        // For example: send to server, store locally, etc.

        SessionReplay.getInstance().recordSessionReplayEvent(event.toString());
    }
}