/*
 * Copyright (c) 2023-2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.logging;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.payload.FileBackedPayload;
import com.newrelic.agent.android.payload.PayloadSender;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.util.Constants;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HttpsURLConnection;

/**
 * LogForwarder uses the FileBackPayload to transfer log data to the collector.
 *
 */
public class LogForwarder extends PayloadSender {

    public LogForwarder(final File logDataFile, AgentConfiguration agentConfiguration) {
        super(agentConfiguration);
        this.payload = new FileBackedPayload(logDataFile);
    }

    @Override
    public PayloadSender call() throws Exception {
        if (shouldUploadOpportunistically()) {
            timer.tic();
            return super.call();
        }
        log.warn("LogForwarder: endpoint is not reachable. Will try later...");

        return this;
    }

    @Override
    protected HttpURLConnection getConnection() throws IOException {
        HttpsURLConnection connection = (HttpsURLConnection) getCollectorURI().toURL().openConnection();

        connection.setRequestMethod("POST");
        connection.setRequestProperty(Constants.Network.CONTENT_TYPE_HEADER, Constants.Network.ContentType.JSON);
        connection.setRequestProperty(Constants.Network.APPLICATION_LICENSE_HEADER, agentConfiguration.getApplicationToken());
        connection.setConnectTimeout((int) TimeUnit.MILLISECONDS.convert(LogReporter.LOG_ENDPOINT_TIMEOUT, TimeUnit.SECONDS));
        connection.setReadTimeout((int) TimeUnit.MILLISECONDS.convert(LogReporter.LOG_ENDPOINT_TIMEOUT, TimeUnit.SECONDS));
        connection.setDoOutput(true);
        connection.setDoInput(true);

        return connection;
    }

    /**
     * Response codes should adhere to the Vortex request spec
     *
     * @param connection Passed from the result of the call() operation
     * @see <a href=""https://source.datanerd.us/agents/agent-specs/blob/main/Collector-Response-Handling.md">Vortex response codes</a>
     **/
    @Override
    protected void onRequestResponse(HttpURLConnection connection) throws IOException {
        switch (connection.getResponseCode()) {
            case HttpsURLConnection.HTTP_OK:
            case HttpsURLConnection.HTTP_ACCEPTED:
                StatsEngine.SUPPORTABILITY.sampleTimeMs(MetricNames.SUPPORTABILITY_LOG_UPLOAD_TIME, timer.duration());
                log.debug("LogForwarder: Log data forwarding took " + timer.duration() + "ms");

                int payloadSize = getPayloadSize();
                StatsEngine.SUPPORTABILITY.sample(MetricNames.SUPPORTABILITY_LOG_UNCOMPRESSED, payloadSize);
                log.info("LogForwarder: [" + payloadSize + "] bytes successfully submitted.");
                break;

            case HttpURLConnection.HTTP_CLIENT_TIMEOUT:
                StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_LOG_UPLOAD_TIMEOUT);
                onFailedUpload("The request to submit the log data payload has timed out - (will try again later) - Response code [" + responseCode + "]");
                break;

            case HttpURLConnection.HTTP_ENTITY_TOO_LARGE:
                StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_LOG_UPLOAD_REJECTED);
                onFailedUpload("The request was rejected due to payload size limits - Response code [" + responseCode + "]");
                break;

            case 429: // Too Many Requests
                StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_LOG_UPLOAD_THROTTLED);
                onFailedUpload("Log upload requests have been throttled (will try again later) - Response code [" + responseCode + "]");
                break;

            case HttpsURLConnection.HTTP_INTERNAL_ERROR:
                StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_LOG_REMOVED_REJECTED);
                onFailedUpload("The log data was rejected and will be deleted - Response code " + connection.getResponseCode());
                break;

            default:
                onFailedUpload("Something went wrong while forwarding (will try again later) - Response code " + connection.getResponseCode());
                break;
        }

        log.debug("Payload [" + payload.getUuid() + "] delivery took " + timer.duration() + "ms");
    }

    @Override
    protected void onFailedUpload(String errorMsg) {
        log.error("LogForwarder: " + errorMsg);
        StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_LOG_FAILED_UPLOAD);
    }

    @Override
    protected void onRequestException(Exception e) {
        onFailedUpload(e.toString());
    }

    /**
     * Send the log data immediately if there is net connectivity, retry later otherwise
     */
    @Override
    protected boolean shouldUploadOpportunistically() {
        try {
            final String dest = getCollectorURI().toURL().getHost();
            InetAddress inet = InetAddress.getByName(dest);
            return dest.equals(inet.getHostName());

        } catch (Exception e) {
        }

        return false;
    }

    @Override
    public boolean shouldRetry() {
        return true;
    }

    @Override
    protected URI getCollectorURI() {
        return URI.create(getProtocol() + agentConfiguration.getCollectorHost() + "/mobile/logs");
    }

}