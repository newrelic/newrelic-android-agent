/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.agentdata;

import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.payload.Payload;
import com.newrelic.agent.android.payload.PayloadController;
import com.newrelic.agent.android.payload.PayloadSender;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.util.Constants;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

public class AgentDataSender extends PayloadSender {

    public AgentDataSender(byte[] bytes, AgentConfiguration agentConfiguration) {
        super(bytes, agentConfiguration);
    }

    public AgentDataSender(Payload payload, AgentConfiguration agentConfiguration) {
        super(payload, agentConfiguration);
    }

    @Override
    protected HttpURLConnection getConnection() throws IOException {
        final String urlString = getProtocol() + agentConfiguration.getHexCollectorHost() + agentConfiguration.getHexCollectorPath();
        final URL url = new URL(urlString);
        final HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        connection.setDoOutput(true);
        connection.setRequestMethod("POST");
        connection.setRequestProperty(Constants.Network.CONTENT_TYPE_HEADER, Constants.Network.ContentType.OCTET_STREAM);
        connection.setRequestProperty(agentConfiguration.getAppTokenHeader(), agentConfiguration.getApplicationToken());
        connection.setRequestProperty(agentConfiguration.getDeviceOsNameHeader(), Agent.getDeviceInformation().getOsName());
        connection.setRequestProperty(agentConfiguration.getAppVersionHeader(), Agent.getApplicationInformation().getAppVersion());
        connection.setConnectTimeout(agentConfiguration.getHexCollectorTimeout());
        connection.setReadTimeout(agentConfiguration.getHexCollectorTimeout());

        return connection;
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

    @Override
    protected boolean shouldUploadOpportunistically() {
        return PayloadController.shouldUploadOpportunistically();
    }
}
