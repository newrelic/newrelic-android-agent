/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation.okhttp2;

import com.newrelic.agent.android.instrumentation.TransactionState;
import com.newrelic.agent.android.test.mock.Providers;
import com.newrelic.agent.android.test.mock.TestHarvest;
import com.squareup.okhttp.Callback;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.net.UnknownHostException;

import static com.newrelic.agent.android.harvest.type.HarvestErrorCodes.NSURLErrorDNSLookupFailed;
import static org.mockito.Mockito.mock;

public class CallbackExtensionTest {

    private TestHarvest testHarvest;
    private TransactionState transactionState;
    private CallbackExtension callbaackExtension;

    @Before
    public void setUp() throws Exception {
        testHarvest = new TestHarvest();
        transactionState = Providers.provideTransactionState();
        callbaackExtension = new CallbackExtension(mock(Callback.class), transactionState);
    }

    @After
    public void tearDown() throws Exception {
        testHarvest.shutdownHarvester();
    }

    @Test
    public void error() throws Exception {
        callbaackExtension.error(new UnknownHostException());
        Assert.assertEquals(NSURLErrorDNSLookupFailed, transactionState.getErrorCode());
    }

}