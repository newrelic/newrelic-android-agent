/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.harvest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.newrelic.agent.android.harvest.type.HarvestableArray;

import java.util.ArrayList;
import java.util.List;

public class ActivityHistory extends HarvestableArray {
    private final List<ActivitySighting> activityHistory;

    public ActivityHistory(final List<ActivitySighting> activityHistory) {
        super();

        this.activityHistory = activityHistory;
    }

    public int size() {
        return activityHistory.size();
    }

    @Override
    public JsonArray asJsonArray() {
        final JsonArray data = new JsonArray();

        for (ActivitySighting sighting : activityHistory) {
            data.add(sighting.asJsonArray());
        }

        return data;
    }

    public JsonArray asJsonArrayWithoutDuration() {
        final JsonArray data = new JsonArray();

        for (ActivitySighting sighting : activityHistory) {
            data.add(sighting.asJsonArrayWithoutDuration());
        }

        return data;
    }

    public static ActivityHistory newFromJson(JsonArray jsonArray) {
        final List<ActivitySighting> sightings = new ArrayList<ActivitySighting>();

        for (JsonElement element : jsonArray) {
            sightings.add(ActivitySighting.newFromJson(element.getAsJsonArray()));
        }

        return new ActivityHistory(sightings);
    }
}
