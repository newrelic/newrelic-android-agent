/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.harvest.type;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * Provides a unified interface for harvestable entities. Harvestable entities are objects which are able to
 * serialize themselves into JSON formats that the collector understands.
 *
 * Harvestable entities can either be arrays, objects or single values.
 */
public interface Harvestable {
    /**
     * Types of valid Harvestable entities. Objects, arrays and simple values are supported.
     */
    public enum Type {
        OBJECT,
        ARRAY,
        VALUE
    }

    /**
     * Returns the type of Harvestable entity the object conforms to.
     * @return A {@link Harvestable.Type} defining the kind of Harvestable entity the object implements.
     */
    public Type getType();


    /**
     * A top level method which returns a JSON representation of the Harvestable entity. This is the primary method which
     * should be called to retrieve the JSON representation for the Harvestable.
     *
     * This method calls the appropriate underlying method for the Harvestable type. For example, an object of {@link Harvestable.Type#OBJECT}
     * will result in a call to {@link #asJsonObject()}.
     *
     * @return The JSON representation of the Harvestable entity.
     */
    public JsonElement asJson();

    /**
     * Returns a JSON representation for the Harvestable. Implementing classes of {@link Harvestable.Type#OBJECT} are
     * required to implement this method.
     *
     * @return A JsonObject representation of the Harvestable, or null if the Harvestable is not of type {@link Harvestable.Type#OBJECT}.
     */
    public JsonObject asJsonObject();

    /**
     * Returns a JSON representation for the Harvestable. Implementing classes of {@link Harvestable.Type#ARRAY} are
     * required to implement this method.
     *
     * @return A JsonArray representation of the Harvestable, or null if the Harvestable is not of type {@link Harvestable.Type#ARRAY}.
     */
    public JsonArray asJsonArray();

    /**
     * Returns a JSON representation for the Harvestable. Implementing classes of {@link Harvestable.Type#VALUE} are
     * required to implement this method.
     *
     * @return A JsonPrimitive representation of the Harvestable, or null if the Harvestable is not of type {@link Harvestable.Type#VALUE}.
     */
    public JsonPrimitive asJsonPrimitive();

    /**
     * Returns a JSON string representation for the Harvestable. Implementing classes of {@link Harvestable.Type#OBJECT} are
     * required to implement this method.
     *
     * @return A JSON string representation of the Harvestable, or null if the Harvestable is not of type {@link Harvestable.Type#OBJECT}.
     */
    public String toJsonString();
}
