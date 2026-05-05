/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android;

import com.newrelic.agent.android.api.common.TransactionData;
import com.newrelic.agent.android.harvest.ApplicationInformation;
import com.newrelic.agent.android.harvest.DeviceInformation;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.util.Encoder;

import java.util.List;
import java.util.Map;

public class Agent {
    public static final String VERSION = "#VERSION#";
    public static final String MONO_INSTRUMENTATION_FLAG = "#MONO_INSTRUMENTATION_FLAG#";

    public static final String DEFAULT_BUILD_ID = "#DEFAULT_BUILD_ID#";

    private static final AgentImpl NULL_AGENT_IMPL = new NullAgentImpl();

    private static Object implLock = new Object();
    private static AgentImpl impl = NULL_AGENT_IMPL;
    private static String buildId = null;
    private static boolean obfuscated = false;

    public static void setImpl(final AgentImpl impl) {
        synchronized (implLock) {
            if (impl == null) {
                Agent.impl = NULL_AGENT_IMPL;
            } else {
                Agent.impl = impl;
            }
        }
    }

    public static AgentImpl getImpl() {
        synchronized (implLock) {
            return impl;
        }
    }

    public static String getVersion() {
        return VERSION;
    }

    public static String getMonoInstrumentationFlag() {
        return MONO_INSTRUMENTATION_FLAG;
    }

    /**
     * Set the build configuration values read from the newrelic_config.json asset.
     * Must be called before getBuildId() or getIsObfuscated() are used.
     */
    static void setNewRelicConfig(String configBuildId, boolean configObfuscated) {
        buildId = configBuildId;
        obfuscated = configObfuscated;
    }

    public static String getBuildId() {
        if (buildId != null) {
            return buildId;
        }

        synchronized (implLock) {
            if (buildId == null) {
                if (getMonoInstrumentationFlag().equals("YES")) {
                    buildId = DEFAULT_BUILD_ID;
                } else {
                    AgentLogManager.getAgentLog().error("Agent.getBuildId() was unable to find a valid build ID. " +
                            "Crashes and handled exceptions will not be accepted.");
                    buildId = "";
                }
            }
        }

        return buildId;
    }

    public static boolean getIsObfuscated() {
        return obfuscated;
    }

    public static String getCrossProcessId() {
        return getImpl().getCrossProcessId();
    }

    public static int getStackTraceLimit() {
        return getImpl().getStackTraceLimit();
    }

    public static int getResponseBodyLimit() {
        return getImpl().getResponseBodyLimit();
    }

    /**
     * Add a TransactionData object to the current set of transactions.
     *
     * @param transactionData
     */
    public static void addTransactionData(TransactionData transactionData) {
        getImpl().addTransactionData(transactionData);
    }

    /**
     * Gets the current set of transactions & clears the internal list of transactions.
     *
     * @return
     */
    public static List<TransactionData> getAndClearTransactionData() {
        return getImpl().getAndClearTransactionData();
    }

    /**
     * Add all given transactions into the internal transaction list.
     *
     * @param transactionDataList List of TransactionData to add.
     */
    public static void mergeTransactionData(final List<TransactionData> transactionDataList) {
        getImpl().mergeTransactionData(transactionDataList);
    }

    /**
     * Get the active network carrier.
     * <p>
     * XXX strange place for this, but we need the Context.
     */
    public static String getActiveNetworkCarrier() {
        return getImpl().getNetworkCarrier();
    }

    /**
     * Get the active WAN connection type.
     */
    public static String getActiveNetworkWanType() {
        return getImpl().getNetworkWanType();
    }

    /**
     * Permanently disable the active version of the agent.
     */
    public static void disable() {
        getImpl().disable();
    }

    /**
     * Determine whether or not the agent is disabled.
     *
     * @return
     */
    public static boolean isDisabled() {
        return getImpl().isDisabled();
    }

    /**
     * Start (or restart) the agent.
     */
    public static void start() {
        getImpl().start();
    }

    /**
     * Stop the agent.
     */
    public static void stop() {
        getImpl().stop();
    }

    /**
     * Set the current location.
     *
     * @param countryCode ISO 3166 country code
     * @param adminRegion Administrative region (such as state or province)
     */
    public static void setLocation(String countryCode, String adminRegion) {
        getImpl().setLocation(countryCode, adminRegion);
    }

    /**
     * Return an implementation specific string encoder. Currently Base64 in Android implementation.
     */
    public static Encoder getEncoder() {
        return getImpl().getEncoder();
    }

    public static DeviceInformation getDeviceInformation() {
        return getImpl().getDeviceInformation();
    }

    public static ApplicationInformation getApplicationInformation() {
        return getImpl().getApplicationInformation();
    }

    public static boolean hasReachableNetworkConnection(final String reachableHost) {
        return getImpl().hasReachableNetworkConnection(reachableHost);
    }

    public static boolean isInstantApp() {
        return getImpl().isInstantApp();
    }

    public static void persistHarvestDataToDisk(String data){
        getImpl().persistHarvestDataToDisk(data);
    }

    public static Map<String, String> getAllOfflineData(){
        return getImpl().getAllOfflineData();
    }
}
