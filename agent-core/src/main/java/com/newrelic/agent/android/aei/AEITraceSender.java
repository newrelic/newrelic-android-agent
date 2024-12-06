/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.aei;

import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.harvest.Harvest;
import com.newrelic.agent.android.harvest.HarvestConfiguration;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.payload.FileBackedPayload;
import com.newrelic.agent.android.payload.PayloadController;
import com.newrelic.agent.android.payload.PayloadSender;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.util.Constants;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

public class AEITraceSender extends PayloadSender {
    static final String AEI_COLLECTOR_PATH = "/mobile/errors?protocol_version=1&platform=native&type=application_exit";
    static final int COLLECTOR_TIMEOUT = PayloadController.PAYLOAD_COLLECTOR_TIMEOUT;

    public AEITraceSender(String aeiTrace, AgentConfiguration agentConfiguration) {
        super(aeiTrace.getBytes(), agentConfiguration);
        setPayload(aeiTrace.getBytes());
    }

    public AEITraceSender(File traceDataFile, AgentConfiguration agentConfiguration) {
        super(traceDataFile.getAbsolutePath().getBytes(StandardCharsets.UTF_8), agentConfiguration);
        this.payload = new FileBackedPayload(traceDataFile);
    }

    /**
     * Header Example
     * <p>
     * POST /mobile/errors?protocol_version=1&platform=native&type=application_exit
     * HTTP/1.1
     * Host: mobile-collector.newrelic.com
     * content-encoding: identity
     * content-type: application/json
     * X-App-License-Key: ${mobile license}
     * X-NewRelic-Session: ${session}
     * X-NewRelic-AgentConfiguration: ${agent-configuration}
     * X-NewRelic-Account-Id: {Account Id}
     * X-NewRelic-Trusted-Account-Id: {someId}
     * X-NewRelic-Entity-Guid: {guid for app}.
     * X-NewRelic-Os-Name: {osName}
     * X-NewRelic-App-Version: {appVersion}
     *
     * @return
     * @throws IOException
     */
    @Override
    protected HttpURLConnection getConnection() throws IOException {
        final HttpURLConnection connection = (HttpURLConnection) getCollectorURI().toURL().openConnection();
        final HarvestConfiguration harvestConfiguration = Harvest.getHarvestConfiguration();

        connection.setDoOutput(true);
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(COLLECTOR_TIMEOUT);
        connection.setReadTimeout(COLLECTOR_TIMEOUT);

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
    public PayloadSender call() {
        try {
            return super.call();

        } catch (Exception e) {
            onFailedUpload("Unable to report AEI trace to New Relic, will try again later. " + e);
        }

        return this;
    }

    /**
     * Response codes should adhere to the Vortex request spec
     *
     * @param connection Passed from the result of the call() operation
     * @see <a href=""https://source.datanerd.us/agents/agent-specs/blob/main/Collector-Response-Handling.md">Vortex response codes</a>
     **/
    @Override
    protected void onRequestResponse(HttpURLConnection connection) throws IOException {
        super.onRequestResponse(connection);

        int responseCode = connection.getResponseCode();

        switch (responseCode) {
            case HttpsURLConnection.HTTP_OK:
            case HttpsURLConnection.HTTP_ACCEPTED:
                StatsEngine.SUPPORTABILITY.sampleTimeMs(MetricNames.SUPPORTABILITY_AEI_UPLOAD_TIME, timer.peek());
                break;

            // If rejected due to Vortex size limits, compress and retry on next harvest cycle
            case HttpsURLConnection.HTTP_ENTITY_TOO_LARGE:
                FileBackedPayload fileBackedPayload = (FileBackedPayload) payload;
                fileBackedPayload.compress(true);
        }

        log.debug("AEITraceSender: data reporting took " + timer.toc() + "ms");
    }

    @Override
    protected void onFailedUpload(String errorMsg) {
        log.error("AEITraceSender: " + errorMsg);
        StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_AEI_FAILED_UPLOAD);
    }

    @Override
    protected void onRequestException(Exception e) {
        log.error("AEITraceSender: Crash upload failed: " + e);
        StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_AEI_FAILED_UPLOAD);
    }

    /**
     * Send the data immediately if there is net connectivity, retry later otherwise
     */
    @Override
    protected boolean shouldUploadOpportunistically() {
        // send the crash report immediately if there is net connectivity
        return Agent.hasReachableNetworkConnection(null);
    }

    @Override
    protected URI getCollectorURI() {
        return URI.create(getProtocol() + agentConfiguration.getCollectorHost() + AEI_COLLECTOR_PATH);
    }

    @Override
    public boolean shouldRetry() {
        return true;
    }
}
