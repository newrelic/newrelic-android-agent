/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.measurement.consumer;

import com.newrelic.agent.android.measurement.ActivityMeasurement;
import com.newrelic.agent.android.measurement.Measurement;
import com.newrelic.agent.android.measurement.MeasurementTest;
import com.newrelic.agent.android.measurement.MeasurementType;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;
import java.util.HashSet;

public class ActivityMeasurementConsumerTest extends MeasurementTest {

    ActivityMeasurementConsumer consumer;
    ActivityMeasurement activityMeasurement;

    @Before
    public void setUp() throws Exception {
        consumer = new ActivityMeasurementConsumer();
        activityMeasurement = (ActivityMeasurement) factory.provideMeasurement(MeasurementType.Activity);
    }

    @Test
    public void consumeMeasurement() {
        consumer.consumeMeasurement(activityMeasurement);
        Assert.assertEquals(2, consumer.metrics.getAll().size());
        Assert.assertEquals(1, consumer.metrics.getAllUnscoped().size());
        Assert.assertEquals(1, consumer.metrics.getAllByScope("providedMeasurementScope").size());
    }

    @Test
    public void formatMetricName() {
        Assert.assertEquals("activityMeasurement", consumer.formatMetricName("activityMeasurement"));
    }

    @Test
    public void consumeMeasurements() {
        Collection<Measurement> activityMeasurementPool = new HashSet<>();

        activityMeasurementPool.add(factory.provideMeasurement(MeasurementType.Activity));
        activityMeasurementPool.add(factory.provideMeasurement(MeasurementType.Activity));
        activityMeasurementPool.add(factory.provideMeasurement(MeasurementType.Activity));
        activityMeasurementPool.add(factory.provideMeasurement(MeasurementType.Activity));
        activityMeasurementPool.add(factory.provideMeasurement(MeasurementType.Activity));

        consumer.consumeMeasurements(activityMeasurementPool);
        Assert.assertFalse(consumer.metrics.isEmpty());
        Assert.assertEquals(2, consumer.metrics.getAll().size());
        Assert.assertEquals(5, consumer.metrics.getAllByScope("providedMeasurementScope").stream().mapToLong(x -> x.getCount()).sum());
        Assert.assertEquals(5, consumer.metrics.getAllUnscoped().stream().mapToLong(x -> x.getCount()).sum());
    }
}