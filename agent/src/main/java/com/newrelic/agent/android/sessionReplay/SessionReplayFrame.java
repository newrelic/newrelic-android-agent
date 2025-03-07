package com.newrelic.agent.android.sessionReplay;

public class SessionReplayFrame {
    private long timestamp;
    private SessionReplayThingy rootThingy;


    public SessionReplayFrame(SessionReplayThingy rootThingy, long timestamp) {
        this.timestamp = timestamp;
        this.rootThingy = rootThingy;
    }

}
