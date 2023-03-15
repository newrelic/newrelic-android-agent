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

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

public class PayloadReaperTest {
    private AgentConfiguration agentConfiguration;
    private Payload payload;
    private PayloadSender payloadSender;
    private HttpURLConnection connection;
    private PayloadReaper payloadReaper;
    private PayloadSender.CompletionHandler handler;

    @Before
    public void setUp() throws Exception {
        agentConfiguration = spy(new AgentConfiguration());
        agentConfiguration.setApplicationToken(CrashReporterTests.class.getSimpleName());
        agentConfiguration.setEnableAnalyticsEvents(true);
        agentConfiguration.setReportCrashes(true);
        agentConfiguration.setReportHandledExceptions(true);
        agentConfiguration.setUseSsl(true);

        connection = spy((HttpURLConnection) new URL("http://www.newrelic.com").openConnection());
        connection.setDoOutput(true);

        payload = new Payload("the tea's too hot".getBytes());
        payloadSender = spy(new PayloadSender(payload, agentConfiguration) {
            @Override
            protected HttpURLConnection getConnection() throws IOException {
                return connection;
            }
        });

        handler = spy(new PayloadSender.CompletionHandler() {
            @Override
            public void onResponse(PayloadSender payloadSender) {
            }

            @Override
            public void onException(PayloadSender payloadSender, Exception e) {
            }
        });

        payloadReaper = spy(new PayloadReaper(payloadSender, handler));
    }

    @Test
    public void shouldThrowWithoutPayloadSender() throws Exception {
        try {
            new PayloadReaper(null, null);
            Assert.fail("Should throw without payloadSender");
        } catch (Exception e) {
        }
    }

    @Test
    public void shouldCallSender() throws Exception {
        payloadReaper.call();
        verify(payloadSender).call();
    }

    @Test
    public void shouldCallOnExceptionHandler() throws Exception {
        RuntimeException e = new RuntimeException("borked");
        doThrow(e).when(payloadSender).call();
        payloadReaper.call();
        verify(handler).onException(payloadSender, e);
    }
}