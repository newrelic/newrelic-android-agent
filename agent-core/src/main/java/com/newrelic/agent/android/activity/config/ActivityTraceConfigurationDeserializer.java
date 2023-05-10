/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.activity.config;

import com.google.gson.*;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;

import java.lang.reflect.Type;

public class ActivityTraceConfigurationDeserializer implements JsonDeserializer<ActivityTraceConfiguration> {
    private final AgentLog log = AgentLogManager.getAgentLog();

    @Override
    public ActivityTraceConfiguration deserialize(JsonElement root, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        ActivityTraceConfiguration configuration = new ActivityTraceConfiguration();

        if (!root.isJsonArray()) {
            error("Expected root element to be an array.");
            return null;
        }

        JsonArray array = root.getAsJsonArray();

        if (array.size() != 2) {
            error("Root array must contain 2 elements.");
            return null;
        }

        Integer maxTotalTraceCount = getInteger(array.get(0));
        if (maxTotalTraceCount == null)
            return null;

        if (maxTotalTraceCount < 0) {
            error("The first element of the root array must not be negative.");
            return null;
        }

        configuration.setMaxTotalTraceCount(maxTotalTraceCount);

        return configuration;
    }

    private Integer getInteger(JsonElement element) {
        if (!element.isJsonPrimitive()) {
            error("Expected an integer.");
            return null;
        }

        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (!primitive.isNumber()) {
            error("Expected an integer.");
            return null;
        }

        int value = primitive.getAsInt();
        if (value < 0) {
            error("Integer value must not be negative");
            return null;
        }
        return value;
    }

    private void error(String message) {
        log.error("ActivityTraceConfigurationDeserializer: " + message);
    }
}
