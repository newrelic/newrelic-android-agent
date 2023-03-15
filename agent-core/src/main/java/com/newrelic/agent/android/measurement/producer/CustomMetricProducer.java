/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.measurement.producer;

import com.newrelic.agent.android.measurement.CustomMetricMeasurement;
import com.newrelic.agent.android.measurement.MeasurementType;
import com.newrelic.agent.android.metric.MetricUnit;

public class CustomMetricProducer extends BaseMeasurementProducer {
    private static final String FILTER_REGEX = "[/\\[\\]|*]";

    public CustomMetricProducer() {
        super(MeasurementType.Custom);
    }

    public void produceMeasurement(String name, String category, int count, double totalValue, double exclusiveValue) {
        produceMeasurement(category, name, count, totalValue, exclusiveValue, null, null);
    }

    public void produceMeasurement(String name, String category, int count, double totalValue, double exclusiveValue, MetricUnit countUnit, MetricUnit valueUnit) {
        String metricName = createMetricName(name, category, countUnit, valueUnit);
        CustomMetricMeasurement custom = new CustomMetricMeasurement(metricName, count, totalValue, exclusiveValue);
        produceMeasurement(custom);
    }

    private String createMetricName(String name, String category, MetricUnit countUnit, MetricUnit valueUnit) {
        final StringBuffer metricName = new StringBuffer();

        metricName.append(category.replaceAll(FILTER_REGEX, ""));
        metricName.append("/");
        metricName.append(name.replaceAll(FILTER_REGEX, ""));


        if (countUnit != null || valueUnit != null) {
            metricName.append("[");
            if (valueUnit != null) {
                metricName.append(valueUnit.getLabel());
            }
            if (countUnit != null) {
                metricName.append("|");
                metricName.append(countUnit.getLabel());
            }
            metricName.append("]");
        }
        return metricName.toString();
    }
}
