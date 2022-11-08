/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sample;

import com.newrelic.agent.android.measurement.Measurement;
import com.newrelic.agent.android.measurement.MeasurementType;
import com.newrelic.agent.android.measurement.consumer.MetricMeasurementConsumer;
import com.newrelic.agent.android.metric.Metric;
import com.newrelic.agent.android.tracing.Sample;

public class MachineMeasurementConsumer extends MetricMeasurementConsumer {

    public MachineMeasurementConsumer() {
        super(MeasurementType.Machine);
    }

    @Override
    protected String formatMetricName(String name) {
        return name;
    }

    @Override
    public void consumeMeasurement(Measurement measurement) { }

    @Override
    public void onHarvest() {
        final Sample memorySample = Sampler.sampleMemory();

        if (memorySample != null) {
            final Metric memoryMetric = new Metric("Memory/Used");
            memoryMetric.sample(memorySample.getValue().doubleValue());
            addMetric(memoryMetric);
        }

        super.onHarvest();
    }
}
