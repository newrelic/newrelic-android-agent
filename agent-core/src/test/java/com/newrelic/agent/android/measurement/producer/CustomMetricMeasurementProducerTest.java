/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.measurement.producer;

import com.newrelic.agent.android.measurement.MeasurementTest;
import com.newrelic.agent.android.measurement.MetricMeasurementFactory;

import org.junit.Before;
import org.junit.Test;

public class CustomMetricMeasurementProducerTest extends MeasurementTest {
    CustomMetricMeasurementProducer producer;

    @Before
    public void setUp() throws Exception {
        producer = new CustomMetricMeasurementProducer();
    }

    @Test
    public void produceMeasurement() {
    }

    @Test
    public void testProduceMeasurement() {
    }

    @Test
    public void createMetricName() {
    }

}