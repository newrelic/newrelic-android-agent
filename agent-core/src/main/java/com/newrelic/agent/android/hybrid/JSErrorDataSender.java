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

public class JSErrorDataSender extends PayloadSender {
    static final String JSERROR_COLLECTOR_PATH = "/mobile/errors?protocol_version=1&platform=";

    public JSErrorDataSender(byte[] bytes, AgentConfiguration agentConfiguration) {
        super(bytes, agentConfiguration);
    }

    public JSErrorDataSender(Payload payload, AgentConfiguration agentConfiguration) {
        super(payload, agentConfiguration);
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
        connection.setRequestProperty(Constants.Network.APP_VERSION_HEADER, Agent.getApplicationInformation().getAppVersion());

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
                StatsEngine.get().sampleTimeMs(MetricNames.SUPPORTABILITY_JSERROR_UPLOAD_TIME, timer.peek());
                break;

            case HttpURLConnection.HTTP_CLIENT_TIMEOUT:
                onFailedUpload("The request to submit the payload [" + payload.getUuid() + "] has timed out (will try again later) - Response code [" + responseCode + "]");
                StatsEngine.get().inc(MetricNames.SUPPORTABILITY_JSERROR_UPLOAD_TIMEOUT);
                break;

            case 429: // 'Too Many Requests' not defined by HttpURLConnection
                onFailedUpload("The request to submit the payload [" + payload.getUuid() + "] was throttled (will try again later) - Response code [" + responseCode + "]");
                StatsEngine.get().inc(MetricNames.SUPPORTABILITY_JSERROR_UPLOAD_THROTTLED);
                break;

            case HttpsURLConnection.HTTP_INTERNAL_ERROR:
            case HttpURLConnection.HTTP_FORBIDDEN:
                onFailedUpload("The data payload [" + payload.getUuid() + "] was rejected and will be deleted - Response code [" + responseCode + "]");
                StatsEngine.get().sampleTimeMs(MetricNames.SUPPORTABILITY_JSERROR_FAILED_UPLOAD, timer.peek());
                break;

            default:
                onFailedUpload("Something went wrong while submitting the payload [" + payload.getUuid() + "] - (will try again later) - Response code [" + responseCode + "]");
                break;
        }

        log.debug("Payload [" + payload.getUuid() + "] delivery took " + timer.toc() + "ms");
    }

    protected void onFailedUpload(String errorMsg) {
        log.error(errorMsg);
        StatsEngine.get().inc(MetricNames.SUPPORTABILITY_JSERROR_FAILED_UPLOAD);
    }

    public String getErrorCollectorPath() {
        return JSERROR_COLLECTOR_PATH + agentConfiguration.getApplicationFramework().toString();
    }

    @Override
    protected boolean shouldUploadOpportunistically() {
        return PayloadController.shouldUploadOpportunistically();
    }
}
