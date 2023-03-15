/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation.httpclient;

import com.newrelic.agent.android.harvest.HttpTransaction;
import com.newrelic.agent.android.harvest.HttpTransactions;
import com.newrelic.agent.android.instrumentation.TransactionState;
import com.newrelic.agent.android.test.mock.Providers;
import com.newrelic.agent.android.test.mock.TestHarvest;

import org.apache.http.HttpEntity;
import org.apache.http.entity.ByteArrayEntity;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.net.UnknownHostException;

import static com.newrelic.agent.android.harvest.type.HarvestErrorCodes.NSURLErrorDNSLookupFailed;

@SuppressWarnings("deprecation")
public class HttpResponseEntityImplTest {
    private TransactionState transactionState;
    private HttpResponseEntityImpl httpResponseEntity;
    private TestHarvest testHarvest;

    @Before
    public void setUp() throws Exception {
        HttpEntity httpEntity = new ByteArrayEntity("ByteArrayEntity".getBytes());

        testHarvest = new TestHarvest();
        transactionState = Providers.provideTransactionState();
        httpResponseEntity = new HttpResponseEntityImpl(httpEntity, transactionState, 666);
    }

    @Test
    public void handleException() throws Exception {
        httpResponseEntity.handleException(new UnknownHostException(), 666L);

        HttpTransactions transactions = testHarvest.verifyQueuedTransactions(1);    // add an HttpTransaction for the request
        HttpTransaction transaction = transactions.getHttpTransactions().iterator().next();
        Assert.assertEquals(NSURLErrorDNSLookupFailed, transactionState.getErrorCode());
        Assert.assertEquals(666, transaction.getBytesReceived());
    }
}