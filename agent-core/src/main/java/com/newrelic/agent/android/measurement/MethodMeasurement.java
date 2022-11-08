/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.measurement;

import com.newrelic.agent.android.instrumentation.MetricCategory;

public class MethodMeasurement extends CategorizedMeasurement {
    public MethodMeasurement(String name, String scope, long startTime, long endTime, long exclusiveTime, MetricCategory category) {
        super(MeasurementType.Method);
        setName(name);
        setScope(scope);
        setStartTime(startTime);
        setEndTime(endTime);
        setExclusiveTime(exclusiveTime);
        setCategory(category);
    }
}
