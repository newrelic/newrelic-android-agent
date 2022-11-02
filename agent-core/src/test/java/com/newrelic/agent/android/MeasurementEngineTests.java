/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android;

import com.newrelic.agent.android.activity.MeasuredActivity;
import com.newrelic.agent.android.harvest.Harvest;
import com.newrelic.agent.android.measurement.MeasurementException;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MeasurementEngineTests {

    private MeasurementEngine measurementEngine;

    @Before
    public void initialize() {
        measurementEngine = new MeasurementEngine();
        Harvest.initialize(new AgentConfiguration());
        Measurements.initialize();
    }

    @After
    public void shutdown() {
        Harvest.shutdown();
        Measurements.shutdown();
    }

    @Test
    public void testTrivialUserDefinedActivity() {

        Measurements.startActivity("SimpleActivity");
        Measurements.endActivity("SimpleActivity");
        Assert.assertEquals(0, measurementEngine.activities.size());

        MeasuredActivity activity = Measurements.startActivity("Another Activity");
        Measurements.endActivity(activity);
        Assert.assertEquals(0, measurementEngine.activities.size());
    }

    @Test
    public void startActivity() throws InterruptedException {
        Assert.assertEquals(1, measurementEngine.rootMeasurementPool.getMeasurementProducers().size());
        Assert.assertEquals(0, measurementEngine.rootMeasurementPool.getMeasurementConsumers().size());

        MeasuredActivity activity = measurementEngine.startActivity("newActivityName");
        // measurement pools are created on background thread, so will not be available on call return
        Assert.assertEquals(0, measurementEngine.rootMeasurementPool.getMeasurementConsumers().size());

        Assert.assertEquals(1, measurementEngine.activities.size());
        Assert.assertTrue(measurementEngine.activities.containsKey(activity.getName()));

        // so snooze a bit
        Thread.sleep(1000);
        Assert.assertEquals(1, measurementEngine.rootMeasurementPool.getMeasurementProducers().size());
        Assert.assertEquals(1, measurementEngine.rootMeasurementPool.getMeasurementConsumers().size());
    }

    @Test
    public void startEndActivity() {
        MeasuredActivity activity = measurementEngine.startActivity("newActivityName");
        Assert.assertNotNull(activity);
        Assert.assertEquals(1, measurementEngine.activities.size());
        Assert.assertEquals(1, measurementEngine.rootMeasurementPool.getMeasurementProducers().size());
        Assert.assertEquals(0, measurementEngine.rootMeasurementPool.getMeasurementConsumers().size());

        measurementEngine.endActivity(activity);
        Assert.assertTrue(activity.isFinished());
        Assert.assertFalse(measurementEngine.activities.containsKey(activity.getName()));
        Assert.assertEquals(0, measurementEngine.activities.size());
    }

    @Test
    public void endActivity() {
        MeasuredActivity activity = measurementEngine.startActivity("newActivityName");
        Assert.assertTrue(measurementEngine.activities.containsKey(activity.getName()));
        try {
            measurementEngine.endActivity("a bogus name");
            Assert.fail("Should throw assertion");
        } catch (Exception e) {
            Assert.assertTrue(e instanceof MeasurementException);
        }
    }

    @Test
    public void renameActivity() {
        MeasuredActivity activity = measurementEngine.startActivity("oldActivityName");
        Assert.assertTrue(measurementEngine.activities.containsKey(activity.getName()));
        measurementEngine.renameActivity("oldActivityName", "newActivityName");
        Assert.assertTrue(activity.getName().equals("newActivityName"));
    }

    @Test
    public void clear() {
        MeasuredActivity activity = measurementEngine.startActivity("newActivityName");
        Assert.assertTrue(measurementEngine.activities.containsKey(activity.getName()));
        measurementEngine.clear();
        Assert.assertFalse(measurementEngine.activities.containsKey(activity.getName()));
        Assert.assertEquals(0, measurementEngine.activities.size());
    }
}
