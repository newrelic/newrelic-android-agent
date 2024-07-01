/*
 * Copyright (c) 2022-2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.measurement;

import com.newrelic.agent.android.Measurements;
import com.newrelic.agent.android.activity.MeasuredActivity;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.concurrent.TimeUnit;

@RunWith(JUnit4.class)
public class MeasurementEngineTests {

    private MeasurementEngine measurementEngine;

    @Before
    public void initialize() {
        measurementEngine = new MeasurementEngine();
    }

    @Test
    public void testTrivialUserDefinedActivity() {

        Measurements.startActivity("SimpleActivity");
        Measurements.endActivity("SimpleActivity");
        Assert.assertEquals(0, measurementEngine.getActivities().size());

        MeasuredActivity activity = Measurements.startActivity("Another Activity");
        Measurements.endActivity(activity);
        Assert.assertEquals(0, measurementEngine.getActivities().size());
    }

    @Test
    public void startActivity() {
        MeasuredActivity activity = measurementEngine.startActivity("newActivityName");

        Assert.assertEquals(1, measurementEngine.getActivities().size());
        Assert.assertTrue(measurementEngine.getActivities().containsKey(activity.getName()));

        // measurement pools are created on background thread, so will not be available on call return
        drainWorkerThread();

        Assert.assertEquals(1, measurementEngine.getRootMeasurementPool().getMeasurementProducers().size());
        Assert.assertEquals(1, measurementEngine.getRootMeasurementPool().getMeasurementConsumers().size());
    }

    @Test
    public void startEndActivity() {
        MeasuredActivity activity = measurementEngine.startActivity("newActivityName");
        Assert.assertNotNull(activity);
        Assert.assertEquals(1, measurementEngine.getActivities().size());

        // measurement pools are created on background thread, so will not be available on call return
        drainWorkerThread();

        Assert.assertEquals(1, measurementEngine.getRootMeasurementPool().getMeasurementProducers().size());
        Assert.assertEquals(1, measurementEngine.getRootMeasurementPool().getMeasurementConsumers().size());


        measurementEngine.endActivity(activity);
        Assert.assertTrue(activity.isFinished());
        Assert.assertFalse(measurementEngine.getActivities().containsKey(activity.getName()));
        Assert.assertEquals(0, measurementEngine.getActivities().size());
    }

    @Test
    public void endActivity() {
        MeasuredActivity activity = measurementEngine.startActivity("newActivityName");
        Assert.assertTrue(measurementEngine.getActivities().containsKey(activity.getName()));

        // measurement pools are created on background thread, so will not be available on call return
        drainWorkerThread();

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
        Assert.assertTrue(measurementEngine.getActivities().containsKey(activity.getName()));

        // measurement pools are created on background thread, so will not be available on call return
        drainWorkerThread();

        measurementEngine.renameActivity("oldActivityName", "newActivityName");
        Assert.assertTrue(activity.getName().equals("newActivityName"));
    }

    @Test
    public void clear() {
        MeasuredActivity activity = measurementEngine.startActivity("newActivityName");
        Assert.assertTrue(measurementEngine.getActivities().containsKey(activity.getName()));
        measurementEngine.clear();
        Assert.assertFalse(measurementEngine.getActivities().containsKey(activity.getName()));
        Assert.assertEquals(0, measurementEngine.getActivities().size());
    }

    private void drainWorkerThread() {
        measurementEngine.worker.shutdown();
        try {
            measurementEngine.worker.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}
