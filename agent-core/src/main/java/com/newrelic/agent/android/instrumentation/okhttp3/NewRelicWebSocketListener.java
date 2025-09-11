package com.newrelic.agent.android.instrumentation.okhttp3;


import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.stats.StatsEngine;

import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
public class NewRelicWebSocketListener extends WebSocketListener {

    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_MOBILE_ANDROID_WEBSOCKET_OPEN);
    }

    @Override
    public void onMessage(WebSocket webSocket, String text) {
        StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_MOBILE_ANDROID_WEBSOCKET_MESSAGE_RECEIVED);
    }

    @Override
    public void onMessage(WebSocket webSocket, ByteString bytes) {
        StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_MOBILE_ANDROID_WEBSOCKET_MESSAGE_RECEIVED);
    }

    @Override
    public void onClosing(WebSocket webSocket, int code, String reason) {
        StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_MOBILE_ANDROID_WEBSOCKET_CLOSING);
    }

    @Override
    public void onClosed(WebSocket webSocket, int code, String reason) {
        StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_MOBILE_ANDROID_WEBSOCKET_CLOSE);
    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_MOBILE_ANDROID_WEBSOCKET_FAILED_CONNECTION);
    }
}
