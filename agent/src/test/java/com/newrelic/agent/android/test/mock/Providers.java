/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.test.mock;

import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.ApplicationFramework;
import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.analytics.AnalyticsEvent;
import com.newrelic.agent.android.analytics.CustomEvent;
import com.newrelic.agent.android.api.common.CarrierType;
import com.newrelic.agent.android.api.common.WanType;
import com.newrelic.agent.android.harvest.ActivityTraces;
import com.newrelic.agent.android.harvest.ApplicationInformation;
import com.newrelic.agent.android.harvest.DataToken;
import com.newrelic.agent.android.harvest.DeviceInformation;
import com.newrelic.agent.android.harvest.HarvestConfiguration;
import com.newrelic.agent.android.harvest.HttpTransaction;
import com.newrelic.agent.android.harvest.HttpTransactions;
import com.newrelic.agent.android.harvest.MachineMeasurements;
import com.newrelic.agent.android.instrumentation.TransactionState;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.tracing.Trace;
import com.newrelic.agent.android.tracing.TraceMachine;
import com.newrelic.agent.android.tracing.TraceType;
import com.newrelic.agent.android.tracing.TracingInactiveException;

import org.junit.Assert;

import java.util.HashSet;
import java.util.Set;

public class Providers {

    public final static String FIXED_DEVICE_ID = "389C9738-A761-44DE-8A66-1668CFD67DA1";

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
        long eventTimestamp = System.currentTimeMillis();
        AnalyticsEvent event = new CustomEvent("CustomEvent", eventAttributes);
        events.add(event);
        return events;
    }

    public static HttpTransaction provideHttpTransaction() {
        HttpTransaction transaction = new HttpTransaction();
        transaction.setUrl("http://httpstat.us/200");
        transaction.setHttpMethod("GET");
        transaction.setCarrier(CarrierType.WIFI);
        transaction.setWanType(WanType.CDMA);
        transaction.setTotalTime(0.2556343);
        transaction.setStatusCode(200);
        transaction.setErrorCode(0);
        transaction.setBytesSent(0);
        transaction.setBytesReceived(6);
        transaction.setAppData(null);
        return transaction;
    }

    public static HttpTransactions provideHttpTransactions() {
        HttpTransactions transactions = new HttpTransactions();
        transactions.add(provideHttpTransaction());
        transactions.add(provideHttpTransaction());
        transactions.add(provideHttpTransaction());
        return transactions;
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
        TraceMachine.enterMethod("methodTrace");
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
        devInfo.setDeviceId(FIXED_DEVICE_ID);
        devInfo.setApplicationFramework(ApplicationFramework.Cordova);
        devInfo.setApplicationFrameworkVersion("1.2.3.4");
        devInfo.setSize("normal");

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
        clone.setApplicationFramework(devInfo.getApplicationFramework());
        clone.setApplicationFrameworkVersion(devInfo.getApplicationFrameworkVersion());

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
        harvestConfiguration.setAccount_id("12345");
        harvestConfiguration.setApplication_id("app-token");
        return harvestConfiguration;
    }


}

