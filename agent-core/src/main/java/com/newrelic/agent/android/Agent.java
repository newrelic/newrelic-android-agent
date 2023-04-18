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

import java.lang.reflect.Field;
import java.util.List;

public class Agent {
    public static final String VERSION = "#VERSION#";
    public static final String MONO_INSTRUMENTATION_FLAG = "#MONO_INSTRUMENTATION_FLAG#";
    public static final String DEFAULT_BUILD_ID = "#DEFAULT_BUILD_ID#";

    private static final AgentImpl NULL_AGENT_IMPL = new NullAgentImpl();

    private static Object implLock = new Object();
    private static AgentImpl impl = NULL_AGENT_IMPL;
    private static String buildId = null;

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

    public static String getBuildId() {

        synchronized (implLock) {
            if (buildId == null) {
                String build_id = "";

                if (getMonoInstrumentationFlag().equals("YES")) {
                    build_id = DEFAULT_BUILD_ID;
                } else {
                    try {
                        // Since we're not in Android land, we cant't check
                        // the SDK build level before making the call
                        ClassLoader classLoader = Agent.class.getClassLoader();
                        Class newRelicConfigClass = classLoader.loadClass("com.newrelic.agent.android.NewRelicConfig");
                        build_id = newRelicConfigClass.getDeclaredField("BUILD_ID").get(null).toString();
                    } catch (Exception e) {
                        AgentLogManager.getAgentLog().error("Agent.getBuildId() was unable to find a valid build Id. " +
                                "Crashes and handled exceptions will not be accepted.");
                    }
                }

                buildId = build_id;
            }
        }

        return buildId;
    }

    public static boolean getIsObfuscated() {

        boolean isObfuscated = false;
        try {
            ClassLoader classLoader = Agent.class.getClassLoader();
            Class newRelicConfigClass = classLoader.loadClass("com.newrelic.agent.android.NewRelicConfig");
            Field field = newRelicConfigClass.getDeclaredField("OBFUSCATED");
            field.setAccessible(true);
            isObfuscated = (Boolean) field.get(null);
            field.setAccessible(false);
        } catch (Exception e) {
            AgentLogManager.getAgentLog().error("Unable to get obfuscated flag in crash");
        }
        return isObfuscated;
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

}
