/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.harvest;

import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.harvest.type.HarvestErrorCodes;
import com.newrelic.agent.android.harvest.type.Harvestable;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.stats.TicToc;
import com.newrelic.agent.android.util.Constants;
import com.newrelic.agent.android.util.Deflator;
import com.newrelic.agent.android.util.ExceptionHelper;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

/**
 * This class establishes network connectivity from a {@link Harvester} to the collector.
 */
public class HarvestConnection implements HarvestErrorCodes {
    private final AgentLog log = AgentLogManager.getAgentLog();

    private static final String COLLECTOR_CONNECT_URI = "/mobile/v4/connect";
    private static final String COLLECTOR_DATA_URI = "/mobile/v3/data";

    private static final int TIMEOUT_IN_SECONDS = 20;
    private static final int READ_TIMEOUT_IN_SECONDS = 4;
    private static final int CONNECTION_TIMEOUT = (int) TimeUnit.MILLISECONDS.convert(TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);
    private static final int READ_TIMEOUT = (int) TimeUnit.MILLISECONDS.convert(READ_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);
    private static final int RESPONSE_BUFFER_SIZE = 8192;
    private static final int MAX_PLAINTEXT_MESSAGE_SIZE = 512;

    private String collectorHost;
    private String applicationToken;

    private long serverTimestamp;

    private ConnectInformation connectInformation;

    private boolean useSsl = true;

    public HarvestConnection() {
    }

    /**
     * Create a {@code HttpURLConnection} that can be sent to the collector.
     * <p/>
     * If the {@code message} length is greater than or equal to 512, deflate/gzip encoding is used for the content.
     * New Relic headers are also added to the post.
     *
     * @param uri The endpoint URI to the collector.
     * @return A {@code HttpURLConnection} object.
     */
    public HttpURLConnection createPost(String uri) {
        HttpURLConnection connection = null;

        try {
            final URL url = new URL(uri);
            connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECTION_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);    // ~20% of connection timeout
            connection.setDoInput(true);
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            connection.setRequestProperty(Constants.Network.APPLICATION_LICENSE_HEADER, applicationToken);
            connection.setRequestProperty(Constants.Network.CONTENT_TYPE_HEADER, Constants.Network.ContentType.JSON);

            String userAgent = System.getProperty("http.agent");
            if (userAgent != null && userAgent.length() > 0) {
                connection.setRequestProperty(Constants.Network.USER_AGENT_HEADER, userAgent);
            }

            if (serverTimestamp != 0) {
                connection.setRequestProperty(Constants.Network.CONNECT_TIME_HEADER, ((Long) serverTimestamp).toString());
            }

        } catch (Exception e) {
            StatsEngine.get().inc(MetricNames.SUPPORTABILITY_COLLECTOR + "Connection/Errors");
            log.error("Failed to create data POST: " + e.getMessage());
        }

        return connection;
    }

    /**
     * Send an {@code HttpURLConnection} to the collector and return a {@link HarvestResponse}.
     *
     * @param connection A {@code HttpURLConnection} that has been created by {@link #createPost(String)}.
     * @param message    The data to send
     * @return A {@link HarvestResponse} object representing the collector's response.
     */
    public HarvestResponse send(HttpURLConnection connection, String message) {
        final HarvestResponse harvestResponse = new HarvestResponse();
        final String contentEncoding = (message.length() <= MAX_PLAINTEXT_MESSAGE_SIZE)
                ? Constants.Network.ContentType.IDENTITY : Constants.Network.ContentType.DEFLATE;

        try {
            TicToc timer = new TicToc();
            timer.tic();

            ByteBuffer byteBuffer;
            if (Constants.Network.ContentType.DEFLATE.equals(contentEncoding.toLowerCase())) {
                byteBuffer = ByteBuffer.wrap(Deflator.deflate(message.getBytes()));
            } else {
                byteBuffer = ByteBuffer.wrap(message.getBytes());
            }

            connection.setFixedLengthStreamingMode(byteBuffer.array().length);
            connection.setRequestProperty(Constants.Network.CONTENT_ENCODING_HEADER, contentEncoding);

            try (final BufferedOutputStream out = new BufferedOutputStream(connection.getOutputStream())) {
                out.write(byteBuffer.array());
            }

            harvestResponse.setResponseTime(timer.toc());
            harvestResponse.setStatusCode(connection.getResponseCode());
            harvestResponse.setResponseBody(readResponse(connection));

            //add supportability metrics
            DeviceInformation deviceInformation = Agent.getDeviceInformation();
            String name = MetricNames.SUPPORTABILITY_SUBDESTINATION_OUTPUT_BYTES
                    .replace(MetricNames.TAG_FRAMEWORK, deviceInformation.getApplicationFramework().name())
                    .replace(MetricNames.TAG_DESTINATION, MetricNames.METRIC_DATA_USAGE_COLLECTOR);
            if (connection.getURL().getFile().contains(COLLECTOR_CONNECT_URI)) {
                name = name.replace(MetricNames.TAG_SUBDESTINATION, "connect");
            } else if (connection.getURL().getFile().contains(COLLECTOR_DATA_URI)) {
                name = name.replace(MetricNames.TAG_SUBDESTINATION, "data");
            }
            float byteReceived = harvestResponse.getResponseBody() == null ? 0 : harvestResponse.getResponseBody().length();
            StatsEngine.get().sampleMetricDataUsage(name, byteBuffer.array().length, byteReceived);
        } catch (IOException e) {
            log.error("Failed to retrieve collector response: " + e.getMessage());
            recordCollectorError(e);

        } catch (Exception e) {
            log.error("Failed to send POST to collector: " + e.getMessage());
            recordCollectorError(e);
            return null;

        } finally {
            connection.disconnect();

        }

        return harvestResponse;
    }

    /**
     * Perform a {@code connect} service call to the collector and return its {@link HarvestResponse}.
     *
     * @return The {@link HarvestResponse} from the collector {@code connect} call.
     */
    public HarvestResponse sendConnect() {
        if (connectInformation == null)
            throw new IllegalArgumentException();

        HttpURLConnection connectPost = createConnectPost();
        if (connectPost == null) {
            log.error("Failed to create connect POST");
            return null;
        }

        TicToc timer = new TicToc();
        timer.tic();

        HarvestResponse response = send(connectPost, connectInformation.toJsonString());

        StatsEngine.get().sampleTimeMs(MetricNames.SUPPORTABILITY_COLLECTOR + "Connect", timer.toc());
        return response;
    }

    /**
     * Perform a {@code data} service call to the collector and return its {@link HarvestResponse}.
     *
     * @return The {@link HarvestResponse} from the collector {@code data} call.
     */
    public HarvestResponse sendData(Harvestable harvestable) {
        if (harvestable == null)
            throw new IllegalArgumentException();

        HttpURLConnection dataPost = createDataPost();
        if (dataPost == null) {
            log.error("Failed to create data POST");
            return null;
        }
        return send(dataPost, harvestable.toJsonString());
    }

    /**
     * Create a {@code HttpURLConnection} for a {@code connect} service call.
     *
     * @return A {@code HttpURLConnection}
     */
    public HttpURLConnection createConnectPost() {
        return createPost(getCollectorConnectUri());
    }

    /**
     * Create a {@code HttpURLConnection} for a {@code data} service call.
     *
     * @return A {@code HttpURLConnection}
     */
    public HttpURLConnection createDataPost() {
        return createPost(getCollectorDataUri());
    }

    @SuppressWarnings("NewApi")
    String readStream(InputStream in) throws IOException {
        final StringBuilder sb = new StringBuilder();
        if (in != null) {
            final char[] buf = new char[RESPONSE_BUFFER_SIZE];
            try (final BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                for (; ; ) {
                    int n = reader.read(buf);
                    if (n < 0) break;
                    sb.append(buf, 0, n);
                }
            }
        }

        return sb.toString();
    }

    // Read the response from an HttpURLConnection.
    public String readResponse(HttpURLConnection response) throws IOException {
        try {
            return readStream(response.getInputStream());
        } catch (IOException e) {
            return readStream(response.getErrorStream());
        }
    }

    private void recordCollectorError(Exception e) {
        log.error("HarvestConnection: Attempting to convert network exception " + e.getClass().getName() + " to error code.");
        StatsEngine.get().inc(MetricNames.SUPPORTABILITY_COLLECTOR + "ResponseErrorCodes/" + ExceptionHelper.exceptionToErrorCode(e));
    }

    private String getCollectorUri(String resource) {
        // unencryped http no longer supported as of 09/24/2021
        String protocol = "https://";
        return protocol + collectorHost + resource;
    }

    private String getCollectorConnectUri() {
        return getCollectorUri(COLLECTOR_CONNECT_URI);
    }

    private String getCollectorDataUri() {
        return getCollectorUri(COLLECTOR_DATA_URI);
    }

    public void setServerTimestamp(long serverTimestamp) {
        log.debug("Setting server timestamp: " + serverTimestamp);
        this.serverTimestamp = serverTimestamp;
    }

    public void useSsl(boolean useSsl) {
        if (!useSsl) {
            log.error("Unencrypted http requests are no longer supported");
        }
        this.useSsl = true;
    }

    public void setApplicationToken(String applicationToken) {
        this.applicationToken = applicationToken;
    }

    public void setCollectorHost(String collectorHost) {
        this.collectorHost = collectorHost;
    }

    public void setConnectInformation(ConnectInformation connectInformation) {
        this.connectInformation = connectInformation;
    }
}
