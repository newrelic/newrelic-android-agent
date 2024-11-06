package com.newrelic.agent.android.sessionReplay.models;

public class Attributes{
    public String id;

    public Attributes(String id) {
        this.id = id;
    }
    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }
}