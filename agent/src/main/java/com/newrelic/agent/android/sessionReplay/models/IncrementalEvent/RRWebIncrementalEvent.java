package com.newrelic.agent.android.sessionReplay.models.IncrementalEvent;

import com.newrelic.agent.android.sessionReplay.models.RRWebEvent;

import java.util.List;

public class RRWebIncrementalEvent implements RRWebEvent {
    public long timestamp;
    public int type = RRWebEvent.RRWEB_EVENT_INCREMENTAL_SNAPSHOT;
    public RRWebIncrementalData data;

    public RRWebIncrementalEvent(long timestamp, RRWebIncrementalData data) {
        this.timestamp = timestamp;
        this.data = data;
    }

    @Override
    public long getTimestamp() {
        return this.timestamp;
    }
}