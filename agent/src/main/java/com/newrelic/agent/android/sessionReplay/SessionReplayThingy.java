package com.newrelic.agent.android.sessionReplay;

import java.util.ArrayList;
import java.util.List;

public class SessionReplayThingy {
    public static class Rect {
        public final int x, y, width, height;
        public Rect(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    public Rect position;
    public int id;
    public String name;
    public List<SessionReplayThingy> childNodes = new ArrayList<>();

    public SessionReplayThingy(Rect position,
                               String name,
                               int id) {
        this.position = position;
        this.name = name;
        this.id = id;
    }
}
