/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.harvest.type;

import com.google.gson.JsonPrimitive;

/**
 * An abstract {@link Harvestable} class which can be used as a base class for {@link Harvestable} entities of type {@link Harvestable.Type#VALUE}
 */
public abstract class HarvestableValue extends BaseHarvestable {

    public HarvestableValue() {
        super(Type.VALUE);
    }

    /**
     * Returns a JsonPrimitive representation of the {@link Harvestable}.
     * @return A JsonPrimitive representation of the {@link Harvestable} entity.
     */
    public abstract JsonPrimitive asJsonPrimitive();
}
