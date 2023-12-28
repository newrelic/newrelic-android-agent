/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.test.stub;

import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.AgentImpl;
import com.newrelic.agent.android.ApplicationFramework;
import com.newrelic.agent.android.api.common.CarrierType;
import com.newrelic.agent.android.api.common.TransactionData;
import com.newrelic.agent.android.api.common.WanType;
import com.newrelic.agent.android.api.v1.Defaults;
import com.newrelic.agent.android.harvest.ApplicationInformation;
import com.newrelic.agent.android.harvest.DeviceInformation;
import com.newrelic.agent.android.harvest.EnvironmentInformation;
import com.newrelic.agent.android.util.Encoder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class StubAgentImpl implements AgentImpl {
    private final ArrayList<TransactionData> transactions = new ArrayList<TransactionData>();

    private int stackTraceLimit = Defaults.STACK_TRACE_LIMIT;
    private int responseBodyLimit = Defaults.RESPONSE_BODY_LIMIT;
    private UUID sessionId = UUID.randomUUID();
    private long sessionStartTimeMillis = System.currentTimeMillis();

    private boolean disabled = false;

    DeviceInformation devInfo = new DeviceInformation();

    public StubAgentImpl() {
        devInfo.setOsName("Android");
        devInfo.setOsVersion("2.3");
        devInfo.setOsBuild("a.b.c");
        devInfo.setModel("StubAgent");
        devInfo.setManufacturer("Fake");
        devInfo.setAgentName("AndroidAgent");
        devInfo.setAgentVersion("5.25.3");
        devInfo.setDeviceId("389C9738-A761-44DE-8A66-1668CFD67DA1");
        devInfo.setArchitecture("StubArchitecture");
        devInfo.setRunTime("StubRuntime");
        devInfo.setSize("800x600");
        devInfo.setCountryCode("US");
        devInfo.setRegionCode("OR");
        devInfo.setApplicationFramework(ApplicationFramework.ReactNative);
        devInfo.setApplicationFrameworkVersion("1.2.3.4");
    }

    @Override
    public void addTransactionData(TransactionData transactionData) {
        transactions.add(transactionData);
    }

    @Override
    public List<TransactionData> getAndClearTransactionData() {
        synchronized (this.transactions) {
            final ArrayList<TransactionData> transactions = new ArrayList<TransactionData>(this.transactions);
            this.transactions.clear();
            return transactions;
        }
    }

    public List<TransactionData> getTransactionData() {
        synchronized (transactions) {
            return transactions;
        }
    }

    @Override
    public void mergeTransactionData(List<TransactionData> transactionDataList) {
        synchronized (transactions) {
            transactions.addAll(transactionDataList);
        }
    }

    @Override
    public String getCrossProcessId() {
        return "TEST_CROSS_PROCESS_ID";
    }

    @Override
    public int getStackTraceLimit() {
        return stackTraceLimit;
    }

    @Override
    public int getResponseBodyLimit() {
        return responseBodyLimit;
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    @Override
    public void disable() {
        disabled = true;
    }

    @Override
    public boolean isDisabled() {
        return disabled;
    }

    @Override
    public String getNetworkCarrier() {
        return "wifi";
    }

    @Override
    public String getNetworkWanType() {
        return "wifi";
    }

    public static StubAgentImpl install() {
        final StubAgentImpl agent = new StubAgentImpl();
        Agent.setImpl(agent);
        return agent;
    }

    public static void uninstall() {
        Agent.setImpl(null);
    }

    @Override
    public void setLocation(String countryCode, String adminRegion) {
    }

    @Override
    public Encoder getEncoder() {
        final java.util.Base64.Encoder encoder = java.util.Base64.getEncoder();

        return new Encoder() {
            public String encode(byte[] bytes) {
                return new String(encoder.encode(bytes));
            }

            @Override
            public String encodeNoWrap(byte[] bytes) {
                return new String(bytes);
            }
        };
    }

    @Override
    public DeviceInformation getDeviceInformation() {
        return devInfo;
    }

    @Override
    public ApplicationInformation getApplicationInformation() {
        return new ApplicationInformation("stub", "0.0", "stub", "1 (hotfix)");
    }

    @Override
    public EnvironmentInformation getEnvironmentInformation() {
        return new EnvironmentInformation(0, 1, CarrierType.NONE, WanType.NONE, new long[]{0, 0});
    }

    @Override
    public boolean updateSavedConnectInformation() {
        return false;
    }

    @Override
    public long getSessionDurationMillis() {
        return sessionStartTimeMillis;
    }

    @Override
    public boolean hasReachableNetworkConnection(String reachableHost) {
        return reachableHost == null;
    }

    @Override
    public boolean isInstantApp() {
        return false;
    }

    @Override
    public void persistDataToDisk(String data) {

    }

    @Override
    public Map<String, String> getAllOfflineData() {
        return new HashMap<String, String>();
    }
}
