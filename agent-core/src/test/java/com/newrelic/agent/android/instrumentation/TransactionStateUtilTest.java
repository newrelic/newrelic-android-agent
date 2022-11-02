/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation;

import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.api.common.TransactionData;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.test.mock.Providers;
import com.newrelic.agent.android.test.mock.TestHarvest;
import com.newrelic.agent.android.test.stub.StubAgentImpl;
import com.newrelic.agent.android.tracing.TraceMachine;
import com.newrelic.agent.android.util.Constants;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.net.HttpURLConnection;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TransactionStateUtilTest {

    private TransactionState transactionState;
    private TestHarvest testHarvest;
    private AgentLog agentLog = mock(AgentLog.class);

    @BeforeClass
    public static void classSetUp() {
        Agent.setImpl(new TestStubAgentImpl());
        TraceMachine.HEALTHY_TRACE_TIMEOUT = 10000;
    }

    @Before
    public void setUp() throws Exception {
        transactionState = Providers.provideTransactionState();
        agentLog = mock(AgentLog.class);
        AgentLogManager.setAgentLog(agentLog);
    }

    @Before
    public void setUpHarvest() {
        testHarvest = new TestHarvest();
    }

    @Before
    public void setUpTracing() {
        TraceMachine.startTracing("TransactionStateUtilTest");
    }

    @Test
    public void testInspectAndInstrument() {
        TransactionStateUtil.inspectAndInstrument(transactionState, Providers.APP_URL, Providers.APP_METHOD);
        Assert.assertEquals("Should set APP_URL", transactionState.getUrl(), Providers.APP_URL);
        Assert.assertEquals("Should set method", transactionState.getHttpMethod(), Providers.APP_METHOD);

        final TransactionData transactionData = transactionState.end();
        Assert.assertEquals("Should set carrier", transactionData.getCarrier(), Agent.getActiveNetworkCarrier());
        Assert.assertEquals("Should set wan type", transactionData.getWanType(), Agent.getActiveNetworkWanType());
    }

    @Test
    public void testInspectAndInstrumentHttpUrlConnection() throws Exception {
        HttpURLConnection httpUrlConnection = Providers.provideHttpUrlConnection();

        TransactionStateUtil.inspectAndInstrument(transactionState, httpUrlConnection);
        Assert.assertEquals("Should set APP_URL", transactionState.getUrl(), Providers.APP_URL);
        Assert.assertEquals("Should set method", transactionState.getHttpMethod(), Providers.APP_METHOD);

        transactionState = Providers.provideTransactionState();
        TransactionStateUtil.inspectAndInstrumentResponse(transactionState, httpUrlConnection);
        Assert.assertEquals("Should contain status code", HttpURLConnection.HTTP_OK, transactionState.getStatusCode());
        Assert.assertEquals("Should contain byes received", Providers.APP_DATA.length(), transactionState.getBytesReceived());
    }

    @Test
    public void testSetCrossProcessHeader() throws Exception {
        HttpURLConnection httpUrlConnection = Providers.provideHttpUrlConnection();
        TransactionStateUtil.setCrossProcessHeader(httpUrlConnection);
        verify(httpUrlConnection).setRequestProperty(Constants.Network.CROSS_PROCESS_ID_HEADER, Providers.X_PROCESS_ID);
    }

    @Test
    public void testInspectAndInstrumentResponse() throws Exception {
        TransactionStateUtil.inspectAndInstrumentResponse(transactionState, Providers.APP_DATA, Providers.APP_DATA.length(), HttpURLConnection.HTTP_OK);
        Assert.assertEquals("Should set status code", HttpURLConnection.HTTP_OK, transactionState.getStatusCode());
        Assert.assertEquals("Should set app data", Providers.APP_DATA, TraceMachine.getCurrentTraceParams().get(Providers.TRACE_ENCODED_DATA));
    }

    @Test
    public void testInspectAndInstrumentHttpUrlConnectionResponse() throws Exception {
        HttpURLConnection httpUrlConnection = Providers.provideHttpUrlConnection();
        TransactionStateUtil.inspectAndInstrumentResponse(transactionState, httpUrlConnection);
        Assert.assertEquals("Should set status code", HttpURLConnection.HTTP_OK, transactionState.getStatusCode());
        Assert.assertEquals("Should set app data", Providers.APP_DATA, TraceMachine.getCurrentTraceParams().get(Providers.TRACE_ENCODED_DATA));
    }

    @Test
    public void testExceptionsThrownOnClosedConnection() throws Exception {
        Exception exception = new IllegalStateException("wut?");
        HttpURLConnection httpUrlConnection = Providers.provideHttpUrlConnection();
        when(httpUrlConnection.getHeaderField(Constants.Network.APP_DATA_HEADER)).thenThrow(exception);

        transactionState = spy(transactionState);

        try {
            TransactionStateUtil.inspectAndInstrumentResponse(transactionState, httpUrlConnection);
        } catch (Exception e) {
            Assert.fail("Should catch exceptions thrown on invalid connection");
        }

        verify(transactionState, times(1)).setBytesReceived(anyLong());
        verify(transactionState, times(1)).setStatusCode(anyInt());
        verify(transactionState, never()).setAppData(anyString());

        verify(agentLog, atLeast(1)).debug(anyString());
    }

    static class TestStubAgentImpl extends StubAgentImpl {

        @Override
        public String getCrossProcessId() {
            return Providers.X_PROCESS_ID;
        }
    }
}