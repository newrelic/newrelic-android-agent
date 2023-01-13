/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.measurement;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MeasurementTests {

    @Test
    public void testGetMeasurementType() {
        Measurement measurement = new BaseMeasurement(MeasurementType.Activity);
        Assert.assertEquals(MeasurementType.Activity, measurement.getType());
    }

    @Test
    public void testIsInstantaneous() {
        BaseMeasurement measurement = new BaseMeasurement(MeasurementType.Activity);

        measurement.setStartTime(System.currentTimeMillis());

        Assert.assertTrue(measurement.isInstantaneous());

        measurement.setEndTime(System.currentTimeMillis());

        Assert.assertFalse(measurement.isInstantaneous());
    }

    @Test
    public void testInvalidEndTime() {
        BaseMeasurement measurement = new BaseMeasurement(MeasurementType.Activity);
        measurement.setStartTime(System.currentTimeMillis());
        try {
            measurement.setEndTime(measurement.getStartTime() - 1);
        } catch (Exception e) {
            Assert.assertEquals(IllegalArgumentException.class, e.getClass());
        }
        Assert.assertEquals(0, measurement.getEndTime());
    }

    @Test
    public void testFinish() {
        Measurement measurement = new BaseMeasurement(MeasurementType.Activity);
        measurement.finish();
        Assert.assertTrue(measurement.isFinished());

        // Ensure calling finish on an already finished Measurement results in an exception.
        try {
            measurement.finish();
        } catch (Exception e) {
            Assert.assertEquals(MeasurementException.class, e.getClass());
            Assert.assertEquals("Finish called on already finished Measurement", e.getMessage());
        }
    }

    @Test
    public void testImmutableAfterFinished() {
        BaseMeasurement measurement = new BaseMeasurement(MeasurementType.Activity);
        measurement.finish();
        try {
            measurement.setEndTime(System.currentTimeMillis());
        } catch (Exception e) {
            Assert.assertEquals(MeasurementException.class, e.getClass());
            Assert.assertEquals("Attempted to modify finished Measurement", e.getMessage());
        }
    }

    @Test
    public void testCustomMeasurement() {
        CustomMetricMeasurement measurement = new CustomMetricMeasurement();

        measurement.setName("Custom measurement"); // What does a Measurement name really look like?

        Assert.assertEquals("Custom measurement", measurement.getName());

        measurement.setThreadInfo(new ThreadInfo() {
            @Override
            public long getId() {
                return 42;
            }

            @Override
            public String getName() {
                return "test thread";
            }
        });

        Assert.assertEquals(42, measurement.getThreadInfo().getId());
        Assert.assertEquals("test thread", measurement.getThreadInfo().getName());
    }

    @Test
    public void testThreadInfo() {
        CustomMetricMeasurement measurement = new CustomMetricMeasurement();
        measurement.setThreadInfo(ThreadInfo.fromThread(Thread.currentThread()));
        CustomMetricMeasurement measurement2 = new CustomMetricMeasurement();
        measurement2.setThreadInfo(new ThreadInfo());

        Assert.assertEquals(measurement.getThreadInfo().getId(), measurement2.getThreadInfo().getId());

    }
}
