/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.measurement.consumer;

import com.newrelic.agent.android.measurement.MeasurementType;

public class ActivityMeasurementConsumer extends MetricMeasurementConsumer {
    public ActivityMeasurementConsumer() {
        super(MeasurementType.Activity);
    }

    @Override
    protected String formatMetricName(String name) {
        return name;
    }
}
