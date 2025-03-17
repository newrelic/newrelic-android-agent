package com.newrelic.agent.android.sessionReplay.models;

import java.util.ArrayList;
import java.util.List;

public class RRWebFullSnapshotEvent implements RRWebEvent {
    public InitialOffset initialOffset;
    public long timestamp;
    public int type = RRWebEvent.RRWEB_EVENT_FULL_SNAPSHOT;
    public List<RRWebNode> childNodes;

    public RRWebFullSnapshotEvent(InitialOffset initialOffset, long timestamp, List<RRWebNode> childNodes) {
        this.initialOffset = initialOffset;
        this.timestamp = timestamp;
        this.childNodes = childNodes;
    }
}
