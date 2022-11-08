/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation.okhttp3;

import static com.newrelic.agent.android.harvest.type.HarvestErrorCodes.NSURLErrorDNSLookupFailed;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.google.gson.JsonElement;
import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.TaskQueue;
import com.newrelic.agent.android.harvest.HarvestData;
import com.newrelic.agent.android.harvest.HttpTransaction;
import com.newrelic.agent.android.harvest.HttpTransactions;
import com.newrelic.agent.android.instrumentation.TransactionState;
import com.newrelic.agent.android.test.mock.Providers;
import com.newrelic.agent.android.test.mock.TestHarvest;
import com.newrelic.agent.android.test.stub.StubAgentImpl;
import com.newrelic.agent.android.util.Constants;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.UnknownHostException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.AsyncTimeout;
import okio.BufferedSource;
import okio.Okio;
import okio.Timeout;

public class CallExtensionTest {
    final static String successfulResponseData = "Hello, World";
    final static String errorResponseData = "Server could not find what was requested";
    final static String errorMessage = "Bad request";
    final static String requestUrl = "http://www.foo.com/";
    final static String appId = "some-app-id";

    TestHarvest testHarvest = new TestHarvest();
    TransactionState transactionState;
    private CallExtension callExtension;

    @Before
    public void beforeTests() {
        testHarvest = new TestHarvest();
        testHarvest.getHarvestData().getHttpTransactions().clear();
    }

    @Before
    public void setUp() throws Exception {
        FeatureFlag.enableFeature(FeatureFlag.DistributedTracing);
        FeatureFlag.enableFeature(FeatureFlag.HttpResponseBodyCapture);

        final Request.Builder builder = new Request.Builder().
                url(Providers.APP_URL).
                header(Constants.Network.APPLICATION_ID_HEADER, appId).
                get();
        final Request request = OkHttp3Instrumentation.build(builder);

        OkHttpClient client = spy(new OkHttpClient());

        Response mockResponse = new Response.Builder().
                request(request).
                protocol(Protocol.HTTP_1_1).
                code(404).
                body(new TestErrorResponseBody()).
                message(errorMessage).
                header(Constants.Network.APP_DATA_HEADER, appId).
                build();

        Call mockCall = new MockCall(client, request, mockResponse);
        when(client.newCall((Request) any())).thenReturn(mockCall);

        testHarvest = new TestHarvest();
        transactionState = new TransactionState();
        callExtension = new CallExtension(client, request, mockCall, transactionState);
        transactionState = callExtension.getTransactionState();

    }

    @After
    public void afterTests() {
        TestHarvest.shutdown();
        StubAgentImpl.uninstall();
    }

    @Test
    public void testCallExecute() throws Exception {
        final StubAgentImpl agent = StubAgentImpl.install();

        assertEquals(0, agent.getTransactionData().size());

        final Request.Builder builder = new Request.Builder().
                url(requestUrl).
                header(Constants.Network.APPLICATION_ID_HEADER, appId).
                get();

        final Request request = OkHttp3Instrumentation.build(builder);

        Response mockResponse = new Response.Builder().
                request(request).
                protocol(Protocol.HTTP_1_1).
                code(200).
                body(new TestResponseBody()).
                message("200 OK").
                header(Constants.Network.APP_DATA_HEADER, appId).
                build();

        OkHttpClient client = new OkHttpClient();
        OkHttpClient spyClient = spy(client);
        Call mockCall = new MockCall(client, request, mockResponse);
        when(spyClient.newCall((Request) any())).thenReturn(mockCall);

        CallExtension instrumentedCall = (CallExtension) OkHttp3Instrumentation.newCall(spyClient, request);

        assertTrue("Instrumented call must be an instance of CallExtension.", instrumentedCall instanceof CallExtension);

        Response response = instrumentedCall.execute();

        assertEquals(request.url().toString(), requestUrl);
        assertEquals(request.header(Constants.Network.APPLICATION_ID_HEADER), appId);
        assertNotNull("Cross-Process ID should not be NULL", request.header(Constants.Network.CROSS_PROCESS_ID_HEADER));
        assertEquals(request.header(Constants.Network.CROSS_PROCESS_ID_HEADER), "TEST_CROSS_PROCESS_ID");

        assertNotNull("Response object should not be null", response);
        assertEquals(200, response.code());
        assertEquals(successfulResponseData, response.body().string());

        TaskQueue.synchronousDequeue();

        HarvestData harvestData = testHarvest.getHarvestData();
        HttpTransactions transactions = harvestData.getHttpTransactions();

        assertEquals(1, transactions.count());

        HttpTransaction transaction = transactions.getHttpTransactions().iterator().next();

        assertEquals(requestUrl, transaction.getUrl());
        assertEquals(200, transaction.getStatusCode());
        assertEquals(agent.getNetworkCarrier(), transaction.getCarrier());
        assertEquals(agent.getNetworkWanType(), transaction.getWanType());
        assertEquals(appId, transaction.getAppData());
        assertEquals(0, transaction.getBytesSent());
        assertEquals(successfulResponseData.length(), transaction.getBytesReceived());
    }

    @Test
    public void testCallEnqueue() {
        final StubAgentImpl agent = StubAgentImpl.install();

        assertEquals(0, agent.getTransactionData().size());

        final Request.Builder builder = new Request.Builder().
                url(requestUrl).
                header(Constants.Network.APPLICATION_ID_HEADER, appId).
                get();

        final Request request = OkHttp3Instrumentation.build(builder);

        Response mockResponse = new Response.Builder().
                request(request).
                protocol(Protocol.HTTP_1_1).
                code(200).
                body(new TestResponseBody()).
                message("200 OK").
                header(Constants.Network.APP_DATA_HEADER, appId).
                build();

        OkHttpClient client = new OkHttpClient();
        OkHttpClient spyClient = spy(client);
        Call mockCall = new MockCall(client, request, mockResponse);
        when(spyClient.newCall((Request) any())).thenReturn(mockCall);

        CallExtension instrumentedCall = (CallExtension) OkHttp3Instrumentation.newCall(spyClient, request);

        assertTrue("Instrumented call must be an instance of CallExtension.", instrumentedCall instanceof CallExtension);

        instrumentedCall.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Assert.fail("Should not return failure");
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                Request request = response.request();
                assertEquals(request.url().toString(), requestUrl);
                assertEquals(request.header(Constants.Network.APPLICATION_ID_HEADER), appId);
                assertNotNull("Cross-Process ID should not be NULL", request.header(Constants.Network.CROSS_PROCESS_ID_HEADER));
                assertEquals(request.header(Constants.Network.CROSS_PROCESS_ID_HEADER), "TEST_CROSS_PROCESS_ID");

                assertNotNull("Response object should not be null", response);
                assertEquals(200, response.code());
                assertEquals(successfulResponseData, response.body().string());
                TaskQueue.synchronousDequeue();

                HarvestData harvestData = testHarvest.getHarvestData();
                HttpTransactions transactions = harvestData.getHttpTransactions();

                assertEquals(1, transactions.count());

                HttpTransaction transaction = transactions.getHttpTransactions().iterator().next();

                assertEquals(requestUrl, transaction.getUrl());
                assertEquals(200, transaction.getStatusCode());
                assertEquals(agent.getNetworkCarrier(), transaction.getCarrier());
                assertEquals(agent.getNetworkWanType(), transaction.getWanType());
                assertEquals(appId, transaction.getAppData());
                assertEquals(0, transaction.getBytesSent());
                assertEquals(successfulResponseData.length(), transaction.getBytesReceived());
            }
        });
    }

    @Test
    public void testErrorResponse() throws Exception {
        final StubAgentImpl agent = StubAgentImpl.install();

        final Request.Builder builder = new Request.Builder().
                url(requestUrl).
                header(Constants.Network.APPLICATION_ID_HEADER, appId).
                get();

        final Request request = OkHttp3Instrumentation.build(builder);

        Response mockResponse = new Response.Builder().
                request(request).
                protocol(Protocol.HTTP_1_1).
                code(404).
                body(new TestErrorResponseBody()).
                message(errorMessage).
                header(Constants.Network.APP_DATA_HEADER, appId).
                build();

        OkHttpClient client = new OkHttpClient();
        OkHttpClient spyClient = spy(client);
        Call mockCall = new MockCall(client, request, mockResponse);
        when(spyClient.newCall((Request) any())).thenReturn(mockCall);

        CallExtension instrumentedCall = (CallExtension) OkHttp3Instrumentation.newCall(spyClient, request);
        Response response = instrumentedCall.execute();

        assertEquals(404, response.code());
        assertEquals(errorResponseData, response.body().string());

        TaskQueue.synchronousDequeue();

        HarvestData harvestData = testHarvest.getHarvestData();
        HttpTransactions transactions = harvestData.getHttpTransactions();


        assertEquals(1, transactions.count());
        HttpTransaction transaction = transactions.getHttpTransactions().iterator().next();
        assertEquals(404, transaction.getStatusCode());
    }

    @Test
    public void testAsyncErrorResponse() {
        final StubAgentImpl agent = StubAgentImpl.install();

        final Request.Builder builder = new Request.Builder().
                url(requestUrl).
                header(Constants.Network.APPLICATION_ID_HEADER, appId).
                get();

        final Request request = OkHttp3Instrumentation.build(builder);

        Response mockResponse = new Response.Builder().
                request(request).
                protocol(Protocol.HTTP_1_1).
                code(404).
                body(new TestErrorResponseBody()).
                message(errorMessage).
                header(Constants.Network.APP_DATA_HEADER, appId).
                build();

        OkHttpClient client = new OkHttpClient();
        OkHttpClient spyClient = spy(client);
        Call mockCall = new MockCall(client, request, mockResponse);
        when(spyClient.newCall((Request) any())).thenReturn(mockCall);

        TestHarvest.getInstance().getHarvestData().reset();

        CallExtension instrumentedCall = (CallExtension) OkHttp3Instrumentation.newCall(spyClient, request);
        instrumentedCall.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Assert.fail("Should not return failure");
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                assertEquals(404, response.code());
                assertEquals(errorResponseData, response.body().string());

                TaskQueue.synchronousDequeue();

                HarvestData harvestData = testHarvest.getHarvestData();
                HttpTransactions transactions = harvestData.getHttpTransactions();


                assertEquals(1, transactions.count());
                HttpTransaction transaction = transactions.getHttpTransactions().iterator().next();
                assertEquals(404, transaction.getStatusCode());
            }
        });
    }

    @Test
    public void testMissingErrorResponse() throws Exception {
        final StubAgentImpl agent = StubAgentImpl.install();

        final Request.Builder builder = new Request.Builder().
                url(requestUrl).
                get();

        final Request request = OkHttp3Instrumentation.build(builder);

        Response mockResponse = new Response.Builder().
                request(request).
                protocol(Protocol.HTTP_1_1).
                code(404).
                message(errorMessage).
                body(new TestResponseBody()).
                build();

        OkHttpClient client = new OkHttpClient();
        OkHttpClient spyClient = spy(client);
        Call mockCall = new MockCall(client, request, mockResponse);
        when(spyClient.newCall((Request) any())).thenReturn(mockCall);

        CallExtension instrumentedCall = (CallExtension) OkHttp3Instrumentation.newCall(spyClient, request);
        Response response = instrumentedCall.execute();

        assertEquals(404, response.code());

        TaskQueue.synchronousDequeue();

        HarvestData harvestData = testHarvest.getHarvestData();
        HttpTransactions transactions = harvestData.getHttpTransactions();


        assertEquals(1, transactions.count());
        HttpTransaction transaction = transactions.getHttpTransactions().iterator().next();
        assertEquals(404, transaction.getStatusCode());
        assertEquals(12, transaction.getBytesReceived());

    }

    @Test
    public void error() throws Exception {
        callExtension.error(new UnknownHostException());
        assertEquals(NSURLErrorDNSLookupFailed, transactionState.getErrorCode());
    }

    @Test
    public void testTimeout() {
        Timeout timeout = callExtension.timeout();
        assertNotNull(timeout);
        assertTrue(timeout instanceof AsyncTimeout);
    }

    @Test
    public void testCloneCall() {
        Call cloneCall = callExtension.clone();
        assertNotNull(cloneCall);
        assertTrue(cloneCall instanceof MockCall);
        assertNotEquals(callExtension.getImpl(), cloneCall);
        assertEquals(callExtension.request(), cloneCall.request());

        try {
            Response response = callExtension.execute();
            Response clonedResponse = cloneCall.execute();
            assertEquals(response, clonedResponse);
        } catch (IOException e) {
            fail();
        }
    }

    private class TestResponseBody extends ResponseBody {
        @Override
        public MediaType contentType() {
            return MediaType.parse("text/html");
        }

        @Override
        public long contentLength() {
            return successfulResponseData.length();
        }

        @Override
        public BufferedSource source() {
            return Okio.buffer(Okio.source(new ByteArrayInputStream(successfulResponseData.getBytes())));
        }
    }

    private class TestErrorResponseBody extends TestResponseBody {
        @Override
        public long contentLength() {
            return errorResponseData.length();
        }

        @Override
        public BufferedSource source() {
            return Okio.buffer(Okio.source(new ByteArrayInputStream(errorResponseData.getBytes())));
        }
    }

}
