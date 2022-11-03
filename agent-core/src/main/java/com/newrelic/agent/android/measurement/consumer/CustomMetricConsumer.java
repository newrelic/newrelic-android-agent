/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.measurement.consumer;

import com.newrelic.agent.android.measurement.CustomMetricMeasurement;
import com.newrelic.agent.android.measurement.Measurement;
import com.newrelic.agent.android.measurement.MeasurementType;
import com.newrelic.agent.android.metric.Metric;

public class CustomMetricConsumer extends MetricMeasurementConsumer {

    private static final String METRIC_PREFIX = "Custom/";

    public CustomMetricConsumer() {
        super(MeasurementType.Custom);
    }

    @Override
    protected String formatMetricName(String name) {
        return METRIC_PREFIX + name;
    }

    @Override
    public void consumeMeasurement(Measurement measurement) {
        CustomMetricMeasurement custom = (CustomMetricMeasurement)measurement;
        Metric metric = custom.getCustomMetric();
        metric.setName(formatMetricName(metric.getName()));
        addMetric(metric);
    }
}
