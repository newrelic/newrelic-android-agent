package com.newrelic.agent.android.sessionReplay.models;

import java.util.ArrayList;

public class TouchMoveData implements TouchData {
    public class Position {
        public int id;
        public float x;
        public float y;

        public Position(int id, float x, float y) {
            this.id = id;
            this.x = x;
            this.y = y;
        }
    }

    public int source = 1;
    public ArrayList<Position> positions = new ArrayList<>();
//    public Position positions;

    public TouchMoveData(int source, int id, float x, float y) {
        this.source = source;
        Position position = new Position(id, x, y);
        positions.add(position);
//        this.positions = position;
    }
}
