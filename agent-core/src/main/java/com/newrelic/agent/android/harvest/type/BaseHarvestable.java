/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.harvest.type;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.reflect.TypeToken;

import java.util.Map;

/**
 * A {@link Harvestable} base class which has a {@link Harvestable.Type} and implements the {@link #asJson()} method.
 */
public class BaseHarvestable implements Harvestable {
    private final Harvestable.Type type;

    /**
     * A GSON TypeToken for converting Map&lt;String, String&gt; to JSON.
     */
    protected final static java.lang.reflect.Type GSON_STRING_MAP_TYPE = new TypeToken<Map<String, String>>() {
    }.getType();

    public BaseHarvestable(Harvestable.Type type) {
        this.type = type;
    }

    @Override
    public JsonElement asJson() {
        switch (type) {
            case OBJECT:
                return asJsonObject();
            case ARRAY:
                return asJsonArray();
            case VALUE:
                return asJsonPrimitive();
            default:
                return null;
        }
    }

    @Override
    public Type getType() {
        return type;
    }

    /**
     * Returns a {@code String} representation of the Harvestable JSON.
     * @return String of JSON.
     */

    @Override
    public String toJsonString() {
        return asJson().toString();
    }

    /**
     * Null base implementation. Subclasses must implement this method.
     * @return null
     */
    public JsonArray asJsonArray() {
        return null;
    }

    /**
     * Null base implementation. Subclasses must implement this method.
     * @return null
     */
    @Override
    public JsonObject asJsonObject() {
        return null;
    }

    /**
     * Null base implementation. Subclasses must implement this method.
     * @return null
     */
    @Override
    public JsonPrimitive asJsonPrimitive() {
        return null;
    }

    /**
     * Ensures {@code argument} is not null or empty.
     * @param argument String to test for emptiness
     * @throws IllegalArgumentException if {@code argument} is empty.
     */
    protected void notEmpty(String argument) {
        if (argument == null || argument.length() == 0)
            throw new IllegalArgumentException("Missing Harvestable field.");
    }

    /**
     * Ensures {@code argument} is not null.
     * @param argument Object to test for nullity
     * @throws IllegalArgumentException if {@code argument} is null.
     */
    protected void notNull(Object argument) {
        if (argument == null)
            throw new IllegalArgumentException("Null field in Harvestable object");
    }

    /**
     * Utility method that returns an empty string if {@code argument} is null.
     * @param argument String argument which may be null.
     * @return The original {@code argument} or empty string.
     */
    protected String optional(String argument) {
        if (argument == null)
            return "";
        return argument;
    }
}
