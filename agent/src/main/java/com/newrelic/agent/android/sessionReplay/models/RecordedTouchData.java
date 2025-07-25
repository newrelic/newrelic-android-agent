package com.newrelic.agent.android.sessionReplay.models;

public class RecordedTouchData {
    public int actionType;
    public int originatingViewId;
    public float xCoordinate;
    public float yCoordinate;
    public long timestamp;

    public RecordedTouchData(int actionType, int originatingViewId, float xCoordinate, float yCoordinate, long timestamp) {
        this.actionType = actionType;
        this.originatingViewId = originatingViewId;
        this.xCoordinate = xCoordinate;
        this.yCoordinate = yCoordinate;
        this.timestamp = timestamp;
    }
}