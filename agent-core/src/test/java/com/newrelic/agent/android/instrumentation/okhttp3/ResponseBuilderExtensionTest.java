/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation.okhttp3;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.Measurements;
import com.newrelic.agent.android.harvest.Harvest;
import com.newrelic.agent.android.harvest.HarvestData;
import com.newrelic.agent.android.test.stub.StubAgentImpl;
import com.newrelic.agent.android.util.Constants;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import okio.Okio;

public class ResponseBuilderExtensionTest {
    TestHarvest testHarvest = new TestHarvest();

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
        when(spyClient.newCall(request)).thenReturn(mockCall);

        CallExtension instrumentedCall = (CallExtension) OkHttp3Instrumentation.newCall(client, request);

        assertTrue("Instrumented call must be an instance of CallExtension.", instrumentedCall instanceof CallExtension);

        Response response = instrumentedCall.execute();

        Response.Builder responseBuilder = new Response.Builder().
                request(request).
                code(response.code()).
                headers(response.headers()).
                message(response.message()).
                protocol(response.protocol());

        responseBuilder = OkHttp3Instrumentation.body(responseBuilder, new TestResponseBody());

        response = responseBuilder.build();

        assertEquals(request.url().toString(), requestUrl);
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

        final Request request = OkHttp3Instrumentation.build(builder);

        Response mockResponse = new Response.Builder().
                request(request).
                protocol(Protocol.HTTP_1_1).
                code(200).
                body(new TestRetrofit2ResponseBody()).
                message("200 OK").
                header(Constants.Network.APP_DATA_HEADER, appId).
                build();

        OkHttpClient client = new OkHttpClient();
        OkHttpClient spyClient = spy(client);
        Call mockCall = new MockCall(client, request, mockResponse);
        when(spyClient.newCall(request)).thenReturn(mockCall);

        CallExtension instrumentedCall = (CallExtension) OkHttp3Instrumentation.newCall(client, request);

        assertTrue("Instrumented call must be an instance of CallExtension.", instrumentedCall instanceof CallExtension);

        Response response = instrumentedCall.execute();

        Response.Builder responseBuilder = new Response.Builder().
                request(request).
                code(response.code()).
                message(response.message()).
                headers(response.headers()).
                protocol(response.protocol());
        responseBuilder = OkHttp3Instrumentation.body(responseBuilder, new TestResponseBody());

        response = responseBuilder.build();

        assertEquals(request.url().toString(), requestUrl);
        assertEquals(request.header(Constants.Network.APPLICATION_ID_HEADER), appId);
        assertNotNull("Cross-Process ID should not be NULL", request.header(Constants.Network.CROSS_PROCESS_ID_HEADER));
        assertEquals(request.header(Constants.Network.CROSS_PROCESS_ID_HEADER), "TEST_CROSS_PROCESS_ID");

        assertNotNull("Response object should not be null", response);
        assertEquals(200, response.code());
        assertEquals(responseData, response.body().string());

    }

    @Test
    public void testAllowableResponseBodyTypes() throws Exception {
        final String responseData = "Hello, World";
        final String requestUrl = "http://www.foo.com/";
        final String appId = "some-app-id";
        final StubAgentImpl agent = StubAgentImpl.install();
        final Request.Builder builder = new Request.Builder().
                url(requestUrl).
                header(Constants.Network.APPLICATION_ID_HEADER, appId).
                get();

        final Request request = OkHttp3Instrumentation.build(builder);

        Response mockResponse = new Response.Builder().
                request(request).
                protocol(Protocol.HTTP_1_1).
                code(200).
                body(new TestRetrofit2ResponseBody()).
                message("200 OK").
                header(Constants.Network.APP_DATA_HEADER, appId).
                build();

        OkHttpClient client = new OkHttpClient();
        OkHttpClient spyClient = spy(client);
        Call mockCall = new MockCall(client, request, mockResponse);
        when(spyClient.newCall(request)).thenReturn(mockCall);

        CallExtension instrumentedCall = (CallExtension) OkHttp3Instrumentation.newCall(client, request);

        assertTrue("Instrumented call must be an instance of CallExtension.", instrumentedCall instanceof CallExtension);

        Response response = instrumentedCall.execute();

        Response.Builder responseBuilder = new Response.Builder().
                request(request).
                code(response.code()).
                headers(response.headers()).
                message(response.message()).
                protocol(response.protocol());

        responseBuilder = OkHttp3Instrumentation.body(responseBuilder, new TestResponseBody());
        response = responseBuilder.build();

        Assert.assertTrue("Should use PrebufferedResponseBody delegate", response.body() instanceof TestResponseBody);
    }

    @Test
    public void testContentLength() throws Exception {
        ResponseBody response = new TestResponseBody();
        Buffer buffer = new Buffer();
        response.source().readAll(buffer);
        Assert.assertEquals("Should return same content length", response.contentLength(), buffer.size());

        response = new TestStreamedResponseBody();
        buffer = new Buffer();
        response.source().readAll(buffer);
        Assert.assertEquals("Should return unknown length (-1)", -1, response.contentLength());
        Assert.assertEquals("Should return buffered content length", 14, buffer.size());
    }

    @Test
    public void testContentType() {
        ResponseBody response = new TestResponseBody();
        BufferedSource source = response.source();
        Assert.assertEquals("Content type should remain unchanged", MediaType.parse("text/html"), response.contentType());

        PrebufferedResponseBody body = new PrebufferedResponseBody(response);
        Assert.assertEquals("Content type should remain unchanged", body.contentType(), response.contentType());
    }

    @Test
    public void testBuilderDeleteWithoutBody() throws IOException {
        final RequestBody EMPTY_REQUEST = RequestBody.create((MediaType) null, "".getBytes());
        final String requestUrl = "https://httpbin.org/status/418/";
        final Request.Builder builder = new Request.Builder().
                url(requestUrl).
                delete();

        final Request request = OkHttp3Instrumentation.build(builder);
        Assert.assertTrue(request.method().equalsIgnoreCase("delete"));
        Assert.assertTrue(request.body().contentLength() == 0);
    }

    @Test
    public void testBuilderDeleteWithNullBody() {
        final String requestUrl = "https://httpbin.org/status/418/";
        final Request.Builder builder = new Request.Builder().
                url(requestUrl).
                delete(null);

        final Request request = OkHttp3Instrumentation.build(builder);
        Assert.assertNull(request.body());
    }

    @Test
    public void testBuilderDeleteRequestWithBody() {
        final String requestUrl = "https://httpbin.org/status/418/";
        final RequestBody requestBody = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), "holla");

        Request.Builder builder = new Request.Builder().
                url(requestUrl).
                delete(requestBody);

        Request request = OkHttp3Instrumentation.build(builder);
        Assert.assertNotNull(request.body());

        builder = new Request.Builder().
                url(requestUrl).
                delete(requestBody);

        request = OkHttp3Instrumentation.build(builder);
        Assert.assertNotNull(request.body());
        Assert.assertEquals(request.body(), requestBody);
    }

    public static class TestResponseBody extends ResponseBody {
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
            return Okio.buffer(Okio.source(new ByteArrayInputStream(responseData.getBytes())));
        }

        protected String content() {
            return responseData;
        }
    }

    public static class TestStreamedResponseBody extends ResponseBody {

        final String responseData = "Hello, World 1";

        @Override
        public MediaType contentType() {
            return MediaType.parse("text/gzip");
        }

        @Override
        public long contentLength() {
            return -1;
        }

        @Override
        public BufferedSource source() {
            InputStream stream = new ByteArrayInputStream(responseData.getBytes(StandardCharsets.UTF_8));
            return Okio.buffer(Okio.source(stream));
        }
    }

    public static final class NoContentResponseBody extends ResponseBody {
        private final MediaType contentType;
        private final long contentLength;

        NoContentResponseBody(MediaType contentType, long contentLength) {
            this.contentType = contentType;
            this.contentLength = contentLength;
        }

        @Override
        public MediaType contentType() {
            return contentType;
        }

        @Override
        public long contentLength() {
            return contentLength;
        }

        @Override
        public BufferedSource source() {
            throw new IllegalStateException("Cannot read raw response body of a converted body.");
        }
    }

    public class TestRetrofit2ResponseBody extends TestResponseBody {
        @Override
        public BufferedSource source() {
            throw new IllegalStateException();
        }
    }

    private class TestHarvest extends Harvest {

        public TestHarvest() {
        }

        public HarvestData getHarvestData() {
            return harvestData;
        }
    }

    private class MockedCacheResponseBody extends TestResponseBody {
    }
}
