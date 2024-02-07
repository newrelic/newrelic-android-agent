/*
 * Copyright (c) 2023-2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.logging;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.payload.PayloadSender;
import com.newrelic.agent.android.util.Constants;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HttpsURLConnection;

public class LogForwarder extends PayloadSender {
    private static final String LOG_API_URL = "https://log-api.newrelic.com/log/v1";

    public LogForwarder(byte[] payloadBytes, AgentConfiguration agentConfiguration) {
        super(payloadBytes, agentConfiguration);
    }

    @Override
    protected HttpURLConnection getConnection() throws IOException {
        URL url = new URL(LOG_API_URL);
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();

        connection.setRequestMethod("POST");
        connection.setRequestProperty(Constants.Network.CONTENT_TYPE_HEADER, Constants.Network.ContentType.JSON);
        connection.setRequestProperty(Constants.Network.APPLICATION_LICENSE_HEADER, agentConfiguration.getApplicationToken());
        connection.setReadTimeout((int) TimeUnit.MILLISECONDS.convert(10, TimeUnit.SECONDS));    // ~20% of connection timeout
        connection.setDoInput(true);
        connection.setDoOutput(true);
        connection.setUseCaches(false);

        connection.setDoOutput(true);
        connection.setDoInput(true);

        return connection;
    }

    @Override
    protected void onRequestResponse(HttpURLConnection connection) throws IOException {
        super.onRequestResponse(connection);
    }

    @Override
    protected void onRequestContent(String responseString) {
        super.onRequestContent(responseString);
    }

    @Override
    protected void onRequestException(Exception e) {
        super.onRequestException(e);
    }

    @Override
    protected void onFailedUpload(String errorMsg) {
        super.onFailedUpload(errorMsg);
    }
}