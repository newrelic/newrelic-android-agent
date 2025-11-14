package com.newrelic.agent.android.sessionReplay.models;

public class RRWebMetaEvent implements RRWebEvent {
    public static class RRWebMetaEventData {
        public String href;
        public int width;
        public int height;

        public RRWebMetaEventData(String href, int width, int height) {
            this.href = href;
            this.width = width;
            this.height = height;
        }
    }

    public int type = RRWE_EVENT_META;
    public RRWebMetaEventData data;
    public long timestamp;

    public RRWebMetaEvent(RRWebMetaEventData data, long timestamp) {
        this.data = data;
        this.timestamp = timestamp;
    }
    @Override
    public long getTimestamp() {
        return this.timestamp;
    }
}
