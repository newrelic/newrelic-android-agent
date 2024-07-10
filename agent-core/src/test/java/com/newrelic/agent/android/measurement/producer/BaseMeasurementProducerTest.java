/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.measurement.producer;

import com.newrelic.agent.android.measurement.BaseMeasurement;
import com.newrelic.agent.android.measurement.Measurement;
import com.newrelic.agent.android.measurement.MeasurementTest;
import com.newrelic.agent.android.measurement.MeasurementType;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;

public class BaseMeasurementProducerTest extends MeasurementTest {
    BaseMeasurementProducer producer;

    @Before
    public void setUp() throws Exception {
        producer = new BaseMeasurementProducer(MeasurementType.Method);
    }

    @Test
    public void testGetProducedMeasurementType() {
        Assert.assertEquals(MeasurementType.Method, producer.getMeasurementType());
    }

    @Test
    public void testProduceMeasurement() {
        Measurement measurement = factory.provideMeasurement(MeasurementType.Method);

        producer.produceMeasurement(measurement);
        Collection<Measurement> producedMeasurements = producer.drainMeasurements();
        Assert.assertEquals(1, producedMeasurements.size());

        Measurement producedMeasurement = producedMeasurements.iterator().next();
        Assert.assertEquals(measurement, producedMeasurement);
    }

    @Test
    public void testDrainMeasurements() {
        Measurement measurement = factory.provideMeasurement(MeasurementType.Method);

        producer.produceMeasurement(measurement);

        Collection<Measurement> drainedMeasurements = producer.drainMeasurements();
        Assert.assertEquals(1, drainedMeasurements.size());

        // Should now be empty
        Assert.assertEquals(0, producer.drainMeasurements().size());

        // Add many more Measurements
        int numMeasurements = 1000;
        for (int i = 0; i < numMeasurements; i++) {
            measurement = new BaseMeasurement(MeasurementType.Method);
            producer.produceMeasurement(measurement);
        }

        drainedMeasurements = producer.drainMeasurements();
        Assert.assertEquals(numMeasurements, drainedMeasurements.size());

        // Should be empty again
        Assert.assertEquals(0, producer.drainMeasurements().size());
    }

}