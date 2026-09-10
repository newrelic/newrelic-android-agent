package com.newrelic.agent.android.sessionReplay.models;

public interface RRWebEvent {
    int RRWEB_EVENT_FULL_SNAPSHOT = 2;
    int RRWEB_EVENT_INCREMENTAL_SNAPSHOT = 3;
    int RRWE_EVENT_META = 4;

    /**
     * {@code EventType.Plugin}. Carries a {@code data.plugin} name and an opaque
     * {@code data.payload} that rrweb hands to whichever registered {@code ReplayPlugin} claims
     * that name, without interpreting it. That is what makes it a usable envelope for a
     * WebView's own event stream: the events inside travel untouched, in their own ID space.
     */
    int RRWEB_EVENT_PLUGIN = 6;

    long getTimestamp();

}
