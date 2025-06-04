/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.payload;

import static com.newrelic.agent.android.util.Constants.Network.CONTENT_LENGTH_HEADER;

import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.stats.TicToc;
import com.newrelic.agent.android.util.Streams;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;

import javax.net.ssl.HttpsURLConnection;

public abstract class PayloadSender implements Callable<PayloadSender> {
    protected static final AgentLog log = AgentLogManager.getAgentLog();

    protected Payload payload;
    protected final AgentConfiguration agentConfiguration;
    protected final TicToc timer;
    protected int responseCode;

    public PayloadSender(AgentConfiguration agentConfiguration) {
        this.agentConfiguration = agentConfiguration;
        this.timer = new TicToc().tic();
        this.responseCode = 0;
    }

    public PayloadSender(Payload payload, AgentConfiguration agentConfiguration) {
        this(agentConfiguration);
        this.payload = payload;
    }

    public PayloadSender(byte[] payloadBytes, AgentConfiguration agentConfiguration) {
        this(agentConfiguration);
        this.payload = new Payload(payloadBytes);
    }

    public Payload getPayload() {
        return payload;
    }

    public int getPayloadSize() {
        return payload.getBytes().length;
    }

    public void setPayload(byte[] payloadBytes) {
        this.payload.putBytes(payloadBytes);
    }

    protected abstract HttpURLConnection getConnection() throws IOException;

    protected void onRequestResponse(HttpURLConnection connection) throws IOException {
        int responseCode = connection.getResponseCode();

        switch (responseCode) {
            case HttpsURLConnection.HTTP_OK:
            case HttpsURLConnection.HTTP_ACCEPTED:
                if (connection.getDoInput()) {
                    InputStream responseInputStream = connection.getInputStream();
                    if (responseInputStream != null) {
                        String responseString = readStream(responseInputStream, responseInputStream.available());
                        onRequestContent(responseString);
                    }
                }
                break;

            case HttpsURLConnection.HTTP_INTERNAL_ERROR:
            case HttpsURLConnection.HTTP_FORBIDDEN:
                onFailedUpload("Payload [" + payload.getUuid() + "] was rejected and will be deleted - Response code [" + responseCode + "]");
                break;

            case HttpURLConnection.HTTP_CLIENT_TIMEOUT:
                onFailedUpload("The request to submit the payload [" + payload.getUuid() + "] has timed out (will try again later) - Response code [" + responseCode + "]");
                break;

            case HttpURLConnection.HTTP_ENTITY_TOO_LARGE:
                onFailedUpload("The request was rejected due to payload size limits - Response code [" + responseCode + "]");
                break;

            case 429: // 'Too Many Requests' not defined by HttpURLConnection
                onFailedUpload("The request to submit the payload [" + payload.getUuid() + "] was throttled (will try again later) - Response code [" + responseCode + "]");
                break;

            default:
                onFailedUpload("Something went wrong while submitting the payload [" + payload.getUuid() + "] (will try again later) - Response code [" + responseCode + "]");
                break;
        }


        log.debug("Payload [" + payload.getUuid() + "] delivery took " + timer.duration() + "ms");
    }

    protected void onRequestContent(String responseString) {
    }

    protected void onRequestException(final Exception e) {
        onFailedUpload("Payload [" + payload.getUuid() + "] upload failed: " + e);
    }

    protected void onFailedUpload(String errorMsg) {
        log.error(errorMsg);
    }


    @Override
    @SuppressWarnings("NewApi")
    public PayloadSender call() throws Exception {
        try {
            byte[] payloadBytes = getPayload().getBytes();
            final HttpURLConnection connection = getConnection();

            try {
                timer.tic();
                connection.connect();
                if (connection.getDoOutput()) {
                    try (final OutputStream out = new BufferedOutputStream(connection.getOutputStream())) {
                        out.write(payloadBytes);
                        out.flush();
                    }
                }

                responseCode = connection.getResponseCode();
                onRequestResponse(connection);

            } catch (Exception e) {
                onRequestException(e);
            } finally {
                connection.disconnect();
            }

            return this;

        } catch (Exception e) {
            onFailedUpload("Unable to upload payload [" + payload.getUuid() + "]  to New Relic, will try again later. " + e);
        }

        return this;
    }

    protected String getProtocol() {
        // unencrypted http no longer supported as of 09/24/2021
        return "https://";
    }

    public int getResponseCode() {
        return responseCode;
    }

    /**
     * Converts the contents of an InputStream to a String.
     */
    @SuppressWarnings("NewApi")
    protected String readStream(InputStream stream, int maxLength) throws IOException {
        // Read InputStream using the UTF-8 charset.
        String result = Streams.slurpString(stream, StandardCharsets.UTF_8.toString());

        if (maxLength < result.length()) {
            result.substring(0, maxLength);
        }

        return result;
    }

    public boolean isSuccessfulResponse() {
        switch (responseCode) {
            case HttpsURLConnection.HTTP_OK:
            case HttpsURLConnection.HTTP_ACCEPTED:
                return true;

            // special case: 503 means payload was rejected so don't retry
            case HttpsURLConnection.HTTP_INTERNAL_ERROR:
                return true;
        }

        return false;
    }

    @Override
    public boolean equals(Object object) {
        if (object != null && object instanceof PayloadSender) {
            return this.getPayload() == ((PayloadSender) object).getPayload();
        }
        return false;
    }

    protected boolean shouldUploadOpportunistically() {
        // Upload if network (wifi) is already active, otherwise batch the payload for later
        return Agent.hasReachableNetworkConnection(null);
    }

    public boolean shouldRetry() {
        return false;
    }

    protected URI getCollectorURI() {
        return URI.create(getProtocol() + agentConfiguration.getCollectorHost());
    }

    public interface CompletionHandler {
        default void onResponse(PayloadSender payloadSender) {
        }

        default void onException(PayloadSender payloadSender, Exception e) {
        }
    }

}
