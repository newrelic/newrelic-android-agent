/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.aei;

import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.payload.FileBackedPayload;
import com.newrelic.agent.android.payload.PayloadSender;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.util.Constants;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.HttpsURLConnection;

public class AEITraceSender extends PayloadSender {
    static final String AEI_COLLECTOR_PATH = "/errors?anr=true";

    public AEITraceSender(String aeiTrace, AgentConfiguration agentConfiguration) {
        super(aeiTrace.getBytes(), agentConfiguration);
        setPayload(aeiTrace.getBytes());
    }

    public  AEITraceSender(File traceDataFile, AgentConfiguration agentConfiguration) {
        super(traceDataFile.getAbsolutePath().getBytes(StandardCharsets.UTF_8), agentConfiguration);
        this.payload = new FileBackedPayload(traceDataFile);
    }

    @Override
    protected HttpURLConnection getConnection() throws IOException {
        final HttpURLConnection connection = (HttpURLConnection) getCollectorURI().toURL().openConnection();

        connection.setDoOutput(true);
        connection.setRequestProperty(Constants.Network.CONTENT_TYPE_HEADER, Constants.Network.ContentType.JSON);
        connection.setRequestProperty(agentConfiguration.getAppTokenHeader(), agentConfiguration.getApplicationToken());
        connection.setRequestProperty(agentConfiguration.getDeviceOsNameHeader(), Agent.getDeviceInformation().getOsName());
        connection.setRequestProperty(agentConfiguration.getAppVersionHeader(), Agent.getApplicationInformation().getAppVersion());
        connection.setConnectTimeout(COLLECTOR_TIMEOUT);
        connection.setReadTimeout(COLLECTOR_TIMEOUT);

        return connection;
    }

    @Override
    public PayloadSender call() {
        try {
            return super.call();

        } catch (Exception e) {
            onFailedUpload("Unable to report crash to New Relic, will try again later. " + e);
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
        int responseCode = connection.getResponseCode();

        switch (responseCode) {
            case HttpsURLConnection.HTTP_OK:
            case HttpsURLConnection.HTTP_ACCEPTED:
                StatsEngine.get().sampleTimeMs(MetricNames.SUPPORTABILITY_CRASH_UPLOAD_TIME, timer.peek());
                break;

            case HttpURLConnection.HTTP_CLIENT_TIMEOUT:
                onFailedUpload("The request to submit the payload [" + payload.getUuid() + "] has timed out - (will try again later) - Response code [" + responseCode + "]");
                break;

            case 429: // Not defined by HttpURLConnection
                onFailedUpload("The request to submit the payload [" + payload.getUuid() + "] was has timed out - (will try again later) - Response code [" + responseCode + "]");
                break;

            case HttpsURLConnection.HTTP_INTERNAL_ERROR:
                onFailedUpload("The payload was rejected and will be deleted - Response code " + connection.getResponseCode());
                break;

            default:
                onFailedUpload("Something went wrong while submitting data (will try again later) - Response code " + connection.getResponseCode());
                break;
        }

        log.debug("AEITraceSender: data reporting took " + timer.toc() + "ms");
    }

    @Override
    protected void onFailedUpload(String errorMsg) {
        log.error("AEITraceSender: " + errorMsg);
        StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_LOG_FAILED_UPLOAD);
    }

    @Override
    protected void onRequestException(Exception e) {
        log.error("AEITraceSender: Crash upload failed: " + e);
        StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_LOG_FAILED_UPLOAD);
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
