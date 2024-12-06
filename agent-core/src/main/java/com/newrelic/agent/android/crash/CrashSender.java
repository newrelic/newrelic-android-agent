/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.crash;

import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.payload.PayloadController;
import com.newrelic.agent.android.payload.PayloadSender;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.util.Constants;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

public class CrashSender extends PayloadSender {
    public static final int CRASH_COLLECTOR_TIMEOUT = PayloadController.PAYLOAD_COLLECTOR_TIMEOUT;
    private static final String CRASH_COLLECTOR_PATH = "/mobile_crash";

    private final Crash crash;

    public CrashSender(Crash crash, AgentConfiguration agentConfiguration) {
        super(crash.toJsonString().getBytes(), agentConfiguration);
        this.crash = crash;
    }

    @Override
    protected HttpURLConnection getConnection() throws IOException {
        final HttpURLConnection connection = (HttpURLConnection) getCollectorURI().toURL().openConnection();

        connection.setDoOutput(true);
        connection.setRequestProperty(Constants.Network.CONTENT_TYPE_HEADER, Constants.Network.ContentType.JSON);
        connection.setRequestProperty(agentConfiguration.getAppTokenHeader(), agentConfiguration.getApplicationToken());
        connection.setRequestProperty(agentConfiguration.getDeviceOsNameHeader(), Agent.getDeviceInformation().getOsName());
        connection.setRequestProperty(agentConfiguration.getAppVersionHeader(), Agent.getApplicationInformation().getAppVersion());
        connection.setConnectTimeout(CRASH_COLLECTOR_TIMEOUT);
        connection.setReadTimeout(CRASH_COLLECTOR_TIMEOUT);

        return connection;
    }

    @Override
    public PayloadSender call() {
        setPayload(crash.toJsonString().getBytes());

        // Increment the upload counter
        crash.incrementUploadCount();
        agentConfiguration.getCrashStore().store(crash);

        try {
            return super.call();

        } catch (Exception e) {
            onFailedUpload("Unable to report crash to New Relic, will try again later. " + e);
        }

        return this;
    }

    @Override
    protected void onRequestResponse(HttpURLConnection connection) throws IOException {
        int responseCode = connection.getResponseCode();

        switch (responseCode) {
            case HttpsURLConnection.HTTP_OK:
            case HttpsURLConnection.HTTP_ACCEPTED:
                StatsEngine.get().sampleTimeMs(MetricNames.SUPPORTABILITY_CRASH_UPLOAD_TIME, timer.peek());
                log.info("CrashSender: Crash " + crash.getUuid().toString() + " successfully submitted.");
                break;

            case HttpURLConnection.HTTP_CLIENT_TIMEOUT:
                StatsEngine.get().inc(MetricNames.SUPPORTABILITY_CRASH_UPLOAD_TIMEOUT);
                onFailedUpload("The request to submit the payload [" + payload.getUuid() + "] has timed out - (will try again later) - Response code [" + responseCode + "]");
                break;

            case 429: // Not defined by HttpURLConnection
                StatsEngine.get().inc(MetricNames.SUPPORTABILITY_CRASH_UPLOAD_THROTTLED);
                onFailedUpload("The request to submit the payload [" + payload.getUuid() + "] was has timed out - (will try again later) - Response code [" + responseCode + "]");
                break;

            case HttpsURLConnection.HTTP_INTERNAL_ERROR:
                StatsEngine.get().inc(MetricNames.SUPPORTABILITY_CRASH_REMOVED_REJECTED);
                onFailedUpload("The crash was rejected and will be deleted - Response code " + connection.getResponseCode());
                break;

            default:
                onFailedUpload("Something went wrong while submitting a crash (will try again later) - Response code " + connection.getResponseCode());
                break;
        }

        log.debug("CrashSender: Crash collection took " + timer.toc() + "ms");
    }

    @Override
    protected void onFailedUpload(String errorMsg) {
        log.error("CrashSender: " + errorMsg);
        StatsEngine.get().inc(MetricNames.SUPPORTABILITY_CRASH_FAILED_UPLOAD);
    }

    @Override
    protected void onRequestException(Exception e) {
        log.error("CrashSender: Crash upload failed: " + e);
    }

    @Override
    protected boolean shouldUploadOpportunistically() {
        // send the crash report immediately if there is net connectivity
        return Agent.hasReachableNetworkConnection(null);
    }

    @Override
    protected URI getCollectorURI() {
        return URI.create(getProtocol() + agentConfiguration.getCrashCollectorHost() + CRASH_COLLECTOR_PATH);
    }

}
