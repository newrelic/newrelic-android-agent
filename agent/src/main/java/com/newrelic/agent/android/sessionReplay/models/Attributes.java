package com.newrelic.agent.android.sessionReplay.models;

import com.google.gson.annotations.JsonAdapter;

import java.util.HashMap;
import java.util.Map;

@JsonAdapter(AttributesSerializer.class)
public class Attributes{
    public String id;
    public String type;
    public String value;
    public Map<String, String> metadata = new HashMap<>();

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public Attributes(String id) {
        this.id = id;
    }
    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
