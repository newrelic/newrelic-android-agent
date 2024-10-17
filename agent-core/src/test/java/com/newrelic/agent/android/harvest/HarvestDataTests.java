/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.harvest;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.NullAgentImpl;
import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.analytics.AnalyticsEvent;
import com.newrelic.agent.android.api.common.CarrierType;
import com.newrelic.agent.android.api.common.WanType;
import com.newrelic.agent.android.instrumentation.MetricCategory;
import com.newrelic.agent.android.measurement.CustomMetricMeasurement;
import com.newrelic.agent.android.measurement.consumer.SummaryMetricMeasurementConsumer;
import com.newrelic.agent.android.metric.Metric;
import com.newrelic.agent.android.test.mock.Providers;
import com.newrelic.agent.android.tracing.ActivityTrace;
import com.newrelic.agent.android.tracing.Trace;
import com.newrelic.agent.android.tracing.TraceMachine;
import com.newrelic.agent.android.util.Constants;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

@RunWith(JUnit4.class)
public class HarvestDataTests {

    @BeforeClass
    public static void classSetUp() {
        TraceMachine.HEALTHY_TRACE_TIMEOUT = 10000;
        Agent.setImpl(NullAgentImpl.instance);
    }

    @Before
    public void setUpFeatureFlags() {
        FeatureFlag.resetFeatures();
        FeatureFlag.enableFeature(FeatureFlag.DefaultInteractions);
        FeatureFlag.enableFeature(FeatureFlag.HttpResponseBodyCapture);
    }

    @Test
    public void testBuildDataToken() {
        DataToken dataToken = new DataToken();

        dataToken.setAccountId(123);
        dataToken.setAgentId(456);
        Assert.assertEquals(123, dataToken.getAccountId());
        Assert.assertEquals(456, dataToken.getAgentId());
    }

    @Test
    public void testBuildHarvestData() {
        HarvestData harvestData = new HarvestData();
        harvestData.setAnalyticsEnabled(true);

        // Data token
        DataToken dataToken = Providers.provideDataToken();
        harvestData.setDataToken(dataToken);

        // Device information
        DeviceInformation devInfo = Providers.provideDeviceInformation();
        harvestData.setDeviceInformation(devInfo);

        // Time since last harvest
        harvestData.setHarvestTimeDelta(59.96653896570206);

        // HTTP Transactions
        HttpTransactions transactions = new HttpTransactions();
        harvestData.setHttpTransactions(transactions);

        // Machine Measurements
        MachineMeasurements machineMeasurements = Providers.provideMachineMeasurements();
        harvestData.setMachineMeasurements(machineMeasurements);

        // Session Attributes
        Set<AnalyticsAttribute> sessionAttributes = Providers.provideSessionAttributes();
        harvestData.setSessionAttributes(sessionAttributes);

        // Analytic Events
        long eventCreationTime = System.currentTimeMillis();
        Set<AnalyticsEvent> events = Providers.provideSessionEvents();
        harvestData.setAnalyticsEvents(events);

        String harvestJson = harvestData.toJsonString();
        Assert.assertNotNull(harvestJson);
        Assert.assertTrue(harvestJson.length() > 0);

        // Convert the string back into a JSON array and validate the contents
        JsonArray array = JsonParser.parseString(harvestJson).getAsJsonArray();

        Assert.assertEquals(10, array.size());
        JsonArray dataTokenElement = array.get(0).getAsJsonArray();
        JsonArray deviceInfoElement = array.get(1).getAsJsonArray();
        double timeSinceHarvestElement = array.get(2).getAsDouble();
        JsonArray httpTransactionsElement = array.get(3).getAsJsonArray();
        JsonArray metricsElement = array.get(4).getAsJsonArray();
        JsonArray httpErrorsElement = array.get(5).getAsJsonArray();
        JsonArray activityTracesElement = array.get(6).getAsJsonArray();
        JsonArray agentHealthElement = array.get(7).getAsJsonArray();
        JsonObject sessionAttributesElement = array.get(8).getAsJsonObject();
        JsonArray analyticsEventsElement = array.get(9).getAsJsonArray();

        // Validate data token
        Assert.assertEquals(2, dataTokenElement.size());
        Assert.assertEquals(1646468, dataTokenElement.get(0).getAsInt());
        Assert.assertEquals(1997527, dataTokenElement.get(1).getAsInt());

        // Validate the device info
        Assert.assertEquals(10, deviceInfoElement.size());
        Assert.assertEquals("Android", deviceInfoElement.get(0).getAsString());
        Assert.assertEquals("1.2.3", deviceInfoElement.get(1).getAsString());
        Assert.assertEquals("6000sux", deviceInfoElement.get(2).getAsString());
        Assert.assertEquals("AndroidAgent", deviceInfoElement.get(3).getAsString());
        Assert.assertEquals("6.5.1", deviceInfoElement.get(4).getAsString());
        Assert.assertEquals("389C9738-A761-44DE-8A66-1668CFD67DA1", deviceInfoElement.get(5).getAsString());

        // Validate the time since last harvest
        Assert.assertEquals(60.00d, timeSinceHarvestElement, 0.1d);

        // Validate the HTTP transactions
        Assert.assertEquals(0, httpTransactionsElement.size());

        // Validate the metrics
        Assert.assertEquals(6, metricsElement.size());

        // Validate the element formerly holding HTTP errors
        Assert.assertEquals(0, httpErrorsElement.size());

        // Validate the activity traces
        Assert.assertEquals(0, activityTracesElement.size());

        // Validate the agent health data
        Assert.assertEquals(0, agentHealthElement.size());

        // Validate the session attributes
        Assert.assertEquals(2, sessionAttributesElement.entrySet().size());
        JsonElement attr1 = sessionAttributesElement.get(AnalyticsAttribute.OS_NAME_ATTRIBUTE);
        Assert.assertNotNull(attr1);
        Assert.assertEquals("Android", attr1.getAsString());

        JsonElement attr2 = sessionAttributesElement.get(AnalyticsAttribute.APP_NAME_ATTRIBUTE);
        Assert.assertNotNull(attr2);
        Assert.assertEquals("Test", attr2.getAsString());

        // Validate the analytics events
        Assert.assertEquals(1, analyticsEventsElement.size());
        JsonObject event1 = analyticsEventsElement.get(0).getAsJsonObject();
        Assert.assertNotNull(event1);
        Assert.assertEquals(5, event1.entrySet().size());
        JsonElement eventAttr1 = event1.get("timestamp");
        JsonElement eventAttr2 = event1.get("name");
        JsonElement eventAttr3 = event1.get("category");
        JsonElement eventAttr4 = event1.get("eventType");
        JsonElement eventAttr5 = event1.get("customAttribute");

        Assert.assertNotNull(eventAttr1);
        Assert.assertNotNull(eventAttr2);
        Assert.assertNotNull(eventAttr3);
        Assert.assertNotNull(eventAttr4);
        Assert.assertNotNull(eventAttr5);

        Assert.assertEquals(eventCreationTime, eventAttr1.getAsLong(), 1000L);
        Assert.assertEquals("CustomEvent", eventAttr2.getAsString());
        Assert.assertEquals("Custom", eventAttr3.getAsString());
        Assert.assertEquals("Mobile", eventAttr4.getAsString());
        Assert.assertEquals("Testing", eventAttr5.getAsString());
    }

    @Test
    public void testActivityTracesArrayShouldBeEmptyWhenDeFaultInteractionsIsDisabled() throws Exception {


        TestHarvest harvest = new TestHarvest();
        HarvestData harvestData = new HarvestData();
        harvest.createHarvester();
        harvest.setHarvestData(harvestData);
        Harvest.setInstance(harvest);

        // ActivityTraces
        ActivityTraces activityTraces = Providers.provideActivityTraces();
        harvestData.setActivityTraces(activityTraces);

        FeatureFlag.disableFeature(FeatureFlag.DefaultInteractions);

        JsonArray array = harvestData.asJsonArray();
        JsonArray activityTracesElement = array.get(6).getAsJsonArray();
        Assert.assertEquals(0, activityTracesElement.size());


    }

    @Test
    public void testActivityTracesArrayShouldNotBeEmptyWhenDeFaultInteractionsIsEnabled() throws Exception {


        TestHarvest harvest = new TestHarvest();
        HarvestData harvestData = new HarvestData();
        harvest.createHarvester();
        harvest.setHarvestData(harvestData);
        Harvest.setInstance(harvest);

        // ActivityTraces
        ActivityTraces activityTraces = Providers.provideActivityTraces();
        harvestData.setActivityTraces(activityTraces);

        JsonArray array = harvestData.asJsonArray();
        JsonArray activityTracesElement = array.get(6).getAsJsonArray();
        Assert.assertEquals(2, activityTracesElement.size());


    }

    @Test
    public void testBuildHarvestHttpTransactions() {
        HttpTransactions transactions = new HttpTransactions();

        /*
            "http://httpstat.us/200",   // url
            "wifi",                     // carrier
            0.2556343,                  // total time
            200,                        // code
            0,                          // error code
            0,                          // bytes sent
            6,                          // bytes received
            null                         // appData (server metrics)
         */

        HttpTransaction transaction = Providers.provideHttpTransaction();
        transaction.setTotalTime(0.2556343);
        transactions.add(transaction);

        String transactionString = transaction.toJsonString();
        Assert.assertEquals("[\"https://httpstat.us/200\",\"" + CarrierType.WIFI + "\",0.2556343,200,0,0,6,null,\"" + WanType.CDMA + "\",\"GET\"]", transactionString);

        String transactionsString = transactions.toJsonString();
        Assert.assertEquals("[[\"https://httpstat.us/200\",\"" + CarrierType.WIFI + "\",0.2556343,200,0,0,6,null,\"" + WanType.CDMA + "\",\"GET\"]]", transactionsString);

        transaction = Providers.provideHttpTransaction();
        transaction.setTotalTime(0.2442052);
        transactions.add(transaction);

        transactionsString = transactions.toJsonString();
        Assert.assertEquals("[[\"https://httpstat.us/200\",\"" + CarrierType.WIFI + "\",0.2556343,200,0,0,6,null,\"" + WanType.CDMA + "\",\"GET\"]," +
                "[\"https://httpstat.us/200\",\"" + CarrierType.WIFI + "\",0.2442052,200,0,0,6,null,\"" + WanType.CDMA + "\",\"GET\"]]", transactionsString);
    }

    @Test
    public void testJsonTokener() {
        int[] dataToken = new int[2];
        try {
            JsonArray array = new Gson().fromJson("[666,222]", JsonArray.class);
            dataToken[0] = array.get(0).getAsInt();
            dataToken[1] = array.get(1).getAsInt();

            Assert.assertEquals("", dataToken[0], 666);
            Assert.assertEquals("", dataToken[1], 222);

            System.out.println(array);
        } catch (JsonParseException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testReportNetworkMetrics() throws Exception {
        TestHarvest harvest = new TestHarvest();
        HarvestData harvestData = new HarvestData();
        harvest.createHarvester();
        harvest.setHarvestData(harvestData);
        Harvest.setInstance(harvest);

        // ActivityTraces
        ActivityTraces activityTraces = Providers.provideActivityTraces();
        harvestData.setActivityTraces(activityTraces);
        Assert.assertEquals("Should contain 2 activity traces", 2, activityTraces.count());

        ActivityTrace[] activityTracesArray = activityTraces.getActivityTraces().toArray(new ActivityTrace[activityTraces.count()]);
        Assert.assertEquals("Should contain 3 network traces", 3, activityTracesArray[1].getTraces().size());

        // translate activityTraces to harvest metrics
        SummaryMetricMeasurementConsumer summaryMetricMeasurementConsumer = new SummaryMetricMeasurementConsumer();

        // SummaryMetricMeasurementConsumer needs at least one metric
        CustomMetricMeasurement customMetricMeasurement = new CustomMetricMeasurement();
        customMetricMeasurement.setCategory(MetricCategory.NETWORK);
        customMetricMeasurement.setScope("testReportNetworkMetrics");
        summaryMetricMeasurementConsumer.consumeMeasurement(customMetricMeasurement);
        summaryMetricMeasurementConsumer.onTraceComplete(activityTracesArray[1]);
        summaryMetricMeasurementConsumer.onHarvest();

        MachineMeasurements metrics = Harvest.getInstance().harvestData.getMetrics();
        Assert.assertFalse(metrics.isEmpty());

        Metric networkCountMetric = metrics.getMetrics().get(activityTracesArray[1].networkCountMetric.getName());
        Assert.assertNotNull(networkCountMetric);
        Assert.assertEquals("CountMetric should have recorded 3 network calls", 3, (int) networkCountMetric.getTotal());
        Assert.assertTrue("CountMetric min should be same as total", networkCountMetric.getTotal() == networkCountMetric.getMin());
        Assert.assertTrue("CountMetric max should be same as total", networkCountMetric.getTotal() == networkCountMetric.getMax());

        Metric networkTimeMetric = metrics.getMetrics().get(activityTracesArray[1].networkTimeMetric.getName());
        Assert.assertNotNull(networkTimeMetric);
        double aggregateTraceTimes = 0f;
        Map<UUID, Trace> traces = activityTracesArray[1].getTraces();
        for (UUID uuid : traces.keySet()) {
            Trace trace = traces.get(uuid);
            aggregateTraceTimes += trace.getDurationAsSeconds();
        }
        Assert.assertTrue("TimeMetric should match aggregate trace times", aggregateTraceTimes == networkTimeMetric.getTotal());
        Assert.assertTrue("TimeMetric min should be same as total", networkTimeMetric.getTotal() == networkTimeMetric.getMin());
        Assert.assertTrue("TimeMetric max should be same as total", networkTimeMetric.getTotal() == networkTimeMetric.getMax());

        // Harvest json
        String harvestJson = harvestData.toJsonString();
        Assert.assertNotNull(harvestJson);
        Assert.assertTrue(harvestJson.length() > 0);

        // Convert the string back into a JSON array and validate the contents
        JsonArray array = JsonParser.parseString(harvestJson).getAsJsonArray();

        Assert.assertEquals(8, array.size());
        JsonArray activityTracesElement = array.get(6).getAsJsonArray();

        // Validate the activity traces
        Assert.assertEquals(2, activityTracesElement.size());
    }

    private class TestHarvest extends Harvest {

        public void setHarvestData(HarvestData harvestData) {
            this.harvestData = harvestData;
        }
    }
}

