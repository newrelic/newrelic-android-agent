/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.agentdata;

import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.harvest.DeviceInformation;
import com.newrelic.agent.android.harvest.Harvest;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.payload.Payload;
import com.newrelic.agent.android.payload.PayloadController;
import com.newrelic.agent.android.payload.PayloadReporter;
import com.newrelic.agent.android.payload.PayloadSender;
import com.newrelic.agent.android.payload.PayloadStore;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.util.Constants;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

public class AgentDataReporter extends PayloadReporter {
    protected static final AtomicReference<AgentDataReporter> instance = new AtomicReference<>(null);
    private static boolean reportExceptions = false;

    protected final PayloadStore<Payload> payloadStore;

    protected final Callable reportCachedAgentDataCallable = new Callable() {
        @Override
        public Object call() throws Exception {
            reportCachedAgentData();
            return null;
        }
    };

    public static AgentDataReporter getInstance() {
        return instance.get();
    }

    public static AgentDataReporter initialize(AgentConfiguration agentConfiguration) {
        instance.compareAndSet(null, new AgentDataReporter(agentConfiguration));
        reportExceptions = agentConfiguration.getReportHandledExceptions();

        return instance.get();
    }

    public static void shutdown() {
        if (isInitialized()) {
            try {
                instance.get().stop();
            } finally {
                instance.set(null);
            }
        }
    }

    public static boolean reportAgentData(byte[] bytes) {
        boolean reported = false;

        if (isInitialized()) {
            if (reportExceptions) {
                Payload payload = new Payload(bytes);
                instance.get().storeAndReportAgentData(payload);
                reported = true;
            }
        } else {
            log.error("AgentDataReporter not initialized");
        }

        return reported;
    }

    protected static boolean isInitialized() {
        return instance.get() != null;
    }

    protected AgentDataReporter(AgentConfiguration agentConfiguration) {
        super(agentConfiguration);
        this.payloadStore = agentConfiguration.getPayloadStore();
        this.isEnabled.set(FeatureFlag.featureEnabled(FeatureFlag.HandledExceptions));
    }

    @Override
    public void start() {
        if (PayloadController.isInitialized()) {
            if (isEnabled()) {
                if (isStarted.compareAndSet(false, true)) {
                    PayloadController.submitCallable(reportCachedAgentDataCallable);
                    Harvest.addHarvestListener(this);
                }
            }
        } else {
            log.error("AgentDataReporter.start(): Must initialize PayloadController first.");
        }
    }

    @Override
    public void stop() {
        Harvest.removeHarvestListener(this);

    }

    // upload any cached agent data posts
    protected void reportCachedAgentData() {
        if(Agent.hasReachableNetworkConnection(null)) {
            if (isInitialized()) {
                if (payloadStore != null) {
                    for (Payload payload : payloadStore.fetchAll()) {
                        if (!isPayloadStale(payload)) {
                            reportAgentData(payload);
                        }
                    }
                }
            } else {
                log.error("AgentDataReporter not initialized");
            }
        }
    }

    public Future reportAgentData(Payload payload) {
        PayloadSender payloadSender = new AgentDataSender(payload, getAgentConfiguration());

        if (payload.getBytes().length > Constants.Network.MAX_PAYLOAD_SIZE) {
            DeviceInformation deviceInformation = Agent.getDeviceInformation();
            String name = MetricNames.SUPPORTABILITY_MAXPAYLOADSIZELIMIT_ENDPOINT
                    .replace(MetricNames.TAG_FRAMEWORK, deviceInformation.getApplicationFramework().name())
                    .replace(MetricNames.TAG_DESTINATION, MetricNames.METRIC_DATA_USAGE_COLLECTOR)
                    .replace(MetricNames.TAG_SUBDESTINATION, "f");
            StatsEngine.notice().inc(name);
            payloadStore.delete(payload);
            log.error("Unable to upload handled exceptions because payload is larger than 1 MB, handled exceptions are discarded.");
            return null;
        }

        Future future = PayloadController.submitPayload(payloadSender, new PayloadSender.CompletionHandler() {
            @Override
            public void onResponse(PayloadSender payloadSender) {
                if (payloadSender.isSuccessfulResponse()) {
                    if (payloadStore != null) {
                        payloadStore.delete(payloadSender.getPayload());
                    }

                    //add supportability metrics
                    DeviceInformation deviceInformation = Agent.getDeviceInformation();
                    String name = MetricNames.SUPPORTABILITY_SUBDESTINATION_OUTPUT_BYTES
                            .replace(MetricNames.TAG_FRAMEWORK, deviceInformation.getApplicationFramework().name())
                            .replace(MetricNames.TAG_DESTINATION, MetricNames.METRIC_DATA_USAGE_COLLECTOR)
                            .replace(MetricNames.TAG_SUBDESTINATION, "f");
                    StatsEngine.get().sampleMetricDataUsage(name, payloadSender.getPayload().getBytes().length, 0);
                } else {
                    // sender will remain in store and retry every harvest cycle
                    //Offline storage: No network at all, don't send back data
                    if (FeatureFlag.featureEnabled(FeatureFlag.OfflineStorage)) {
                        log.warn("AgentDataReporter didn't send due to lack of network connection");
                    }
                }
            }

            @Override
            public void onException(PayloadSender payloadSender, Exception e) {
                log.error("AgentDataReporter.reportAgentData(Payload): " + e);
            }
        });

        return future;
    }

    public Future storeAndReportAgentData(Payload payload) {
        // Store the payload bytes right away. Will be deleted later once sent or expired
        if (payloadStore != null && payload.isPersisted()) {
            if (payloadStore.store(payload)) {
                payload.setPersisted(false);    // don't save it more than needed
            }
        }

        return reportAgentData(payload);
    }

    protected boolean isPayloadStale(Payload payload) {
        if (payload.isStale(agentConfiguration.getPayloadTTL())) {
            payloadStore.delete(payload);
            log.info("Payload [" + payload.getUuid() + "] has become stale, and has been removed");
            StatsEngine.get().inc(MetricNames.SUPPORTABILITY_PAYLOAD_REMOVED_STALE);
            return true;
        }

        return false;
    }

    @Override
    public void onHarvest() {
        PayloadController.submitCallable(reportCachedAgentDataCallable);
    }

}
