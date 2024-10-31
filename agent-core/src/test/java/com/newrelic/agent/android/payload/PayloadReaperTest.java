/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.payload;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.crash.CrashReporterTests;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

public class PayloadReaperTest {
    private AgentConfiguration agentConfiguration;
    private Payload payload;
    private PayloadSender payloadSender;
    private HttpURLConnection connection;
    private PayloadReaper payloadReaper;
    private PayloadSender.CompletionHandler handler;

    @Before
    public void setUp() throws Exception {
        agentConfiguration = Mockito.spy(new AgentConfiguration());
        agentConfiguration.setApplicationToken(CrashReporterTests.class.getSimpleName());
        agentConfiguration.setEnableAnalyticsEvents(true);
        agentConfiguration.setReportCrashes(true);
        agentConfiguration.setReportHandledExceptions(true);
        agentConfiguration.setUseSsl(true);

        connection = Mockito.spy((HttpURLConnection) new URL("http://www.newrelic.com").openConnection());
        Mockito.doReturn(false).when(connection).getDoOutput();
        Mockito.doReturn(false).when(connection).getDoInput();
        Mockito.doReturn(HttpsURLConnection.HTTP_OK).when(connection).getResponseCode();
        Mockito.doNothing().when(connection).connect();

        payload = new Payload("the tea's too hot".getBytes());
        payloadSender = Mockito.spy(new PayloadSender(payload, agentConfiguration) {
            @Override
            protected HttpURLConnection getConnection() {
                return connection;
            }
        });

        handler = Mockito.spy(new PayloadSender.CompletionHandler(){});
        payloadReaper = Mockito.spy(new PayloadReaper(payloadSender, handler));
    }

    @Test
    public void shouldThrowWithoutPayloadSender() {
        try {
            new PayloadReaper(null, null);
            Assert.fail("Should throw without payloadSender");
        } catch (Exception e) {
            Assert.assertTrue(e instanceof NullPointerException);
        }
    }

    @Test
    public void shouldCallSender() throws Exception {
        payloadReaper.call();
        Mockito.verify(payloadSender).call();
    }

    @Test
    public void shouldCallOnExceptionHandler() throws Exception {
        RuntimeException e = new RuntimeException("borked");
        Mockito.doThrow(e).when(payloadSender).call();
        payloadReaper.call();
        Mockito.verify(handler).onException(payloadSender, e);
    }
}