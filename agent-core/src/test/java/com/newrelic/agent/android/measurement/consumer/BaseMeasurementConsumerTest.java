/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.measurement.consumer;

import com.newrelic.agent.android.measurement.BaseMeasurement;
import com.newrelic.agent.android.measurement.Measurement;
import com.newrelic.agent.android.measurement.MeasurementTest;
import com.newrelic.agent.android.measurement.MeasurementType;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;
import java.util.HashSet;

public class BaseMeasurementConsumerTest extends MeasurementTest {
    BaseMeasurementConsumer consumer;
    BaseMeasurement baseMeasurement;

    @Before
    public void setUp() throws Exception {
        consumer = new BaseMeasurementConsumer(MeasurementType.Any);
        baseMeasurement = (BaseMeasurement) factory.provideMeasurement(MeasurementType.Any);
    }

    @Test
    public void getMeasurementType() {
        Assert.assertEquals(MeasurementType.Any, consumer.getMeasurementType());
    }

    @Test
    public void consumeMeasurement() {
        consumer.consumeMeasurement(baseMeasurement);
    }

    @Test
    public void consumeMeasurements() {
        Collection<Measurement> measurementPool = new HashSet<>();

        measurementPool.add(factory.provideMeasurement(MeasurementType.Any));
        measurementPool.add(factory.provideMeasurement(MeasurementType.Any));
        measurementPool.add(factory.provideMeasurement(MeasurementType.Any));
        measurementPool.add(factory.provideMeasurement(MeasurementType.Any));
        measurementPool.add(factory.provideMeasurement(MeasurementType.Any));

        consumer.consumeMeasurements(measurementPool);
    }
}