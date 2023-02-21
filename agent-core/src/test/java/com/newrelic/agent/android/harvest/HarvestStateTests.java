/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.harvest;

import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.stats.StatsEngine;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class HarvestStateTests {

    @Test
    public void testHarvestStateTransitions() {
        TestHarvester harvester = new TestHarvester();

        harvester.transition(Harvester.State.DISABLED);
        Assert.assertEquals(Harvester.State.DISABLED, harvester.getCurrentState());

        try {
            harvester.transition(Harvester.State.UNINITIALIZED);
        } catch (Exception e) {
            Assert.assertEquals(IllegalStateException.class, e.getClass());
        }

        // We've entered DISABLED state which cannot transition. Reset test harvester.
        harvester = new TestHarvester();

        // Transition to DISCONNECTED.
        // Valid next states are DISCONNECTED, UNINITIALIZED or CONNECTED.
        harvester.transition(Harvester.State.DISCONNECTED);
        Assert.assertEquals(Harvester.State.DISCONNECTED, harvester.getCurrentState());

        // Identity transition. DISCONNECTED -> DISCONNECTED
        harvester.transition(Harvester.State.DISCONNECTED);
        Assert.assertEquals(Harvester.State.DISCONNECTED, harvester.getCurrentState());

        // DISCONNECTED -> UNINITIALIZED -> DISCONNECTED
        harvester.transition(Harvester.State.UNINITIALIZED);
        Assert.assertEquals(Harvester.State.UNINITIALIZED, harvester.getCurrentState());

        harvester.transition(Harvester.State.DISCONNECTED);
        Assert.assertEquals(Harvester.State.DISCONNECTED, harvester.getCurrentState());

        // Transition to CONNECTED.
        // Valid next states are CONNECTED, DISCONNECTED, DISABLED.
        harvester.transition(Harvester.State.CONNECTED);
        Assert.assertEquals(Harvester.State.CONNECTED, harvester.getCurrentState());

        // CONNECTED -> DISCONNECTED -> CONNECTED
        harvester.transition(Harvester.State.DISCONNECTED);
        Assert.assertEquals(Harvester.State.DISCONNECTED, harvester.getCurrentState());

        harvester.transition(Harvester.State.CONNECTED);
        Assert.assertEquals(Harvester.State.CONNECTED, harvester.getCurrentState());

        // Finally test CONNECTED -> DISABLED
        harvester.transition(Harvester.State.DISABLED);
        Assert.assertEquals(Harvester.State.DISABLED, harvester.getCurrentState());

        try {
            harvester.transition(Harvester.State.CONNECTED);
        } catch (Exception e) {
            Assert.assertEquals(IllegalStateException.class, e.getClass());
        }
        Assert.assertEquals(Harvester.State.DISABLED, harvester.getCurrentState());
    }

    @Test
    public void testInvalidDataToken() {
        TestHarvester harvester = new TestHarvester();
        harvester.setHarvestData(new HarvestData());

        harvester.transition(Harvester.State.CONNECTED);
        Assert.assertEquals(harvester.getCurrentState(), Harvester.State.CONNECTED);

        harvester.getHarvestData().getDataToken().clear(); // invalidate the data token
        harvester.connected();

        Assert.assertTrue(harvester.stateChanged);
        Assert.assertEquals(harvester.getCurrentState(), Harvester.State.DISCONNECTED);
        Assert.assertTrue(StatsEngine.SUPPORTABILITY.getStatsMap().keySet().contains(MetricNames.SUPPORTABILITY_INVALID_DATA_TOKEN));
    }

    // Increases visibility of methods for testing.
    private class TestHarvester extends Harvester {
        @Override
        public void execute() {
            super.execute();
        }

        @Override
        public void transition(Harvester.State newState) {
            // Reset stateChanged each transition for easier testing.
            stateChanged = false;
            super.transition(newState);
        }

        // Override the state methods, as we just want to test transitions here.
        @Override
        protected void uninitialized() {
        }

        @Override
        protected void disconnected() {
        }

        @Override
        protected void disabled() {
        }
    }
}
