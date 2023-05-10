/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.harvest.type;

import com.google.gson.JsonArray;

/**
 * An abstract {@link Harvestable} class which can be used as a base class for {@link Harvestable} entities of type {@link Harvestable.Type#ARRAY}
 */
public abstract class HarvestableArray extends BaseHarvestable {

    /**
     * Creates a new {@link HarvestableArray} by calling the superclass with a {@link Harvestable.Type} of {@link Harvestable.Type#ARRAY}
     */
    public HarvestableArray() {
        super(Type.ARRAY);
    }

    /**
     *  Returns a JsonArray representation of the {@link Harvestable}.
     * @return A JsonArray representation of the {@link Harvestable} entity.
     */
    public abstract JsonArray asJsonArray();
}
