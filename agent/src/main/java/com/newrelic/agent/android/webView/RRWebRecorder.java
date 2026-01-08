package com.newrelic.agent.android.webView;

import android.content.Context;
import android.os.Build;
import android.os.Looper;
import android.util.Log;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.newrelic.agent.android.sessionReplay.SessionReplay;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class RRWebRecorder implements RRWebJavaScriptInterface.RRWebEventListener {
    private static final String TAG = "RRWebRecorder";
    private static final String RRWEB_ASSET_FILE = "rrweb.min.js";
    private WebView webView;
    private RRWebJavaScriptInterface jsInterface;
    private Context context;
    private String rrwebScript;
    private boolean isRecording = false;

    public RRWebRecorder(WebView webView) {
        this.webView = webView;
        this.context = webView.getContext();
        this.jsInterface = new RRWebJavaScriptInterface(this);
        this.rrwebScript = loadRRWebScript();
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
        if (rrwebScript == null || rrwebScript.isEmpty()) {
            Log.e(TAG, "Cannot start recording - rrweb script not loaded");
            return;
        }

        Log.d(TAG, "Starting RRWeb recording");
        isRecording = true;

        // Inject script immediately if page is already loaded
        injectScript();

        // Note: The WebViewClient will also inject on subsequent page loads
    }

    private void injectScript() {
        String script = getInjectionScript();
        if (script.isEmpty()) {
            Log.e(TAG, "Cannot inject script - rrweb script not loaded");
            return;
        }

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
        if (rrwebScript == null || rrwebScript.isEmpty()) {
            Log.e(TAG, "RRWeb script not loaded");
            return "";
        }
        return "console.log('WebView Started');\n" +
                rrwebScript + ";\n" +  // Add semicolon and newline to properly terminate the rrweb script
                "(function() {" +
                "  console.log('Starting RRWeb initialization...');" +
                "  if (window.rrwebRecorder) {" +
                "    console.log('RRWeb already initialized');" +
                "    RRWebAndroid.logError('RRWeb already initialized');" +
                "    return;" +
                "  }" +
                "  if (typeof rrweb === 'undefined') {" +
                "    console.error('rrweb is not defined - script failed to load');" +
                "    RRWebAndroid.logError('rrweb is not defined - script failed to load');" +
                "    return;" +
                "  }" +
                "  if (typeof rrweb.record !== 'function') {" +
                "    console.error('rrweb.record is not a function');" +
                "    RRWebAndroid.logError('rrweb.record is not a function');" +
                "    return;" +
                "  }" +
                "  try {" +
                "    console.log('Calling rrweb.record...');" +
                "    window.rrwebRecorder = rrweb.record({" +
                "      emit: function(event) {" +
                "        try {" +
                "          console.log('RRWeb sending Events:', event.type);" +
                "          RRWebAndroid.sendEvent(JSON.stringify(event));" +
                "        } catch(e) {" +
                "          console.error('Failed to send event:', e);" +
                "          if (typeof RRWebAndroid !== 'undefined' && RRWebAndroid.logError) {" +
                "            RRWebAndroid.logError('Failed to send event: ' + e.message);" +
                "          }" +
                "        }" +
                "      }," +
                "      checkoutEveryNms: 10000," +
                "      sampling: {" +
                "        mousemove: true," +
                "        mouseInteraction: true," +
                "        scroll: 150," +
                "        input: 'last'" +
                "      }" +
                "    });" +
                "    console.log('RRWeb recording started successfully');" +
                "    RRWebAndroid.logError('RRWeb recording started successfully');" +
                "  } catch(e) {" +
                "    console.error('Failed to start recording:', e);" +
                "    if (typeof RRWebAndroid !== 'undefined' && RRWebAndroid.logError) {" +
                "      RRWebAndroid.logError('Failed to start recording: ' + e.message + ', Stack: ' + e.stack);" +
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

    private String loadRRWebScript() {
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(
                            context.getAssets().open(RRWEB_ASSET_FILE),
                            StandardCharsets.UTF_8
                    )
            );
            StringBuilder script = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                script.append(line);
            }
            reader.close();
            Log.d(TAG, "Successfully loaded rrweb script from assets, size: " + script.length() + " bytes");
            return script.toString();
        } catch (IOException e) {
            Log.e(TAG, "Failed to load rrweb script from assets. Ensure rrweb.min.js exists in agent/src/main/assets/", e);
            return "";
        }
    }
}