package com.newrelic.agent.android.sessionReplay.models.IncrementalEvent;

public class RRWebMouseInteractionData extends RRWebIncrementalData {
    public int type;
    public int id;
    public float x;
    public float y;
    
    public RRWebMouseInteractionData(int type, int id, float x, float y) {
        this.source = RRWebIncrementalSource.TOUCH_MOVE;
        this.type = type;
        this.id = id;
        this.x = x;
        this.y = y;
    }
}