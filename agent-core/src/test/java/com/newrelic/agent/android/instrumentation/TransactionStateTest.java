/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation;

import com.newrelic.agent.android.api.common.TransactionData;
import com.newrelic.agent.android.api.v1.Defaults;
import com.newrelic.agent.android.distributedtracing.DistributedTracing;
import com.newrelic.agent.android.test.mock.Providers;

import org.apache.http.HttpStatus;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static com.newrelic.agent.android.harvest.type.HarvestErrorCodes.NSURLErrorCannotFindHost;

public class TransactionStateTest {
    private TransactionState transactionState;

    @Before
    public void setUp() throws Exception {
        transactionState = Providers.provideTransactionState();
    }

    @Test
    public void isSent() throws Exception {
        Assert.assertFalse(transactionState.isSent());
        transactionState.setBytesSent(666l);
        Assert.assertFalse(transactionState.isSent());
    }

    @Test
    public void isComplete() throws Exception {
        Assert.assertFalse(transactionState.isComplete());
        transactionState.end();
        Assert.assertTrue(transactionState.isComplete());
    }

    @Test
    public void end() throws Exception {
        Assert.assertFalse(transactionState.isComplete());
        TransactionData transactionData = transactionState.end();
        Assert.assertNotNull(transactionData);
    }

    @Test
    public void isErrorOrFailure() throws Exception {
        transactionState.setErrorCode(0);
        transactionState.setStatusCode(0);
        Assert.assertFalse(transactionState.isErrorOrFailure());

        transactionState.setStatusCode((int) Defaults.MIN_HTTP_ERROR_STATUS_CODE);
        Assert.assertTrue(transactionState.isErrorOrFailure());

        transactionState.setErrorCode(0);
        transactionState.setStatusCode(HttpStatus.SC_OK);
        Assert.assertFalse(transactionState.isErrorOrFailure());

        transactionState.setErrorCode(NSURLErrorCannotFindHost);
        Assert.assertTrue(transactionState.isErrorOrFailure());
    }

    @Test
    public void isNetworkFailure() throws Exception {
        transactionState.setErrorCode(0);
        Assert.assertFalse(transactionState.isRequestFailure());

        transactionState.setErrorCode(NSURLErrorCannotFindHost);
        Assert.assertTrue(transactionState.isRequestFailure());
    }

    @Test
    public void isRequestError() throws Exception {
        transactionState.setStatusCode(HttpStatus.SC_OK);
        Assert.assertFalse(transactionState.isRequestError());

        transactionState.setStatusCode(HttpStatus.SC_TEMPORARY_REDIRECT);
        Assert.assertFalse(transactionState.isRequestError());

        transactionState.setStatusCode(HttpStatus.SC_BAD_REQUEST);
        Assert.assertTrue(transactionState.isRequestError());
    }

    @Test
    public void shouldClipInvalidResponseTimes() {
        Assert.assertFalse(transactionState.isComplete());
        // State is changed prior to end() call (somehow)
        transactionState.setState(TransactionState.State.COMPLETE);
        TransactionData transactionData = transactionState.end();
        Assert.assertNotNull(transactionData);
        Assert.assertTrue("Response time is not negative", transactionData.getTime() >= 0f);
    }

    @Test
    public void shouldSetTrace() {
        Assert.assertNull(transactionState.getTrace());
        transactionState.setTrace(DistributedTracing.getInstance().startTrace(transactionState));
        Assert.assertNotNull(transactionState.getTrace());
    }
}