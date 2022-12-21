/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.Measurements;
import com.newrelic.agent.android.TaskQueue;
import com.newrelic.agent.android.api.common.WanType;
import com.newrelic.agent.android.harvest.HttpTransaction;
import com.newrelic.agent.android.harvest.HttpTransactions;
import com.newrelic.agent.android.test.mock.TestHarvest;
import com.newrelic.agent.android.test.stub.StubAgentImpl;
import com.newrelic.agent.android.util.Constants;
import com.newrelic.agent.android.util.TestUtil;
import com.newrelic.agent.android.util.Util;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@SuppressWarnings("deprecation")
public class DefaultHttpClientInstrumentationTest {
    private static final String RESPONSE_APP_DATA = "abcdefg==";
    private static final int TEST_SERVER_PORT = 33333;
    private static final String STUB_URL = "http://localhost:" + TEST_SERVER_PORT + "/stub";
    private static final String DEFAULT_RESPONSE_CONTENT = "Hello, World";

    private StubAgentImpl agent;
    private HttpServer httpServer;

    private int statusCode;
    private String responseContent;

    private TestHarvest testHarvest;
    private Lock lock = new ReentrantLock();

    @Before
    public void setUp() throws Exception {
        testHarvest = new TestHarvest();
    }

    @Before
    public void beginTest() throws Exception {
        statusCode = 200;
        responseContent = DEFAULT_RESPONSE_CONTENT;
        agent = StubAgentImpl.install();

        //
        // XXX think this requires Oracle JDK 6
        //
        httpServer = HttpServer.create(new InetSocketAddress(TEST_SERVER_PORT), 0);
        httpServer.createContext("/stub", new HttpHandler() {
            @Override
            public void handle(HttpExchange t) throws IOException {
                final InputStream in = t.getRequestBody();
                TestUtil.slurp(in);
                t.getResponseHeaders().add(Constants.Network.APP_DATA_HEADER, RESPONSE_APP_DATA);
                t.sendResponseHeaders(statusCode, responseContent.length());
                final OutputStream out = t.getResponseBody();
                out.write(responseContent.getBytes());
                out.close();
            }
        });
        httpServer.setExecutor(null);
        httpServer.start();

        TaskQueue.clear();
        testHarvest = new TestHarvest();
        TestHarvest.setInstance(testHarvest);
        TestHarvest.initialize(new AgentConfiguration());
        Measurements.initialize();
    }

    @After
    public void endTest() {
        TestHarvest.shutdown();
        Measurements.shutdown();
        killServer();
        StubAgentImpl.uninstall();
    }

    @After
    public void tearDown() throws Exception {
        TaskQueue.synchronousDequeue();
    }

    private void killServer() {
        if (httpServer != null) {
            httpServer.stop(1);
            httpServer = null;
        }
    }

    // @Test
    public void successfulResponseShouldGenerateTransaction() throws Exception {
        lock.lock();
        assertEquals(0, agent.getTransactionData().size());

        TestHarvest harvest = new TestHarvest();
        TestHarvest.setInstance(harvest);
        TestHarvest.initialize(new AgentConfiguration());

        final DefaultHttpClient c = new DefaultHttpClient();
        final HttpGet request = new HttpGet(STUB_URL);
        final HttpResponse response = ApacheInstrumentation.execute(c, request);
        final InputStream in = response.getEntity().getContent();

        TestUtil.slurp(in);

        TaskQueue.synchronousDequeue();

        HttpTransactions transactions = harvest.verifyQueuedTransactions(1);
        HttpTransaction transaction = transactions.getHttpTransactions().iterator().next();
        assertEquals(0, transaction.getBytesSent());
        assertEquals(responseContent.getBytes().length, transaction.getBytesReceived());
        assertEquals(200, transaction.getStatusCode());
        assertEquals(STUB_URL, transaction.getUrl());
        assertEquals("GET", transaction.getHttpMethod());
        assertEquals(RESPONSE_APP_DATA, transaction.getAppData());
        lock.unlock();
    }

    // @Test
    public void statusCodeErrorShouldGenerateTransaction() throws Exception {
        lock.lock();
        statusCode = 404;

        assertEquals(0, agent.getTransactionData().size());

        final DefaultHttpClient c = new DefaultHttpClient();
        final HttpGet request = new HttpGet(STUB_URL);
        final HttpResponse response = ApacheInstrumentation.execute(c, request);

        final InputStream in = response.getEntity().getContent();
        TestUtil.slurp(in);

        TaskQueue.synchronousDequeue();

        HttpTransactions transactions = testHarvest.verifyQueuedTransactions(1);
        HttpTransaction transaction = transactions.getHttpTransactions().iterator().next();
        assertEquals(0, transaction.getBytesSent());
        assertEquals(responseContent.getBytes().length, transaction.getBytesReceived());
        assertEquals(404, transaction.getStatusCode());
        assertEquals(STUB_URL, transaction.getUrl());
        assertEquals("GET", transaction.getHttpMethod());
        assertEquals("wifi", transaction.getWanType());

        lock.unlock();
    }

    // @Test
    public void ioErrorShouldGenerateErrorTransaction() throws Exception {
        lock.lock();
        //
        // Stop the server to simulate an error.
        //
        killServer();

        assertEquals(0, agent.getTransactionData().size());

        final HttpGet request = new HttpGet(STUB_URL);
        final DefaultHttpClient c = new DefaultHttpClient();

        try {
            ApacheInstrumentation.execute(c, request);
            fail("Expected this request to fail");
        } catch (HttpHostConnectException e) {
            // Pass
        }

        TaskQueue.synchronousDequeue();

        HttpTransactions transactions = testHarvest.getHarvestData().getHttpTransactions();
        assertEquals(1, transactions.count());

        HttpTransaction transaction = transactions.getHttpTransactions().iterator().next();
        assertEquals(0, transaction.getBytesSent());
        assertEquals(0, transaction.getBytesReceived());
        assertEquals(0, transaction.getStatusCode());
        assertEquals(-1004, transaction.getErrorCode());
        lock.unlock();
    }

    // @Test
    public void shouldReportPostBodyLength() throws Exception {
        lock.lock();
        final String requestContent = "Hello, World";

        final DefaultHttpClient c = new DefaultHttpClient();
        final HttpPost post = new HttpPost(STUB_URL);
        post.setEntity(new StringEntity(requestContent));
        ApacheInstrumentation.execute(c, post);

        TaskQueue.synchronousDequeue();

        HttpTransactions transactions = testHarvest.getHarvestData().getHttpTransactions();
        assertEquals(1, transactions.count());

        HttpTransaction transaction = transactions.getHttpTransactions().iterator().next();
        assertEquals(requestContent.getBytes("UTF-8").length, transaction.getBytesSent());
        assertEquals(responseContent.getBytes("UTF-8").length, transaction.getBytesReceived());
        assertEquals(RESPONSE_APP_DATA, transaction.getAppData());
        lock.unlock();
    }

    //
    // Previous implementations of the instrumentation. required you to read the full
    // response for a transaction to be created.
    //
    // @Test
    public void shouldNotNeedToReadInputStreamToCompletionToSeeTransaction() throws Exception {
        lock.lock();
        assertEquals(0, agent.getTransactionData().size());

        final DefaultHttpClient c = new DefaultHttpClient();
        final HttpGet request = new HttpGet(STUB_URL);

        ApacheInstrumentation.execute(c, request);
        TaskQueue.synchronousDequeue();

        HttpTransactions transactions = testHarvest.verifyQueuedTransactions(1);
        HttpTransaction transaction = transactions.getHttpTransactions().iterator().next();

        assertEquals(DEFAULT_RESPONSE_CONTENT.length(), transaction.getBytesReceived());
        lock.unlock();
    }

}
