/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.measurement;

import com.newrelic.agent.android.measurement.producer.BaseMeasurementProducer;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Collection;

@RunWith(JUnit4.class)
public class MeasurementProducerTests {

    @Test
    public void testGetProducedMeasurementType() {
        BaseMeasurementProducer producer = new BaseMeasurementProducer(MeasurementType.Method);
        Assert.assertEquals(MeasurementType.Method, producer.getMeasurementType());
    }

    @Test
    public void testProduceMeasurement() {
        BaseMeasurementProducer producer = new BaseMeasurementProducer(MeasurementType.Method);
        Measurement measurement = new BaseMeasurement(MeasurementType.Method);

        producer.produceMeasurement(measurement);
        Collection<Measurement> producedMeasurements = producer.drainMeasurements();
        Assert.assertEquals(1, producedMeasurements.size());

        Measurement producedMeasurement = producedMeasurements.iterator().next();
        Assert.assertEquals(measurement, producedMeasurement);
    }

    @Test
    public void testDrainMeasurements() {
        BaseMeasurementProducer producer = new BaseMeasurementProducer(MeasurementType.Method);
        Measurement measurement = new BaseMeasurement(MeasurementType.Method);

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
