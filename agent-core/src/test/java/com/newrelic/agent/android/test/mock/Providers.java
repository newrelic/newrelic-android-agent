/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.test.mock;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.analytics.AnalyticsEvent;
import com.newrelic.agent.android.analytics.CustomEvent;
import com.newrelic.agent.android.api.common.CarrierType;
import com.newrelic.agent.android.api.common.TransactionData;
import com.newrelic.agent.android.api.common.WanType;
import com.newrelic.agent.android.distributedtracing.DistributedTracing;
import com.newrelic.agent.android.distributedtracing.TraceContext;
import com.newrelic.agent.android.distributedtracing.TraceFacade;
import com.newrelic.agent.android.harvest.ActivityTraces;
import com.newrelic.agent.android.harvest.ApplicationInformation;
import com.newrelic.agent.android.harvest.DataToken;
import com.newrelic.agent.android.harvest.DeviceInformation;
import com.newrelic.agent.android.harvest.HarvestConfiguration;
import com.newrelic.agent.android.harvest.HttpTransaction;
import com.newrelic.agent.android.harvest.MachineMeasurements;
import com.newrelic.agent.android.instrumentation.TransactionState;
import com.newrelic.agent.android.instrumentation.TransactionStateUtil;
import com.newrelic.agent.android.logging.LogLevel;
import com.newrelic.agent.android.logging.LogReportingConfiguration;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.test.stub.StubAnalyticsAttributeStore;
import com.newrelic.agent.android.tracing.Trace;
import com.newrelic.agent.android.tracing.TraceMachine;
import com.newrelic.agent.android.tracing.TraceType;
import com.newrelic.agent.android.tracing.TracingInactiveException;
import com.newrelic.agent.android.util.Constants;
import com.newrelic.agent.android.util.TestUtil;

import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.junit.Assert;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class Providers {

    public final static String FIXED_DEVICE_ID = "389C9738-A761-44DE-8A66-1668CFD67DA1";
    public static final String APP_URL = "https://httpstat.us/200";
    public static final String APP_METHOD = "GET";
    public static final String APP_DATA = "{AppData}";
    public static final String X_PROCESS_ID = "X_PROCESS_ID";
    public static final String TRACE_ENCODED_DATA = "encoded_app_data";

    public static MachineMeasurements provideMachineMeasurements() {
        MachineMeasurements machineMeasurements = new MachineMeasurements();
        machineMeasurements.addMetric("CPU/Total/Utilization", 0.000432774684809529);
        machineMeasurements.addMetric(MetricNames.SUPPORTABILITY_COLLECTOR + "Connect", 1191.094);
        machineMeasurements.addMetric("CPU/System/Utilization", 0.0001993445045550672);
        machineMeasurements.addMetric("CPU/User/Utilization", 0.0002334301802544618);
        machineMeasurements.addMetric(MetricNames.SUPPORTABILITY_COLLECTOR + "ResponseStatusCodes/200", 1);
        machineMeasurements.addMetric("Memory/Used", 19.76953125);

        return machineMeasurements;
    }

    public static DataToken provideDataToken() {
        DataToken dataToken = new DataToken(1646468, 1997527);
        return dataToken;
    }

    public static Set<AnalyticsAttribute> provideSessionAttributes() {
        Set<AnalyticsAttribute> sessionAttributes = new HashSet<AnalyticsAttribute>();
        sessionAttributes.add(new AnalyticsAttribute(AnalyticsAttribute.OS_NAME_ATTRIBUTE, "Android"));
        sessionAttributes.add(new AnalyticsAttribute(AnalyticsAttribute.APP_NAME_ATTRIBUTE, "Test"));
        return sessionAttributes;
    }

    public static Set<AnalyticsEvent> provideSessionEvents() {
        Set<AnalyticsEvent> events = new HashSet<AnalyticsEvent>();
        Set<AnalyticsAttribute> eventAttributes = new HashSet<AnalyticsAttribute>();
        eventAttributes.add(new AnalyticsAttribute("customAttribute", "Testing"));
        AnalyticsEvent event = new CustomEvent("CustomEvent", eventAttributes);
        events.add(event);
        return events;
    }

    public static HttpTransaction provideHttpTransaction() {
        Map<String, String> params = new HashMap<String, String>();
        params.put(Constants.Transactions.CONTENT_TYPE, "text/html; charset=UTF-8");
        params.put(Constants.Transactions.CONTENT_LENGTH, "9");

        HttpTransaction transaction = new HttpTransaction();
        transaction.setUrl(APP_URL);
        transaction.setHttpMethod("GET");
        transaction.setCarrier(CarrierType.WIFI);
        transaction.setWanType(WanType.CDMA);
        transaction.setTotalTime(0.2556343);
        transaction.setStatusCode(200);
        transaction.setErrorCode(0);
        transaction.setBytesSent(0);
        transaction.setBytesReceived(6);
        transaction.setAppData(null);
        transaction.setResponseBody("200 Party");
        transaction.setParams(params);
        if (FeatureFlag.featureEnabled(FeatureFlag.DistributedTracing)) {
            TransactionState transactionState = new TransactionState();
            TransactionStateUtil.inspectAndInstrument(transactionState, transaction.getUrl(), transaction.getHttpMethod());
            transaction.setTraceContext(DistributedTracing.getInstance().startTrace(transactionState));
        }
        return transaction;
    }

    public static ActivityTraces provideActivityTraces() throws TracingInactiveException, InterruptedException {
        /**
         * The timing limit of the TraceMachine is very low, so debugging can be difficult.
         * Be careful where you place breakpoints.
         */

        ActivityTraces activityTraces = new ActivityTraces();

        TraceMachine.startTracing("methodTraces");
        activityTraces.add(TraceMachine.getActivityTrace());
        provideMethodTrace();
        TraceMachine.endTrace();

        TraceMachine.startTracing("networkTraces", true);
        activityTraces.add(TraceMachine.getActivityTrace());
        provideNetworkTrace();
        provideNetworkTrace();
        provideNetworkTrace();
        TraceMachine.endTrace();

        return activityTraces;
    }

    public static Trace provideMethodTrace() throws TracingInactiveException, InterruptedException {
        TraceMachine.enterMethod("tracedMethod");
        Trace trace = TraceMachine.getCurrentTrace();
        Assert.assertEquals(TraceType.TRACE, trace.getType());
        Thread.sleep(100);
        TraceMachine.exitMethod();

        return trace;
    }

    public static Trace provideNetworkTrace() throws TracingInactiveException, InterruptedException {
        TransactionState transactionState = provideTransactionState();
        Trace trace = TraceMachine.getCurrentTrace();
        Assert.assertEquals(TraceType.NETWORK, trace.getType());
        Thread.sleep(100);
        TraceMachine.exitMethod();

        return trace;
    }

    public static TransactionState provideTransactionState() {
        TransactionState transactionState = new TransactionState();
        transactionState.setUrl("http://httpstat.us/200");
        transactionState.setStatusCode(200);
        return transactionState;
    }

    public static TransactionData provideTransactionData() {
        Map<String, String> params = new HashMap<>();
        params.put(Constants.Transactions.CONTENT_TYPE, "text/html");
        params.put(Constants.Transactions.CONTENT_LENGTH, "11");

        TransactionState transactionState = new TransactionState();
        TransactionStateUtil.inspectAndInstrument(transactionState, APP_URL, "GET");

        TraceContext traceContext = DistributedTracing.getInstance().startTrace(transactionState);
        TransactionData transactionData = new TransactionData(APP_URL,
                "GET",
                CarrierType.CELLULAR,
                3.1415882F,
                200,
                0,
                13,
                24,
                APP_DATA,
                WanType.EDGE, traceContext,
                traceContext.asTraceAttributes());

        transactionData.setResponseBody("201 Created");
        transactionData.setParams(params);

        return transactionData;
    }

    /**
     * Return a copy of the app ApplicationInfo member
     *
     * @return Cloned ApplicationInformation
     */
    public static ApplicationInformation provideApplicationInformation() {
        ApplicationInformation appInfo = Agent.getApplicationInformation();
        ApplicationInformation clone = new ApplicationInformation();
        clone.setAppName(appInfo.getAppName());
        clone.setAppVersion(appInfo.getAppVersion());
        clone.setPackageId(appInfo.getPackageId());
        clone.setAppBuild(appInfo.getAppBuild());
        clone.setVersionCode(appInfo.getVersionCode());

        return clone;
    }

    /**
     * Return a copy of the app DeviceInfo member
     *
     * @return Cloned DeviceInformation
     */
    public static DeviceInformation provideDeviceInformation() {
        DeviceInformation devInfo = Agent.getDeviceInformation();
        // fill in missing or device-dependant values
        devInfo.setOsVersion("1.2.3");
        devInfo.setOsBuild("4");
        devInfo.setManufacturer("spacely sprockets");
        devInfo.setModel("6000sux");
        devInfo.setRunTime("1.7.0");
        devInfo.setArchitecture("x86_64");

        DeviceInformation clone = new DeviceInformation();
        clone.setOsName(devInfo.getOsName());
        clone.setOsVersion(devInfo.getOsVersion());
        clone.setOsBuild(devInfo.getOsBuild());
        clone.setManufacturer(devInfo.getManufacturer());
        clone.setModel(devInfo.getModel());
        clone.setCountryCode(devInfo.getCountryCode());
        clone.setRegionCode(devInfo.getRegionCode());
        clone.setAgentName(devInfo.getAgentName());
        clone.setAgentVersion(devInfo.getAgentVersion());
        clone.setDeviceId(devInfo.getDeviceId());
        clone.setArchitecture(devInfo.getArchitecture());
        clone.setRunTime(devInfo.getRunTime());
        clone.setSize(devInfo.getSize());
        clone.setManufacturer(devInfo.getManufacturer());
        clone.setModel(devInfo.getModel());
        clone.setOsVersion(devInfo.getOsName());
        clone.setOsVersion(devInfo.getOsVersion());
        clone.setOsBuild(devInfo.getOsBuild());

        return clone;
    }

    /**
     * Return a simple test HarvestConfiguration
     *
     * @throws Exception
     */
    public static HarvestConfiguration provideHarvestConfiguration() {
        HarvestConfiguration harvestConfiguration = new HarvestConfiguration();

        harvestConfiguration.setCross_process_id("x-process-id");
        harvestConfiguration.setError_limit(111);
        harvestConfiguration.setCollect_network_errors(true);
        harvestConfiguration.setData_token(new int[]{111, 111});
        harvestConfiguration.setActivity_trace_max_report_attempts(222);
        harvestConfiguration.setActivity_trace_max_size(333);
        harvestConfiguration.setData_report_period(444);
        harvestConfiguration.setReport_max_transaction_count(555);
        harvestConfiguration.setReport_max_transaction_age(666);
        harvestConfiguration.setActivity_trace_max_size(777);
        harvestConfiguration.setActivity_trace_max_report_attempts(888);
        harvestConfiguration.setStack_trace_limit(999);
        harvestConfiguration.setActivity_trace_min_utilization(0.3333);
        harvestConfiguration.setServer_timestamp(9876543210L);
        harvestConfiguration.setResponse_body_limit(1111);
        harvestConfiguration.setTrusted_account_key("33");
        harvestConfiguration.getRequest_headers_map().put("NR-AgentConfiguration", "+cNeWo");
        harvestConfiguration.getRequest_headers_map().put("NR-Session", "AyAAAAC1NxdWFyZVRvb2xz");
        harvestConfiguration.setEntity_guid("MTA4MTY5OTR8TU9CSUxFfEFQUExJQ0FUSU9OfDE1MjIzNDU3Mg");
        harvestConfiguration.setAccount_id("33");

        return harvestConfiguration;
    }

    public static HttpTransaction provideHttpRequestError() {
        Map<String, String> params = new HashMap<>();
        params.put(Constants.Transactions.CONTENT_TYPE, "text/xml");
        params.put(Constants.Transactions.CONTENT_LENGTH, "10");

        HttpTransaction httpTransaction = provideHttpTransaction();
        httpTransaction.setStatusCode(401);
        httpTransaction.setErrorCode(0);
        httpTransaction.setResponseBody("401 Borked");
        httpTransaction.setParams(params);
        return httpTransaction;
    }

    public static HttpTransaction provideHttpRequestFailure() {
        Map<String, String> params = new HashMap<>();
        params.put(Constants.Transactions.CONTENT_TYPE, "text/xml");
        params.put(Constants.Transactions.CONTENT_LENGTH, "12");

        HttpTransaction httpTransaction = provideHttpTransaction();
        httpTransaction.setStatusCode(0);
        httpTransaction.setErrorCode(-1100);
        httpTransaction.setResponseBody("Client error");
        httpTransaction.setParams(params);
        return httpTransaction;
    }

    public static HttpHost provideHttpHost() {
        return new HttpHost("localhost", 6661);
    }

    public static HttpUriRequest provideHttpUriRequest() {
        HttpUriRequest httpUriRequest = new HttpGet(APP_URL);
        return httpUriRequest;
    }

    public static HttpRequest provideHttpRequest() {
        return new BasicHttpRequest(APP_METHOD, APP_URL);
    }

    public static HttpResponse provideHttpResponse() {
        ProtocolVersion proto = new ProtocolVersion("HTTP", 1, 1);
        BasicStatusLine status = new BasicStatusLine(proto, HttpStatus.SC_OK, "test");
        BasicHttpResponse httpResponse = new BasicHttpResponse(status);
        return provideHeaders(httpResponse);
    }

    public static HttpResponse provideHeaders(HttpResponse response) {
        response.addHeader(Constants.Network.CROSS_PROCESS_ID_HEADER, X_PROCESS_ID);
        response.addHeader(Constants.Network.APP_DATA_HEADER, APP_DATA);
        response.addHeader(Constants.Network.CONTENT_LENGTH_HEADER, String.valueOf(APP_DATA.length()));
        response.addHeader(Constants.Network.CONTENT_TYPE_HEADER, "mime/html");
        return response;
    }

    public static HttpURLConnection provideHttpUrlConnection() throws IOException {
        HttpURLConnection urlConnection = mock(HttpURLConnection.class);
        when(urlConnection.getRequestProperty(Constants.Network.APP_DATA_HEADER)).thenReturn(APP_DATA);
        when(urlConnection.getHeaderField(Constants.Network.APP_DATA_HEADER)).thenReturn(APP_DATA);
        when(urlConnection.getResponseCode()).thenReturn(HttpStatus.SC_OK);
        when(urlConnection.getContentLength()).thenReturn(APP_DATA.length());
        when(urlConnection.getURL()).thenReturn(new URL(APP_URL));
        when(urlConnection.getRequestMethod()).thenReturn(APP_METHOD);

        return urlConnection;
    }

    public static TraceFacade provideTraceFacade() {
        DistributedTracing facade = DistributedTracing.getInstance();
        HarvestConfiguration harvestConfiguration = new HarvestConfiguration();
        harvestConfiguration.setApplication_id("1");
        harvestConfiguration.setAccount_id("1");
        facade.setConfiguration(harvestConfiguration);
        return facade;
    }

    public static AgentConfiguration provideAgentConfiguration() {
        final AgentConfiguration conf = new AgentConfiguration();
        
        conf.setApplicationToken("dead-beef-baad-f00d");
        conf.setAnalyticsAttributeStore(new StubAnalyticsAttributeStore());
        conf.getLogReportingConfiguration().setConfiguration(provideLogReportingConfiguration());

        return conf;
    }

    public static LogReportingConfiguration provideLogReportingConfiguration() {
        final LogReportingConfiguration logReportingConfiguration = new LogReportingConfiguration(false, LogLevel.NONE);
        return logReportingConfiguration;
    }

    public static JsonObject provideJsonObject(Object obj, Class clazz) {
        String json = new Gson().toJson(obj, clazz);
        return new Gson().fromJson(json, JsonObject.class);
    }

    public static JsonObject provideJsonObject(String path) {
        try (InputStream is = Providers.class.getResourceAsStream(path)) {
            final String json = TestUtil.slurp(is);
            return new Gson().fromJson(json, JsonObject.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}

