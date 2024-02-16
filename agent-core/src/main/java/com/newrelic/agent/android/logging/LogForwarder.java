/*
 * Copyright (c) 2023-2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.logging;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.payload.Payload;
import com.newrelic.agent.android.payload.PayloadSender;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.util.Constants;
import com.newrelic.agent.android.util.Streams;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HttpsURLConnection;

/**
 * LogForwarder is an implementation of PayloadSender that encodes the log data fiilename
 * as its payload data, rather than bytes of the log data itself.
 */
public class LogForwarder extends PayloadSender {
    // TODO Provide for EU and FedRamp collectors
    private static final String LOG_API_URL = "https://log-api.newrelic.com/mobile/logs";
    private final File file;

    public LogForwarder(final File logDataFile, AgentConfiguration agentConfiguration) {
        super(logDataFile.getAbsolutePath().getBytes(StandardCharsets.UTF_8), agentConfiguration);
        this.file = logDataFile;
        this.payload.setPersisted(false);   // already in a file
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
    public Payload getPayload() {
        try {
            return new Payload(Streams.readAllBytes(file));
        } catch (IOException e) {
            AgentLogManager.getAgentLog().error("LogForwarder: failed to get payload. " + e);
        }
        return new Payload();
    }

    @Override
    public void setPayload(byte[] payloadBytes) {
        file.delete();
        try (BufferedWriter writer = Streams.newBufferedFileWriter(file)) {
            writer.write(new String(payloadBytes, StandardCharsets.UTF_8));
            writer.flush();
        } catch (IOException e) {
            AgentLogManager.getAgentLog().error("LogForwarder: failed to set payload. " + e);
        }
    }

    /**
     * Returns the size of the payload (file). However, the max capacity of a ByteBuffer
     * is 0x7fffffff (2^31-1), while a File length is 0x7fffffffffffffffL (2^53-1);
     *
     * @return Minimum of file size or Integer.MAX_VALUE.
     */
    @Override
    public int getPayloadSize() {
        String logDataFile = new String(super.getPayload().getBytes(), StandardCharsets.UTF_8);
        try {
            return Math.toIntExact(new File(logDataFile).length());
        } catch (ArithmeticException e) {
        }

        return Integer.MAX_VALUE;
    }

    @Override
    protected HttpURLConnection getConnection() throws IOException {
        URL url = new URL(LOG_API_URL);
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();

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
                StatsEngine.SUPPORTABILITY.sampleTimeMs(MetricNames.SUPPORTABILITY_LOG_UPLOAD_TIME, timer.toc());
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
                onFailedUpload("The request to rejected due to Vortex payload size limits - Response code [" + responseCode + "]");
                break;

            case 429: // Too Many Requests
                StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_LOG_UPLOAD_THROTTLED);
                onFailedUpload("Log upload requests have been throttled- (will try again later) - Response code [" + responseCode + "]");
                break;

            case HttpsURLConnection.HTTP_INTERNAL_ERROR:
                StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_LOG_REMOVED_REJECTED);
                onFailedUpload("The log data was rejected and will be deleted - Response code " + connection.getResponseCode());
                break;

            default:
                onFailedUpload("Something went wrong while forwarding (will try again later) - Response code " + connection.getResponseCode());
                break;
        }

        log.debug("Payload [" + file.getName() + "] delivery took " + timer.toc() + "ms");
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
            final String dest = new URL(LOG_API_URL).getHost();
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

}