/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.crash;

import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.harvest.DeviceInformation;
import com.newrelic.agent.android.harvest.Harvest;
import com.newrelic.agent.android.harvest.HarvestLifecycleAware;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.payload.PayloadController;
import com.newrelic.agent.android.payload.PayloadReporter;
import com.newrelic.agent.android.payload.PayloadSender;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.util.Constants;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

public class CrashReporter extends PayloadReporter {
    protected static AtomicReference<CrashReporter> instance = new AtomicReference<>(null);

    // Default to false (crash is reported on next app launch)
    private static boolean jitCrashReporting = false;

    private final UncaughtExceptionHandler uncaughtExceptionHandler;
    protected final CrashStore crashStore;

    public static CrashReporter getInstance() {
        return instance.get();
    }

    public static CrashReporter initialize(AgentConfiguration agentConfiguration) {
        instance.compareAndSet(null, new CrashReporter(agentConfiguration));
        Harvest.addHarvestListener(instance.get());
        return instance.get();
    }

    public static void shutdown() {
        if (isInitialized()) {
            instance.get().stop();
            instance.set(null);
        }
    }

    public static void setReportCrashes(boolean reportCrashes) {
        if (isInitialized()) {
            CrashReporter.jitCrashReporting = reportCrashes;
        }
    }

    public static boolean getReportCrashes() {
        return jitCrashReporting;
    }

    public static UncaughtExceptionHandler getUncaughtExceptionHandler() {
        if (isInitialized()) {
            return instance.get().uncaughtExceptionHandler;
        }
        return null;
    }

    protected static boolean isInitialized() {
        return instance.get() != null;
    }

    protected CrashReporter(AgentConfiguration agentConfiguration) {
        super(agentConfiguration);
        this.uncaughtExceptionHandler = new UncaughtExceptionHandler(this);
        this.crashStore = agentConfiguration.getCrashStore();
        this.isEnabled.set(FeatureFlag.featureEnabled(FeatureFlag.CrashReporting));
    }

    @Override
    public void start() {
        if (isInitialized()) {
            if (isEnabled()) {
                if (isStarted.compareAndSet(false, true)) {
                    uncaughtExceptionHandler.installExceptionHandler();
                    jitCrashReporting = agentConfiguration.getReportCrashes();
                }
            } else {
                log.warn("CrashReporter: Crash reporting feature is disabled.");
            }
        } else {
            log.error("CrashReporter: Must initialize PayloadController first.");
        }
    }

    @Override
    protected void stop() {
        if (getUncaughtExceptionHandler() != null) {
            getUncaughtExceptionHandler().resetExceptionHandler();
        }
    }

    protected void reportSavedCrashes() {
        if (crashStore != null) {
            for (Crash crash : crashStore.fetchAll()) {
                if (crash.isStale()) {
                    crashStore.delete(crash);
                    log.info("CrashReporter: Crash [" + crash.getUuid().toString() + "] has become stale, and has been removed");
                    StatsEngine.get().inc(MetricNames.SUPPORTABILITY_CRASH_REMOVED_STALE);
                } else {
                    reportCrash(crash);
                }
            }
        }
    }

    protected Future reportCrash(final Crash crash) {
        if (crash != null) {
            final boolean hasValidDataToken = crash.getDataToken().isValid();

            if (isEnabled()) {
                if (hasValidDataToken) {
                    final CrashSender sender = new CrashSender(crash, agentConfiguration);

                    long crashSize = crash.asJsonObject().toString().getBytes().length;
                    if (crashSize > Constants.Network.MAX_PAYLOAD_SIZE) {
                        DeviceInformation deviceInformation = Agent.getDeviceInformation();
                        String name = MetricNames.SUPPORTABILITY_MAXPAYLOADSIZELIMIT_ENDPOINT
                                .replace(MetricNames.TAG_FRAMEWORK, deviceInformation.getApplicationFramework().name())
                                .replace(MetricNames.TAG_DESTINATION, MetricNames.METRIC_DATA_USAGE_COLLECTOR)
                                .replace(MetricNames.TAG_SUBDESTINATION, "mobile_crash");
                        StatsEngine.notice().inc(name);
                        crashStore.delete(crash);
                        log.error("Unable to upload crashes because payload is larger than 1 MB, crash report is discarded.");
                        return null;
                    }

                    final PayloadSender.CompletionHandler completionHandler = new PayloadSender.CompletionHandler() {
                        @Override
                        public void onResponse(PayloadSender payloadSender) {
                            if (payloadSender.isSuccessfulResponse()) {
                                if (crashStore != null) {
                                    crashStore.delete(crash);
                                }

                                //add supportability metrics
                                DeviceInformation deviceInformation = Agent.getDeviceInformation();
                                String name = MetricNames.SUPPORTABILITY_SUBDESTINATION_OUTPUT_BYTES
                                        .replace(MetricNames.TAG_FRAMEWORK, deviceInformation.getApplicationFramework().name())
                                        .replace(MetricNames.TAG_DESTINATION, MetricNames.METRIC_DATA_USAGE_COLLECTOR)
                                        .replace(MetricNames.TAG_SUBDESTINATION, "mobile_crash");
                                StatsEngine.get().sampleMetricDataUsage(name, crash.asJsonObject().toString().getBytes().length, 0);
                            } else {
                                //Offline storage: No network at all, don't send back data
                                if (FeatureFlag.featureEnabled(FeatureFlag.OfflineStorage)) {
                                    log.warn("CrashReporter didn't send due to lack of network connection");
                                }
                            }
                        }

                        @Override
                        public void onException(PayloadSender payloadSender, Exception e) {
                            log.error("CrashReporter: Crash upload failed: " + e);
                        }
                    };

                    if (!sender.shouldUploadOpportunistically()) {
                        log.warn("CrashReporter: network is unreachable. Crash will be uploaded on next app launch");
                    }

                    return PayloadController.submitPayload(sender, completionHandler);
                } else {
                    log.warn("CrashReporter: attempted to report null crash.");
                }

            } else {
                log.warn("CrashReporter: agent has not successfully connected and cannot report crashes.");
            }
        }

        return null;
    }

    public void storeAndReportCrash(Crash crash) {
        // Store the crash right away.  We'll delete it later if we're able to send it.
        boolean stored = false;

        if (crashStore != null) {
            if (crash != null) {
                stored = crashStore.store(crash);
                if (!stored) {
                    log.warn("CrashReporter: failed to store passed crash.");
                }
            } else {
                log.warn("CrashReporter: attempted to store null crash.");
            }
        } else {
            log.warn("CrashReporter: attempted to store crash without a crash store.");
        }

        try {
            // hand off crash to PayloadController
            if (jitCrashReporting) {
                reportCrash(crash);
            } else if (stored) {
                log.debug("CrashReporter: Crash has been recorded and will be uploaded during the next app launch.");
            } else {
                log.error("CrashReporter: Crash was dropped (Crash not stored and Just-in-time crash reporting is disabled).");
            }
        } catch (Exception e) {
            log.warn("CrashReporter.storeAndReportCrash(Crash): " + e);
        }
    }

    @Override
    public void onHarvestConnected() {
        PayloadController.submitCallable(new Callable() {
            @Override
            public Void call() {
                reportSavedCrashes();
                return null;
            }
        });
    }

}
