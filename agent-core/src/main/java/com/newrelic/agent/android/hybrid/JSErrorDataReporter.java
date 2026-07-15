/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.hybrid;

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
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.util.Constants;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.newrelic.agent.android.hybrid.JSErrorDataController.HarvestSnapshot;

public class JSErrorDataReporter extends PayloadReporter {
    protected static final AtomicReference<JSErrorDataReporter> instance = new AtomicReference<>(null);
    private static final AtomicBoolean reportExceptions = new AtomicBoolean(false);

    protected final MobileErrorStore jsErrorStore;

    private final AtomicBoolean startupSendInProgress = new AtomicBoolean(false);
    /**
     * Number of in-flight startup-send POSTs from a single fan-out. Used to keep
     * {@link #startupSendInProgress} {@code true} until every group's response
     * handler fires. Set on fan-out, decremented in each completion handler;
     * once it hits zero, {@code startupSendInProgress} is cleared.
     */
    private final AtomicInteger inFlightStartupSends = new AtomicInteger(0);

    protected final Callable reportCachedJSErrorDataCallable = new Callable() {
        @Override
        public Object call() throws Exception {
            reportCachedJSErrorData();
            return null;
        }
    };

    public static JSErrorDataReporter getInstance() {
        JSErrorDataReporter reporter = instance.get();
        if (reporter == null) {
            log.warn("JSErrorDataReporter.getInstance(): Reporter not initialized");
        }
        return reporter;
    }

    public static JSErrorDataReporter initialize(AgentConfiguration agentConfiguration) {
        instance.compareAndSet(null, new JSErrorDataReporter(agentConfiguration));
        reportExceptions.set(agentConfiguration.getReportHandledExceptions());

        return instance.get();
    }

    public static void shutdown() {
        if (isInitialized()) {
            try {
                instance.get().stop();
            } finally {
                instance.set(null);
                JSErrorDataController.reset();
            }
        }
    }

    protected static boolean isInitialized() {
        return instance.get() != null;
    }

    protected JSErrorDataReporter(AgentConfiguration agentConfiguration) {
        super(agentConfiguration);
        this.jsErrorStore = agentConfiguration.getMobileErrorStore();
        this.isEnabled.set(FeatureFlag.featureEnabled(FeatureFlag.JSError));
    }

    @Override
    public void start() {
        if (PayloadController.isInitialized()) {
            if (isEnabled()) {
                if (isStarted.compareAndSet(false, true)) {
                    Harvest.addHarvestListener(this);
                }
            }
        } else {
            log.error("JSErrorDataReporter.start(): Must initialize PayloadController first.");
        }
    }

    @Override
    public void stop() {
        Harvest.removeHarvestListener(this);
    }

    protected void reportCachedJSErrorData() {
        if (Agent.hasReachableNetworkConnection(null)) {
            if (jsErrorStore != null) {
                startupSendInProgress.set(true);
                try {
                    List<HarvestSnapshot> snapshots = JSErrorDataController.getInstance().getStoredJSErrorData();
                    if (snapshots == null || snapshots.isEmpty()) {
                        startupSendInProgress.set(false);
                        return;
                    }
                    // Pre-arm the in-flight counter for the entire fan-out so the
                    // first group's completion can't prematurely clear the flag.
                    inFlightStartupSends.set(snapshots.size());
                    for (HarvestSnapshot snapshot : snapshots) {
                        Payload payload = new Payload(snapshot.payloadJson.getBytes(StandardCharsets.UTF_8));
                        Future future = reportJSErrorData(payload, snapshot.snapshotIds, true, snapshot.appVersion);
                        if (future == null) {
                            // submit was rejected (e.g. oversized payload) — that group will
                            // never complete, so decrement here. Clear the flag if we hit zero.
                            if (inFlightStartupSends.decrementAndGet() == 0) {
                                startupSendInProgress.set(false);
                            }
                        }
                    }
                } catch (Exception e) {
                    inFlightStartupSends.set(0);
                    startupSendInProgress.set(false);
                    log.error("JSErrorDataReporter: Failed to report cached JS errors: " + e.getMessage());
                }
            } else {
                log.warn("JSErrorStore is not initialized");
            }
        }
    }

    /**
     * @param payload      the payload to send
     * @param sentIds      IDs to delete from the store on HTTP 200/202
     * @param isStartupSend {@code true} only when called from the startup cache-replay path;
     *                     causes the completion handler to decrement
     *                     {@link #inFlightStartupSends} and, on the last completion,
     *                     clear {@link #startupSendInProgress} so that
     *                     {@link #onHarvest} is unblocked. Must be {@code false} for
     *                     all harvest-cycle sends to avoid prematurely clearing the
     *                     flag while a startup send is still in-flight.
     * @param appVersionOverride Value for the {@code X-NewRelic-App-Version}
     *                          upload header. Set to the recorded app version
     *                          when sending cached errors from a previous build;
     *                          {@code null}/empty to use the current runtime
     *                          app version.
     */
    public Future reportJSErrorData(Payload payload, List<String> sentIds, boolean isStartupSend, String appVersionOverride) {
        PayloadSender payloadSender = new JSErrorDataSender(payload, getAgentConfiguration(), appVersionOverride);

        if (payload.getBytes().length > Constants.Network.MAX_PAYLOAD_SIZE) {
            DeviceInformation deviceInformation = Agent.getDeviceInformation();
            String name = MetricNames.SUPPORTABILITY_MAXPAYLOADSIZELIMIT_ENDPOINT
                    .replace(MetricNames.TAG_FRAMEWORK, deviceInformation.getApplicationFramework().name())
                    .replace(MetricNames.TAG_DESTINATION, MetricNames.METRIC_DATA_USAGE_COLLECTOR)
                    .replace(MetricNames.TAG_SUBDESTINATION, "f");
            StatsEngine.notice().inc(name);
            log.error("Unable to upload handled exceptions because payload is larger than 1 MB, handled exceptions are discarded.");
            // Caller (the fan-out loop) decrements inFlightStartupSends when we
            // return null, so don't decrement here.
            return null;
        }

        Future future = PayloadController.submitPayload(payloadSender, new PayloadSender.CompletionHandler() {
            @Override
            public void onResponse(PayloadSender payloadSender) {
                if (isStartupSend) {
                    if (inFlightStartupSends.decrementAndGet() <= 0) {
                        startupSendInProgress.set(false);
                    }
                }

                if (payloadSender.isSuccessfulResponse()) {
                    // HTTP 200/202/500: collector confirmed receipt (or permanently rejected).
                    // Delete the snapshotted IDs so they are not re-sent.
                    JSErrorDataController.getInstance().deleteErrors(sentIds);

                    //add supportability metrics
                    DeviceInformation deviceInformation = Agent.getDeviceInformation();
                    String name = MetricNames.SUPPORTABILITY_SUBDESTINATION_OUTPUT_BYTES
                            .replace(MetricNames.TAG_FRAMEWORK, deviceInformation.getApplicationFramework().name())
                            .replace(MetricNames.TAG_DESTINATION, MetricNames.METRIC_DATA_USAGE_COLLECTOR)
                            .replace(MetricNames.TAG_SUBDESTINATION, "f");
                    StatsEngine.get().sampleMetricDataUsage(name, payloadSender.getPayload().getBytes().length, 0);
                } else if (payloadSender.getResponseCode() == java.net.HttpURLConnection.HTTP_FORBIDDEN) {
                    // HTTP 403: permanent auth/token rejection — collector will never accept
                    // this payload regardless of retries. Delete to prevent infinite retry.
                    JSErrorDataController.getInstance().deleteErrors(sentIds);
                    log.error("JSErrorDataReporter: payload permanently rejected (403), errors discarded.");
                } else {
                    // Transient failure (408, 429, network error, etc.) — retain for retry.
                    log.warn("JSErrorDataReporter: payload send failed, errors retained for retry");
                }
            }

            @Override
            public void onException(PayloadSender payloadSender, Exception e) {
                if (isStartupSend) {
                    if (inFlightStartupSends.decrementAndGet() <= 0) {
                        startupSendInProgress.set(false);
                    }
                }
                log.error("JSErrorDataReporter.reportJSErrorData(Payload): " + e);
            }
        });

        return future;
    }

    public Future storeAndReportJSErrorData(Payload payload, List<String> sentIds, String appVersionOverride) {
        return reportJSErrorData(payload, sentIds, false, appVersionOverride);
    }

    @Override
    public void onHarvest() {
        PayloadController.submitCallable(reportCachedJSErrorDataCallable);
    }
}
