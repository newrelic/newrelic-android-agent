/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android;

import com.newrelic.agent.android.api.common.CarrierType;
import com.newrelic.agent.android.api.common.TransactionData;
import com.newrelic.agent.android.api.common.WanType;
import com.newrelic.agent.android.api.v1.Defaults;
import com.newrelic.agent.android.harvest.ApplicationInformation;
import com.newrelic.agent.android.harvest.DeviceInformation;
import com.newrelic.agent.android.harvest.EnvironmentInformation;
import com.newrelic.agent.android.stats.TicToc;
import com.newrelic.agent.android.util.Encoder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NullAgentImpl implements AgentImpl {
    public static final NullAgentImpl instance = new NullAgentImpl();
    private int responseBodyLimit = Defaults.RESPONSE_BODY_LIMIT;
    private TicToc sessionDurationMillis = new TicToc();

    DeviceInformation devInfo;

    public NullAgentImpl() {
    }

    @Override
    public void addTransactionData(TransactionData transactionData) {
    }

    @Override
    public List<TransactionData> getAndClearTransactionData() {
        return new ArrayList<TransactionData>();
    }

    @Override
    public void mergeTransactionData(List<TransactionData> transactionDataList) {
    }

    @Override
    public String getCrossProcessId() {
        return null;
    }

    @Override
    public int getCurrentProcessId() {
        return 0;
    }

    @Override
    public int getStackTraceLimit() {
        return 0;
    }

    @Override
    public int getResponseBodyLimit() {
        return responseBodyLimit;
    }

    public void setResponseBodyLimit(int responseBodyLimit) {
        this.responseBodyLimit = responseBodyLimit;
    }

    @Override
    public void start() {
        sessionDurationMillis.tic();
    }

    @Override
    public void stop() {
        sessionDurationMillis.toc();
    }

    @Override
    public void disable() {
    }

    @Override
    public boolean isDisabled() {
        return true;
    }

    @Override
    public String getNetworkCarrier() {
        return CarrierType.UNKNOWN;
    }

    @Override
    public String getNetworkWanType() {
        return WanType.UNKNOWN;
    }

    @Override
    public void setLocation(String countryCode, String adminRegion) {
    }

    @Override
    public Encoder getEncoder() {
        return new Encoder() {
            public String encode(byte[] bytes) {
                return new String(bytes);
            }

            @Override
            public String encodeNoWrap(byte[] bytes) {
                return encode(bytes);
            }
        };
    }

    @Override
    public DeviceInformation getDeviceInformation() {
        if (devInfo == null) {
            devInfo = new DeviceInformation();
            devInfo.setOsName("Android");
            devInfo.setOsVersion("12");
            devInfo.setOsBuild("12.0.1");
            devInfo.setManufacturer("NullAgent");
            devInfo.setModel("NullAgent");
            devInfo.setAgentName("AndroidAgent");
            devInfo.setAgentVersion("6.5.1");
            devInfo.setDeviceId("389C9738-A761-44DE-8A66-1668CFD67DA1");
            devInfo.setArchitecture("Fake Arch");
            devInfo.setRunTime("1.8.0");
            devInfo.setSize("Fake Size");
            devInfo.setApplicationFramework(ApplicationFramework.Native);
        }
        return devInfo;
    }

    @Override
    public ApplicationInformation getApplicationInformation() {
        return new ApplicationInformation("null", "0.0", "null", "0");
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
        return sessionDurationMillis.peek();
    }

    @Override
    public boolean hasReachableNetworkConnection(String reachableHost) {
        return true;
    }

    @Override
    public boolean isInstantApp() {
        return false;
    }

    @Override
    public void persistHarvestDataToDisk(String data) {
    }

    @Override
    public Map<String, String> getAllOfflineData() {
        return new HashMap<String, String>();
    }
}
