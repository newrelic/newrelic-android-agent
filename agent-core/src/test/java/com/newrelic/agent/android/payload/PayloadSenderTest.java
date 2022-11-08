/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.payload;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.crash.CrashReporterTests;
import com.newrelic.agent.android.stats.StatsEngine;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;

import javax.net.ssl.HttpsURLConnection;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.internal.verification.VerificationModeFactory.atLeastOnce;

public class PayloadSenderTest {
    private AgentConfiguration agentConfiguration;
    private Payload payload;
    private PayloadSender payloadSender;
    private HttpURLConnection connection;

    @Before
    public void setUp() throws Exception {
        agentConfiguration = spy(new AgentConfiguration());
        agentConfiguration.setApplicationToken(CrashReporterTests.class.getSimpleName());
        agentConfiguration.setEnableAnalyticsEvents(true);
        agentConfiguration.setReportCrashes(true);
        agentConfiguration.setReportHandledExceptions(true);
        agentConfiguration.setUseSsl(false);

        payload = new Payload("the tea's too hot".getBytes());

        resetPayloadSender();
    }

    void resetPayloadSender() {
        try {
            connection = spy((HttpURLConnection) new URL("https://www.newrelic.com").openConnection());
            connection.setDoOutput(true);
            payloadSender = spy(new PayloadSender(payload, agentConfiguration) {
                @Override
                protected HttpURLConnection getConnection() throws IOException {
                    return connection;
                }
            });
            StatsEngine.get().getStatsMap().clear();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void getPayload() throws Exception {
        Assert.assertEquals(payloadSender.getPayload(), payload);
    }

    @Test
    public void setPayload() throws Exception {
        Payload newPayload = new Payload("the coffee's just right".getBytes());
        payloadSender.setPayload(newPayload.getBytes());
        Assert.assertEquals(ByteBuffer.wrap(payloadSender.getPayload().getBytes()), ByteBuffer.wrap(newPayload.getBytes()));
    }

    @Test
    public void getConnection() throws Exception {
        payloadSender = spy(new PayloadSender(payload, agentConfiguration) {
            @Override
            protected HttpURLConnection getConnection() throws IOException {
                final String urlString = getProtocol() + agentConfiguration.getHexCollectorHost() + agentConfiguration.getHexCollectorPath();
                final URL url = new URL(urlString);
                return (HttpURLConnection) spy(url.openConnection());
            }
        });

        HttpURLConnection connection = payloadSender.getConnection();
        URL url = connection.getURL();
        Assert.assertEquals(url.getProtocol(), "https");
        Assert.assertEquals(url.getHost(), agentConfiguration.getHexCollectorHost());
        Assert.assertEquals(url.getPath(), agentConfiguration.getHexCollectorPath());
    }

    @Test
    public void onRequestResponse() throws Exception {
        doReturn(HttpsURLConnection.HTTP_OK).when(connection).getResponseCode();
        payloadSender.call();
        verify(payloadSender).onRequestResponse(any(HttpURLConnection.class));

        resetPayloadSender();
        doReturn(HttpsURLConnection.HTTP_ACCEPTED).when(connection).getResponseCode();
        payloadSender.call();
        Assert.assertEquals(HttpURLConnection.HTTP_ACCEPTED, payloadSender.responseCode);
        verify(payloadSender).onRequestResponse(any(HttpURLConnection.class));

        resetPayloadSender();
        doReturn(HttpsURLConnection.HTTP_CREATED).when(connection).getResponseCode();
        payloadSender.call();
        Assert.assertEquals(HttpURLConnection.HTTP_CREATED, payloadSender.responseCode);
        verify(payloadSender).onRequestResponse(any(HttpURLConnection.class));
    }

    @Test
    public void onRequestException() throws Exception {
        doThrow(new RuntimeException("borked")).when(connection).connect();
        payloadSender.call();
        verify(payloadSender).onRequestException(any(Exception.class));
    }

    @Test
    public void onFailedUpload() throws Exception {
        doThrow(new RuntimeException("borked")).when(payloadSender).getConnection();
        payloadSender.call();
        verify(payloadSender).onFailedUpload(anyString());
    }

    @Test
    public void onRequestContent() throws Exception {
        String response = "Shenanigans are afoot";
        InputStream in = new ByteArrayInputStream(response.getBytes("UTF-8"));
        doReturn(in).when(connection).getInputStream();

        doReturn(HttpsURLConnection.HTTP_OK).when(connection).getResponseCode();
        payloadSender.call();
        verify(payloadSender).onRequestContent(response);

        Mockito.reset();
        StatsEngine.get().getStatsMap().clear();
        doReturn(HttpsURLConnection.HTTP_CREATED).when(connection).getResponseCode();
        payloadSender.call();
        verify(payloadSender).onRequestContent(response);

        Mockito.reset();
        StatsEngine.get().getStatsMap().clear();
        doReturn(HttpsURLConnection.HTTP_ACCEPTED).when(connection).getResponseCode();
        payloadSender.call();
        verify(payloadSender).onRequestContent(response);

        Mockito.reset();
        StatsEngine.get().getStatsMap().clear();
        doReturn(in).when(connection).getInputStream();
        doReturn(HttpsURLConnection.HTTP_ACCEPTED).when(connection).getResponseCode();
        payloadSender.call();
        verify(payloadSender).onRequestContent(response);
    }

    @Test
    public void call() throws Exception {
        doReturn(HttpsURLConnection.HTTP_OK).when(connection).getResponseCode();
        payloadSender.call();
        Assert.assertEquals(payloadSender.getResponseCode(), HttpsURLConnection.HTTP_OK);
        Assert.assertTrue(payloadSender.isSuccessfulResponse());

        resetPayloadSender();
        doReturn(HttpsURLConnection.HTTP_ACCEPTED).when(connection).getResponseCode();
        payloadSender.call();
        Assert.assertEquals(HttpsURLConnection.HTTP_ACCEPTED, payloadSender.getResponseCode());
        Assert.assertTrue(payloadSender.isSuccessfulResponse());

        resetPayloadSender();
        doReturn(HttpsURLConnection.HTTP_CREATED).when(connection).getResponseCode();
        payloadSender.call();
        Assert.assertEquals(HttpsURLConnection.HTTP_CREATED, payloadSender.getResponseCode());
        Assert.assertFalse(payloadSender.isSuccessfulResponse());
    }

    @Test
    public void getProtocol() throws Exception {
        agentConfiguration.setUseSsl(true);
        Assert.assertEquals(payloadSender.getProtocol(), "https://");
        agentConfiguration.setUseSsl(false);
        Assert.assertEquals(payloadSender.getProtocol(), "https://");
    }

    @Test
    public void testSuccessfulResponse() throws Exception {
        doReturn(HttpsURLConnection.HTTP_OK).when(connection).getResponseCode();
        payloadSender.call();
        Assert.assertTrue(payloadSender.isSuccessfulResponse());

        resetPayloadSender();
        doReturn(HttpsURLConnection.HTTP_ACCEPTED).when(connection).getResponseCode();
        payloadSender.call();
        Assert.assertEquals(HttpsURLConnection.HTTP_ACCEPTED, connection.getResponseCode());
        Assert.assertTrue(payloadSender.isSuccessfulResponse());

        resetPayloadSender();
        connection.disconnect();
        doReturn(HttpsURLConnection.HTTP_CREATED).when(connection).getResponseCode();
        payloadSender.call();
        Assert.assertFalse(payloadSender.isSuccessfulResponse());
    }

    @Test
    public void testHTTPInternalError() throws Exception {
        doReturn(HttpsURLConnection.HTTP_INTERNAL_ERROR).when(connection).getResponseCode();
        payloadSender.call();
        Assert.assertTrue(payloadSender.isSuccessfulResponse());
    }

    @Test
    public void testHTTPForbidden() throws Exception {
        doReturn(HttpsURLConnection.HTTP_FORBIDDEN).when(connection).getResponseCode();
        payloadSender.call();
        Assert.assertFalse(payloadSender.isSuccessfulResponse());
    }

    @Test
    public void testRequestTimeOut() throws Exception {
        doReturn(HttpsURLConnection.HTTP_CLIENT_TIMEOUT).when(connection).getResponseCode();
        payloadSender.call();
        Assert.assertFalse(payloadSender.isSuccessfulResponse());
        verify(payloadSender, atLeastOnce()).onFailedUpload(anyString());
    }

    @Test
    public void testRequestThrottled() throws Exception {
        doReturn(429).when(connection).getResponseCode();
        payloadSender.call();
        Assert.assertFalse(payloadSender.isSuccessfulResponse());
        verify(payloadSender, atLeastOnce()).onFailedUpload(anyString());
    }

    @Test
    public void testEquals() throws Exception {
        PayloadSender thisSender = new PayloadSender(payload, agentConfiguration) {
            @Override
            protected HttpURLConnection getConnection() throws IOException {
                return connection;
            }
        };

        PayloadSender thatSender = new PayloadSender(payload, agentConfiguration) {
            @Override
            protected HttpURLConnection getConnection() throws IOException {
                return connection;
            }
        };

        Assert.assertEquals("Equal payloads mean senders are equal", thisSender, thatSender);

        payload = new Payload("the coffee's ice cold".getBytes());
        PayloadSender theOtherSender = new PayloadSender(payload, agentConfiguration) {
            @Override
            protected HttpURLConnection getConnection() throws IOException {
                return connection;
            }
        };
        Assert.assertNotEquals("Different payloads mean senders are NOT equal", thisSender, theOtherSender);

    }
}