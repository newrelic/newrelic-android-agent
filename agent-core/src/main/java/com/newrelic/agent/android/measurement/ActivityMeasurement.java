/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.measurement;

public class ActivityMeasurement extends BaseMeasurement {
    public ActivityMeasurement(String name, long startTime, long endTime) {
        super(MeasurementType.Activity);

        setName(name);
        setStartTime(startTime);
        setEndTime(endTime);
    }
}
