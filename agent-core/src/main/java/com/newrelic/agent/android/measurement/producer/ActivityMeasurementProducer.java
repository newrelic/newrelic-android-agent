/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.measurement.producer;

import com.newrelic.agent.android.activity.MeasuredActivity;
import com.newrelic.agent.android.measurement.ActivityMeasurement;
import com.newrelic.agent.android.measurement.MeasurementType;

public class ActivityMeasurementProducer extends BaseMeasurementProducer {
    public ActivityMeasurementProducer() {
        super(MeasurementType.Activity);
    }

    public void produceMeasurement(MeasuredActivity measuredActivity) {
        // We duplicate this metric twice.  Once with its standard metric name and once with its background name.
        super.produceMeasurement(new ActivityMeasurement(measuredActivity.getMetricName(), measuredActivity.getStartTime(), measuredActivity.getEndTime()));
        super.produceMeasurement(new ActivityMeasurement(measuredActivity.getBackgroundMetricName(), measuredActivity.getStartTime(), measuredActivity.getEndTime()));
    }
}
