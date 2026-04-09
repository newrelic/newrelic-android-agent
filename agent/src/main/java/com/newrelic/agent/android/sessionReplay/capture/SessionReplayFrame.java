package com.newrelic.agent.android.sessionReplay.capture;

import com.newrelic.agent.android.sessionReplay.viewMapper.SessionReplayViewThingyInterface;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SessionReplayFrame {
    public long timestamp;
    public SessionReplayViewThingyInterface rootThingy;
    public List<SessionReplayViewThingyInterface> windowRoots;
    public boolean hasDimBehind;
    public int width;
    public int height;

    public SessionReplayFrame(SessionReplayViewThingyInterface rootThingy, long timestamp, int width, int height) {
        this.timestamp = timestamp;
        this.rootThingy = rootThingy;
        this.windowRoots = new ArrayList<>(Collections.singletonList(rootThingy));
        this.hasDimBehind = false;
        this.width = width;
        this.height = height;
    }

    public SessionReplayFrame(List<SessionReplayViewThingyInterface> windowRoots, boolean hasDimBehind,
                              long timestamp, int width, int height) {
        this.timestamp = timestamp;
        this.windowRoots = windowRoots;
        this.hasDimBehind = hasDimBehind;
        this.rootThingy = windowRoots.isEmpty() ? null : windowRoots.get(0);
        this.width = width;
        this.height = height;
    }
}
