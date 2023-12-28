/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android;

import com.newrelic.agent.android.api.common.TransactionData;
import com.newrelic.agent.android.harvest.ApplicationInformation;
import com.newrelic.agent.android.harvest.DeviceInformation;
import com.newrelic.agent.android.harvest.EnvironmentInformation;
import com.newrelic.agent.android.util.Encoder;

import java.util.List;
import java.util.Map;

public interface AgentImpl {
    void addTransactionData(TransactionData transactionData);
    List<TransactionData> getAndClearTransactionData();
    void mergeTransactionData(List<TransactionData> transactionDataList);

    String getCrossProcessId();
    int getStackTraceLimit();
    int getResponseBodyLimit();
	
    void start();
    void stop();
	
    void disable();
    boolean isDisabled();

    String getNetworkCarrier();
    String getNetworkWanType();
	
    void setLocation(String countryCode, String adminRegion);

    Encoder getEncoder();

    DeviceInformation getDeviceInformation();

    ApplicationInformation getApplicationInformation();

    EnvironmentInformation getEnvironmentInformation();

    boolean updateSavedConnectInformation();

    long getSessionDurationMillis();

    boolean hasReachableNetworkConnection(String reachableHost);

    boolean isInstantApp();

    void persistDataToDisk(String data);

    Map<String, String> getAllOfflineData();
}
