/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.harvest;

import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;
import com.newrelic.agent.android.harvest.type.HarvestableArray;
import com.newrelic.agent.android.util.SafeJsonPrimitive;

public class ActivitySighting extends HarvestableArray {
    private String name;
    private final long timestampMs;

    private long durationMs = 0;

    public ActivitySighting(long timestampMs, String name) {
        super();

        this.timestampMs = timestampMs;
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        synchronized (this) {
            this.name = name;
        }
    }

    public long getTimestampMs() {
        return timestampMs;
    }

    public long getDuration() {
        return durationMs;
    }

    public void end(long endTimestampMs) {
        synchronized (this) {
            durationMs = endTimestampMs - timestampMs;
        }
    }

    // Used for interaction trace activity history
    @Override
    public JsonArray asJsonArray() {
        final JsonArray data = new JsonArray();
        synchronized (this) {
            data.add(SafeJsonPrimitive.factory(name));
            data.add(SafeJsonPrimitive.factory(timestampMs));
            data.add(SafeJsonPrimitive.factory(durationMs));
        }
        return data;
    }

    // Used for crash activity history
    public JsonArray asJsonArrayWithoutDuration() {
        final JsonArray data = new JsonArray();
        synchronized (this) {
            data.add(SafeJsonPrimitive.factory(timestampMs));
            data.add(SafeJsonPrimitive.factory(name));
        }
        return data;
    }

    public static ActivitySighting newFromJson(JsonArray jsonArray) {
        return new ActivitySighting(jsonArray.get(0).getAsLong(), jsonArray.get(1).getAsString());
    }
}
