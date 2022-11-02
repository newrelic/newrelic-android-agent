/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation.okhttp2;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.Measurements;
import com.newrelic.agent.android.harvest.Harvest;
import com.newrelic.agent.android.harvest.HarvestData;
import com.newrelic.agent.android.test.stub.StubAgentImpl;
import com.newrelic.agent.android.util.Constants;
import com.squareup.okhttp.Call;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Protocol;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.ResponseBody;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;

import okio.BufferedSource;
import okio.Okio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public class ResponseBuilderExtensionTest {
    TestHarvest testHarvest = new TestHarvest();

    public static class TestResponseBody extends ResponseBody {
        final String responseData = "Hello, World";
        final BufferedSource source = Okio.buffer(Okio.source(new ByteArrayInputStream(responseData.getBytes())));

        @Override
        public MediaType contentType() {
            return MediaType.parse("text/html");
        }

        @Override
        public long contentLength() {
            return responseData.length();
        }

        @Override
        public BufferedSource source() {
            return source;
        }
    }

    public class TestRetrofit2ResponseBody extends ResponseBody {
        final String responseData = "Hello, World";

        @Override
        public MediaType contentType() {
            return MediaType.parse("text/html");
        }

        @Override
        public long contentLength() {
            return responseData.length();
        }

        @Override
        public BufferedSource source() {
            throw new IllegalStateException();
        }
    }

    @Before
    public void beforeTests() {
        testHarvest = new TestHarvest();
        TestHarvest.setInstance(testHarvest);
        TestHarvest.initialize(new AgentConfiguration());
        Measurements.initialize();
    }

    @After
    public void uninstallAgent() {
        TestHarvest.shutdown();
        Measurements.shutdown();
        StubAgentImpl.uninstall();
    }

    @Test
    public void testBuildResponse() throws Exception {
        final String responseData = "Hello, World";
        final String requestUrl = "http://www.foo.com/";
        final String appId = "some-app-id";

        final StubAgentImpl agent = StubAgentImpl.install();

        assertEquals(0, agent.getTransactionData().size());

        final Request.Builder builder = new Request.Builder().
                url(requestUrl).
                header(Constants.Network.APPLICATION_ID_HEADER, appId).
                get();

        final Request request = OkHttp2Instrumentation.build(builder);

        Response mockResponse = new Response.Builder().
                request(request).
                protocol(Protocol.HTTP_1_1).
                code(200).
                body(new TestResponseBody()).
                header(Constants.Network.APP_DATA_HEADER, appId).
                build();

        OkHttpClient client = new OkHttpClient();
        OkHttpClient spyClient = spy(client);
        Call mockCall = new MockCall(client, request, mockResponse);
        when(spyClient.newCall(request)).thenReturn(mockCall);

        CallExtension instrumentedCall = (CallExtension) OkHttp2Instrumentation.newCall(client, request);

        assertTrue("Instrumented call must be an instance of CallExtension.", instrumentedCall instanceof CallExtension);

        Response response = instrumentedCall.execute();

        Response.Builder responseBuilder = new Response.Builder().
                request(request).
                code(response.code()).
                headers(response.headers()).
                protocol(response.protocol());
        responseBuilder = OkHttp2Instrumentation.body(responseBuilder, new TestResponseBody());

        response = responseBuilder.build();

        assertEquals(request.urlString(), requestUrl);
        assertEquals(request.header(Constants.Network.APPLICATION_ID_HEADER), appId);
        assertNotNull("Cross-Process ID should not be NULL", request.header(Constants.Network.CROSS_PROCESS_ID_HEADER));
        assertEquals(request.header(Constants.Network.CROSS_PROCESS_ID_HEADER), "TEST_CROSS_PROCESS_ID");

        assertNotNull("Response object should not be null", response);
        assertEquals(200, response.code());
        assertEquals(responseData, response.body().string());

    }

    @Test
    public void testBuildResponseRetrofit2() throws Exception {
        final String responseData = "Hello, World";
        final String requestUrl = "http://www.foo.com/";
        final String appId = "some-app-id";

        final StubAgentImpl agent = StubAgentImpl.install();

        assertEquals(0, agent.getTransactionData().size());

        final Request.Builder builder = new Request.Builder().
                url(requestUrl).
                header(Constants.Network.APPLICATION_ID_HEADER, appId).
                get();

        final Request request = OkHttp2Instrumentation.build(builder);

        Response mockResponse = new Response.Builder().
                request(request).
                protocol(Protocol.HTTP_1_1).
                code(200).
                body(new TestRetrofit2ResponseBody()).
                header(Constants.Network.APP_DATA_HEADER, appId).
                build();

        OkHttpClient client = new OkHttpClient();
        OkHttpClient spyClient = spy(client);
        Call mockCall = new MockCall(client, request, mockResponse);
        when(spyClient.newCall(request)).thenReturn(mockCall);

        CallExtension instrumentedCall = (CallExtension) OkHttp2Instrumentation.newCall(client, request);

        assertTrue("Instrumented call must be an instance of CallExtension.", instrumentedCall instanceof CallExtension);

        Response response = instrumentedCall.execute();

        Response.Builder responseBuilder = new Response.Builder().
                request(request).
                code(response.code()).
                headers(response.headers()).
                protocol(response.protocol());
        responseBuilder = OkHttp2Instrumentation.body(responseBuilder, new TestResponseBody());

        response = responseBuilder.build();

        assertEquals(request.urlString(), requestUrl);
        assertEquals(request.header(Constants.Network.APPLICATION_ID_HEADER), appId);
        assertNotNull("Cross-Process ID should not be NULL", request.header(Constants.Network.CROSS_PROCESS_ID_HEADER));
        assertEquals(request.header(Constants.Network.CROSS_PROCESS_ID_HEADER), "TEST_CROSS_PROCESS_ID");

        assertNotNull("Response object should not be null", response);
        assertEquals(200, response.code());
        assertEquals(responseData, response.body().string());

    }

    private class TestHarvest extends Harvest {

        public TestHarvest() {
        }

        public HarvestData getHarvestData() {
            return harvestData;
        }
    }
}
