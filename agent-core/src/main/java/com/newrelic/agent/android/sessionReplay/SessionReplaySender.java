/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay;

import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.analytics.AnalyticsControllerImpl;
import com.newrelic.agent.android.harvest.Harvest;
import com.newrelic.agent.android.harvest.HarvestConfiguration;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.payload.Payload;
import com.newrelic.agent.android.payload.PayloadSender;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.util.Constants;



import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

public class SessionReplaySender extends PayloadSender {

    protected Payload payload;
    private HarvestConfiguration harvestConfiguration;
    private Boolean isFirstChunk;

    public SessionReplaySender(byte[] bytes, AgentConfiguration agentConfiguration) {
        super(bytes, agentConfiguration);
    }

    public SessionReplaySender(Payload payload, AgentConfiguration agentConfiguration, HarvestConfiguration harvestConfiguration,Boolean isFirstChunk) {
        super(payload, agentConfiguration);
        this.payload = payload;
        this.harvestConfiguration = harvestConfiguration;
        this.isFirstChunk = isFirstChunk;
        setPayload(payload.getBytes());
    }

    @Override
    protected HttpURLConnection getConnection() throws IOException {

        final AnalyticsControllerImpl controller = AnalyticsControllerImpl.getInstance();
        final AnalyticsAttribute userIdAttr = controller.getAttribute(AnalyticsAttribute.USER_ID_ATTRIBUTE);

        Map<String, String> attributes = new HashMap<>();
        attributes.put("entityGuid", AgentConfiguration.getInstance().getEntityGuid());
        attributes.put("agentVersion", Agent.getDeviceInformation().getAgentVersion());
        attributes.put("sessionId", AgentConfiguration.getInstance().getSessionID());
        attributes.put("isFirstChunk", String.valueOf(isFirstChunk.booleanValue()));
        attributes.put("rrweb.version", "^2.0.0-alpha.17");
        attributes.put("decompressedBytes",this.payload.getBytes().length + "");
        attributes.put("payload.type", "standard");
        attributes.put("replay.firstTimestamp", (System.currentTimeMillis() - Harvest.getInstance().getHarvestTimer().timeSinceStart()) + "");
        attributes.put("replay.lastTimestamp", System.currentTimeMillis() + "");
        if(userIdAttr != null) {
            attributes.put("enduser.id", userIdAttr.getStringValue());
        }

        StringBuilder attributesString = new StringBuilder();
        try {
            attributes.forEach((key, value) -> {
                if (attributesString.length() > 0) {
                    attributesString.append("%26"); // URL-encoded '&'
                }
                attributesString.append(encodeValue(key))
                        .append("%3D") // URL-encoded '='
                        .append(encodeValue(value));
            });
        } catch (Exception e) {
            log.error("Error encoding attributes: " + e.getMessage());
        }

         String urlString = "https://staging-mobile-collector.newrelic.com/mobile/blobs?" +
                "type=SessionReplay" +
                "&app_id="+harvestConfiguration.getApplication_id() +
                "&protocol_version=0" +
                "&timestamp=" + System.currentTimeMillis() +
                "&attributes=" + attributesString;

        final URL url = new URL(urlString);
        final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setDoOutput(true);
        connection.setRequestMethod("POST");
        connection.setRequestProperty(agentConfiguration.getAppTokenHeader(), agentConfiguration.getApplicationToken());
        connection.setRequestProperty(Constants.Network.CONTENT_TYPE_HEADER, Constants.Network.ContentType.OCTET_STREAM);
        connection.setRequestProperty("Content-Encoding", "gzip");
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
                StatsEngine.get().sampleTimeMs(MetricNames.SUPPORTABILITY_HEX_UPLOAD_TIME, timer.peek());
                break;

            case HttpURLConnection.HTTP_CLIENT_TIMEOUT:
                onFailedUpload("The request to submit the payload [" + payload.getUuid() + "] has timed out (will try again later) - Response code [" + responseCode + "]");
                StatsEngine.get().inc(MetricNames.SUPPORTABILITY_HEX_UPLOAD_TIMEOUT);
                break;

            case 429: // 'Too Many Requests' not defined by HttpURLConnection
                onFailedUpload("The request to submit the payload [" + payload.getUuid() + "] was throttled (will try again later) - Response code [" + responseCode + "]");
                StatsEngine.get().inc(MetricNames.SUPPORTABILITY_HEX_UPLOAD_THROTTLED);
                break;

            case HttpsURLConnection.HTTP_INTERNAL_ERROR:
            case HttpURLConnection.HTTP_FORBIDDEN:
                onFailedUpload("The data payload [" + payload.getUuid() + "] was rejected and will be deleted - Response code [" + responseCode + "]");
                StatsEngine.get().sampleTimeMs(MetricNames.SUPPORTABILITY_HEX_FAILED_UPLOAD, timer.peek());
                break;

            default:
                onFailedUpload("Something went wrong while submitting the payload [" + payload.getUuid() + "] - (will try again later) - Response code [" + responseCode + "]");
                break;
        }

        log.debug("Payload [" + payload.getUuid() + "] delivery took " + timer.toc() + "ms");
    }

    protected void onFailedUpload(String errorMsg) {
        log.error(errorMsg);
        StatsEngine.get().inc(MetricNames.SUPPORTABILITY_HEX_FAILED_UPLOAD);
    }
}
