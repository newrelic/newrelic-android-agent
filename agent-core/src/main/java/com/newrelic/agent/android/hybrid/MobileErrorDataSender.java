/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.hybrid;

import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.harvest.Harvest;
import com.newrelic.agent.android.harvest.HarvestConfiguration;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.payload.Payload;
import com.newrelic.agent.android.payload.PayloadController;
import com.newrelic.agent.android.payload.PayloadSender;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.util.Constants;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

public class MobileErrorDataSender extends PayloadSender {
    static final String ERROR_COLLECTOR_PATH = "/mobile/errors?protocol_version=1&platform=";

    /**
     * App version override used for the {@code X-NewRelic-App-Version} header.
     * When non-null and non-empty, takes precedence over the current runtime
     * {@code Agent.getApplicationInformation().getAppVersion()}. Used to send
     * cached errors recorded under an older app version with the matching
     * upload header so the backend picks the right ProGuard mapping.
     */
    private final String appVersionOverride;

    public MobileErrorDataSender(byte[] bytes, AgentConfiguration agentConfiguration) {
        this(bytes, agentConfiguration, null);
    }

    public MobileErrorDataSender(byte[] bytes, AgentConfiguration agentConfiguration, String appVersionOverride) {
        super(bytes, agentConfiguration);
        this.appVersionOverride = appVersionOverride;
    }

    public MobileErrorDataSender(Payload payload, AgentConfiguration agentConfiguration) {
        this(payload, agentConfiguration, null);
    }

    public MobileErrorDataSender(Payload payload, AgentConfiguration agentConfiguration, String appVersionOverride) {
        super(payload, agentConfiguration);
        this.appVersionOverride = appVersionOverride;
    }

    @Override
    protected HttpURLConnection getConnection() throws IOException {
        final String urlString = getProtocol() + agentConfiguration.getErrorCollectorHost() + getErrorCollectorPath();
        final URL url = new URL(urlString);
        final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        final HarvestConfiguration harvestConfiguration = Harvest.getHarvestConfiguration();

        connection.setDoOutput(true);
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(PayloadController.PAYLOAD_COLLECTOR_TIMEOUT);
        connection.setReadTimeout(PayloadController.PAYLOAD_COLLECTOR_TIMEOUT);

        connection.setRequestProperty(Constants.Network.CONTENT_ENCODING_HEADER, Constants.Network.Encoding.IDENTITY);
        connection.setRequestProperty(Constants.Network.CONTENT_TYPE_HEADER, Constants.Network.ContentType.JSON);
        connection.setRequestProperty(Constants.Network.APPLICATION_LICENSE_HEADER, agentConfiguration.getApplicationToken());
        connection.setRequestProperty(Constants.Network.ACCOUNT_ID_HEADER, harvestConfiguration.getAccount_id());
        connection.setRequestProperty(Constants.Network.TRUSTED_ACCOUNT_ID_HEADER, harvestConfiguration.getTrusted_account_key());
        connection.setRequestProperty(Constants.Network.ENTITY_GUID_HEADER, harvestConfiguration.getEntity_guid());
        connection.setRequestProperty(Constants.Network.DEVICE_OS_NAME_HEADER, Agent.getDeviceInformation().getOsName());
        final String appVersionHeader = (appVersionOverride != null && !appVersionOverride.isEmpty())
                ? appVersionOverride
                : Agent.getApplicationInformation().getAppVersion();
        connection.setRequestProperty(Constants.Network.APP_VERSION_HEADER, appVersionHeader);

        // apply the headers passed in harvest configuration (X-NewRelic-Session, X-NewRelic-AgentConfiguration)
        Map<String, String> requestHeaders = Harvest.getHarvestConfiguration().getRequest_headers_map();
        for (Map.Entry<String, String> stringStringEntry : requestHeaders.entrySet()) {
            connection.setRequestProperty(stringStringEntry.getKey(), stringStringEntry.getValue());
        }

        return connection;
    }

    @Override
    protected void onRequestResponse(HttpURLConnection connection) throws IOException {
        final int responseCode = connection.getResponseCode();

        switch (responseCode) {
            case HttpsURLConnection.HTTP_OK:
            case HttpsURLConnection.HTTP_ACCEPTED:
                StatsEngine.get().sampleTimeMs(MetricNames.SUPPORTABILITY_MOBILE_ERROR_UPLOAD_TIME, timer.peek());
                break;

            case HttpURLConnection.HTTP_CLIENT_TIMEOUT:
                onFailedUpload("The request to submit the payload [" + payload.getUuid() + "] has timed out (will try again later) - Response code [" + responseCode + "]");
                StatsEngine.get().inc(MetricNames.SUPPORTABILITY_MOBILE_ERROR_UPLOAD_TIMEOUT);
                break;

            case 429: // 'Too Many Requests' not defined by HttpURLConnection
                onFailedUpload("The request to submit the payload [" + payload.getUuid() + "] was throttled (will try again later) - Response code [" + responseCode + "]");
                StatsEngine.get().inc(MetricNames.SUPPORTABILITY_MOBILE_ERROR_UPLOAD_THROTTLED);
                break;

            case HttpsURLConnection.HTTP_INTERNAL_ERROR:
            case HttpURLConnection.HTTP_FORBIDDEN:
                onFailedUpload("The data payload [" + payload.getUuid() + "] was rejected and will be deleted - Response code [" + responseCode + "]");
                StatsEngine.get().sampleTimeMs(MetricNames.SUPPORTABILITY_MOBILE_ERROR_FAILED_UPLOAD, timer.peek());
                break;

            default:
                onFailedUpload("Something went wrong while submitting the payload [" + payload.getUuid() + "] - (will try again later) - Response code [" + responseCode + "]");
                break;
        }

        log.debug("Payload [" + payload.getUuid() + "] delivery took " + timer.toc() + "ms");
    }

    protected void onFailedUpload(String errorMsg) {
        log.error(errorMsg);
        StatsEngine.get().inc(MetricNames.SUPPORTABILITY_MOBILE_ERROR_FAILED_UPLOAD);
    }

    public String getErrorCollectorPath() {
        return ERROR_COLLECTOR_PATH + agentConfiguration.getApplicationFramework().toString();
    }

    @Override
    protected boolean shouldUploadOpportunistically() {
        // Send JS errors immediately when there's network. Falling back to
        // PayloadController.shouldUploadOpportunistically() (which checks the
        // global opportunisticUploads flag, hardcoded to false) would queue the
        // reaper on payloadReaperQueue and delay the actual POST until the
        // next harvest tick fires PayloadController.dequeueRunnable. Matches
        // CrashSender and AEITraceSender semantics.
        return Agent.hasReachableNetworkConnection(null);
    }
}
