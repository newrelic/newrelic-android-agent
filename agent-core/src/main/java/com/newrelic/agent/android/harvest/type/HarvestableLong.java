/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.harvest.type;

import com.google.gson.JsonPrimitive;

/**
 * An abstract {@link HarvestableValue} class which can be used as a base class for {@link Harvestable} entities of type {@link Harvestable.Type#VALUE}
 * that are backed by a {@code long} value.
 */
public class HarvestableLong extends HarvestableValue {
    private long value;

    public HarvestableLong() {
        super();
    }

    public HarvestableLong(long value) {
        this();
        this.value = value;
    }

    /**
     * Returns a JsonPrimitive representation of the long value.
     * @return JsonPrimitive representation of the long value.
     */
    @Override
    public JsonPrimitive asJsonPrimitive() {
        return new JsonPrimitive(value);
    }
}
