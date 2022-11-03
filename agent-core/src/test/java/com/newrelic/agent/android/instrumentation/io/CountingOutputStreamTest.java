/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation.io;

import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.logging.ConsoleAgentLog;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class CountingOutputStreamTest {

    private CountingOutputStream cos;
    private ByteArrayOutputStream osSpy;

    @BeforeClass
    public static void setUpClass() {
        AgentLogManager.setAgentLog(new ConsoleAgentLog());
    }

    @Before
    public void setUp() throws Exception {
        osSpy = Mockito.spy(new ByteArrayOutputStream());
        cos = Mockito.spy(new CountingOutputStream(osSpy));
    }

    @Test
    public void closeWithBuggyCompletionHandler() {
        cos.addStreamCompleteListener(new StreamCompleteListener() {
            @Override
            public void streamError(StreamCompleteEvent e) {
                Assert.fail("Should not be called: " + e.getException().getLocalizedMessage());
            }

            @Override
            public void streamComplete(StreamCompleteEvent e) {
                throw new IllegalStateException("IllegalStateException thrown from streamComplete handler");
            }
        });

        try {
            cos.close();
        } catch (Exception e) {
            Assert.fail(e.getLocalizedMessage());
        }
    }

    @Test
    public void closeWithError() throws IOException {
        Mockito.doThrow(new IllegalStateException()).when(osSpy).close();
        cos.addStreamCompleteListener(new StreamCompleteListener() {
            @Override
            public void streamError(StreamCompleteEvent e) {
                throw new RuntimeException("RuntimeException thrown from streamComplete handler");
            }

            @Override
            public void streamComplete(StreamCompleteEvent e) {
                Assert.fail(e.getException().getLocalizedMessage());
            }
        });

        try {
            cos.close();
        } catch (Exception e) {
            Assert.fail(e.getLocalizedMessage());
        }
    }
}