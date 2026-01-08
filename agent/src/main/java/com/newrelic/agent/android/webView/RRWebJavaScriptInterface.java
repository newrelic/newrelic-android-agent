package com.newrelic.agent.android.webView;

import android.util.Log;
import android.webkit.JavascriptInterface;

import com.newrelic.agent.android.sessionReplay.SessionReplay;

import org.json.JSONObject;

public class RRWebJavaScriptInterface {
    private static final String TAG = "RRWebInterface";
    private RRWebEventListener eventListener;

    public RRWebJavaScriptInterface(RRWebEventListener listener) {
        this.eventListener = listener;
    }

    @JavascriptInterface
    public void sendEvent(String eventJson) {
        try {
            Log.d(TAG, "Received rrweb event: " + eventJson);
            JSONObject eventObject = new JSONObject(eventJson);

            SessionReplay.getInstance().recordSessionReplayEvent(eventObject.toString());
            if (eventListener != null) {
                eventListener.onRRWebEvent(eventObject);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing rrweb event", e);
        }
    }

    @JavascriptInterface
    public void logError(String message) {
        Log.e(TAG, "RRWeb Error: " + message);
    }

    public interface RRWebEventListener {
        void onRRWebEvent(JSONObject event);
    }
}
