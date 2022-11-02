/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.test.mock;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.Measurements;
import com.newrelic.agent.android.TaskQueue;
import com.newrelic.agent.android.harvest.Harvest;
import com.newrelic.agent.android.harvest.HarvestData;
import com.newrelic.agent.android.harvest.HttpTransactions;

import static org.junit.Assert.assertEquals;

public class TestHarvest extends Harvest {

    public TestHarvest() {
        Harvest.setInstance(this);
        Harvest.initialize(new AgentConfiguration());

        TaskQueue.clear();
        harvestData.reset();

        Measurements.shutdown();;
        Measurements.initialize();
    }

    public static void shutdown() {
        Harvest.shutdown();
        Measurements.shutdown();
    }

    public HarvestData getHarvestData() {
        return harvestData;
    }

    public HttpTransactions getQueuedTransactions() throws InterruptedException {
        TaskQueue.synchronousDequeue();
        return harvestData.getHttpTransactions();
    }

    public HttpTransactions verifyQueuedTransactions(int count) throws InterruptedException {
        HttpTransactions transactions = getQueuedTransactions();
        assertEquals(count, transactions.count());
        return transactions;
    }
}

