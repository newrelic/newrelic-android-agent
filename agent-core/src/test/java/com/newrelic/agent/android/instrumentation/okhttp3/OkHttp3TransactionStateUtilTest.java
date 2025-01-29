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
        Assert.assertNull("Should have no status URL", transactionState.getUrl());
        Assert.assertNull("Should have no request method", transactionState.getHttpMethod());
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
        Assert.assertNull("Should have no content or content type", transactionState.getContentType());
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
        Assert.assertNull(transactionState.getTrace());
        CallExtension call = (CallExtension) OkHttp3Instrumentation.newCall(client, provideRequest());
        Assert.assertNotNull(call.getTransactionState().getTrace());

        FeatureFlag.disableFeature(FeatureFlag.DistributedTracing);
        call = (CallExtension) OkHttp3Instrumentation.newCall(client, provideRequest());
        Assert.assertNull(call.getTransactionState().getTrace());
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

        CallExtension call = (CallExtension) OkHttp3Instrumentation.newCall(client, request);

        assertNotNull("Trace payload should not be null", call.request.header(TracePayload.TRACE_PAYLOAD_HEADER));
        assertNotNull("Trace context parent should not be null", call.request.header(TraceParent.TRACE_PARENT_HEADER));
        assertNotNull("Trace context state should not be null", call.request.header(TraceState.TRACE_STATE_HEADER));

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

}
