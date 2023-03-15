/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.harvest;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.newrelic.agent.android.harvest.type.HarvestableArray;
import com.newrelic.agent.android.metric.Metric;
import com.newrelic.agent.android.metric.MetricStore;

import java.util.HashMap;

/**
 * A managed collection of {@code Harvestable} machine measurements such as CPU and Memory.
 */
public class MachineMeasurements extends HarvestableArray {
    private final MetricStore metrics = new MetricStore();

    // CPU Total
    // CPU System
    // CPU User
    // Memory/Used
    // Supportability

    public void addMetric(String name, double value) {
        Metric metric = new Metric(name);
        metric.sample(value);
        addMetric(metric);
    }

    public void addMetric(Metric metric) {
        metrics.add(metric);
    }

    public void clear() {
        metrics.clear();
    }

    public boolean isEmpty() {
        return metrics.isEmpty();
    }

    public MetricStore getMetrics() {
        return metrics;
    }

    @Override
    public JsonArray asJsonArray() {
        JsonArray metricArray = new JsonArray();

        for(Metric metric : metrics.getAll()) {
            JsonArray metricJson = new JsonArray();

            HashMap<String, String> header = new HashMap<String, String>();
            header.put("name", metric.getName());
            header.put("scope", metric.getStringScope());

            metricJson.add(new Gson().toJsonTree(header, GSON_STRING_MAP_TYPE));
            metricJson.add(metric.asJsonObject());
            metricArray.add(metricJson);
        }

        return metricArray;
    }
}
