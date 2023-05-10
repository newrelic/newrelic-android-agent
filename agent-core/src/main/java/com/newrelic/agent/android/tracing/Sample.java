/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.tracing;

import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;
import com.newrelic.agent.android.harvest.type.HarvestableArray;
import com.newrelic.agent.android.util.SafeJsonPrimitive;

public class Sample extends HarvestableArray {
    private long timestamp;
    private SampleValue sampleValue;
    private SampleType type;

    public static enum SampleType {
        MEMORY,
        CPU,
    }

    public Sample(SampleType type) {
        setSampleType(type);
        setTimestamp(System.currentTimeMillis());
    }

    public Sample(long timestamp) {
        setTimestamp(timestamp);
    }

    public Sample(long timestamp, SampleValue sampleValue) {
        setTimestamp(timestamp);
        setSampleValue(sampleValue);
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public SampleValue getSampleValue() {
        return sampleValue;
    }

    public void setSampleValue(SampleValue sampleValue) {
        this.sampleValue = sampleValue;
    }

    public void setSampleValue(double value) {
        sampleValue = new SampleValue(value);
    }

    public void setSampleValue(long value) {
        sampleValue = new SampleValue(value);
    }

    public Number getValue() {
        return sampleValue.getValue();
    }

    public SampleType getSampleType() {
        return type;
    }

    public void setSampleType(SampleType type) {
        this.type = type;
    }

    @Override
    public JsonArray asJsonArray() {
        JsonArray jsonArray = new JsonArray();

        jsonArray.add(SafeJsonPrimitive.factory(timestamp));
        jsonArray.add(SafeJsonPrimitive.factory(sampleValue.getValue()));

        return jsonArray;
    }
}
