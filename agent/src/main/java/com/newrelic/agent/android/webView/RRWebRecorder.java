package com.newrelic.agent.android.webView;

import android.content.Context;
import android.os.Looper;
import android.util.Log;
import android.webkit.WebView;

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

    public RRWebRecorder(WebView webView) {
        this.webView = webView;
        this.context = webView.getContext();
        this.jsInterface = new RRWebJavaScriptInterface(this);
        this.rrwebScript = loadRRWebScript();
        setupWebView();
    }

    private void setupWebView() {
        webView.getSettings().setJavaScriptEnabled(true);
        webView.addJavascriptInterface(jsInterface, "RRWebAndroid");
    }

    public void startRecording() {
        String script = getInjectionScript();
        if (script.isEmpty()) {
            Log.e(TAG, "Cannot start recording - rrweb script not loaded");
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
        return "console.log('WebView Started');"+ rrwebScript +
                "(function() {" +
                "  if (window.rrwebRecorder) {" +
                "    console.log('RRWeb already initialized');" +
                "    return;" +
                "  }" +
                "  try {" +
                "    window.rrwebRecorder = rrweb.record({" +
                "      emit: function(event) {" +
                "        try {" +
                "          RRWebAndroid.sendEvent(JSON.stringify(event));" +
                "        } catch(e) {" +
                "          RRWebAndroid.logError('Failed to send event: ' + e.message);" +
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
                "    console.log('RRWeb recording started');" +
                "  } catch(e) {" +
                "    RRWebAndroid.logError('Failed to start recording: ' + e.message);" +
                "  }" +
                "})();";
    }

    public void stopRecording() {
        String script = "(function() {" +
                "  if (window.rrwebRecorder) {" +
                "    window.rrwebRecorder();" +
                "    window.rrwebRecorder = null;" +
                "    console.log('RRWeb recording stopped');" +
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