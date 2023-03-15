/**
 * Copyright 2021 New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sample;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.Measurements;
import com.newrelic.agent.android.SpyContext;
import com.newrelic.agent.android.TaskQueue;
import com.newrelic.agent.android.harvest.Harvest;
import com.newrelic.agent.android.harvest.HarvestData;
import com.newrelic.agent.android.measurement.BaseMeasurement;
import com.newrelic.agent.android.measurement.MeasurementType;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class MachineMeasurementConsumerTest {

    private MachineMeasurementConsumer machineMeasurementConsumer;
    private SpyContext spyContext;

    @Before
    public void setUp() throws Exception {
        spyContext = new SpyContext();
        machineMeasurementConsumer = new MachineMeasurementConsumer();
        Sampler.init(spyContext.getContext());
    }

    @Before
    public void harvestSetUp() throws Exception {
        Harvest.initialize(new AgentConfiguration());
        Measurements.initialize();
    }

    @Test
    public void testMeasurementType() throws Exception {
        Assert.assertEquals("Should be machine measurement consumer", MeasurementType.Machine, machineMeasurementConsumer.getMeasurementType());
    }

    @Test
    public void testFormatMetricName() throws Exception {
        Assert.assertEquals("Measurement name should not be changed", "testName", machineMeasurementConsumer.formatMetricName("testName"));
    }

    @Test
    public void testConsumeMeasurement() throws Exception {
        BaseMeasurement measurement = new BaseMeasurement(MeasurementType.Machine);

        machineMeasurementConsumer.consumeMeasurement(measurement);     // should be ignored
        machineMeasurementConsumer.onHarvest();

        // StatsEngine queues its metrics. Run the queue to ensure they have been processed.
        TaskQueue.synchronousDequeue();

        HarvestData harvestData = Harvest.getInstance().getHarvestData();
        Assert.assertEquals("Should ignore consumed measurement", 1, harvestData.getMetrics().getMetrics().getAll().size());
    }

    @Test
    public void testOnHarvest() throws Exception {
        machineMeasurementConsumer.onHarvest();

        // StatsEngine queues its metrics. Run the queue to ensure they have been processed.
        TaskQueue.synchronousDequeue();

        HarvestData harvestData = Harvest.getInstance().getHarvestData();
        Assert.assertEquals("Should contain 1 measurement", 1, harvestData.getMetrics().getMetrics().getAll().size());
        Assert.assertNotNull("Should contain Memory/Used metric", harvestData.getMetrics().getMetrics().get("Memory/Used"));
    }
}