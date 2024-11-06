package com.newrelic.agent.android.sessionReplay.models;


import java.util.ArrayList;

public class Node{
    public int type;
    public int id;
    public ArrayList<ChildNode> childNodes;

    public Node(int type, int id, ArrayList<ChildNode> childNodes) {
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

    public ArrayList<ChildNode> getChildNodes() {
        return childNodes;
    }

    public void setChildNodes(ArrayList<ChildNode> childNodes) {
        this.childNodes = childNodes;
    }

}
