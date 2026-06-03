/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay;

import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.ApplicationFramework;
import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.analytics.AnalyticsControllerImpl;
import com.newrelic.agent.android.harvest.DeviceInformation;
import com.newrelic.agent.android.harvest.Harvest;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;

import javax.net.ssl.HttpsURLConnection;

public class SessionReplayReporter extends PayloadReporter {
    protected static final AtomicReference<SessionReplayReporter> instance = new AtomicReference<>(null);
    protected final SessionReplayStore sessionReplayStore;
    private Boolean hasMeta = true;

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

        if (isInitialized() && attributes != null) {
            Payload payload = new Payload(bytes);
            instance.get().storeAndReportSessionReplayData(payload, attributes);
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
                    if (FeatureFlag.featureEnabled(FeatureFlag.OfflineStorage)) {
                        Harvest.addHarvestListener(this);
                    }
                }
            }
        } else {
            log.error("SessionReplayDataReporter.start(): Must initialize PayloadController first.");
        }
    }

    @Override
    public void stop() {
        Harvest.removeHarvestListener(this);
    }

    /**
     * Drain the offline cache serially in FIFO order. On the first failure (or stale
     * payload still failing) we halt so the next harvest cycle can retry — the network
     * may have come back partway through, but a sender failing again likely indicates
     * an ongoing problem and continuing would just waste battery.
     */
    protected void reportCachedSessionReplayData() {
        if (!FeatureFlag.featureEnabled(FeatureFlag.OfflineStorage)) {
            return;
        }
        if (!isInitialized()) {
            return;
        }
        OfflineSessionReplayStore store = agentConfiguration.getOfflineSessionReplayStore();
        if (store == null || store.count() == 0) {
            return;
        }
        if (!Agent.hasReachableNetworkConnection(null)) {
            return;
        }

        long ttl = agentConfiguration.getPayloadTTL();
        for (OfflineSessionReplayPayload cached : store.fetchAll()) {
            if (cached.isStale(ttl)) {
                store.delete(cached);
                StatsEngine.get().inc(MetricNames.SUPPORTABILITY_SESSION_REPLAY_OFFLINE_STALE_DROPPED);
                continue;
            }

            boolean sent = sendCachedPayload(cached);
            if (sent) {
                store.delete(cached);
                StatsEngine.get().inc(MetricNames.SUPPORTABILITY_SESSION_REPLAY_OFFLINE_FLUSHED);
            } else {
                // halt-on-failure; retry on next harvest
                break;
            }
        }
    }

    private boolean sendCachedPayload(OfflineSessionReplayPayload cached) {
        try {
            SessionReplaySender sender = new SessionReplaySender(cached, getAgentConfiguration(),
                    HarvestConfiguration.getDefaultHarvestConfiguration());
            Future future = PayloadController.submitPayload(sender, null);
            if (future == null) {
                return false;
            }
            Object result = future.get();
            if (result instanceof PayloadSender) {
                return ((PayloadSender) result).isSuccessfulResponse();
            }
            return false;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException ee) {
            log.error("SessionReplayReporter.sendCachedPayload: " + ee);
            return false;
        } catch (Exception e) {
            log.error("SessionReplayReporter.sendCachedPayload: " + e);
            return false;
        }
    }

    /**
     * Build the exact attribute map that {@link SessionReplaySender#getConnection()} would
     * have built at this moment. Frozen at capture time so a retry reproduces the original
     * request rather than mixing in a different session's attributes.
     */
    private Map<String, String> buildFrozenAttributes(Map<String, Object> replayDataMap) {
        final AnalyticsControllerImpl controller = AnalyticsControllerImpl.getInstance();
        final AgentConfiguration cfg = AgentConfiguration.getInstance();
        final Map<String, String> attributes = new LinkedHashMap<>();

        attributes.put(Constants.SessionReplay.ENTITY_GUID, cfg.getEntityGuid());
        attributes.put(Constants.SessionReplay.IS_FIRST_CHUNK, replayDataMap.get(Constants.SessionReplay.IS_FIRST_CHUNK) + "");
        attributes.put(Constants.SessionReplay.RRWEB_VERSION, Constants.SessionReplay.RRWEB_VERSION_VALUE);
        attributes.put(Constants.SessionReplay.DECOMPRESSED_BYTES, replayDataMap.get(Constants.SessionReplay.DECOMPRESSED_BYTES) + "");
        attributes.put(Constants.SessionReplay.PAYLOAD_TYPE, Constants.SessionReplay.PAYLOAD_TYPE_STANDARD);
        attributes.put(Constants.SessionReplay.REPLAY_FIRST_TIMESTAMP, replayDataMap.get("firstTimestamp") + "");
        attributes.put(Constants.SessionReplay.REPLAY_LAST_TIMESTAMP, replayDataMap.get("lastTimestamp") + "");
        attributes.put(Constants.SessionReplay.CONTENT_ENCODING, Constants.SessionReplay.CONTENT_ENCODING_GZIP);
        attributes.put(Constants.SessionReplay.APP_VERSION, Agent.getApplicationInformation().getAppVersion());
        attributes.put(Constants.INSTRUMENTATION_PROVIDER, Constants.INSTRUMENTATION_PROVIDER_ATTRIBUTE);
        attributes.put(Constants.INSTRUMENTATION_NAME,
                cfg.getApplicationFramework().equals(ApplicationFramework.Native)
                        ? Constants.INSTRUMENTATION_ANDROID_NAME
                        : cfg.getApplicationFramework().name());
        attributes.put(Constants.INSTRUMENTATION_VERSION, cfg.getApplicationFrameworkVersion());
        attributes.put(Constants.INSTRUMENTATION_COLLECTOR_NAME, Constants.INSTRUMENTATION_ANDROID_NAME);

        for (AnalyticsAttribute analyticsAttribute : controller.getSessionAttributes()) {
            attributes.put(analyticsAttribute.getName(), analyticsAttribute.asJsonElement().getAsString());
        }
        attributes.put(Constants.SessionReplay.HAS_META, replayDataMap.get(Constants.SessionReplay.HAS_META) + "");

        if (replayDataMap.get(Constants.SessionReplay.SESSION_ID) != null) {
            attributes.put(Constants.SessionReplay.SESSION_ID, replayDataMap.get(Constants.SessionReplay.SESSION_ID) + "");
        }
        return attributes;
    }

    /**
     * 408, 429, and any 5xx are transient — keep the payload for retry. 400 and 403 are
     * server-rejected — drop. responseCode 0 means we never got an HTTP roundtrip
     * (the sender bailed offline before connecting), so persist as a network failure.
     */
    private static boolean shouldPersistForRetry(int responseCode) {
        if (responseCode == 0) {
            return true;
        }
        if (responseCode == HttpsURLConnection.HTTP_BAD_REQUEST
                || responseCode == HttpsURLConnection.HTTP_FORBIDDEN) {
            return false;
        }
        if (responseCode == HttpsURLConnection.HTTP_CLIENT_TIMEOUT
                || responseCode == 429
                || responseCode >= 500) {
            return true;
        }
        // unknown code — be conservative and persist
        return true;
    }

    public Future reportSessionReplayData(Payload payload, Map<String, Object> attributes) throws IOException {

        attributes.put(Constants.SessionReplay.HAS_META, hasMeta);
        attributes.put(Constants.SessionReplay.DECOMPRESSED_BYTES, payload.getBytes().length);

        byte[] compressedBytes = gzipCompress(payload.getBytes());
        if(compressedBytes.length > Constants.Network.MAX_PAYLOAD_SIZE) {
            DeviceInformation deviceInformation = Agent.getDeviceInformation();
            String name = MetricNames.SUPPORTABILITY_MAXPAYLOADSIZELIMIT_ENDPOINT
                    .replace(MetricNames.TAG_FRAMEWORK, deviceInformation.getApplicationFramework().name())
                    .replace(MetricNames.TAG_DESTINATION, MetricNames.METRIC_DATA_USAGE_COLLECTOR)
                    .replace(MetricNames.TAG_SUBDESTINATION,"SessionReplay");
            StatsEngine.SUPPORTABILITY.sample(name,compressedBytes.length);
            log.warn("SessionReplayReporter.reportSessionReplayData(Payload): Payload size (" + compressedBytes.length + " bytes) exceeds maximum allowed size (" + Constants.Network.MAX_PAYLOAD_SIZE + " bytes). Payload not sent.");
            return null;
        }

        // Build the frozen snapshot once. Used both for proactive offline persist and for
        // failure-time persist via the completion handler.
        final OfflineSessionReplayStore offlineStore = agentConfiguration.getOfflineSessionReplayStore();
        final boolean offlineEnabled = FeatureFlag.featureEnabled(FeatureFlag.OfflineStorage)
                && offlineStore != null;

        OfflineSessionReplayPayload snapshot = null;
        if (offlineEnabled) {
            // Proactive persist: network unreachable at capture. Tag offline=true to match
            // Crash/Error/Event/AgentData. Snapshot built only on this branch so the tag
            // doesn't leak onto failure-persisted (5xx/timeout) payloads.
            if (!Agent.hasReachableNetworkConnection(null)) {
                final Map<String, String> frozenAttrs = buildFrozenAttributes(attributes);
                frozenAttrs.put(AnalyticsAttribute.OFFLINE_NAME_ATTRIBUTE, "true");
                final long ts = System.currentTimeMillis();
                offlineStore.store(new OfflineSessionReplayPayload(
                        payload.getUuid(), ts, ts, frozenAttrs, compressedBytes));
                StatsEngine.get().inc(MetricNames.SUPPORTABILITY_SESSION_REPLAY_OFFLINE_PERSISTED);
                StatsEngine.notice().inc(MetricNames.OFFLINE_STORAGE_SESSION_REPLAY_COUNT);
                log.debug("SessionReplayReporter: network unreachable; payload [" + payload.getUuid() + "] persisted to offline cache");
                return null;
            }

            // Failure-persist snapshot (no offline tag — network was reachable at capture).
            final long ts = System.currentTimeMillis();
            snapshot = new OfflineSessionReplayPayload(
                    payload.getUuid(), ts, ts, buildFrozenAttributes(attributes), compressedBytes);
        }

        Payload compressedPayload = new Payload(compressedBytes);
        PayloadSender payloadSender = new SessionReplaySender(compressedPayload, getAgentConfiguration(), HarvestConfiguration.getDefaultHarvestConfiguration(), attributes);

        final OfflineSessionReplayPayload snapshotForCallback = snapshot;
        final OfflineSessionReplayStore storeForCallback = offlineStore;
        Future future = PayloadController.submitPayload(payloadSender, new PayloadSender.CompletionHandler() {
            @Override
            public void onResponse(PayloadSender payloadSender) {
                if (payloadSender.isSuccessfulResponse()) {
                    return;
                }
                if (!offlineEnabled || snapshotForCallback == null || storeForCallback == null) {
                    return;
                }
                int code = payloadSender.getResponseCode();
                if (shouldPersistForRetry(code)) {
                    storeForCallback.store(snapshotForCallback);
                    StatsEngine.get().inc(MetricNames.SUPPORTABILITY_SESSION_REPLAY_OFFLINE_PERSISTED);
                    log.debug("SessionReplayReporter: payload [" + snapshotForCallback.getUuid()
                            + "] persisted for retry (response " + code + ")");
                }
            }

            @Override
            public void onException(PayloadSender payloadSender, Exception e) {
                log.error("SessionReplayReporter.reportSessionReplayData(Payload): " + e);
                if (!offlineEnabled || snapshotForCallback == null || storeForCallback == null) {
                    return;
                }
                storeForCallback.store(snapshotForCallback);
                StatsEngine.get().inc(MetricNames.SUPPORTABILITY_SESSION_REPLAY_OFFLINE_PERSISTED);
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
        PayloadController.submitCallable(reportCachedSessionReplayDataCallable);
    }

}