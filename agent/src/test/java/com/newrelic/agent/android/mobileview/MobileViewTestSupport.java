/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.mobileview;

import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.AgentImpl;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.ApplicationFramework;
import com.newrelic.agent.android.api.common.CarrierType;
import com.newrelic.agent.android.api.common.TransactionData;
import com.newrelic.agent.android.api.common.WanType;
import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.analytics.AnalyticsAttributeStore;
import com.newrelic.agent.android.analytics.AnalyticsControllerImpl;
import com.newrelic.agent.android.analytics.AnalyticsEvent;
import com.newrelic.agent.android.analytics.AnalyticsEventStore;
import com.newrelic.agent.android.harvest.ApplicationInformation;
import com.newrelic.agent.android.harvest.DeviceInformation;
import com.newrelic.agent.android.harvest.EnvironmentInformation;
import com.newrelic.agent.android.util.Decoder;
import com.newrelic.agent.android.util.Encoder;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal stand-ins for agent-core's test-only stubs (StubAgentImpl, TestEventStore,
 * StubAnalyticsAttributeStore), which live in agent-core's test source set and are not
 * on the agent module's test classpath. Just enough for
 * {@code AnalyticsControllerImpl.initialize(...)} to be usable so that
 * {@code MobileViewContext}'s {@code isInitializedAndEnabled()} guard doesn't short-circuit
 * the lifecycle-callback logic under test.
 */
final class MobileViewTestSupport {
    private MobileViewTestSupport() {
    }

    static void initAnalyticsController() {
        AgentConfiguration config = new AgentConfiguration();
        config.setEnableAnalyticsEvents(true);
        config.setEventStore(new NoopEventStore());
        config.setAnalyticsAttributeStore(new NoopAttributeStore());

        AnalyticsControllerImpl.shutdown();
        AnalyticsControllerImpl.initialize(config, new NoopAgentImpl());
    }

    static void shutdownAnalyticsController() {
        AnalyticsControllerImpl.shutdown();
    }

    private static class NoopEventStore implements AnalyticsEventStore {
        private final Map<String, AnalyticsEvent> events = new LinkedHashMap<>();

        @Override
        public boolean store(AnalyticsEvent event) {
            events.put(event.getEventUUID(), event);
            return true;
        }

        @Override
        public List<AnalyticsEvent> fetchAll() {
            return new ArrayList<>(events.values());
        }

        @Override
        public int count() {
            return events.size();
        }

        @Override
        public void clear() {
            events.clear();
        }

        @Override
        public void delete(AnalyticsEvent event) {
            events.remove(event.getEventUUID());
        }

        @Override
        public String getRootPath() {
            return "";
        }
    }

    private static class NoopAttributeStore implements AnalyticsAttributeStore {
        @Override
        public boolean store(AnalyticsAttribute attribute) {
            return true;
        }

        @Override
        public List<AnalyticsAttribute> fetchAll() {
            return new ArrayList<>();
        }

        @Override
        public int count() {
            return 0;
        }

        @Override
        public void clear() {
        }

        @Override
        public void delete(AnalyticsAttribute attribute) {
        }

        @Override
        public String getRootPath() {
            return "";
        }
    }

    private static class NoopAgentImpl implements AgentImpl {
        private final DeviceInformation devInfo = new DeviceInformation();
        private boolean disabled = false;

        NoopAgentImpl() {
            devInfo.setOsName("Android");
            devInfo.setOsVersion("2.3");
            devInfo.setOsBuild("a.b.c");
            devInfo.setModel("Test");
            devInfo.setManufacturer("Test");
            devInfo.setAgentName("AndroidAgent");
            devInfo.setAgentVersion("0.0.0");
            devInfo.setDeviceId("00000000-0000-0000-0000-000000000000");
            devInfo.setArchitecture("TestArchitecture");
            devInfo.setRunTime("TestRuntime");
            devInfo.setSize("800x600");
            devInfo.setCountryCode("US");
            devInfo.setRegionCode("OR");
            devInfo.setApplicationFramework(ApplicationFramework.Native);
            devInfo.setApplicationFrameworkVersion("1.0.0");
        }

        @Override
        public void addTransactionData(TransactionData transactionData) {
        }

        @Override
        public List<TransactionData> getAndClearTransactionData() {
            return new ArrayList<>();
        }

        @Override
        public void mergeTransactionData(List<TransactionData> transactionDataList) {
        }

        @Override
        public String getCrossProcessId() {
            return "TEST_CROSS_PROCESS_ID";
        }

        @Override
        public int getCurrentProcessId() {
            return 123;
        }

        @Override
        public int getStackTraceLimit() {
            return 100;
        }

        @Override
        public int getResponseBodyLimit() {
            return 2048;
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

        @Override
        public void setLocation(String countryCode, String adminRegion) {
        }

        @Override
        public Encoder getEncoder() {
            final Base64.Encoder encoder = Base64.getEncoder();
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
        public Decoder getDecoder() {
            final Base64.Decoder decoder = Base64.getDecoder();
            return new Decoder() {
                public byte[] decode(String bytes) {
                    return decoder.decode(bytes);
                }

                @Override
                public byte[] decodeNoWrap(String bytes) {
                    return bytes.getBytes();
                }
            };
        }

        @Override
        public DeviceInformation getDeviceInformation() {
            return devInfo;
        }

        @Override
        public ApplicationInformation getApplicationInformation() {
            return new ApplicationInformation("test", "0.0", "test", "1");
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
            return System.currentTimeMillis();
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
        public void persistHarvestDataToDisk(String data) {
        }

        @Override
        public Map<String, String> getAllOfflineData() {
            return new HashMap<>();
        }
    }
}
