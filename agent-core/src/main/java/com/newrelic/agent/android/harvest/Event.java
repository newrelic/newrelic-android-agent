/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.harvest;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;
import com.newrelic.agent.android.harvest.type.HarvestableArray;

import java.util.HashMap;
import java.util.Map;

public class Event extends HarvestableArray {
    private long timestamp;
    private long eventName;
    private Map<String, String> params = new HashMap<String, String>();

    public Event() {}

    @Override
    public JsonArray asJsonArray() {
        JsonArray array = new JsonArray();
        array.add(new JsonPrimitive(timestamp));
        array.add(new JsonPrimitive(eventName));
        array.add(new Gson().toJsonTree(params, GSON_STRING_MAP_TYPE));
        return array;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public long getEventName() {
        return eventName;
    }

    public void setEventName(long eventName) {
        this.eventName = eventName;
    }

    public Map<String, String> getParams() {
        return params;
    }

    public void setParams(Map<String, String> params) {
        this.params = params;
    }
}
