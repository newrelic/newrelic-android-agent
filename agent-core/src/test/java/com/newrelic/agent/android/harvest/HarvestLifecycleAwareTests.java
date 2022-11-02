/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.harvest;

import com.newrelic.agent.android.AgentConfiguration;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class HarvestLifecycleAwareTests {
    @Before
    public void setUp() throws Exception {
        Harvest.setInstance(new Harvest());
        Harvest.initialize(new AgentConfiguration());
    }

    @After
    public void tearDown() throws Exception {
        Harvest.shutdown();
    }

    @Test
    public void testRegisterBeforeInitialization() {
        TestHarvestAdapter listener = new TestHarvestAdapter();

        Harvest.addHarvestListener(listener);
        Assert.assertFalse(listener.isStarted());
        Harvest.start();
        Assert.assertTrue(listener.isStarted());
    }

    public void testRegisterAndUnregisterBeforeInitialization() {
        TestHarvestAdapter listener = new TestHarvestAdapter();

        Harvest.addHarvestListener(listener);
        Assert.assertFalse(listener.isStarted());
        Harvest.removeHarvestListener(listener);
        Harvest.initialize(new AgentConfiguration());
        Harvest.start();
        Assert.assertFalse(listener.isStarted());
    }

    private class TestHarvestAdapter extends HarvestAdapter {
        boolean started;

        @Override
        public void onHarvestStart() {
            started = true;
        }

        private boolean isStarted() {
            return started;
        }
    }
}
