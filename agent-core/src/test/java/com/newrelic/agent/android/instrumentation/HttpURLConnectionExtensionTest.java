/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.Measurements;
import com.newrelic.agent.android.TaskQueue;
import com.newrelic.agent.android.distributedtracing.TraceParent;
import com.newrelic.agent.android.distributedtracing.TracePayload;
import com.newrelic.agent.android.distributedtracing.TraceState;
import com.newrelic.agent.android.harvest.Harvest;
import com.newrelic.agent.android.harvest.HarvestData;
import com.newrelic.agent.android.harvest.HttpTransaction;
import com.newrelic.agent.android.harvest.HttpTransactions;
import com.newrelic.agent.android.instrumentation.okhttp2.OkHttp2Instrumentation;
import com.newrelic.agent.android.instrumentation.okhttp3.OkHttp3Instrumentation;
import com.newrelic.agent.android.test.stub.StubAgentImpl;
import com.newrelic.agent.android.util.Constants;
import com.newrelic.agent.android.util.TestUtil;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HttpURLConnectionExtensionTest {
    TestHarvest testHarvest = new TestHarvest();

    @Before
    public void beforeTests() {
        testHarvest = new TestHarvest();
        TestHarvest.setInstance(testHarvest);
        TestHarvest.initialize(new AgentConfiguration());
        Measurements.initialize();
        FeatureFlag.enableFeature(FeatureFlag.DistributedTracing);
    }

    @After
    public void uninstallAgent() {
        TestHarvest.shutdown();
        Measurements.shutdown();
        StubAgentImpl.uninstall();
    }

    // @Test
    public void successfulResponseUsingHttpURLConnectionExtensionShouldGenerateTransaction() throws Exception {
        final String responseData = "Hello, World";
        final String requestUrl = "http://www.foo.com";
        final String appId = "some-app-id";

        final StubAgentImpl agent = StubAgentImpl.install();

        assertEquals(0, agent.getTransactionData().size());

        final HttpURLConnection mockConnection = mock(HttpURLConnection.class);
        when(mockConnection.getURL()).thenReturn(new URL(requestUrl));
        when(mockConnection.getResponseCode()).thenReturn(200);
        when(mockConnection.getContentLength()).thenReturn(-1);
        when(mockConnection.getInputStream()).thenReturn(new ByteArrayInputStream(responseData.getBytes()));
        when(mockConnection.getHeaderField(Constants.Network.APP_DATA_HEADER)).thenReturn(appId);

        final URLConnection instrumentedConnection = URLConnectionInstrumentation.openConnection(mockConnection);
        assertTrue(instrumentedConnection instanceof HttpURLConnectionExtension);

        final HttpURLConnectionExtension extension = (HttpURLConnectionExtension) instrumentedConnection;
        final InputStream inputStream = extension.getInputStream();
        final String data = TestUtil.slurp(inputStream);
        assertEquals(data, responseData);

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
        assertEquals(responseData.length(), transaction.getBytesReceived());
        assertNotNull(transaction.getTraceContext());
    }

    @Test
    public void testValidateHttpsURLConnection() throws Exception {
        final String responseData = "Hello, World";
        final String requestUrl = "https://www.foo.com";
        final String appId = "some-app-id";

        final StubAgentImpl agent = StubAgentImpl.install();

        assertEquals(0, agent.getTransactionData().size());

        final HttpURLConnection mockConnection = mock(HttpsURLConnection.class);
        when(mockConnection.getURL()).thenReturn(new URL(requestUrl));
        when(mockConnection.getResponseCode()).thenReturn(200);
        when(mockConnection.getContentLength()).thenReturn(-1);
        when(mockConnection.getInputStream()).thenReturn(new ByteArrayInputStream(responseData.getBytes()));
        when(mockConnection.getHeaderField(Constants.Network.APP_DATA_HEADER)).thenReturn(appId);

        final URLConnection instrumentedConnection = URLConnectionInstrumentation.openConnection(mockConnection);
        assertTrue(instrumentedConnection instanceof HttpsURLConnectionExtension);

        final HttpsURLConnectionExtension extension = (HttpsURLConnectionExtension) instrumentedConnection;
        final InputStream inputStream = extension.getInputStream();
        final String data = TestUtil.slurp(inputStream);
        assertEquals(data, responseData);

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
        assertEquals(responseData.length(), transaction.getBytesReceived());
        assertNotNull(transaction.getTraceContext());
    }

    @Test
    public void testOkHttp2Factory() throws Exception {
        com.squareup.okhttp.OkHttpClient httpClient = new com.squareup.okhttp.OkHttpClient();
        com.squareup.okhttp.OkUrlFactory factory = new com.squareup.okhttp.OkUrlFactory(httpClient);

        URL url = new URL("https://www.foo.com");
        HttpURLConnection connection = OkHttp2Instrumentation.open(factory, url);
        assertTrue("Should be HttpsURLConnectionExtension", connection instanceof HttpsURLConnectionExtension);
        assertTrue("Should be instanceOf HttpsURLConnection", connection instanceof HttpsURLConnection);
        assertTrue("Should be instanceOf HttpURLConnection", connection instanceof HttpURLConnection);

        try {
            HttpURLConnection httpUrlConn = (HttpURLConnection) connection;
        } catch (ClassCastException e) {
            Assert.fail("Cast should not throw.");
        }

        url = new URL("http://www.foo.com");
        connection = OkHttp2Instrumentation.open(factory, url);
        assertFalse("Should not be HttpsURLConnectionExtension", connection instanceof HttpsURLConnectionExtension);
        assertTrue("Should be instanceOf HttpURLConnection", connection instanceof HttpURLConnection);

        try {
            HttpsURLConnection httpsUrlConn = (HttpsURLConnection) connection;
            Assert.fail("Cast should throw.");
        } catch (Exception e) {
            assertTrue("Should throw cast exception", e instanceof ClassCastException);
        }
    }

    @Test
    public void testOkHttp3Factory() throws Exception {
        okhttp3.OkHttpClient httpClient = new okhttp3.OkHttpClient();
        @SuppressWarnings("deprecation")
        okhttp3.OkUrlFactory factory = new okhttp3.OkUrlFactory(httpClient);

        URL url = new URL("https://www.google.com");
        HttpURLConnection connection = OkHttp3Instrumentation.open(factory, url);
        assertTrue("Should be HttpsURLConnectionExtension", connection instanceof HttpsURLConnectionExtension);
        assertTrue("Should be instanceOf HttpsURLConnection", connection instanceof HttpsURLConnection);
        assertTrue("Should be instanceOf HttpURLConnection", connection instanceof HttpURLConnection);

        try {
            HttpURLConnection httpUrlConn = (HttpURLConnection) connection;
        } catch (ClassCastException e) {
            Assert.fail("Cast should not throw.");
        }

        url = new URL("http://www.foo.com");
        connection = OkHttp3Instrumentation.open(factory, url);
        assertFalse("Should not be HttpsURLConnectionExtension", connection instanceof HttpsURLConnectionExtension);
        assertTrue("Should be instanceOf HttpURLConnection", connection instanceof HttpURLConnection);

        try {
            HttpsURLConnection httpsUrlConn = (HttpsURLConnection) connection;
            Assert.fail("Cast should throw.");
        } catch (Exception e) {
            assertTrue("Should throw cast exception", e instanceof ClassCastException);
        }
    }

    final String responseData = "\n" +
            "    -=[ teapot ]=-\n" +
            "\n" +
            "       _...._\n" +
            "     .'  _ _ `.\n" +
            "    | .\"` ^ `\". _,\n" +
            "    \\_;`\"---\"`|//\n" +
            "      |       ;/\n" +
            "      \\_     _/\n" +
            "        `\"\"\"`\n";

    final String requestUrl = "http://httpbin.org/status/418";

    @Test
    public void testErrorStreamResponseBody() throws Exception {
        final HttpURLConnection mockConnection = mock(HttpURLConnection.class);
        when(mockConnection.getURL()).thenReturn(new URL(requestUrl));
        when(mockConnection.getResponseCode()).thenReturn(418);
        when(mockConnection.getContentLength()).thenReturn(responseData.length());
        when(mockConnection.getInputStream()).thenReturn(null);
        when(mockConnection.getErrorStream()).thenReturn(new ByteArrayInputStream(responseData.getBytes()));

        final HttpURLConnectionExtension instrumentedConnection = new HttpURLConnectionExtension(mockConnection);

        TransactionState transactionState = instrumentedConnection.getTransactionState();
        TransactionStateUtil.inspectAndInstrument(transactionState, mockConnection);
        TransactionStateUtil.inspectAndInstrumentResponse(transactionState, mockConnection);
        instrumentedConnection.addTransactionAndErrorData(transactionState);

        // read the stream's data
        assertEquals(responseData, TestUtil.slurp(instrumentedConnection.getErrorStream()));

        TaskQueue.synchronousDequeue();

        HarvestData harvestData = testHarvest.getHarvestData();
        HttpTransactions transactions = harvestData.getHttpTransactions();
        HttpTransaction transaction = transactions.getHttpTransactions().iterator().next();

        assertEquals(requestUrl, transaction.getUrl());
        assertEquals(418, transaction.getStatusCode());
        assertEquals(0, transaction.getBytesSent());
        assertEquals(responseData.length(), transaction.getBytesReceived());
        assertEquals(responseData, transaction.getResponseBody());
        assertNotNull(transaction.getTraceContext());
    }

    @Test
    public void testInputStreamResponseBody() throws Exception {
        final HttpURLConnection mockConnection = mock(HttpURLConnection.class);
        when(mockConnection.getURL()).thenReturn(new URL(requestUrl));
        when(mockConnection.getResponseCode()).thenReturn(418);
        when(mockConnection.getContentLength()).thenReturn(responseData.length());
        when(mockConnection.getInputStream()).thenReturn(new ByteArrayInputStream(responseData.getBytes()));
        when(mockConnection.getErrorStream()).thenReturn(null);

        final HttpURLConnectionExtension instrumentedConnection = new HttpURLConnectionExtension(mockConnection);

        TransactionState transactionState = instrumentedConnection.getTransactionState();
        TransactionStateUtil.inspectAndInstrument(transactionState, mockConnection);
        TransactionStateUtil.inspectAndInstrumentResponse(transactionState, mockConnection);
        instrumentedConnection.addTransactionAndErrorData(transactionState);

        // read the stream's data
        assertEquals(responseData, TestUtil.slurp(instrumentedConnection.getInputStream()));

        TaskQueue.synchronousDequeue();

        HarvestData harvestData = testHarvest.getHarvestData();
        HttpTransactions transactions = harvestData.getHttpTransactions();
        HttpTransaction transaction = transactions.getHttpTransactions().iterator().next();

        assertEquals(requestUrl, transaction.getUrl());
        assertEquals(418, transaction.getStatusCode());
        assertEquals(0, transaction.getBytesSent());
        assertEquals(responseData.length(), transaction.getBytesReceived());
        assertEquals(null, transaction.getResponseBody());
        assertNotNull(transaction.getTraceContext());
    }

    @Test
    public void testException() throws Exception {
        final HttpURLConnection mockConnection = mock(HttpURLConnection.class);
        when(mockConnection.getURL()).thenReturn(new URL(requestUrl));
        when(mockConnection.getResponseCode()).thenReturn(418);
        when(mockConnection.getContentLength()).thenReturn(responseData.length());
        when(mockConnection.getInputStream()).thenReturn(null);
        when(mockConnection.getErrorStream()).thenReturn(new ByteArrayInputStream(responseData.getBytes()));

        final HttpURLConnectionExtension instrumentedConnection = new HttpURLConnectionExtension(mockConnection);

        TransactionState transactionState = instrumentedConnection.getTransactionState();
        TransactionStateUtil.inspectAndInstrument(transactionState, mockConnection);
        TransactionStateUtil.inspectAndInstrumentResponse(transactionState, mockConnection);
        instrumentedConnection.error(new FileNotFoundException());

        // read the stream's data
        assertEquals(responseData, TestUtil.slurp(instrumentedConnection.getErrorStream()));

        TaskQueue.synchronousDequeue();

        HarvestData harvestData = testHarvest.getHarvestData();
        HttpTransactions transactions = harvestData.getHttpTransactions();
        HttpTransaction transaction = transactions.getHttpTransactions().iterator().next();

        assertEquals(requestUrl, transaction.getUrl());
        assertEquals(418, transaction.getStatusCode());
        assertEquals(0, transaction.getBytesSent());
        assertEquals(responseData.length(), transaction.getBytesReceived());
        assertEquals(responseData, transaction.getResponseBody());
        assertNotNull(transaction.getTraceContext());
    }

    @Test
    public void testSetDistributedTracePayload() throws IOException {
        FeatureFlag.enableFeature(FeatureFlag.DistributedTracing);

        final HttpURLConnection mockConnection = mock(HttpURLConnection.class);
        when(mockConnection.getURL()).thenReturn(new URL(requestUrl));
        when(mockConnection.getResponseCode()).thenReturn(201);
        when(mockConnection.getContentLength()).thenReturn(responseData.length());
        
        final HttpURLConnectionExtension instrumentedConnection = new HttpURLConnectionExtension(mockConnection);
        TransactionState transactionState = instrumentedConnection.getTransactionState();
        TransactionStateUtil.setDistributedTraceHeaders(transactionState, mockConnection);
        
        Assert.assertNotNull(transactionState.getTrace());
        Map<String, List<String>> headers = instrumentedConnection.getRequestProperties();
        // Assert.assertTrue(headers.containsKey(TracePayload.TRACE_PAYLOAD_HEADER));
        // Assert.assertTrue(headers.containsKey(TraceState.TRACE_STATE_HEADER));
        // Assert.assertTrue(headers.containsKey(TraceParent.TRACE_PARENT_HEADER));
        
        FeatureFlag.disableFeature(FeatureFlag.DistributedTracing);
    }

    @Test
    public void testSetDistributedTracePayloadWhenConnected() throws IOException {
        FeatureFlag.enableFeature(FeatureFlag.DistributedTracing);

        try {
            URL url = new URL(requestUrl);
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            final HttpURLConnectionExtension instrumentedConnection = new HttpURLConnectionExtension(urlConnection);

            instrumentedConnection.connect();

            TransactionState transactionState = instrumentedConnection.getTransactionState();
            Assert.assertNotNull(transactionState.getTrace());

            Map<String, List<String>> headers = instrumentedConnection.getHeaderFields();
        }catch(IllegalStateException e){
            Assert.fail(e.getMessage());
        }catch(Exception e){
            Assert.fail(e.getMessage());
        }

        FeatureFlag.disableFeature(FeatureFlag.DistributedTracing);
    }

    @Test
    public void testSetDistributedTraceRequestPayload() throws IOException {
        FeatureFlag.enableFeature(FeatureFlag.DistributedTracing);

        try {
            URL url = new URL(requestUrl);
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            final HttpURLConnectionExtension instrumentedConnection = new HttpURLConnectionExtension(urlConnection);

            TransactionState transactionState = instrumentedConnection.getTransactionState();
            Assert.assertNotNull(transactionState.getTrace());

            Map<String, List<String>> requestPayload = instrumentedConnection.getRequestProperties();
            Assert.assertTrue(requestPayload.containsKey(TracePayload.TRACE_PAYLOAD_HEADER));
            Assert.assertTrue(requestPayload.containsKey(TraceState.TRACE_STATE_HEADER));
            Assert.assertTrue(requestPayload.containsKey(TraceParent.TRACE_PARENT_HEADER));
        }catch(IllegalStateException e){
            Assert.fail(e.getMessage());
        }catch(Exception e){
            Assert.fail(e.getMessage());
        }

        FeatureFlag.disableFeature(FeatureFlag.DistributedTracing);
    }

    private class TestHarvest extends Harvest {
        public HarvestData getHarvestData() {
            return harvestData;
        }
    }
}
