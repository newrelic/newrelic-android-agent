/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.measurement.consumer;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.Measurements;
import com.newrelic.agent.android.harvest.Harvest;
import com.newrelic.agent.android.measurement.BaseMeasurement;
import com.newrelic.agent.android.measurement.CustomMetricMeasurement;
import com.newrelic.agent.android.measurement.Measurement;
import com.newrelic.agent.android.measurement.MeasurementTest;
import com.newrelic.agent.android.measurement.MeasurementType;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class MetricMeasurementTest extends MeasurementTest {

    MetricMeasurementConsumer consumer;

    @Before
    public void setUp() throws Exception {
        MeasurementType type = factory.provideMeasurement().getType();
        consumer = new MetricMeasurementConsumer(type) {
            @Override
            protected String formatMetricName(String name) {
                return "metric#" + name + getMeasurementType().name().toUpperCase();
            }
        };
        consumer = Mockito.spy(consumer);
    }

    @Test
    public void formatMetricName() {
        Assert.assertEquals("metric#metricName" + consumer.getMeasurementType().name().toUpperCase(), consumer.formatMetricName("metricName"));
    }

    @Test
    public void consumeMeasurement() {
        Assert.assertTrue(consumer.recordUnscopedMetrics);
        CustomMetricMeasurement scopedMeasurement = factory.provideCustomMeasurement();
        scopedMeasurement.setScope("scoped/Metric");
        consumer.consumeMeasurement(factory.provideCategorizedMeasurement());
        Assert.assertEquals(2, consumer.getMetrics().getAll().size());
        Assert.assertEquals(1, consumer.getMetrics().getAllUnscoped().size());

        consumer.metrics.clear();
        consumer.recordUnscopedMetrics = false;
        consumer.consumeMeasurement(scopedMeasurement);
        Assert.assertFalse(consumer.recordUnscopedMetrics);
        Assert.assertEquals(1, consumer.getMetrics().getAll().size());
        Assert.assertEquals(0, consumer.getMetrics().getAllUnscoped().size());

        consumer.metrics.clear();
        CustomMetricMeasurement unscopedMeasurement = factory.provideCustomMeasurement();
        unscopedMeasurement.setScope(null);
        consumer.consumeMeasurement(unscopedMeasurement);
        Assert.assertEquals(0, consumer.getMetrics().getAll().size());
        Assert.assertEquals(0, consumer.getMetrics().getAllUnscoped().size());
    }

    @Test
    public void addMetric() {
        consumer.addMetric(factory.provideMetric());
        Assert.assertEquals(1, consumer.getMetrics().getAll().size());
    }

    @Test
    public void getMetrics() {
        consumer.addMetric(factory.provideMetric());

        Assert.assertNotNull(consumer.getMetrics());
        Assert.assertEquals(1, consumer.getMetrics().getAll().size());
        Assert.assertEquals(1, consumer.getMetrics().getAllByScope("provided/Metric/Scope").size());
        Assert.assertEquals(0, consumer.getMetrics().getAllUnscoped().size());
    }

    @Test
    public void onHarvest() {
        consumer.addMetric(factory.provideMetric());
        consumer.addMetric(factory.provideMetric());
        consumer.addMetric(factory.provideMetric());
        Assert.assertEquals(3, consumer.metrics.getAllByScope("provided/Metric/Scope").size());

        Harvest.initialize(AgentConfiguration.getInstance());
        consumer.onHarvest();
        Assert.assertEquals(3, Harvest.getInstance().getHarvestData().getMetrics().getMetrics().getAll().size());
        Harvest.shutdown();
    }

    @Test
    public void onHarvestComplete() {
        consumer.addMetric(factory.provideMetric());
        consumer.addMetric(factory.provideMetric());
        consumer.addMetric(factory.provideMetric());
        Assert.assertEquals(3, consumer.metrics.getAll().size());

        consumer.onHarvestComplete();
        Assert.assertEquals(0, consumer.metrics.getAll().size());
    }

    @Test
    public void onHarvestError() {
        consumer.addMetric(factory.provideMetric());
        consumer.addMetric(factory.provideMetric());
        consumer.addMetric(factory.provideMetric());
        Assert.assertEquals(3, consumer.metrics.getAll().size());

        consumer.onHarvestError();
        Assert.assertEquals(3, consumer.metrics.getAll().size());
    }

    @Test
    public void onHarvestSendFailed() {
        consumer.addMetric(factory.provideMetric());
        consumer.addMetric(factory.provideMetric());
        consumer.addMetric(factory.provideMetric());
        Assert.assertEquals(3, consumer.metrics.getAll().size());

        consumer.onHarvestSendFailed();
        Assert.assertEquals(3, consumer.metrics.getAll().size());
    }
}
