package com.newrelic.agent.android.sessionReplay.models;

import java.util.ArrayList;

public class RRWebTouchMoveData implements RRWebTouchData {
    public static class Position {
        public int id;
        public float x;
        public float y;
        public long timeOffset;

        public Position(int id, float x, float y, long timeOffset) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.timeOffset = timeOffset;
        }
    }

    public int source = 1;
    public ArrayList<Position> positions = new ArrayList<>();
//    public Position positions;

    public RRWebTouchMoveData(int source, ArrayList<Position> positions) {
        this.source = source;
        this.positions = positions;
    }
}
