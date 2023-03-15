/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.measurement;

import com.newrelic.agent.android.measurement.consumer.CustomMetricConsumer;
import com.newrelic.agent.android.measurement.producer.CustomMetricProducer;
import com.newrelic.agent.android.metric.Metric;
import com.newrelic.agent.android.metric.MetricStore;
import com.newrelic.agent.android.metric.MetricUnit;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Collection;
import java.util.List;

@RunWith(JUnit4.class)
public class CustomMetricTests {

    @Test
    public void testMetricNameFilteringAndUnits() {
        CustomMetricProducer producer = new CustomMetricProducer();

        producer.produceMeasurement("Foo/bar", "Database" , 1, 1.0, 1.0, MetricUnit.OPERATIONS, MetricUnit.SECONDS);
        Collection<Measurement> measurements =  producer.drainMeasurements();

        Assert.assertEquals(1, measurements.size());

        CustomMetricMeasurement custom = (CustomMetricMeasurement) measurements.iterator().next();
        Assert.assertEquals("Database/Foobar[sec|op]", custom.getCustomMetric().getName());
    }

    @Test
    public void testConsumerAggregatesCustomMetric() {
        TestCustomMetricConsumer consumer = new TestCustomMetricConsumer();

        CustomMetricMeasurement metricMeasurement = new CustomMetricMeasurement("Foobar", 1, 1.0, 1.0);

        consumer.consumeMeasurement(metricMeasurement);

        MetricStore metrics = consumer.getMetrics();
        List<Metric> allMetrics = metrics.getAll();

        Assert.assertEquals(1, allMetrics.size());

        Metric metric = allMetrics.get(0);

        Assert.assertEquals("Custom/Foobar", metric.getName());
        Assert.assertEquals(1, metric.getCount());

        double e = 0.01;
        Assert.assertEquals(1.0, metric.getTotal(), e);

        metricMeasurement = new CustomMetricMeasurement("Foobar", 1, 1.0, 1.0);
        consumer.consumeMeasurement(metricMeasurement);
        Assert.assertEquals(2.0, metric.getTotal(), e);
        Assert.assertEquals(2, metric.getCount());
    }

    class TestCustomMetricConsumer extends CustomMetricConsumer {
        public MetricStore getMetrics() {
            return metrics;
        }
    }
}
