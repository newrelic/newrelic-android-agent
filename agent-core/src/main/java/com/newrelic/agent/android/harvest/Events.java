/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.harvest;

import com.google.gson.JsonArray;
import com.newrelic.agent.android.harvest.type.HarvestableArray;

import java.util.ArrayList;
import java.util.Collection;

public class Events extends HarvestableArray {
    private final Collection<Event> events = new ArrayList<Event>();

    @Override
    public JsonArray asJsonArray() {
        JsonArray array = new JsonArray();
        for (Event event : events) {
            array.add(event.asJson());
        }
        return array;
    }

    public void addEvent(Event event) {
        events.add(event);
    }
}
