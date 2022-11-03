/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.harvest;

import com.google.gson.JsonArray;
import com.newrelic.agent.android.harvest.type.HarvestableArray;
import com.newrelic.agent.android.tracing.ActivityTrace;

import java.util.ArrayList;
import java.util.Collection;

public class ActivityTraces extends HarvestableArray {
    private final Collection<ActivityTrace> activityTraces = new ArrayList<ActivityTrace>();

    @Override
    public JsonArray asJsonArray() {
        JsonArray array = new JsonArray();

        for (ActivityTrace activityTrace : activityTraces) {
            array.add(activityTrace.asJson());
        }

        return array;
    }

    public synchronized void add(ActivityTrace activityTrace) {
        activityTraces.add(activityTrace);
    }

    public synchronized void remove(ActivityTrace activityTrace) {
        activityTraces.remove(activityTrace);
    }

    public void clear() {
        activityTraces.clear();
    }

    public int count() {
        return activityTraces.size();
    }

    public Collection<ActivityTrace> getActivityTraces() {
        return activityTraces;
    }
}
