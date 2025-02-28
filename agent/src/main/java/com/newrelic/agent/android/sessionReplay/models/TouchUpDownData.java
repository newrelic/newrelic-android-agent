package com.newrelic.agent.android.sessionReplay.models;


public class TouchUpDownData implements TouchData {
    public int source = 2;
    public int type;
    public int id;
    public float x;
    public float y;

    public TouchUpDownData(int source, int type, int id, float x, float y) {
        this.source = source;
        this.type = type;
        this.id = id;
        this.x = x;
        this.y = y;
    }
}
