/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.metric;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MetricStore {
    private final Map<String, Map<String, Metric>> metricStore;

    public MetricStore() {
        metricStore = new ConcurrentHashMap<String, Map<String, Metric>>();
    }

    public void add(Metric metric) {
        final String scope = metric.getStringScope();
        final String name = metric.getName();

        if (!metricStore.containsKey(scope)) {
            metricStore.put(scope, new HashMap<String, Metric>());
        }

        if (metricStore.get(scope).containsKey(name)) {
            metricStore.get(scope).get(name).aggregate(metric);
        } else {
            metricStore.get(scope).put(name, metric);
        }
    }

    public Metric get(String name) {
        return get(name, "");
    }

    public Metric get(String name, String scope) {
        try {
            return metricStore.get(scope == null ? "" : scope).get(name);
        } catch (NullPointerException e) {
            return null;
        }
    }

    public List<Metric> getAll() {
        List<Metric> metrics = new ArrayList<Metric>();

        for (Map.Entry<String, Map<String, Metric>> entry : metricStore.entrySet()) {
            for (Map.Entry<String, Metric> metricEntry : entry.getValue().entrySet()) {
                metrics.add(metricEntry.getValue());
            }
        }

        return metrics;
    }

    public List<Metric> getAllByScope(String scope) {
        List<Metric> metrics = new ArrayList<Metric>();

        try {
            for (Map.Entry<String, Metric> metricEntry : metricStore.get(scope).entrySet()) {
                metrics.add(metricEntry.getValue());
            }
        } catch (NullPointerException e) {
            // noop
        }

        return metrics;
    }

    public List<Metric> getAllUnscoped() {
        return getAllByScope("");
    }

    public void remove(Metric metric) {
        final String scope = metric.getStringScope();
        final String name = metric.getName();

        if (!metricStore.containsKey(scope))
            return;

        if (!metricStore.get(scope).containsKey(name))
            return;

        metricStore.get(scope).remove(name);
    }

    public void removeAll(List<Metric> metrics) {
        synchronized (metricStore) {
            for (final Metric metric : metrics) {
                remove(metric);
            }
        }
    }

    public List<Metric> removeAllWithScope(String scope) {
        List<Metric> metrics = getAllByScope(scope);
        if (!metrics.isEmpty()) {
            removeAll(metrics);
        }
        return metrics;
    }

    public void clear() {
        metricStore.clear();
    }

    public boolean isEmpty() {
        return metricStore.isEmpty();
    }
}

