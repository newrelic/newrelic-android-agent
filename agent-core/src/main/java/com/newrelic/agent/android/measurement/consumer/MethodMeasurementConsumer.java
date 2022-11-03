/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.measurement.consumer;

import com.newrelic.agent.android.measurement.MeasurementType;

public class MethodMeasurementConsumer extends MetricMeasurementConsumer {
    private static final String METRIC_PREFIX = "Method/";

    public MethodMeasurementConsumer() {
        super(MeasurementType.Method);
    }

    @Override
    protected String formatMetricName(String name) {
        return METRIC_PREFIX + name.replace("#", "/");
    }
}
