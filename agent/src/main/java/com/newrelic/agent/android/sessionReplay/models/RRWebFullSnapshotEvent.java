package com.newrelic.agent.android.sessionReplay.models;

import java.util.ArrayList;
import java.util.List;

public class RRWebFullSnapshotEvent implements RRWebEvent {
    public long timestamp;
    public int type = RRWebEvent.RRWEB_EVENT_FULL_SNAPSHOT;
    public Data data;

    public RRWebFullSnapshotEvent( long timestamp,Data data) {
        this.timestamp = timestamp;
        this.data = data;
    }


    @Override
    public long getTimestamp() {
        return this.timestamp;
    }
}
