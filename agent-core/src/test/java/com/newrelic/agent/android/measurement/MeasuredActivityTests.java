/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.measurement;

import com.newrelic.agent.android.activity.BaseMeasuredActivity;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MeasuredActivityTests {

    @Test
    public void testMeasuredActivityFields() {
        BaseMeasuredActivity activity = new BaseMeasuredActivity();

        activity.setName("Test activity");

        long time = System.currentTimeMillis();
        activity.setStartTime(time);
        Assert.assertEquals(time, activity.getStartTime());

        activity.setEndTime(time);
        Assert.assertEquals(time, activity.getEndTime());

        ThreadInfo ti = new ThreadInfo() {
            @Override
            public long getId() {
                return 0;
            }

            @Override
            public String getName() {
                return "test thread";
            }
        };

        activity.setStartingThread(ti);
        Assert.assertEquals(ti, activity.getStartingThread());

        activity.setEndingThread(ti);
        Assert.assertEquals(ti, activity.getEndingThread());

        activity.setAutoInstrumented(true);
        Assert.assertEquals(true, activity.isAutoInstrumented());

        Assert.assertFalse(activity.isFinished());
        activity.finish();
        Assert.assertTrue(activity.isFinished());
    }

    @Test
    public void testFinishedActivity() throws Exception {
        BaseMeasuredActivity activity = new BaseMeasuredActivity();

        activity.setName("Test activity");
        Assert.assertEquals("Should rename activity", activity.getName(), "Test activity");
        activity.finish();

        try {
            activity.setName("New activity");
            Assert.assertEquals("Should not rename activity", activity.getName(), "Test activity");
        } catch (MeasurementException e) {
            Assert.fail("Should not throw MeasurementException!");
        }
    }
}
