package com.newrelic.agent.android.sessionReplay.models;

public class RRWebTextNode implements RRWebNode {
    public int type = RRWEB_NODE_TYPE_TEXT;
    public String textContent;
    public Boolean isStyle;
    public int id;

    public RRWebTextNode(String text, Boolean isStyle, int id) {
        this.textContent = text;
        this.isStyle = isStyle;
        this.id = id;
    }
}
