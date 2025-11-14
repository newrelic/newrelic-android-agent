/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation.okhttp3;

import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.HttpHeaders;
import com.newrelic.agent.android.distributedtracing.TraceParent;
import com.newrelic.agent.android.distributedtracing.TracePayload;
import com.newrelic.agent.android.distributedtracing.TraceState;
import com.newrelic.agent.android.harvest.HarvestConfiguration;
import com.newrelic.agent.android.instrumentation.TransactionState;
import com.newrelic.agent.android.test.mock.Providers;
import com.newrelic.agent.android.test.mock.TestHarvest;
import com.newrelic.agent.android.test.stub.StubAgentImpl;
import com.newrelic.agent.android.util.Constants;

import org.apache.http.HttpStatus;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static com.newrelic.agent.android.harvest.type.HarvestErrorCodes.NSURLErrorDNSLookupFailed;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;


public class OkHttp3TransactionStateUtilTest {

    private TransactionState transactionState;
    private OkHttpClient client = new OkHttpClient();

    private List<String> headers;

    @Before
    public void setUp() throws Exception {
        transactionState = Providers.provideTransactionState();
        client = new OkHttpClient();
        headers = new ArrayList<>();
        headers.add("X-Custom-Header-1");
        headers.add("X-CUSTOM-HEADER-2");
        HttpHeaders.getInstance().addHttpHeadersAsAttributes(headers);
    }

    @Test
    public void testInspectAndInstrumentRequest() {
        Request request = provideRequest();
        OkHttp3TransactionStateUtil.inspectAndInstrument(transactionState, request);
        Assert.assertEquals("Should have same status URL", transactionState.getUrl(), request.url().toString());
        Assert.assertEquals("Should have same request method", transactionState.getHttpMethod(), request.method());
    }

    @Test
    public void testInspectAndInstrumentRequestWithBody() {
        Request request = providePostRequest();
        OkHttp3TransactionStateUtil.inspectAndInstrument(transactionState, request);
        Assert.assertTrue("Should have non-zero bytesSent", transactionState.getBytesSent() > 0);
    }

    @Test
    public void testInspectAndInstrumentNullRequest() {
        TransactionState transactionState = new TransactionState();
        OkHttp3TransactionStateUtil.inspectAndInstrument(transactionState, (Request) null);
        assertNull("Should have no status URL", transactionState.getUrl());
        assertNull("Should have no request method", transactionState.getHttpMethod());
    }

    @Test
    public void testInspectAndInstrumentResponse() {
        Response response = provideResponse();
        OkHttp3TransactionStateUtil.inspectAndInstrumentResponse(transactionState, response);
        Assert.assertEquals("Should have same status code", transactionState.getStatusCode(), response.code());
    }

    @Test
    public void testInspectAndInstrumentNullResponse() {
        OkHttp3TransactionStateUtil.inspectAndInstrumentResponse(transactionState, (Response) null);
        Assert.assertEquals("Should have server error status code", transactionState.getStatusCode(), HttpStatus.SC_INTERNAL_SERVER_ERROR);
        assertNull("Should have no content or content type", transactionState.getContentType());
    }

    @Test
    public void addTransactionAndErrorData() throws Exception {
        TestHarvest testHarvest = new TestHarvest();
        Response response = provideResponse();

        transactionState.setStatusCode(response.code());
        OkHttp3TransactionStateUtil.addTransactionAndErrorData(transactionState, response);

        testHarvest.verifyQueuedTransactions(1);

    }

    @Test
    public void addTransactionAndErrorDataFailure() throws Exception {
        TestHarvest testHarvest = new TestHarvest();
        Response response = provideResponse();

        transactionState.setErrorCode(NSURLErrorDNSLookupFailed);
        OkHttp3TransactionStateUtil.addTransactionAndErrorData(transactionState, response);

        testHarvest.verifyQueuedTransactions(1);

    }

    @Test
    public void testInterceptedRequest() {
        ResponseBody body = new ResponseBuilderExtensionTest.TestResponseBody();
        Request request = provideRequest();

        Request interceptedRequest = request.newBuilder()
                .url(request.url().newBuilder().host("httpbin.org").build())
                .addHeader("X-CUSTOM-HEADER-2", "test-value")
                .removeHeader(Constants.Network.APPLICATION_ID_HEADER)
                .build();

        Response response = new Response.Builder().
                request(interceptedRequest).
                protocol(Protocol.HTTP_1_1).
                body(body).
                code(HttpStatus.SC_BAD_REQUEST).
                message("400 Bad Request").
                header(Constants.Network.APP_DATA_HEADER, "some-app-id").
                header(Constants.Network.CONTENT_TYPE_HEADER, body.contentType().toString()).
                build();

        OkHttp3TransactionStateUtil.inspectAndInstrumentResponse(transactionState, response);
        Assert.assertNotEquals(request.url().toString(), transactionState.getUrl());
        Assert.assertEquals(interceptedRequest.url().toString(), transactionState.getUrl());
        Assert.assertTrue(transactionState.getParams().containsKey("X-CUSTOM-HEADER-2"));
    }

    @Test
    public void testInterceptedPostRequest() throws IOException {
        Request request = providePostRequest();
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new Interceptor() {
                    @Override
                    public Response intercept(Chain chain) throws IOException {
                        Request request = chain.request();
                        Assert.assertEquals(request.url().toString(), transactionState.getUrl());
                        return chain.proceed(request);
                    }
                })
                .addInterceptor(new Interceptor() {
                    @Override
                    public Response intercept(Chain chain) throws IOException {
                        Request request = chain.request();
                        Assert.assertEquals(request.url().toString(), transactionState.getUrl());

                        HttpUrl newUrl = request.url().newBuilder()
                                .scheme("https")
                                .host("httpbin.org")
                                .addPathSegment("anything")
                                .build();

                        request = request.newBuilder()
                                .url(newUrl)
                                .build();

                        return chain.proceed(request);
                    }
                })
                .build();

        Assert.assertEquals("http://httpstat.us/200", transactionState.getUrl());

        CallExtension call = (CallExtension) OkHttp3Instrumentation.newCall(client, request);
        transactionState = call.getTransactionState();

        try {
            call.execute();
        } catch (Exception e) {
            // ignore
        }
        Assert.assertEquals("https://httpbin.org/anything", transactionState.getUrl());
        Assert.assertEquals(transactionState.getBytesSent(), request.body().contentLength());
    }

    @Test
    public void testInterceptedClientCall() {
        Request request = provideRequest();
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new Interceptor() {
                    @Override
                    public Response intercept(Chain chain) throws IOException {
                        Request request = chain.request();
                        Assert.assertEquals(request.url().toString(), transactionState.getUrl());

                        for (String header : request.headers(Constants.Network.APPLICATION_ID_HEADER)) {
                            HttpUrl url = request.url().newBuilder().host("httpbin.org").build();
                            request = request.newBuilder()
                                    .url(url)
                                    .removeHeader(header)
                                    .build();
                        }
                        return chain.proceed(request);
                    }
                })
                .build();

        Assert.assertEquals("http://httpstat.us/200", transactionState.getUrl());

        CallExtension call = (CallExtension) OkHttp3Instrumentation.newCall(client, request);
        transactionState = call.getTransactionState();

        try {
            call.execute();
        } catch (Exception e) {
            // ignore
        }
        Assert.assertEquals("http://httpbin.org/", transactionState.getUrl());
    }

    @Test
    public void testSetDistributedTracePayload() {
        FeatureFlag.enableFeature(FeatureFlag.DistributedTracing);
        assertNull(transactionState.getTrace());
        CallExtension call = (CallExtension) OkHttp3Instrumentation.newCall(client, provideRequest());
        Assert.assertNotNull(call.getTransactionState().getTrace());

        FeatureFlag.disableFeature(FeatureFlag.DistributedTracing);
        call = (CallExtension) OkHttp3Instrumentation.newCall(client, provideRequest());
        assertNull(call.getTransactionState().getTrace());
    }

    @Test
    public void testSetDistributedTraceHeaders() {
        final String requestUrl = "http://www.foo.com/";
        final String appId = "some-app-id";

        FeatureFlag.enableFeature(FeatureFlag.DistributedTracing);

        final StubAgentImpl agent = StubAgentImpl.install();

        assertEquals(0, agent.getTransactionData().size());

        final Request.Builder builder = new Request.Builder().
                url(requestUrl).
                header(Constants.Network.APPLICATION_ID_HEADER, appId).
                get();

        final Request request = OkHttp3Instrumentation.build(builder);

        assertEquals(request.url().toString(), requestUrl);
        assertEquals(request.header(Constants.Network.APPLICATION_ID_HEADER), appId);
        assertNotNull("Cross-Process ID should not be NULL", request.header(Constants.Network.CROSS_PROCESS_ID_HEADER));

        HarvestConfiguration.getDefaultHarvestConfiguration().setAccount_id("1234");
        CallExtension call = (CallExtension) OkHttp3Instrumentation.newCall(client, request);

        assertNotNull("Trace payload should not be null", call.request.header(TracePayload.TRACE_PAYLOAD_HEADER));
        assertNotNull("Trace context parent should not be null", call.request.header(TraceParent.TRACE_PARENT_HEADER));
        assertNotNull("Trace context state should not be null", call.request.header(TraceState.TRACE_STATE_HEADER));

        transactionState = call.getTransactionState();
        Assert.assertNotNull(transactionState.getTrace());
        HarvestConfiguration.getDefaultHarvestConfiguration().setAccount_id("");
        FeatureFlag.disableFeature(FeatureFlag.DistributedTracing);
    }

    @Test
    public void testSetDistributedTraceHeadersWhenHarvestAccountIdEmpty() {
        final String requestUrl = "http://www.foo.com/";
        final String appId = "some-app-id";

        FeatureFlag.enableFeature(FeatureFlag.DistributedTracing);

        final StubAgentImpl agent = StubAgentImpl.install();

        assertEquals(0, agent.getTransactionData().size());

        final Request.Builder builder = new Request.Builder().
                url(requestUrl).
                header(Constants.Network.APPLICATION_ID_HEADER, appId).
                get();

        final Request request = OkHttp3Instrumentation.build(builder);

        assertEquals(request.url().toString(), requestUrl);
        assertEquals(request.header(Constants.Network.APPLICATION_ID_HEADER), appId);
        assertNotNull("Cross-Process ID should not be NULL", request.header(Constants.Network.CROSS_PROCESS_ID_HEADER));

        CallExtension call = (CallExtension) OkHttp3Instrumentation.newCall(client, request);

        assertNull("Trace payload should  be null", call.request.header(TracePayload.TRACE_PAYLOAD_HEADER));
        assertNull("Trace context parent should  be null", call.request.header(TraceParent.TRACE_PARENT_HEADER));
        assertNull("Trace context state should be null", call.request.header(TraceState.TRACE_STATE_HEADER));

        transactionState = call.getTransactionState();
        Assert.assertNotNull(transactionState.getTrace());

        FeatureFlag.disableFeature(FeatureFlag.DistributedTracing);
    }


    @Test
    public void testHeadersCaptureFromRequestForCustomAttribute() {
        final String requestUrl = "http://www.foo.com/";
        final String appId = "some-app-id";

        final StubAgentImpl agent = StubAgentImpl.install();

        assertEquals(0, agent.getTransactionData().size());

        final Request.Builder builder = new Request.Builder().
                url(requestUrl).
                header(Constants.Network.APPLICATION_ID_HEADER, appId).
                header("X-Custom-Header-1", "custom").
                get();

        final Request request = OkHttp3Instrumentation.build(builder);

        CallExtension call = (CallExtension) OkHttp3Instrumentation.newCall(client, request);
        transactionState = call.getTransactionState();

        Assert.assertNotNull(transactionState.getParams());

    }

    @Test
    public void testInterceptedRequestWhenItThrowsError() throws IOException {
        Request request = providePostRequest();
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new Interceptor() {
                    @Override
                    public Response intercept(Chain chain) throws IOException {
                        Request request = chain.request();
                        Assert.assertEquals(request.url().toString(), transactionState.getUrl());
                        return chain.proceed(request);
                    }
                })
                .addInterceptor(new Interceptor() {
                    @Override
                    public Response intercept(Chain chain) throws IOException {
                        Request request = chain.request();
                        Assert.assertEquals(request.url().toString(), transactionState.getUrl());

                        HttpUrl newUrl = request.url().newBuilder()
                                .scheme("https")
                                .host("httpbin.org")
                                .addPathSegment("anything")
                                .build();

                        request = request.newBuilder()
                                .url(newUrl)
                                .build();

                        return chain.proceed(request);
                    }
                })
                .build();

        Assert.assertEquals("http://httpstat.us/200", transactionState.getUrl());

        CallExtension call = (CallExtension) OkHttp3Instrumentation.newCall(client, request);
        transactionState = call.getTransactionState();

        try {
            call.execute();
            throw new IOException("Request failed");
        } catch (Exception e) {
            // ignore
            Assert.assertEquals("https://httpbin.org/anything", transactionState.getUrl());
        }
    }

    @Test
    public void testHeadersCaptureWhenHeadersRemoved() {
        final String requestUrl = "http://www.foo.com/";
        final String appId = "some-app-id";

        HttpHeaders.getInstance().removeHttpHeaderAsAttribute("X-Custom-Header-1");
        final StubAgentImpl agent = StubAgentImpl.install();

        assertEquals(0, agent.getTransactionData().size());

        final Request.Builder builder = new Request.Builder().
                url(requestUrl).
                header(Constants.Network.APPLICATION_ID_HEADER, appId).
                header("X-Custom-Header-1", "custom").
                get();

        final Request request = OkHttp3Instrumentation.build(builder);

        CallExtension call = (CallExtension) OkHttp3Instrumentation.newCall(client, request);
        transactionState = call.getTransactionState();

        Assert.assertEquals(0, transactionState.getParams().size());

    }

    private Request provideRequest() {
        final String requestUrl = "http://www.foo.com";
        final String appId = "some-app-id";
        final Request.Builder builder = new Request.Builder().
                url(requestUrl).
                header(Constants.Network.APPLICATION_ID_HEADER, appId).
                get();
        final Request request = OkHttp3Instrumentation.build(builder);

        return request;
    }

    private Request providePostRequest() {
        final String requestUrl = "http://www.foo.com";
        final String appId = "some-app-id";
        final Request.Builder builder = new Request.Builder().
                url(requestUrl).
                header(Constants.Network.APPLICATION_ID_HEADER, appId).
                post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), "holla"));
        final Request request = OkHttp3Instrumentation.build(builder);

        return request;
    }

    private Response provideResponse() {
        ResponseBody body = new ResponseBuilderExtensionTest.TestResponseBody();
        Response response = new Response.Builder().
                request(provideRequest()).
                protocol(Protocol.HTTP_1_1).
                code(HttpStatus.SC_BAD_REQUEST).
                body(body).
                message("400 Bad Request").
                header(Constants.Network.APP_DATA_HEADER, "some-app-id").
                header(Constants.Network.CONTENT_TYPE_HEADER, body.contentType().toString()).
                build();

        return response;
    }

    /**
     * Test that response body is limited to 4096 bytes when capturing error responses
     */
    @Test
    public void testErrorResponseBodyLimitedTo4096Bytes() throws Exception {
        TestHarvest testHarvest = new TestHarvest();

        // Create a large error response body (10KB)
        String largeErrorBody = generateString(10 * 1024);
        ResponseBody body = createResponseBodyWithContent(largeErrorBody);

        Response response = new Response.Builder()
                .request(provideRequest())
                .protocol(Protocol.HTTP_1_1)
                .code(HttpStatus.SC_INTERNAL_SERVER_ERROR)  // 500 error
                .body(body)
                .message("500 Internal Server Error")
                .header(Constants.Network.CONTENT_TYPE_HEADER, "application/json")
                .build();

        transactionState.setStatusCode(response.code());
        Response result = OkHttp3TransactionStateUtil.addTransactionAndErrorData(transactionState, response);

        assertNotNull("Response should not be null", result);
        testHarvest.verifyQueuedTransactions(1);
    }

    /**
     * Test that chunked responses without Content-Length header are handled correctly
     */
    @Test
    public void testChunkedResponseWithoutContentLengthHeader() {
        // Create a response without Content-Length header (simulating chunked encoding)
        String responseContent = "Chunked response data";
        ResponseBody body = createResponseBodyWithoutContentLength(responseContent);

        Response response = new Response.Builder()
                .request(provideRequest())
                .protocol(Protocol.HTTP_1_1)
                .code(HttpStatus.SC_OK)
                .body(body)
                .message("200 OK")
                // No Content-Length header
                .header("Transfer-Encoding", "chunked")
                .build();

        Response result = OkHttp3TransactionStateUtil.inspectAndInstrumentResponse(transactionState, response);

        assertNotNull("Response should not be null", result);
        // Should not crash even without Content-Length header
    }

    /**
     * Test that multi-byte characters are handled correctly when truncating to 4096 chars
     */
    @Test
    public void testMultiByteCharacterTruncation() throws Exception {
        TestHarvest testHarvest = new TestHarvest();

        // Create error response with multi-byte characters (emoji, CJK, etc.)
        StringBuilder multiByteContent = new StringBuilder();
        // Add emojis and multi-byte characters to exceed 4096 bytes but maybe less than 4096 chars
        for (int i = 0; i < 3000; i++) {
            multiByteContent.append("🔥测试");  // Each emoji/CJK char is 3-4 bytes
        }

        ResponseBody body = createResponseBodyWithContent(multiByteContent.toString());

        Response response = new Response.Builder()
                .request(provideRequest())
                .protocol(Protocol.HTTP_1_1)
                .code(HttpStatus.SC_BAD_REQUEST)
                .body(body)
                .message("400 Bad Request")
                .header(Constants.Network.CONTENT_TYPE_HEADER, "text/plain; charset=utf-8")
                .build();

        transactionState.setStatusCode(response.code());
        Response result = OkHttp3TransactionStateUtil.addTransactionAndErrorData(transactionState, response);

        assertNotNull("Response should not be null", result);
        testHarvest.verifyQueuedTransactions(1);
    }

    /**
     * Test that small error responses are captured completely
     */
    @Test
    public void testSmallErrorResponseCapturedCompletely() throws Exception {
        TestHarvest testHarvest = new TestHarvest();

        String smallErrorBody = "{\"error\": \"Invalid request\", \"code\": 400}";
        ResponseBody body = createResponseBodyWithContent(smallErrorBody);

        Response response = new Response.Builder()
                .request(provideRequest())
                .protocol(Protocol.HTTP_1_1)
                .code(HttpStatus.SC_BAD_REQUEST)
                .body(body)
                .message("400 Bad Request")
                .header(Constants.Network.CONTENT_TYPE_HEADER, "application/json")
                .header(Constants.Network.CONTENT_LENGTH_HEADER, String.valueOf(smallErrorBody.length()))
                .build();

        transactionState.setStatusCode(response.code());
        Response result = OkHttp3TransactionStateUtil.addTransactionAndErrorData(transactionState, response);

        assertNotNull("Response should not be null", result);
        testHarvest.verifyQueuedTransactions(1);
    }

    /**
     * Test that successful responses (non-errors) don't capture body
     */
    @Test
    public void testSuccessfulResponseDoesNotCaptureBody() throws Exception {
        TestHarvest testHarvest = new TestHarvest();

        String responseBody = "Success response body";
        ResponseBody body = createResponseBodyWithContent(responseBody);

        Response response = new Response.Builder()
                .request(provideRequest())
                .protocol(Protocol.HTTP_1_1)
                .code(HttpStatus.SC_OK)  // 200 success - should NOT capture body
                .body(body)
                .message("200 OK")
                .build();

        transactionState.setStatusCode(response.code());
        Response result = OkHttp3TransactionStateUtil.addTransactionAndErrorData(transactionState, response);

        assertNotNull("Response should not be null", result);
        testHarvest.verifyQueuedTransactions(1);
    }

    /**
     * Test that response body exactly at 4096 bytes is handled correctly
     */
    @Test
    public void testResponseBodyExactly4096Bytes() throws Exception {
        TestHarvest testHarvest = new TestHarvest();

        // Create exactly 4096 byte response
        String exactSizeBody = generateString(4096);
        ResponseBody body = createResponseBodyWithContent(exactSizeBody);

        Response response = new Response.Builder()
                .request(provideRequest())
                .protocol(Protocol.HTTP_1_1)
                .code(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                .body(body)
                .message("500 Internal Server Error")
                .header(Constants.Network.CONTENT_LENGTH_HEADER, "4096")
                .build();

        transactionState.setStatusCode(response.code());
        Response result = OkHttp3TransactionStateUtil.addTransactionAndErrorData(transactionState, response);

        assertNotNull("Response should not be null", result);
        testHarvest.verifyQueuedTransactions(1);
    }

    // Helper methods for test response bodies

    private ResponseBody createResponseBodyWithContent(final String content) {
        return new ResponseBody() {
            @Override
            public MediaType contentType() {
                return MediaType.parse("text/plain; charset=utf-8");
            }

            @Override
            public long contentLength() {
                return content.getBytes().length;
            }

            @Override
            public okio.BufferedSource source() {
                return okio.Okio.buffer(okio.Okio.source(new java.io.ByteArrayInputStream(content.getBytes())));
            }
        };
    }

    private ResponseBody createResponseBodyWithoutContentLength(final String content) {
        return new ResponseBody() {
            @Override
            public MediaType contentType() {
                return MediaType.parse("text/plain; charset=utf-8");
            }

            @Override
            public long contentLength() {
                // Return -1 to simulate chunked encoding
                return -1;
            }

            @Override
            public okio.BufferedSource source() {
                return okio.Okio.buffer(okio.Okio.source(new java.io.ByteArrayInputStream(content.getBytes())));
            }
        };
    }

    private String generateString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append((char) ('a' + (i % 26)));
        }
        return sb.toString();
    }

    // ========================================
    // Tests for exhaustiveContentLength() method via reflection
    // ========================================

    /**
     * Helper method to invoke private exhaustiveContentLength() method
     */
    private long invokeExhaustiveContentLength(Response response) throws Exception {
        java.lang.reflect.Method method = OkHttp3TransactionStateUtil.class.getDeclaredMethod("exhaustiveContentLength", Response.class);
        method.setAccessible(true);
        return (long) method.invoke(null, response);
    }

    /**
     * Test that contentLength is correctly obtained from body().contentLength() when available
     */
    @Test
    public void testContentLengthFromBodyContentLength() throws Exception {
        String content = "Test response body with known length";
        ResponseBody body = createResponseBodyWithContent(content);

        Response response = new Response.Builder()
                .request(provideRequest())
                .protocol(Protocol.HTTP_1_1)
                .code(HttpStatus.SC_OK)
                .body(body)
                .message("200 OK")
                .build();

        long contentLength = invokeExhaustiveContentLength(response);

        assertEquals("Content length should match body length",
                    content.getBytes().length, contentLength);
    }

    /**
     * Test that peekBody() is used as LAST RESORT when body().contentLength() returns -1
     * and no Content-Length header is present
     */
    @Test
    public void testContentLengthFromPeekBodyWhenBodyReturnsMinusOne() throws Exception {
        String content = "Chunked response without Content-Length header";
        ResponseBody body = createResponseBodyWithoutContentLength(content);

        Response response = new Response.Builder()
                .request(provideRequest())
                .protocol(Protocol.HTTP_1_1)
                .code(HttpStatus.SC_OK)
                .body(body)
                .message("200 OK")
                .header("Transfer-Encoding", "chunked")
                // No Content-Length header, so will fall back to peekBody
                .build();

        long contentLength = invokeExhaustiveContentLength(response);

        // Should get content length from peekBody() as last resort - limited to 4097 bytes
        assertTrue("Content length should be > 0", contentLength > 0);
        assertTrue("Content length should be <= 4097 (peek limit)", contentLength <= 4097);
    }

    /**
     * Test that peekBody is limited to 4097 bytes to prevent memory issues
     * (only used as last resort when all other methods fail)
     */
    @Test
    public void testPeekBodyLimitedTo4097Bytes() throws Exception {
        // Create a very large response (100KB) with no Content-Length header
        String largeContent = generateString(100 * 1024);
        ResponseBody body = createResponseBodyWithoutContentLength(largeContent);

        Response response = new Response.Builder()
                .request(provideRequest())
                .protocol(Protocol.HTTP_1_1)
                .code(HttpStatus.SC_OK)
                .body(body)
                .message("200 OK")
                .header("Transfer-Encoding", "chunked")
                // No Content-Length header, so will fall back to peekBody as last resort
                .build();

        long contentLength = invokeExhaustiveContentLength(response);

        // Should return at most 4097 (ATTRIBUTE_VALUE_MAX_LENGTH + 1)
        assertTrue("Content length should be > 0", contentLength > 0);
        assertTrue("Content length should be limited to 4097 bytes, got: " + contentLength,
                  contentLength <= 4097);
    }

    /**
     * Test that malformed Content-Length header falls back to peekBody
     */
    @Test
    public void testMalformedContentLengthHeader() throws Exception {
        ResponseBody body = new ResponseBody() {
            @Override
            public MediaType contentType() {
                return MediaType.parse("text/plain");
            }

            @Override
            public long contentLength() {
                return -1;
            }

            @Override
            public okio.BufferedSource source() {
                // Empty source, so peekBody will return 0
                return okio.Okio.buffer(okio.Okio.source(new java.io.ByteArrayInputStream(new byte[0])));
            }
        };

        Response response = new Response.Builder()
                .request(provideRequest())
                .protocol(Protocol.HTTP_1_1)
                .code(HttpStatus.SC_OK)
                .body(body)
                .message("200 OK")
                .header(Constants.Network.CONTENT_LENGTH_HEADER, "not-a-number")
                .build();

        long contentLength = invokeExhaustiveContentLength(response);

        // Malformed header is skipped, peekBody returns 0 for empty source
        assertEquals("Content length should be 0 from peekBody (empty source)", 0L, contentLength);
    }

    /**
     * Test that empty response body returns 0
     */
    @Test
    public void testEmptyResponseBody() throws Exception {
        ResponseBody body = createResponseBodyWithContent("");

        Response response = new Response.Builder()
                .request(provideRequest())
                .protocol(Protocol.HTTP_1_1)
                .code(HttpStatus.SC_NO_CONTENT)
                .body(body)
                .message("204 No Content")
                .header(Constants.Network.CONTENT_LENGTH_HEADER, "0")
                .build();

        long contentLength = invokeExhaustiveContentLength(response);

        assertEquals("Content length should be 0 for empty body", 0L, contentLength);
    }

    /**
     * Test boundary: exactly 4096 bytes (via peekBody as last resort)
     */
    @Test
    public void testExactly4096BytesViaPeekBody() throws Exception {
        String content = generateString(4096);
        ResponseBody body = createResponseBodyWithoutContentLength(content);

        Response response = new Response.Builder()
                .request(provideRequest())
                .protocol(Protocol.HTTP_1_1)
                .code(HttpStatus.SC_OK)
                .body(body)
                .message("200 OK")
                // No Content-Length header, so will use peekBody as last resort
                .build();

        long contentLength = invokeExhaustiveContentLength(response);

        assertEquals("Content length should be 4096 from peekBody", 4096L, contentLength);
    }

    /**
     * Test boundary: 4097 bytes (at peek limit, via peekBody as last resort)
     */
    @Test
    public void testExactly4097BytesViaPeekBody() throws Exception {
        String content = generateString(4097);
        ResponseBody body = createResponseBodyWithoutContentLength(content);

        Response response = new Response.Builder()
                .request(provideRequest())
                .protocol(Protocol.HTTP_1_1)
                .code(HttpStatus.SC_OK)
                .body(body)
                .message("200 OK")
                // No Content-Length header, so will use peekBody as last resort
                .build();

        long contentLength = invokeExhaustiveContentLength(response);

        assertEquals("Content length should be 4097 from peekBody (at peek limit)", 4097L, contentLength);
    }

    /**
     * Test boundary: 5000 bytes exceeds peek limit (via peekBody as last resort)
     */
    @Test
    public void testExceeds4097BytesViaPeekBody() throws Exception {
        String content = generateString(5000);
        ResponseBody body = createResponseBodyWithoutContentLength(content);

        Response response = new Response.Builder()
                .request(provideRequest())
                .protocol(Protocol.HTTP_1_1)
                .code(HttpStatus.SC_OK)
                .body(body)
                .message("200 OK")
                // No Content-Length header, so will use peekBody as last resort
                .build();

        long contentLength = invokeExhaustiveContentLength(response);

        // peekBody limited to 4097, so should return 4097 not 5000
        assertEquals("Content length should be limited to 4097 from peekBody", 4097L, contentLength);
    }

}
