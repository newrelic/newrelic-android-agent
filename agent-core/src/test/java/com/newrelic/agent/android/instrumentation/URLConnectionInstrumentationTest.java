/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation;

import com.newrelic.agent.android.test.mock.Providers;
import com.newrelic.agent.android.test.mock.TestHarvest;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.net.UnknownHostException;

public class URLConnectionInstrumentationTest {

    private TestHarvest testHarvest;
    private TransactionState transactionState;

    @Before
    public void setUp() throws Exception {
        testHarvest = new TestHarvest();
        transactionState = Providers.provideTransactionState();
    }

    @After
    public void tearDown() throws Exception {
        testHarvest.shutdownHarvester();
    }



    @Test
    public void httpClientError() throws Exception {
        URLConnectionInstrumentation.httpClientError(transactionState, new UnknownHostException());
        Assert.assertEquals(-1006, transactionState.getErrorCode());
    }


}