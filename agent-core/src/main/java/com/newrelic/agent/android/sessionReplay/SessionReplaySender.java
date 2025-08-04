/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay;

import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.ApplicationFramework;
import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.analytics.AnalyticsControllerImpl;
import com.newrelic.agent.android.harvest.HarvestConfiguration;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.payload.Payload;
import com.newrelic.agent.android.payload.PayloadSender;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.util.Constants;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

public class SessionReplaySender extends PayloadSender {

    protected Payload payload;
    private HarvestConfiguration harvestConfiguration;
    private Map<String, Object> replayDataMap;

    public SessionReplaySender(byte[] bytes, AgentConfiguration agentConfiguration) {
        super(bytes, agentConfiguration);
    }

    public SessionReplaySender(Payload payload, AgentConfiguration agentConfiguration, HarvestConfiguration harvestConfiguration, Map<String, Object> replayDataMap) throws IOException {
        super(payload, agentConfiguration);
        this.payload = payload;
        this.harvestConfiguration = harvestConfiguration;
        this.replayDataMap = replayDataMap;
        setPayload(this.payload.getBytes());
    }

    @Override
    protected HttpURLConnection getConnection() throws IOException {

        final AnalyticsControllerImpl controller = AnalyticsControllerImpl.getInstance();

        Map<String, String> attributes = new HashMap<>();
        attributes.put(Constants.SessionReplay.ENTITY_GUID, AgentConfiguration.getInstance().getEntityGuid());
        attributes.put(Constants.SessionReplay.IS_FIRST_CHUNK, replayDataMap.get(Constants.SessionReplay.IS_FIRST_CHUNK) + "");
        attributes.put(Constants.SessionReplay.RRWEB_VERSION, Constants.SessionReplay.RRWEB_VERSION_VALUE);
        attributes.put(Constants.SessionReplay.DECOMPRESSED_BYTES, replayDataMap.get(Constants.SessionReplay.DECOMPRESSED_BYTES) + "");
        attributes.put(Constants.SessionReplay.PAYLOAD_TYPE, Constants.SessionReplay.PAYLOAD_TYPE_STANDARD);
        attributes.put(Constants.SessionReplay.REPLAY_FIRST_TIMESTAMP, replayDataMap.get("firstTimestamp") + "");
        attributes.put(Constants.SessionReplay.REPLAY_LAST_TIMESTAMP, replayDataMap.get("lastTimestamp") + "");
        attributes.put(Constants.SessionReplay.CONTENT_ENCODING, Constants.SessionReplay.CONTENT_ENCODING_GZIP);
        attributes.put(Constants.SessionReplay.APP_VERSION, Agent.getApplicationInformation().getAppVersion());
        attributes.put(Constants.INSTRUMENTATION_PROVIDER, Constants.INSTRUMENTATION_PROVIDER_ATTRIBUTE);
        attributes.put(Constants.INSTRUMENTATION_NAME, AgentConfiguration.getInstance().getApplicationFramework().equals(ApplicationFramework.Native) ? Constants.INSTRUMENTATION_ANDROID_NAME : AgentConfiguration.getInstance().getApplicationFramework().name());
        attributes.put(Constants.INSTRUMENTATION_VERSION, AgentConfiguration.getInstance().getApplicationFrameworkVersion());
        attributes.put(Constants.INSTRUMENTATION_COLLECTOR_NAME, Constants.INSTRUMENTATION_ANDROID_NAME);

        for (AnalyticsAttribute analyticsAttribute : controller.getSessionAttributes()) {
            attributes.put(analyticsAttribute.getName(), analyticsAttribute.asJsonElement().getAsString());
        }
        attributes.put(Constants.SessionReplay.HAS_META, replayDataMap.get(Constants.SessionReplay.HAS_META) + "");

        // overwrite sessionId from Attribute
        if (replayDataMap.get(Constants.SessionReplay.SESSION_ID) != null) {
            attributes.put(Constants.SessionReplay.SESSION_ID, replayDataMap.get(Constants.SessionReplay.SESSION_ID) + "");
        }

        StringBuilder attributesString = new StringBuilder();
        try {
            attributes.forEach((key, value) -> {
                if (attributesString.length() > 0) {
                    attributesString.append(Constants.SessionReplay.URL_ENCODED_AMPERSAND);
                }
                attributesString.append(encodeValue(key))
                        .append(Constants.SessionReplay.URL_ENCODED_EQUALS)
                        .append(encodeValue(value));
            });
        } catch (Exception e) {
            log.error("Error encoding attributes: " + e.getMessage());
        }

        String urlString = getCollectorURI() +
                Constants.SessionReplay.URL_TYPE_PARAM +
                "&" + Constants.SessionReplay.URL_APP_ID_PARAM + harvestConfiguration.getApplication_id() +
                "&" + Constants.SessionReplay.URL_PROTOCOL_VERSION_PARAM +
                "&" + Constants.SessionReplay.URL_TIMESTAMP_PARAM + System.currentTimeMillis() +
                "&" + Constants.SessionReplay.URL_ATTRIBUTES_PARAM + attributesString;

        final HttpURLConnection connection = getHttpURLConnection(urlString);
        return connection;
    }

    private HttpURLConnection getHttpURLConnection(String urlString) throws IOException {
        final URL url = new URL(urlString);
        final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setDoOutput(true);
        connection.setRequestMethod("POST");
        connection.setRequestProperty(agentConfiguration.getAppTokenHeader(), agentConfiguration.getApplicationToken());
        connection.setRequestProperty(Constants.Network.CONTENT_TYPE_HEADER, Constants.Network.ContentType.OCTET_STREAM);
        connection.setRequestProperty("Content-Encoding", Constants.SessionReplay.CONTENT_ENCODING_GZIP);
        return connection;
    }

    private String encodeValue(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            log.error("Encoding error: " + e.getMessage());
            return ""; // Return an empty string in case of an error
        }
    }

    @Override
    protected void onRequestResponse(HttpURLConnection connection) throws IOException {
        final int responseCode = connection.getResponseCode();

        switch (responseCode) {
            case HttpsURLConnection.HTTP_OK:
            case HttpsURLConnection.HTTP_ACCEPTED:
                StatsEngine.get().sampleTimeMs(MetricNames.SUPPORTABILITY_SESSION_REPLAY_UPLOAD_TIME, timer.peek());
                int payloadSize = getPayloadSize();
                StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_SESSION_REPLAY_COMPRESSED, payloadSize);
                StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_SESSION_REPLAY_UNCOMPRESSED, ((Integer)this.replayDataMap.get("decompressedBytes")).intValue());
                log.info("Session Replay Blob: [" + payloadSize + "] bytes successfully submitted.");
                break;

            case HttpURLConnection.HTTP_CLIENT_TIMEOUT:
                onFailedUpload("The request to submit the payload [" + payload.getUuid() + "] has timed out (will try again later) - Response code [" + responseCode + "]");
                StatsEngine.get().sampleTimeMs(MetricNames.SUPPORTABILITY_SESSION_REPLAY_UPLOAD_TIMEOUT,timer.peek());
                break;

            case 429: // 'Too Many Requests' not defined by HttpURLConnection
                onFailedUpload("The request to submit the payload [" + payload.getUuid() + "] was throttled (will try again later) - Response code [" + responseCode + "]");
                StatsEngine.get().inc(MetricNames.SUPPORTABILITY_SESSION_REPLAY_UPLOAD_THROTTLED);
                break;

            case HttpsURLConnection.HTTP_INTERNAL_ERROR:
                StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_SESSION_REPLAY_UPLOAD_REJECTED);
                onFailedUpload("Session Replay Blob upload requests have been throttled (will try again later) - Response code [" + responseCode + "]");
                break;
            case HttpURLConnection.HTTP_FORBIDDEN:
                onFailedUpload("The data payload [" + payload.getUuid() + "] was rejected and will be deleted - Response code [" + responseCode + "]");
                StatsEngine.get().inc(MetricNames.SUPPORTABILITY_SESSION_REPLAY_FAILED_UPLOAD, timer.peek());
                break;

            default:
                onFailedUpload("Something went wrong while submitting the payload [" + payload.getUuid() + "] - (will try again later) - Response code [" + responseCode + "]");
                break;
        }

        log.debug("Payload [" + payload.getUuid() + "] delivery took " + timer.toc() + "ms");
    }

    protected void onFailedUpload(String errorMsg) {
        log.error(errorMsg);
        StatsEngine.get().inc(MetricNames.SUPPORTABILITY_SESSION_REPLAY_FAILED_UPLOAD);
    }

    @Override
    public PayloadSender call() throws Exception {
        if (shouldUploadOpportunistically()) {
            timer.tic();
            return super.call();
        }
        log.warn("Session Replay: endpoint is not reachable. Will try later...");

        return this;
    }

    protected URI getCollectorURI() {
        return URI.create(getProtocol() + agentConfiguration.getCollectorHost() + "/mobile/blobs?");
    }
}