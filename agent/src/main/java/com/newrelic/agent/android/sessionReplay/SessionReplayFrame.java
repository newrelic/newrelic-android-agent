package com.newrelic.agent.android.sessionReplay;

public class SessionReplayFrame {
    public long timestamp;
    public SessionReplayViewThingyInterface rootThingy;


    public SessionReplayFrame(SessionReplayViewThingyInterface rootThingy, long timestamp) {
        this.timestamp = timestamp;
        this.rootThingy = rootThingy;
    }

}
