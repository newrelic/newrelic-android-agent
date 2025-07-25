package com.newrelic.agent.android.sessionReplay.models;

public class SessionReplayRoot {
    public int type;
    public Data data;
    public long timestamp;


    public SessionReplayRoot(int type, Data data, long timestamp) {
        this.type = type;
        this.data = data;
        this.timestamp = timestamp;
    }

    public int getType() {
        return type;
    }

    public Data getData() {
        return data;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setType(int type) {
        this.type = type;
    }

    public void setData(Data data) {
        this.data = data;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

}
