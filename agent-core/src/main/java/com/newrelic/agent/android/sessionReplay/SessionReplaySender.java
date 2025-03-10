/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay;

import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.payload.Payload;
import com.newrelic.agent.android.payload.PayloadSender;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.util.Constants;
import com.newrelic.agent.android.util.Streams;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.HttpsURLConnection;

public class SessionReplaySender extends PayloadSender {

    protected Payload payload;

    public SessionReplaySender(byte[] bytes, AgentConfiguration agentConfiguration) {
        super(bytes, agentConfiguration);
    }

    public SessionReplaySender(Payload payload, AgentConfiguration agentConfiguration) {
        super(payload, agentConfiguration);
        this.payload = payload;
        setPayload(payload.getBytes());
    }

    @Override
    protected HttpURLConnection getConnection() throws IOException {
        final String urlString = "https://staging-bam.nr-data.net/browser/blobs?" +
                "browser_monitoring_key=NRJS-136db61998107c1947d" +
                "&type=SessionReplay" +
                "&app_id=213729589" +
                "&protocol_version=0" +
                "&timestamp=" + System.currentTimeMillis() +
                "&attributes=" +
                "entityGuid%MTA4MTY5OTR8QlJPV1NFUnxBUFBMSUNBVElPTnwyMTM3Mjk1ODk%26" +
                "harvestId%3D852c55a391bf26cf_e511ee33802cb580_2%26" +
                "replay.firstTimestamp%3D1740776671411%26" +
                "replay.lastTimestamp%3D1740776691411%26" +
                "replay.nodes%3D311%26" +
                "session.durationMs%3D32708%26" +
                "agentVersion%3D" + Agent.getDeviceInformation().getAgentVersion() + "%26" +
                "session%3D" + agentConfiguration.getSessionID() + "%26" +
                "hasMeta%3Dtrue%26" +
                "hasSnapshot%3Dtrue%26" +
                "hasError%3Dfalse%26" +
                "isFirstChunk%3Dtrue%26" +
                "invalidStylesheetsDetected%3Dfalse%26" +
                "inlinedAllStylesheets%3Dtrue%26" +
                "rrweb.version%3D%255E2.0.0-alpha.17%26" +
                "payload.type%3Dstandard%26" +
                "enduser.id%3Dywang%40newrelic.com%26" +
                "currentUrl%3Dhttps%3A%2F%2Fstaging-one.newrelic.com%2F" +
                "catalogs%2Fsoftware";
        final URL url = new URL(urlString);
        final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setDoOutput(true);
        connection.setRequestMethod("POST");
        connection.setRequestProperty(Constants.Network.CONTENT_TYPE_HEADER, Constants.Network.ContentType.OCTET_STREAM);
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
}
