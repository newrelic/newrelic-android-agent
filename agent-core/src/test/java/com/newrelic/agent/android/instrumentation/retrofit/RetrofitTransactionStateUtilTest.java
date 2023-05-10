/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation.retrofit;

import com.newrelic.agent.android.TaskQueue;
import com.newrelic.agent.android.instrumentation.TransactionState;
import com.newrelic.agent.android.test.mock.Providers;
import com.newrelic.agent.android.test.mock.TestHarvest;

import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;

import retrofit.client.Header;
import retrofit.client.Response;

import static com.newrelic.agent.android.harvest.type.HarvestErrorCodes.NSURLErrorDNSLookupFailed;

public class RetrofitTransactionStateUtilTest {

    private TransactionState transactionState;
    private TestHarvest testHarvest;

    @Before
    public void setUp() throws Exception {
        testHarvest = new TestHarvest();
        transactionState = Providers.provideTransactionState();
        TaskQueue.clear();
        TaskQueue.synchronousDequeue();
    }

    @Test
    public void addTransactionAndErrorData() throws Exception {
        Response response = provideErrorResponse();

        transactionState.setStatusCode(response.getStatus());
        RetrofitTransactionStateUtil.addTransactionAndErrorData(transactionState, response);

        testHarvest.verifyQueuedTransactions(1);

    }

    @Test
    public void addTransactionAndErrorDataFailure() throws Exception {
        Response response = provideErrorResponse();

        transactionState.setErrorCode(NSURLErrorDNSLookupFailed);
        RetrofitTransactionStateUtil.addTransactionAndErrorData(transactionState, response);

        testHarvest.verifyQueuedTransactions(1);
    }

    private Response provideErrorResponse() {
        return new Response(Providers.APP_URL, HttpStatus.SC_BAD_GATEWAY, "Bad Gateway", new ArrayList<Header>(), null);
    }

}