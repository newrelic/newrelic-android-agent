/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay;

import static com.newrelic.agent.android.util.Constants.SessionReplay.InstrumentationDetails;

import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.ApplicationFramework;
import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.harvest.DeviceInformation;
import com.newrelic.agent.android.harvest.HarvestConfiguration;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.payload.Payload;
import com.newrelic.agent.android.payload.PayloadController;
import com.newrelic.agent.android.payload.PayloadReporter;
import com.newrelic.agent.android.payload.PayloadSender;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.util.Constants;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;

public class SessionReplayReporter extends PayloadReporter {
    protected static final AtomicReference<SessionReplayReporter> instance = new AtomicReference<>(null);
    protected final SessionReplayStore sessionReplayStore;
    private Boolean isFirstChunk = true;
    private Boolean hasMeta = true;
    private static String getInstrumentationName() {
        String name;
        if(AgentConfiguration.getInstance().getApplicationFramework().equals(ApplicationFramework.Native )) {
            name = InstrumentationDetails.ANDROID_NAME;
        } else {
            name = AgentConfiguration.getInstance().getApplicationFramework().name();
        }
        return name;
    }
    static Map<String, Object> setCommonBlockAttributes(Map<String,Object> attributes) {
        Map<String, Object> attr;
        if (attributes.isEmpty()){
            log.error("setCommonBlockAttributes called with empty attributes property");
            attr = new HashMap<>();
        } else {
            attr = attributes;
        }
        attr.put(InstrumentationDetails.PROVIDER, InstrumentationDetails.PROVIDER_ATTRIBUTE);
        attr.put(InstrumentationDetails.NAME, getInstrumentationName());
        attr.put(InstrumentationDetails.VERSION, AgentConfiguration.getInstance().getApplicationFrameworkVersion());
        attr.put(InstrumentationDetails.COLLECTOR_NAME, InstrumentationDetails.ANDROID_NAME);

        return attr;
    }

    protected final Callable reportCachedSessionReplayDataCallable = new Callable() {
        @Override
        public Object call() throws Exception {
            reportCachedSessionReplayData();
            return null;
        }
    };

    public static SessionReplayReporter getInstance() {
        return instance.get();
    }

    public static SessionReplayReporter initialize(AgentConfiguration agentConfiguration) {
        instance.compareAndSet(null, new SessionReplayReporter(agentConfiguration));

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

    public static boolean reportSessionReplayData(byte[] bytes, Map<String, Object> attributes) {
        boolean reported = false;

        if (isInitialized()) {
            Map<String, Object> sessionReplayAttributes = setCommonBlockAttributes(attributes);
            Payload payload = new Payload(bytes);
            instance.get().storeAndReportSessionReplayData(payload, sessionReplayAttributes);
            reported = true;
        } else {
            log.error("SessionReplayDataReporter not initialized");
        }

        return reported;
    }

    // Helper method to apply gzip compression
    private static byte[] gzipCompress(byte[] uncompressedData) throws IOException {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream(uncompressedData.length);
        try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(byteStream)) {
            gzipOutputStream.write(uncompressedData);
        }
        return byteStream.toByteArray();
    }

    protected static boolean isInitialized() {
        return instance.get() != null;
    }

    protected SessionReplayReporter(AgentConfiguration agentConfiguration) {
        super(agentConfiguration);
        this.sessionReplayStore = agentConfiguration.getSessionReplayStore();
        this.isEnabled.set(FeatureFlag.featureEnabled(FeatureFlag.HandledExceptions));
    }

    @Override
    public void start() {
        if (PayloadController.isInitialized()) {
            if (isEnabled()) {
                if (isStarted.compareAndSet(false, true)) {
                    //PayloadController.submitCallable(reportCachedSessionReplayDataCallable);
//                    Harvest.addHarvestListener(this);
                }
            }
        } else {
            log.error("SessionReplayDataReporter.start(): Must initialize PayloadController first.");
        }
    }

    @Override
    public void stop() {
//        Harvest.removeHarvestListener(this);
    }

    // upload any cached agent data posts
    protected void reportCachedSessionReplayData() {
        if (isInitialized()) {
            SessionReplayStore sessionStore = agentConfiguration.getSessionReplayStore();
            List data = sessionStore.fetchAll();
            String sessionReplayData = data.get(0).toString();
            reportSessionReplayData(sessionReplayData.getBytes(), null);
        } else {
            log.error("SessionReplayDataReporter not initialized");
        }
    }

    public Future reportSessionReplayData(Payload payload, Map<String, Object> attributes) throws IOException {

        attributes.put(Constants.SessionReplay.IS_FIRST_CHUNK, isFirstChunk);
        attributes.put(Constants.SessionReplay.HAS_META, hasMeta);
        attributes.put(Constants.SessionReplay.DECOMPRESSED_BYTES, payload.getBytes().length);

        byte[] compressedBytes = gzipCompress(payload.getBytes());
        if(compressedBytes.length > Constants.Network.MAX_PAYLOAD_SIZE) {
            DeviceInformation deviceInformation = Agent.getDeviceInformation();
            String name = MetricNames.SUPPORTABILITY_MAXPAYLOADSIZELIMIT_ENDPOINT
                    .replace(MetricNames.TAG_FRAMEWORK, deviceInformation.getApplicationFramework().name())
                    .replace(MetricNames.TAG_DESTINATION, MetricNames.METRIC_DATA_USAGE_COLLECTOR)
                    .replace(MetricNames.TAG_SUBDESTINATION,"SessionReplay");
            StatsEngine.SUPPORTABILITY.inc(name);
            return null;
        }
        Payload compressedPayload = new Payload(compressedBytes);
        PayloadSender payloadSender = new SessionReplaySender(compressedPayload, getAgentConfiguration(), HarvestConfiguration.getDefaultHarvestConfiguration(), attributes);

        isFirstChunk = false; // Set to false after the first chunk is sent
        Future future = PayloadController.submitPayload(payloadSender, new PayloadSender.CompletionHandler() {
            @Override
            public void onResponse(PayloadSender payloadSender) {
                if (payloadSender.isSuccessfulResponse()) {
                    //add supportability metrics

                } else {
                    // sender will remain in store and retry every harvest cycle
                    //Offline storage: No network at all, don't send back data
                    if (FeatureFlag.featureEnabled(FeatureFlag.OfflineStorage)) {
                        log.warn("SessionReplayReporter didn't send due to lack of network connection");
                    }
                }
            }

            @Override
            public void onException(PayloadSender payloadSender, Exception e) {
                log.error("SessionReplayReporter.reportSessionReplayData(Payload): " + e);
            }
        });

        return future;
    }

    public void storeAndReportSessionReplayData(Payload payload, Map<String, Object> attributes) {
        try {
            reportSessionReplayData(payload, attributes);
        } catch (IOException e) {
            log.error("SessionReplayReporter.storeAndReportSessionReplayData(Payload): " + e);
        }

    }

    @Override
    public void onHarvest() {

    }

}
