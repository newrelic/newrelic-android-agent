/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.measurement;

import com.newrelic.agent.android.metric.Metric;

public class CustomMetricMeasurement extends CategorizedMeasurement {
    private Metric customMetric;

    public CustomMetricMeasurement() {
        super(MeasurementType.Custom);
    }

    public CustomMetricMeasurement(String name, int count, double totalValue, double exclusiveValue) {
        this();
        setName(name);
        customMetric = new Metric(name);

        // set customMetric value one by one
        customMetric.setCount(count);
        customMetric.setTotal(totalValue);
        customMetric.setSumOfSquares(totalValue * totalValue);
        customMetric.setMin(totalValue);
        customMetric.setMax(totalValue);
        customMetric.setExclusive(exclusiveValue);
    }

    public Metric getCustomMetric() {
        return customMetric;
    }

}
