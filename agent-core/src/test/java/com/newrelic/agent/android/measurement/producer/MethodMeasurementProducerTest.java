/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.measurement.producer;

import com.newrelic.agent.android.measurement.MeasurementTest;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class MethodMeasurementProducerTest extends MeasurementTest {
    private MethodMeasurementProducer producer;

    @Before
    public void setUp() throws Exception {
        producer = new MethodMeasurementProducer();
    }

    @Test
    public void produceMeasurementFromTrace() {
        producer.produceMeasurement(factory.provideRootTrace());
        Assert.assertFalse(producer.drainMeasurements().isEmpty());
    }
}