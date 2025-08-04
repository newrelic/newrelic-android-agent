package com.newrelic.agent.android.sessionReplay.models;

public class InitialOffset{
    public int top;
    public int left;

    public InitialOffset(int top, int left) {
        this.top = top;
        this.left = left;
    }

    public int getTop() {
        return top;
    }

    public int getLeft() {
        return left;
    }

    public void setTop(int top) {
        this.top = top;
    }

    public void setLeft(int left) {
        this.left = left;
    }

}
