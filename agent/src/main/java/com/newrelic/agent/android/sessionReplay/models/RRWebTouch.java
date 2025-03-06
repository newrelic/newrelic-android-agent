package com.newrelic.agent.android.sessionReplay.models;

public class RRWebTouch {

    public long timestamp;
    public int type = 3;
    public RRWebTouchData data;

    public RRWebTouch(long timestamp, int type, RRWebTouchData data) {
        this.timestamp = timestamp;
        this.type = type;
        this.data = data;
    }
}
