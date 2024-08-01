/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class MeasurementsTest {

    @Before
    public void setUp() throws Exception {
        Measurements.initialize();
    }

    @After
    public void tearDown() throws Exception {
        Measurements.shutdown();
    }

    @Test
    public void initialize() {
        Assert.assertEquals(5, Measurements.measurementEngine.getRootMeasurementPool().getMeasurementProducers().size());
        Assert.assertEquals(5, Measurements.measurementEngine.getRootMeasurementPool().getMeasurementConsumers().size());

        Assert.assertTrue(TaskQueue.getQueue().isEmpty());
        Assert.assertNotNull(TaskQueue.dequeueFuture);
    }

    @Test
    public void shutdown() {
        Assert.assertEquals(5, Measurements.measurementEngine.getRootMeasurementPool().getMeasurementProducers().size());
        Assert.assertEquals(5, Measurements.measurementEngine.getRootMeasurementPool().getMeasurementConsumers().size());

        Measurements.shutdown();
        Measurements.measurementEngine.removeMeasurementProducer(Measurements.measurementEngine.getRootMeasurementPool());

        Assert.assertEquals(0, Measurements.measurementEngine.getRootMeasurementPool().getMeasurementProducers().size());
        Assert.assertEquals(0, Measurements.measurementEngine.getRootMeasurementPool().getMeasurementConsumers().size());

        Assert.assertTrue(TaskQueue.getQueue().isEmpty());
        Assert.assertNull(TaskQueue.dequeueFuture);
    }

    @Test
    public void addHttpTransaction() {
    }

    @Test
    public void addCustomMetric() {
    }

    @Test
    public void testAddCustomMetric() {
    }

    @Test
    public void setBroadcastNewMeasurements() {
    }

    @Test
    public void broadcast() {
    }

    @Test
    public void startActivity() {
    }

    @Test
    public void renameActivity() {
    }

    @Test
    public void endActivity() {
    }

    @Test
    public void testEndActivity() {
    }

    @Test
    public void endActivityWithoutMeasurement() {
    }

    @Test
    public void addTracedMethod() {
    }

    @Test
    public void addMeasurementProducer() {
    }
}