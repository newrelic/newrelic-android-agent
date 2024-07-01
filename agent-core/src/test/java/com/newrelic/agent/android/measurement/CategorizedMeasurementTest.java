/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.measurement;

import static org.junit.Assert.*;

import com.newrelic.agent.android.instrumentation.MetricCategory;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class CategorizedMeasurementTest {

    private CategorizedMeasurement metric;

    @Before
    public void setUp() throws Exception {
        metric = new CategorizedMeasurement(MeasurementType.Custom);
        metric.setCategory(MetricCategory.NONE);
    }

    @Test
    public void getCategory() {
        Assert.assertNotNull(metric.getCategory());
        Assert.assertEquals(MetricCategory.NONE, metric.getCategory());
        Assert.assertEquals(MeasurementType.Custom, metric.getType());
    }

    @Test
    public void setCategory() {
        metric.setCategory(MetricCategory.JSON);
        Assert.assertEquals(MetricCategory.JSON, metric.getCategory());
    }
}