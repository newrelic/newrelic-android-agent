package com.newrelic.agent.android.sessionReplay;

public class SessionReplayFrame {
    public long timestamp;
    public SessionReplayViewThingyInterface rootThingy;
    public int width;
    public int height;



    public SessionReplayFrame(SessionReplayViewThingyInterface rootThingy, long timestamp, int width, int height) {
        this.timestamp = timestamp;
        this.rootThingy = rootThingy;
        this.width = width;
        this.height = height;
    }

}
