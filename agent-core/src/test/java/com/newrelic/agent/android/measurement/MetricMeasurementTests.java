/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.measurement;

import com.newrelic.agent.android.measurement.consumer.MetricMeasurementConsumer;
import com.newrelic.agent.android.metric.Metric;
import com.newrelic.agent.android.metric.MetricStore;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MetricMeasurementTests {
    private static final String TEST_NAME = "test name";
    private static final String TEST_SCOPE = "test scope";
    private static final long TEST_START_TIME = 1000;
    private static final long TEST_END_TIME = 2000;
    private static final long TEST_EXCLUSIVE_TIME = 1000;
    private static final double TEST_EXCLUSIVE_TIME_IN_SECONDS = 1.0;
    private static final double TEST_DELTA = 1.0;

    @Test
    public void testAddMetricWithoutScopedMetric() {
        TestMetricMeasurementConsumer metricMeasurementConsumer = new TestMetricMeasurementConsumer();

        Metric unScopedMetric = new Metric(TEST_NAME);
        unScopedMetric.setCount(1);
        metricMeasurementConsumer.testAddMetric(unScopedMetric);

        Assert.assertEquals(1, metricMeasurementConsumer.getMetricStore().getAll().size());
        Assert.assertEquals(1, metricMeasurementConsumer.getMetricStore().get(TEST_NAME).getCount());

        metricMeasurementConsumer.testAddMetric(unScopedMetric);
        Assert.assertEquals(1, metricMeasurementConsumer.getMetricStore().getAll().size());
        Assert.assertEquals(2, metricMeasurementConsumer.getMetricStore().get(TEST_NAME).getCount());
    }

    @Test
    public void testAddMetricWithScopedMetric() {
        TestMetricMeasurementConsumer metricMeasurementConsumer = new TestMetricMeasurementConsumer();

        Metric scopedMetric = new Metric(TEST_NAME, TEST_SCOPE);
        scopedMetric.setCount(1);
        metricMeasurementConsumer.testAddMetric(scopedMetric);

        Assert.assertEquals(1, metricMeasurementConsumer.getMetricStore().getAll().size());
        Assert.assertEquals(1, metricMeasurementConsumer.getMetricStore().get(TEST_NAME, TEST_SCOPE).getCount());

        metricMeasurementConsumer.testAddMetric(scopedMetric);
        Assert.assertEquals(1, metricMeasurementConsumer.getMetricStore().getAll().size());
        Assert.assertEquals(2, metricMeasurementConsumer.getMetricStore().get(TEST_NAME, TEST_SCOPE).getCount());
    }

    @Test
    public void testConsumeMeasurementWithoutScopedMeasurement() {
        TestMetricMeasurementConsumer metricMeasurementConsumer = new TestMetricMeasurementConsumer();

        BaseMeasurement measurement = measurementFactory();

        metricMeasurementConsumer.consumeMeasurement(measurement);

        Assert.assertEquals(1, metricMeasurementConsumer.getMetricStore().getAll().size());

        Metric unScopedMetric = metricMeasurementConsumer.getMetricStore().get(TEST_NAME);

        Assert.assertEquals(TEST_NAME, unScopedMetric.getName());
        Assert.assertEquals("", unScopedMetric.getStringScope());
        Assert.assertEquals(TEST_DELTA, unScopedMetric.getTotal(), 0.0);
        Assert.assertEquals(TEST_EXCLUSIVE_TIME_IN_SECONDS, unScopedMetric.getExclusive(), 0.0);
        Assert.assertEquals(1, unScopedMetric.getCount());
    }

    @Test
    public void testConsumeMeasurementWithScopedMeasurement() {
        TestMetricMeasurementConsumer metricMeasurementConsumer = new TestMetricMeasurementConsumer();

        BaseMeasurement measurement = measurementFactory();
        measurement.setScope(TEST_SCOPE);

        metricMeasurementConsumer.consumeMeasurement(measurement);

        Assert.assertEquals(2, metricMeasurementConsumer.getMetricStore().getAll().size());

        Metric unScopedMetric = metricMeasurementConsumer.getMetricStore().get(TEST_NAME);

        Assert.assertEquals(TEST_NAME, unScopedMetric.getName());
        Assert.assertEquals("", unScopedMetric.getStringScope());
        Assert.assertEquals(TEST_DELTA, unScopedMetric.getTotal(), 0.0);
        Assert.assertEquals(TEST_EXCLUSIVE_TIME_IN_SECONDS, unScopedMetric.getExclusive(), 0.0);
        Assert.assertEquals(1, unScopedMetric.getCount());

        Metric scopedMetric = metricMeasurementConsumer.getMetricStore().get(TEST_NAME, TEST_SCOPE);
        Assert.assertEquals(TEST_NAME, scopedMetric.getName());
        Assert.assertEquals(TEST_SCOPE, scopedMetric.getStringScope());
        Assert.assertEquals(TEST_DELTA, scopedMetric.getTotal(), 0.0);
        Assert.assertEquals(TEST_EXCLUSIVE_TIME_IN_SECONDS, scopedMetric.getExclusive(), 0.0);
        Assert.assertEquals(1, scopedMetric.getCount());
    }

    @Test
    public void testOnHarvestComplete() {
        TestMetricMeasurementConsumer metricMeasurementConsumer = new TestMetricMeasurementConsumer();

        metricMeasurementConsumer.consumeMeasurement(measurementFactory());
        metricMeasurementConsumer.onHarvestComplete();
        Assert.assertTrue(metricMeasurementConsumer.getMetricStore().getAll().isEmpty());
    }

    @Test
    public void testOnHarvestError() {
        TestMetricMeasurementConsumer metricMeasurementConsumer = new TestMetricMeasurementConsumer();

        metricMeasurementConsumer.consumeMeasurement(measurementFactory());
        metricMeasurementConsumer.onHarvestError();
        Assert.assertTrue(metricMeasurementConsumer.getMetricStore().getAll().isEmpty());
    }

    @Test
    public void testOnHarvestSendFailed() {
        TestMetricMeasurementConsumer metricMeasurementConsumer = new TestMetricMeasurementConsumer();

        metricMeasurementConsumer.consumeMeasurement(measurementFactory());
        metricMeasurementConsumer.onHarvestSendFailed();
        Assert.assertTrue(metricMeasurementConsumer.getMetricStore().getAll().isEmpty());
    }

    private BaseMeasurement measurementFactory() {
        BaseMeasurement measurement = new BaseMeasurement(MeasurementType.Machine);

        measurement.setName(TEST_NAME);
        measurement.setStartTime(TEST_START_TIME);
        measurement.setEndTime(TEST_END_TIME);
        measurement.setExclusiveTime(TEST_EXCLUSIVE_TIME);

        return measurement;
    }

    private class TestMetricMeasurementConsumer extends MetricMeasurementConsumer {
        public TestMetricMeasurementConsumer() {
            super(MeasurementType.Machine);
        }

        @Override
        protected String formatMetricName(String name) {
            return name;
        }

        public MetricStore getMetricStore() {
            return metrics;
        }

        public void testAddMetric(Metric metric) {
            addMetric(metric);
        }
    }
}
