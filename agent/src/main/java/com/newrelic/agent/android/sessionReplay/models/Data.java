package com.newrelic.agent.android.sessionReplay.models;

public class Data{
    public InitialOffset initialOffset;
    public Node node;

    public Data(InitialOffset initialOffset, Node node) {
        this.initialOffset = initialOffset;
        this.node = node;
    }


    public InitialOffset getInitialOffset() {
        return initialOffset;
    }

    public Node getNode() {
        return node;
    }

    public void setInitialOffset(InitialOffset initialOffset) {
        this.initialOffset = initialOffset;
    }

    public void setNode(Node node) {
        this.node = node;
    }


}
