/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation.okhttp3;

import static com.newrelic.agent.android.harvest.type.HarvestErrorCodes.NSURLErrorDNSLookupFailed;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.newrelic.agent.android.instrumentation.TransactionState;
import com.newrelic.agent.android.test.mock.Providers;
import com.newrelic.agent.android.test.mock.TestHarvest;
import com.newrelic.agent.android.util.Constants;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

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
import okio.BufferedSource;
import okio.Okio;


public class CallbackExtensionTest {

    private TestHarvest testHarvest;
    private TransactionState transactionState;
    private CallbackExtension callbackExtension;
    private CallExtension callExtension;
    private Call mockCall;
    private Response mockResponse;
    final static String requestUrl = "http://www.foo.com/";
    final static String appId = "some-app-id";
    final static String successfulResponseData = "Hello, World";
    final static String errorResponseData = "Server could not find what was requested";
    final static String errorMessage = "Bad request";
    private Callback mockCallBack;

    @Before
    public void setUp() throws Exception {
        mockCallBack = Mockito.mock(Callback.class);
        testHarvest = new TestHarvest();
        transactionState = Providers.provideTransactionState();

        final Request.Builder builder = new Request.Builder().
                url(Providers.APP_URL).
                header(Constants.Network.APPLICATION_ID_HEADER, appId).
                get();
        final Request request = OkHttp3Instrumentation.build(builder);

        OkHttpClient client = spy(new OkHttpClient());

        mockResponse = new Response.Builder().
                request(request).
                protocol(Protocol.HTTP_1_1).
                code(404).
                body(new TestErrorResponseBody()).
                message(errorMessage).
                header(Constants.Network.APP_DATA_HEADER, appId).
                build();

        mockCall = new MockCall(client, request, mockResponse);
        when(client.newCall((Request) Mockito.any())).thenReturn(mockCall);

        transactionState = new TransactionState();
        callExtension = new CallExtension(client, request, mockCall, transactionState);
        callbackExtension = new CallbackExtension(mockCallBack, transactionState, callExtension);
    }

    @After
    public void tearDown() throws Exception {
        testHarvest.shutdownHarvester();
    }

    @Test
    public void error() throws Exception {
        callbackExtension.error(new UnknownHostException());
        Assert.assertEquals(NSURLErrorDNSLookupFailed, transactionState.getErrorCode());
    }

    @Test
    public void whenOnResponsePassCallExtension() throws IOException {

        doNothing().when(mockCallBack).onResponse(Mockito.any(CallExtension.class), Mockito.any(Response.class));
        callbackExtension.onResponse(mockCall, mockResponse);
        verify(mockCallBack, times(1)).onResponse(callExtension, mockResponse);
    }

    @Test
    public void whenOnFailurePassCallExtension() throws IOException {

        IOException e = new IOException("Error");
        doNothing().when(mockCallBack).onFailure(Mockito.any(CallExtension.class), Mockito.any(IOException.class));
        callbackExtension.onFailure(mockCall, e);
        verify(mockCallBack, times(1)).onFailure(callExtension, e);
    }

    @Test
    public void whenOnResponsePassCall() throws IOException {

        doNothing().when(mockCallBack).onResponse(Mockito.any(CallExtension.class), Mockito.any(Response.class));
        callbackExtension.onResponse(mockCall, mockResponse);
        verify(mockCallBack, times(0)).onResponse(mockCall, mockResponse);
    }

    @Test
    public void whenOnFailurePassCall() throws IOException {

        IOException e = new IOException("Error");
        doNothing().when(mockCallBack).onFailure(Mockito.any(CallExtension.class), Mockito.any(IOException.class));
        callbackExtension.onFailure(mockCall, e);
        verify(mockCallBack, times(0)).onFailure(mockCall, e);
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