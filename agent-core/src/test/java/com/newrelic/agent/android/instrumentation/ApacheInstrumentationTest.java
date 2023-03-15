/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation;

import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.distributedtracing.TraceParent;
import com.newrelic.agent.android.distributedtracing.TracePayload;
import com.newrelic.agent.android.distributedtracing.TraceState;
import com.newrelic.agent.android.instrumentation.httpclient.HttpRequestEntityImpl;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.test.mock.Providers;
import com.newrelic.agent.android.test.mock.TestHarvest;
import com.newrelic.agent.android.test.stub.StubAgentImpl;
import com.newrelic.agent.android.tracing.TraceMachine;
import com.newrelic.agent.android.util.Constants;
import com.newrelic.agent.android.util.ExceptionHelper;

import org.apache.http.Header;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.UnknownHostException;

import static com.newrelic.agent.android.harvest.type.HarvestErrorCodes.NSURLErrorDNSLookupFailed;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ApacheInstrumentationTest {

    private TransactionState transactionState;
    private TestHarvest testHarvest;
    private AgentLog agentLog = mock(AgentLog.class);

    @BeforeClass
    public static void classSetUp() {
        FeatureFlag.enableFeature(FeatureFlag.DistributedTracing);
        Agent.setImpl(new TransactionStateUtilTest.TestStubAgentImpl());
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


    // Validating fix for bug: https://newrelic.atlassian.net/browse/MOBILE-122
    // @Test
    public void multipleRequestsShouldReplaceCrossAppIdHeader() throws Exception {
        final StubAgentImpl agent = StubAgentImpl.install();
        for (int i = 0; i < 5; i++) {
            try (CloseableHttpClient client = HttpClientBuilder.create().build()) {
                final HttpGet get = new HttpGet("https://google.com");
                ApacheInstrumentation.execute(client, get);
                final Header[] requestHeaders = get.getHeaders(Constants.Network.CROSS_PROCESS_ID_HEADER);
                assertTrue(requestHeaders != null);
                assertEquals(requestHeaders.length, 1);
                assertEquals(requestHeaders[0].getValue(), agent.getCrossProcessId());
            }
        }
    }

    // Validating fix for bug: https://newrelic.atlassian.net/browse/MOBILE-1373
    // @Test
    public void headRequestWith404Response() throws Exception {
        try (CloseableHttpClient client = HttpClientBuilder.create().build()) {
            HttpHead httpHead = new HttpHead();
            httpHead.setURI(new URI("http://httpstat.us/404"));
            HttpResponse response = ApacheInstrumentation.execute(client, httpHead);
            // The HEAD method is identical to GET except that the server MUST NOT return a message-body in the response
            // So a null entity is OK in this case and we shouldn't error out
            assertTrue(response.getEntity() == null);
        }
    }

    @Test
    public void testInspectAndInstrumentUriRequest() {
        HttpUriRequest httpUriRequest = spy(Providers.provideHttpUriRequest());
        ApacheInstrumentation.inspectAndInstrument(transactionState, httpUriRequest);
        verify(httpUriRequest).setHeader(Constants.Network.CROSS_PROCESS_ID_HEADER, Agent.getCrossProcessId());

        if (FeatureFlag.featureEnabled(FeatureFlag.DistributedTracing)) {
            Assert.assertTrue(httpUriRequest.containsHeader(TracePayload.TRACE_PAYLOAD_HEADER));
            Assert.assertTrue(httpUriRequest.containsHeader(TraceState.TRACE_STATE_HEADER));
            Assert.assertTrue(httpUriRequest.containsHeader(TraceParent.TRACE_PARENT_HEADER));
        }
    }

    @Test
    public void testInspectAndInstrumentHttpResponse() throws Exception {
        HttpResponse httpResponse = Providers.provideHttpResponse();
        ApacheInstrumentation.inspectAndInstrument(transactionState, httpResponse);
        testHarvest.verifyQueuedTransactions(1);
    }

    @Test
    public void testInspectAndInstrumentHttpErrorResponse() throws Exception {
        HttpResponse httpResponse = Providers.provideHttpResponse();
        httpResponse.setStatusCode(HttpStatus.SC_NOT_FOUND);
        ApacheInstrumentation.inspectAndInstrument(transactionState, httpResponse);
        testHarvest.verifyQueuedTransactions(1);

        transactionState = Providers.provideTransactionState();
        httpResponse.removeHeaders(Constants.Network.CONTENT_LENGTH_HEADER);
        ApacheInstrumentation.inspectAndInstrument(transactionState, httpResponse);
        Assert.assertEquals("Should contain 0 bytes received", 0, transactionState.getBytesReceived());
        testHarvest.verifyQueuedTransactions(2);
    }

    @Test
    public void testInspectAndInstrumentRequestFailureResponse() throws Exception {
        HttpResponse httpResponse = Providers.provideHttpResponse();
        transactionState.setErrorCode(NSURLErrorDNSLookupFailed);
        ApacheInstrumentation.inspectAndInstrument(transactionState, httpResponse);
        testHarvest.verifyQueuedTransactions(1);

        transactionState = Providers.provideTransactionState();
        httpResponse.removeHeaders(Constants.Network.CONTENT_LENGTH_HEADER);
        ApacheInstrumentation.inspectAndInstrument(transactionState, httpResponse);
        Assert.assertEquals("Should contain 0 bytes received", 0, transactionState.getBytesReceived());
        testHarvest.verifyQueuedTransactions(2);
    }

    @Test
    public void testInspectAndInstrumentHttpRequest() {
        HttpRequest httpRequest = spy(Providers.provideHttpRequest());
        ApacheInstrumentation.inspectAndInstrument(transactionState, Providers.provideHttpHost(), httpRequest);
        Assert.assertEquals("Should set APP_URL", transactionState.getUrl(), Providers.APP_URL);
        Assert.assertEquals("Should set method", transactionState.getHttpMethod(), Providers.APP_METHOD);

        httpRequest = spy(Providers.provideHttpRequest());
        when(httpRequest.getRequestLine()).thenReturn(null);
        try {
            ApacheInstrumentation.inspectAndInstrument(transactionState, Providers.provideHttpHost(), httpRequest);
        } catch (Exception e) {
            Assert.fail("HttpRequest.getRequestLine should not be null");
        }

        transactionState = new TransactionState();
        transactionState.setUrl(null);
        transactionState.setHttpMethod(null);

        ApacheInstrumentation.inspectAndInstrument(transactionState, Providers.provideHttpHost(), httpRequest);

        verify(httpRequest, atLeast(2)).setHeader(Constants.Network.CROSS_PROCESS_ID_HEADER, Agent.getCrossProcessId());
        verify(agentLog, atLeastOnce()).error(anyString(), any(Throwable.class));
        // DT?
    }

    @Test
    public void testSetErrorCodeFromException() throws Exception {
        Exception exception = new UnknownHostException("host lookup error");
        int errorCode = ExceptionHelper.exceptionToErrorCode(exception);
        TransactionStateUtil.setErrorCodeFromException(transactionState, exception);
        Assert.assertEquals("Should set exception error code", errorCode, transactionState.getErrorCode());

        HttpResponse httpResponse = Providers.provideHttpResponse();
        ApacheInstrumentation.inspectAndInstrument(transactionState, httpResponse);

        testHarvest.verifyQueuedTransactions(1);    // add an HttpTransaction for the request
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testWrapRequestEntity() {
        HttpEntityEnclosingRequest httpEntityEnclosingRequest = spy(new BasicHttpEntityEnclosingRequest(Providers.APP_METHOD, Providers.APP_URL));
        httpEntityEnclosingRequest.setEntity(new InputStreamEntity(new ByteArrayInputStream(Providers.APP_DATA.getBytes()), -1));

        ApacheInstrumentation.inspectAndInstrument(transactionState, Providers.provideHttpHost(), httpEntityEnclosingRequest);
        verify(httpEntityEnclosingRequest, atLeastOnce()).setEntity(any(HttpRequestEntityImpl.class));
    }

    @Test
    public void testSetDistributedTraceHeaders() {
        HttpRequest httpRequest = spy(Providers.provideHttpRequest());
        ApacheInstrumentation.inspectAndInstrument(transactionState, Providers.provideHttpHost(), httpRequest);
        Assert.assertEquals("Should set APP_URL", transactionState.getUrl(), Providers.APP_URL);
        Assert.assertEquals("Should set method", transactionState.getHttpMethod(), Providers.APP_METHOD);

        httpRequest = spy(Providers.provideHttpRequest());
        when(httpRequest.getRequestLine()).thenReturn(null);
        try {
            ApacheInstrumentation.inspectAndInstrument(transactionState, Providers.provideHttpHost(), httpRequest);
        } catch (Exception e) {
            Assert.fail("HttpRequest.getRequestLine should not be null");
        }

        Assert.assertTrue(httpRequest.containsHeader(TracePayload.TRACE_PAYLOAD_HEADER));
        Assert.assertTrue(httpRequest.containsHeader(TraceParent.TRACE_PARENT_HEADER));
        Assert.assertTrue(httpRequest.containsHeader(TraceState.TRACE_STATE_HEADER));
    }
}
