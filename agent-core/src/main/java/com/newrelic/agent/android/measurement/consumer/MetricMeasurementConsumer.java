/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.measurement.consumer;

import com.newrelic.agent.android.harvest.Harvest;
import com.newrelic.agent.android.harvest.HarvestLifecycleAware;
import com.newrelic.agent.android.measurement.Measurement;
import com.newrelic.agent.android.measurement.MeasurementType;
import com.newrelic.agent.android.metric.Metric;
import com.newrelic.agent.android.metric.MetricStore;

public abstract class MetricMeasurementConsumer extends BaseMeasurementConsumer implements HarvestLifecycleAware {
    protected MetricStore metrics;
    protected boolean recordUnscopedMetrics = true;

    public MetricMeasurementConsumer(MeasurementType measurementType) {
        super(measurementType);

        metrics = new MetricStore();
        Harvest.addHarvestListener(this);
    }

    protected abstract String formatMetricName(String name);

    @Override
    public void consumeMeasurement(Measurement measurement) {
        final String name = formatMetricName(measurement.getName());
        final String scope = measurement.getScope();
        final double delta = measurement.getEndTimeInSeconds() - measurement.getStartTimeInSeconds();

        // We record both a scoped and an unscoped metric for scoped measurements
        if (scope != null) {
            Metric scopedMetric = metrics.get(name, scope);
            if (scopedMetric == null) {
                scopedMetric = new Metric(name, scope);
                metrics.add(scopedMetric);
            }

            scopedMetric.sample(delta);
            scopedMetric.addExclusive(measurement.getExclusiveTimeInSeconds());
        }

        // Allow subclasses the option of generating only scoped metrics.
        if (!recordUnscopedMetrics)
            return;

        Metric unscopedMetric = metrics.get(name);
        if (unscopedMetric == null) {
            unscopedMetric = new Metric(name);
            metrics.add(unscopedMetric);
        }

        unscopedMetric.sample(delta);
        unscopedMetric.addExclusive(measurement.getExclusiveTimeInSeconds());
    }

    protected void addMetric(Metric newMetric) {
        Metric metric;

        if (newMetric.getScope() != null) {
            metric = metrics.get(newMetric.getName(), newMetric.getScope());
        } else {
            metric = metrics.get(newMetric.getName());
        }

        if (metric != null)
            metric.aggregate(newMetric);
        else
            metrics.add(newMetric);
    }

    @Override
    public void onHarvest() {
        for (Metric metric : metrics.getAll()) {
            Harvest.addMetric(metric);
        }
    }

    @Override
    public void onHarvestComplete() {
        metrics.clear();
    }

    @Override
    public void onHarvestError() {
        metrics.clear();
    }

    @Override
    public void onHarvestSendFailed() {
        metrics.clear();
    }
}
