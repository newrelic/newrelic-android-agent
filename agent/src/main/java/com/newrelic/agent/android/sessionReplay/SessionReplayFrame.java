package com.newrelic.agent.android.sessionReplay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SessionReplayFrame {
    public long timestamp;
    public SessionReplayViewThingyInterface rootThingy;
    public List<SessionReplayViewThingyInterface> windowRoots;
    public int width;
    public int height;

    public SessionReplayFrame(SessionReplayViewThingyInterface rootThingy, long timestamp, int width, int height) {
        this.timestamp = timestamp;
        this.rootThingy = rootThingy;
        this.windowRoots = new ArrayList<>(Collections.singletonList(rootThingy));
        this.width = width;
        this.height = height;
    }

    public SessionReplayFrame(List<SessionReplayViewThingyInterface> windowRoots, long timestamp, int width, int height) {
        this.timestamp = timestamp;
        this.windowRoots = windowRoots;
        this.rootThingy = windowRoots.isEmpty() ? null : windowRoots.get(0);
        this.width = width;
        this.height = height;
    }
}
