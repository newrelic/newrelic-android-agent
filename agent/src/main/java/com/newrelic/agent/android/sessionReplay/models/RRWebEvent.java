package com.newrelic.agent.android.sessionReplay.models;

public interface RRWebEvent {
    int RRWEB_EVENT_FULL_SNAPSHOT = 2;
    int RRWEB_EVENT_INCREMENTAL_SNAPSHOT = 3;
    int RRWE_EVENT_META = 4;

    long getTimestamp();

}
