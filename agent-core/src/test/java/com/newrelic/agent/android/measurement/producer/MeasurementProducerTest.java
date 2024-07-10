/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.measurement.producer;

import com.newrelic.agent.android.measurement.Measurement;
import com.newrelic.agent.android.measurement.MeasurementTest;
import com.newrelic.agent.android.measurement.MeasurementType;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;
import java.util.HashSet;

public class MeasurementProducerTest extends MeasurementTest {

    MeasurementProducer producer;

    @Before
    public void setUp() throws Exception {
        measurementPool = new HashSet<>();
        producer = new MeasurementProducer() {
            Collection<Measurement> measurements = new HashSet<>();

            @Override
            public MeasurementType getMeasurementType() {
                return MeasurementType.Custom;
            }

            @Override
            public void produceMeasurement(Measurement measurement) {
                measurements.add(measurement);
            }

            @Override
            public void produceMeasurements(Collection<Measurement> measurements) {
                this.measurements.addAll(measurements);
            }

            @Override
            public Collection<Measurement> drainMeasurements() {
                return measurements;
            }
        };
    }

    @Test
    public void getMeasurementType() {
        Assert.assertEquals(MeasurementType.Custom, producer.getMeasurementType());
    }

    @Test
    public void produceMeasurement() {
        producer.produceMeasurement(factory.provideCustomMeasurement());
        Assert.assertEquals(1, producer.drainMeasurements().size());
    }

    @Test
    public void produceMeasurements() {
        measurementPool.add(factory.provideCustomMeasurement());
        measurementPool.add(factory.provideCustomMeasurement());
        measurementPool.add(factory.provideCustomMeasurement());

        producer.produceMeasurements(measurementPool);
        Assert.assertEquals(measurementPool.size(), producer.drainMeasurements().size());
    }

    @Test
    public void drainMeasurements() {
        measurementPool.add(factory.provideCustomMeasurement());
        measurementPool.add(factory.provideCustomMeasurement());
        measurementPool.add(factory.provideCustomMeasurement());

        producer.produceMeasurements(measurementPool);
        Assert.assertEquals(measurementPool.size(), producer.drainMeasurements().size());
    }
}