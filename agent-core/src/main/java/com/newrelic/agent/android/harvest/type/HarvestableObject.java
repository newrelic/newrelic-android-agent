/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.harvest.type;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.Map;

/**
 * An abstract {@link Harvestable} class which can be used as a base class for {@link Harvestable} entities of type {@link Harvestable.Type#OBJECT}
 */
public abstract class HarvestableObject extends BaseHarvestable {

    /**
     * A utility method which transforms a {@code Map&ltString,String&gt;} into a {@link HarvestableObject}
     * @param map A {@link Map} of Strings to transform.
     * @return A {@link HarvestableObject} representation of the {@link Map}.
     */
    public static HarvestableObject fromMap(final Map<String, String> map) {
        return new HarvestableObject() {
            @Override
            public JsonObject asJsonObject() {
                return (JsonObject) new Gson().toJsonTree(map, GSON_STRING_MAP_TYPE);
            }
        };
    }

    public HarvestableObject() {
        super(Type.OBJECT);
    }

    /**
     *  Returns a JsonObject representation of the {@link Harvestable}.
     * @return A JsonObject representation of the {@link Harvestable} entity.
     */
    public abstract JsonObject asJsonObject();
}
