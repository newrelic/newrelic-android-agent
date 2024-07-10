/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.measurement.consumer;

import com.newrelic.agent.android.measurement.CustomMetricMeasurement;
import com.newrelic.agent.android.measurement.MeasurementTest;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class CustomMetricMeasurementConsumerTest extends MeasurementTest {

    CustomMetricMeasurementConsumer consumer;
    private CustomMetricMeasurement customMeasurement;

    @Before
    public void setUp() throws Exception {
        consumer = new CustomMetricMeasurementConsumer();
        customMeasurement = factory.provideCategorizedMeasurement();
    }

    @Test
    public void formatMetricName() {
        Assert.assertEquals(CustomMetricMeasurementConsumer.METRIC_PREFIX + "customMetricName", consumer.formatMetricName("customMetricName"));
    }

    @Test
    public void consumeMeasurement() {
        consumer.consumeMeasurement(customMeasurement);
        Assert.assertEquals(1, consumer.metrics.getAllUnscoped().size());
        Assert.assertNotNull(consumer.metrics.get("Custom/customMeasurement", ""));
    }

}