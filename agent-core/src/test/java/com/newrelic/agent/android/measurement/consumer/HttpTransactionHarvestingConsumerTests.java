/*
 * Copyright (c) 2021-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.measurement.consumer;

import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.analytics.AnalyticsControllerImpl;
import com.newrelic.agent.android.analytics.EventManagerImpl;
import com.newrelic.agent.android.analytics.NetworkEventTransformer;
import com.newrelic.agent.android.api.common.CarrierType;
import com.newrelic.agent.android.api.common.WanType;
import com.newrelic.agent.android.harvest.Harvest;
import com.newrelic.agent.android.harvest.HttpTransaction;
import com.newrelic.agent.android.measurement.http.HttpTransactionMeasurement;
import com.newrelic.agent.android.test.mock.Providers;
import com.newrelic.agent.android.test.stub.StubAgentImpl;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Collection;
import java.util.HashMap;

@RunWith(JUnit4.class)
public class HttpTransactionHarvestingConsumerTests {
    private static final String TEST_URL = "https://dispicable.me";
    private static final String TEST_METHOD = "Man";
    private static final int TEST_STATUS = 204;
    private static final int TEST_HTTP_ERROR_STATUS = 401;
    private static final int TEST_TOTAL_TIME = 1984;
    private static final long TEST_BYTES_SENT = 2001;
    private static final long TEST_BYTES_RCVD = 2112;
    private static final String TEST_RESPONSE_BODY = "Test";

    private TestStubAgentImpl agent;
    private HttpTransactionHarvestingConsumer consumer;
    private final Long noTimeLikeThePresent = System.currentTimeMillis();

    @Before
    public void setUp() throws Exception {
        agent = new TestStubAgentImpl();
        Agent.setImpl(agent);

        consumer = new HttpTransactionHarvestingConsumer();
        Harvest.getInstance().createHarvester();

        FeatureFlag.enableFeature(FeatureFlag.DistributedTracing);

        // reset any listener
        AnalyticsControllerImpl.getInstance().getEventManager().setEventListener(null);
    }

    @Test
    public void testConsumeMeasurement() throws Exception {
        HttpTransaction[] array;

        agent.setNetworkCarrier("VirginMobile");
        agent.setWanType(WanType.EDGE);
        consumer.consumeMeasurement(measurementFactory());

        Collection<HttpTransaction> transactions = Harvest.getInstance().getHarvestData().getHttpTransactions().getHttpTransactions();
        Assert.assertEquals("Should contain 1 transaction", 1, transactions.size());
        array = transactions.toArray(new HttpTransaction[transactions.size()]);
        HttpTransaction transaction = array[0];

        Assert.assertEquals("URL should contain minions", TEST_URL, transaction.getUrl());
        Assert.assertEquals("HTTP status should be no content", 204, transaction.getStatusCode());
        Assert.assertEquals("Method should be WuTang", TEST_METHOD, transaction.getHttpMethod());
        Assert.assertEquals("George should spend 1984 ms in flight", TEST_TOTAL_TIME, (int) transaction.getTotalTime());
        Assert.assertEquals("Stanley should send 2001 bytes ", TEST_BYTES_SENT, transaction.getBytesSent());
        Assert.assertEquals("Geddy should receive 2112 bytes", TEST_BYTES_RCVD, transaction.getBytesReceived());
        Assert.assertEquals("Should record the transaction NOW!", noTimeLikeThePresent, transaction.getTimestamp());
        Assert.assertEquals("Carrier should be Virgin", "VirginMobile", transaction.getCarrier());
        Assert.assertEquals("WAN type should be 2G", WanType.EDGE, transaction.getWanType());

        agent.setNetworkCarrier("T-Mobile");
        agent.setWanType(WanType.LTE);
        consumer.consumeMeasurement(measurementFactory());
        transactions = Harvest.getInstance().getHarvestData().getHttpTransactions().getHttpTransactions();
        Assert.assertEquals("Should contain 2 transaction", 2, transactions.size());
        array = transactions.toArray(new HttpTransaction[transactions.size()]);
        transaction = array[1];
        Assert.assertEquals("Carrier should be T-Mobile", "T-Mobile", transaction.getCarrier());
        Assert.assertEquals("WAN type should be 4G", WanType.LTE, transaction.getWanType());
    }

    @Test
    public void testConsumeTransformedMeasurement() throws Exception {
        HttpTransaction[] array;

        HashMap<String, String> transforms = new HashMap<String, String>() {{
            put("me", "you");
            put("dispicable", "lovable");
        }};
        NetworkEventTransformer transformer = new NetworkEventTransformer(transforms);
        EventManagerImpl eventManager = (EventManagerImpl) AnalyticsControllerImpl.getInstance().getEventManager();
        eventManager.setEventListener(transformer);
        HttpTransactionMeasurement measurements = measurementFactory();
        Assert.assertEquals(TEST_URL, measurements.getUrl());

        consumer.consumeMeasurement(measurements);

        Collection<HttpTransaction> transactions = Harvest.getInstance().getHarvestData().getHttpTransactions().getHttpTransactions();
        Assert.assertEquals("Should contain 1 transaction", 1, transactions.size());
        array = transactions.toArray(new HttpTransaction[transactions.size()]);
        HttpTransaction transaction = array[0];

        Assert.assertEquals("URL should transformed url", "https://lovable.you", transaction.getUrl());
    }

    @Test
    public void testConsumeMeasurementWithResponseBody() throws Exception {
        HttpTransaction[] array;

        agent.setNetworkCarrier("VirginMobile");
        agent.setWanType(WanType.EDGE);
        consumer.consumeMeasurement(measurementFactoryWithResponseBody());

        Collection<HttpTransaction> transactions = Harvest.getInstance().getHarvestData().getHttpTransactions().getHttpTransactions();
        Assert.assertEquals("Should contain 1 transaction", 1, transactions.size());
        array = transactions.toArray(new HttpTransaction[transactions.size()]);
        HttpTransaction transaction = array[0];

        Assert.assertEquals("Response should not be Null", TEST_RESPONSE_BODY, transaction.getResponseBody());
        Assert.assertEquals("HTTP status should be no content", TEST_HTTP_ERROR_STATUS, transaction.getStatusCode());

    }

    @Test
    public void testOptionalProperties() throws Exception {
        FeatureFlag.enableFeature(FeatureFlag.HttpResponseBodyCapture);

        consumer.consumeMeasurement(measurementFactory());
        consumer.consumeMeasurement(new HttpTransactionMeasurement(Providers.provideTransactionData()));

        Collection<HttpTransaction> transactions = Harvest.getInstance().getHarvestData().getHttpTransactions().getHttpTransactions();
        Assert.assertEquals("Should contain 2 transactions", 2, transactions.size());

        HttpTransaction[] httpTransactions = transactions.toArray(new HttpTransaction[transactions.size()]);
        Assert.assertNull("Optional responseBody should be null by default", httpTransactions[0].getResponseBody());
        Assert.assertNull("Optional params should be null by default", httpTransactions[0].getParams());
        Assert.assertNull("Optional traceAttributes should be null by default", httpTransactions[0].getTraceAttributes());

        Assert.assertNotNull("Optional responseBody should be set", httpTransactions[1].getResponseBody());
        Assert.assertNotNull("Optional params should be set", httpTransactions[1].getParams());
        Assert.assertNotNull("Optional traceAttributes should be set", httpTransactions[1].getTraceAttributes());
    }

    private HttpTransactionMeasurement measurementFactory() {
        HttpTransactionMeasurement measurement = new HttpTransactionMeasurement(TEST_URL, TEST_METHOD,
                TEST_STATUS, 0, noTimeLikeThePresent, TEST_TOTAL_TIME, TEST_BYTES_SENT,
                TEST_BYTES_RCVD, "hotbox!");

        return measurement;
    }

    private HttpTransactionMeasurement measurementFactoryWithResponseBody() {
        HttpTransactionMeasurement measurement = new HttpTransactionMeasurement(TEST_URL, TEST_METHOD,
                TEST_HTTP_ERROR_STATUS, 0, noTimeLikeThePresent, TEST_TOTAL_TIME, TEST_BYTES_SENT,
                TEST_BYTES_RCVD, "hotbox!", TEST_RESPONSE_BODY);

        return measurement;
    }

    private static class TestStubAgentImpl extends StubAgentImpl {
        private String wanType = WanType.CDMA;

        public void setNetworkCarrier(String networkCarrier) {
            this.networkCarrier = networkCarrier;
        }

        private String networkCarrier = CarrierType.CELLULAR;

        public void setWanType(String wanType) {
            this.wanType = wanType;
        }

        @Override
        public String getNetworkWanType() {
            return wanType;
        }

        @Override
        public String getNetworkCarrier() {
            return networkCarrier;
        }

    }

}
