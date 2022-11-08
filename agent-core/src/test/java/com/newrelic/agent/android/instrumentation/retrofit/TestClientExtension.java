/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation.retrofit;

import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.TaskQueue;
import com.newrelic.agent.android.harvest.HarvestData;
import com.newrelic.agent.android.harvest.HttpTransaction;
import com.newrelic.agent.android.harvest.HttpTransactions;
import com.newrelic.agent.android.instrumentation.TransactionState;
import com.newrelic.agent.android.test.mock.Providers;
import com.newrelic.agent.android.test.mock.TestHarvest;
import com.newrelic.agent.android.test.stub.StubAgentImpl;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.UnknownHostException;

import retrofit.RestAdapter;
import retrofit.client.Client;
import retrofit.client.Request;
import retrofit.client.Response;
import retrofit.http.GET;
import retrofit.mime.TypedInput;
import retrofit.mime.TypedOutput;

import static com.newrelic.agent.android.harvest.type.HarvestErrorCodes.NSURLErrorDNSLookupFailed;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;

public class TestClientExtension {
    private TestHarvest testHarvest;
    private ClientExtension clientExtension;
    private TransactionState transactionState;

    @Before
    public void beforeTests() {
        TaskQueue.clear();
        testHarvest = new TestHarvest();
    }

    @Before
    public void setUp() throws Exception {
        clientExtension = new ClientExtension(new TestClient());
    }

    @After
    public void tearDown() throws Exception {
        TaskQueue.synchronousDequeue();
    }

    @After
    public void uninstallAgent() {
        TestHarvest.shutdown();
        StubAgentImpl.uninstall();
    }


    @Test
    public void testClientExecuteNullBody() throws Exception {
        Client client = new TestNullClient();

        RestAdapter.Builder builder = new RestAdapter.Builder().setEndpoint("http://foo.com");
        builder = RetrofitInstrumentation.setClient(builder, client);
        RestAdapter restAdapter = builder.build();

        TestService service = restAdapter.create(TestService.class);
        TestObject result = service.getTestObject();

        assertEquals(null, result);

        // Also test where Retrofit 1.9 returns a null Response.body
        client = new TestNullBody();
        builder = RetrofitInstrumentation.setClient(builder, client);
        restAdapter = builder.build();

        service = restAdapter.create(TestService.class);
        result = service.getTestObject();

        assertEquals(null, result);
    }

    @Test
    public void testClientExecuteEmptyBody() throws Exception {
        TestEmptyClient client = new TestEmptyClient();

        RestAdapter.Builder builder = new RestAdapter.Builder().setEndpoint("http://foo.com");
        builder = RetrofitInstrumentation.setClient(builder, client);
        RestAdapter restAdapter = builder.build();

        TestService service = restAdapter.create(TestService.class);
        TestObject result = service.getTestObject();

        assertEquals(null, result);
    }

    @Test
    public void testClientExecute() throws Exception {
        final String requestUrl = "http://foo.com/testObject";
        final String appId = "some-app-id";
        final StubAgentImpl agent = StubAgentImpl.install();
        FeatureFlag.enableFeature(FeatureFlag.DistributedTracing);

        assertEquals(0, agent.getTransactionData().size());

        TestClient client = new TestClient();
        RestAdapter.Builder builder = new RestAdapter.Builder().setEndpoint("http://foo.com");
        builder = RetrofitInstrumentation.setClient(builder, client);
        RestAdapter restAdapter = builder.build();
        TestService service = restAdapter.create(TestService.class);
        TestObject result = service.getTestObject();

        assertNotNull("Result object should not be null", result);
        assertEquals(result.value, "Test Body");

        TaskQueue.synchronousDequeue();

        HarvestData harvestData = testHarvest.getHarvestData();
        HttpTransactions transactions = harvestData.getHttpTransactions();

        assertEquals(1, transactions.count());

        HttpTransaction transaction = transactions.getHttpTransactions().iterator().next();

        assertEquals(requestUrl, transaction.getUrl());
        assertEquals(200, transaction.getStatusCode());
        assertEquals(agent.getNetworkCarrier(), transaction.getCarrier());
        assertEquals(agent.getNetworkWanType(), transaction.getWanType());
        assertEquals(0, transaction.getBytesSent());
        assertEquals(TestTypedInput.BODY.length(), transaction.getBytesReceived());
        assertNotNull("Trace Payload should not be null on a completed transaction", transaction.getTraceContext());
    }

    @Test
    public void testClientExecuteNoDistributedTracing() throws Exception {
        final String requestUrl = "http://foo.com/testObject";
        final String appId = "some-app-id";
        final StubAgentImpl agent = StubAgentImpl.install();
        FeatureFlag.disableFeature(FeatureFlag.DistributedTracing);

        assertEquals(0, agent.getTransactionData().size());

        TestClient client = new TestClient();
        RestAdapter.Builder builder = new RestAdapter.Builder().setEndpoint("http://foo.com");
        builder = RetrofitInstrumentation.setClient(builder, client);
        RestAdapter restAdapter = builder.build();
        TestService service = restAdapter.create(TestService.class);
        TestObject result = service.getTestObject();

        assertNotNull("Result object should not be null", result);
        assertEquals(result.value, "Test Body");

        TaskQueue.synchronousDequeue();

        HarvestData harvestData = testHarvest.getHarvestData();
        HttpTransactions transactions = harvestData.getHttpTransactions();

        assertEquals(1, transactions.count());

        HttpTransaction transaction = transactions.getHttpTransactions().iterator().next();

        assertEquals(requestUrl, transaction.getUrl());
        assertEquals(200, transaction.getStatusCode());
        assertEquals(agent.getNetworkCarrier(), transaction.getCarrier());
        assertEquals(agent.getNetworkWanType(), transaction.getWanType());
        assertEquals(0, transaction.getBytesSent());
        assertEquals(TestTypedInput.BODY.length(), transaction.getBytesReceived());
        assertNull("Trace Payload should be null on a completed transaction without Distributed Tracing", transaction.getTraceContext());
    }

    @Test
    public void error() throws Exception {
        Request request = provideRequest();
        clientExtension.setRequest(request);
        transactionState = clientExtension.getTransactionState();

        clientExtension.error(new UnknownHostException());
        Assert.assertEquals(NSURLErrorDNSLookupFailed, transactionState.getErrorCode());
    }

    private Request provideRequest() {
        return new Request("GET", Providers.APP_URL, null, mock(TypedOutput.class));
    }

    public class NullTypedInput implements TypedInput {
        @Override
        public String mimeType() {
            return "blarg";
        }

        @Override
        public long length() {
            return 0;
        }

        @Override
        public InputStream in() throws IOException {
            return null;
        }
    }

    public class EmptyTypedInput implements TypedInput {
        @Override
        public String mimeType() {
            return "blarg";
        }

        @Override
        public long length() {
            return 0;
        }

        @Override
        public InputStream in() throws IOException {
            return new ByteArrayInputStream(new byte[0]);
        }
    }

    private class TestTypedInput implements TypedInput {
        public static final String BODY = "{\"value\":\"Test Body\"}";

        @Override
        public String mimeType() {
            return "text/html";
        }

        @Override
        public long length() {
            return BODY.length();
        }

        @Override
        public InputStream in() throws IOException {
            return new ByteArrayInputStream(BODY.getBytes());
        }
    }

    private class TestNullClient implements Client {
        @Override
        public Response execute(Request request) throws IOException {
            Response response = new Response(request.getUrl(), 200, "OK", request.getHeaders(), new NullTypedInput());
            return response;
        }
    }

    private class TestNullBody implements Client {
        @Override
        public Response execute(Request request) throws IOException {
            Response response = new Response(request.getUrl(), 201, "Created", request.getHeaders(), null);
            return response;
        }
    }

    private class TestEmptyClient implements Client {
        @Override
        public Response execute(Request request) throws IOException {
            Response response = new Response(request.getUrl(), 200, "OK", request.getHeaders(), new EmptyTypedInput());
            return response;
        }
    }

    private class TestClient implements Client {
        @Override
        public Response execute(Request request) throws IOException {
            Response response = new Response(request.getUrl(), 200, "OK", request.getHeaders(), new TestTypedInput());
            return response;
        }
    }

    class TestObject {
        String value;
    }

    interface TestService {
        @GET("/testObject")
        TestObject getTestObject();
    }
}
