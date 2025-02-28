package com.newrelic.agent.android.sessionReplay.models;

public class Touch {

    public long timestamp;
    public int type = 3;
    public TouchData data;

    public Touch(long timestamp, int type, TouchData data) {
        this.timestamp = timestamp;
        this.type = type;
        this.data = data;
    }
}
