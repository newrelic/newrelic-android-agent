package com.newrelic.agent.android.sessionReplay.models;


import java.util.ArrayList;
import java.util.List;

public class Node{
    public int type;
    public int id;
    public List<RRWebElementNode> childNodes;

    public Node(int type, int id, List<RRWebElementNode> childNodes) {
        this.type = type;
        this.id = id;
        this.childNodes = childNodes;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public List<RRWebElementNode> getChildNodes() {
        return childNodes;
    }

    public void setChildNodes(ArrayList<RRWebElementNode> childNodes) {
        this.childNodes = childNodes;
    }

}
