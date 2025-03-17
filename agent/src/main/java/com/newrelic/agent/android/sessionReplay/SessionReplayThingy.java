package com.newrelic.agent.android.sessionReplay;

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
    public List<SessionReplayThingy> childNodes;

    public SessionReplayThingy(Rect position,
                               String name,
                               int id,
                               List<SessionReplayThingy> childNodes) {
        this.position = position;
        this.name = name;
        this.id = id;
        this.childNodes = childNodes;
    }

    public String generateCssDescription() {
        StringBuilder cssString = new StringBuilder();
        String cssSelector = generateCssSelector();

        cssString.append("#")
                .append(cssSelector)
                .append("{")
                .append(generatePositionCss());

        return cssString.toString();
    }

    public String generateCssSelector() {
        return (name + "-" + id);
    }

    private StringBuilder generatePositionCss() {
        StringBuilder positionStringBuilder = new StringBuilder();

        positionStringBuilder.append("position: fixed;")
                .append("left: ")
                .append(position.x)
                .append("px;")
                .append("top: ")
                .append(position.y)
                .append("px;")
                .append("width: ")
                .append(position.width)
                .append("px;")
                .append("height: ")
                .append(position.height)
                .append("px;");

        return positionStringBuilder;
    }
}
