/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.harvest;

import com.google.gson.stream.MalformedJsonException;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.Measurements;
import com.newrelic.agent.android.TaskQueue;
import com.newrelic.agent.android.harvest.AgentHealthException;
import com.newrelic.agent.android.harvest.Harvest;
import com.newrelic.agent.android.harvest.HarvestData;
import com.newrelic.agent.android.harvest.type.HarvestErrorCodes;
import com.newrelic.agent.android.test.stub.StubAgentImpl;
import com.newrelic.agent.android.util.ExceptionHelper;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.channels.FileLockInterruptionException;
import java.util.Map;
import java.util.NoSuchElementException;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLPeerUnverifiedException;

@RunWith(JUnit4.class)
public class ExceptionHelperTests implements HarvestErrorCodes {
    private static final String EXCEPTION_MSG = "testing";
    private static final String TEST_NAME = "test name";
    private TestHarvest testHarvest = new TestHarvest();

    @Before
    public void setUp() throws Exception {
        StubAgentImpl.install();
        testHarvest = new TestHarvest();
        TestHarvest.setInstance(testHarvest);
        TestHarvest.initialize(new AgentConfiguration());
        Measurements.initialize();
    }

    @After
    public void tearDown() throws Exception {
        TaskQueue.synchronousDequeue();
        TestHarvest.shutdown();
        Measurements.shutdown();
        StubAgentImpl.uninstall();
    }

    @Test
    public void testExceptionToErrorCodeTranslation() {
        int errorCode = NSURLErrorUnknown;

        errorCode = ExceptionHelper.exceptionToErrorCode(new UnknownHostException(EXCEPTION_MSG));
        Assert.assertEquals(errorCode, NSURLErrorDNSLookupFailed);

        errorCode = ExceptionHelper.exceptionToErrorCode(new SocketTimeoutException(EXCEPTION_MSG));
        Assert.assertEquals(errorCode, NSURLErrorTimedOut);

        errorCode = ExceptionHelper.exceptionToErrorCode(new ConnectException(EXCEPTION_MSG));
        Assert.assertEquals(errorCode, NSURLErrorCannotConnectToHost);

        errorCode = ExceptionHelper.exceptionToErrorCode(new MalformedURLException(EXCEPTION_MSG));
        Assert.assertEquals(errorCode, NSURLErrorBadURL);

        errorCode = ExceptionHelper.exceptionToErrorCode(new SSLException(EXCEPTION_MSG));
        Assert.assertEquals(errorCode, NSURLErrorSecureConnectionFailed);

        errorCode = ExceptionHelper.exceptionToErrorCode(new IOException(EXCEPTION_MSG));
        Assert.assertEquals(errorCode, NSURLErrorUnknown);

        errorCode = ExceptionHelper.exceptionToErrorCode(new RuntimeException(EXCEPTION_MSG));
        Assert.assertEquals(errorCode, NSURLErrorUnknown);

        errorCode = ExceptionHelper.exceptionToErrorCode(new EOFException(EXCEPTION_MSG));
        Assert.assertEquals(errorCode, NSURLErrorRequestBodyStreamExhausted);

        errorCode = ExceptionHelper.exceptionToErrorCode(new FileNotFoundException(EXCEPTION_MSG));
        Assert.assertEquals(errorCode, NRURLErrorFileDoesNotExist);
    }

    @Test
    public void testIOExceptionSubclassesToErrorCodeTranslation() {
        int errorCode = NSURLErrorUnknown;

        // IOException represents a large set of possible exceptions we could receive, so test
        // derived classes as well. These types gleaned from okHttp when looking at #88662316

        errorCode = ExceptionHelper.exceptionToErrorCode(new ProtocolException(EXCEPTION_MSG));
        Assert.assertEquals(errorCode, NSURLErrorUnknown);

        // SSLPeerUnverifiedException is a IOException subclass, but its also extends SSLException,
        // which we trap on its own
        errorCode = ExceptionHelper.exceptionToErrorCode(new SSLPeerUnverifiedException(EXCEPTION_MSG));
        Assert.assertEquals(errorCode, NSURLErrorSecureConnectionFailed);

        // EOFException is a IOException subclass that we trap as a NSURLErrorFileIOException
        errorCode = ExceptionHelper.exceptionToErrorCode(new EOFException(EXCEPTION_MSG));
        Assert.assertEquals(errorCode, NSURLErrorRequestBodyStreamExhausted);

    }

    @Test
    public void testRuntimeExceptionSubclassesToErrorCodeTranslation() {
        int errorCode = NSURLErrorUnknown;

        // RuntimeException represents a set of possible exceptions we could receive, so test
        // derived classes as well. These types gleaned from okHttp when looking at #88662316

        errorCode = ExceptionHelper.exceptionToErrorCode(new IllegalStateException(EXCEPTION_MSG));
        Assert.assertEquals(errorCode, NSURLErrorUnknown);

        errorCode = ExceptionHelper.exceptionToErrorCode(new IllegalArgumentException(EXCEPTION_MSG));
        Assert.assertEquals(errorCode, NSURLErrorUnknown);

        errorCode = ExceptionHelper.exceptionToErrorCode(new NullPointerException(EXCEPTION_MSG));
        Assert.assertEquals(errorCode, NSURLErrorUnknown);

        errorCode = ExceptionHelper.exceptionToErrorCode(new NoSuchElementException(EXCEPTION_MSG));
        Assert.assertEquals(errorCode, NSURLErrorUnknown);

    }

    @Test
    public void testIOExceptionLogging() {
        // test an IOException subclass
        Exception exception = new MalformedJsonException(EXCEPTION_MSG);
        ExceptionHelper.exceptionToErrorCode(exception);

        TaskQueue.synchronousDequeue();

        Map<String, AgentHealthException> agentHealthExceptions = testHarvest.getAgentHealthExceptions();
        Assert.assertEquals(1, agentHealthExceptions.size());

        final String key = testHarvest.getKey(exception);

        AgentHealthException agentHealthException = agentHealthExceptions.get(key);
        Assert.assertEquals(1, agentHealthException.getCount());
        Assert.assertNotNull(agentHealthException.getMessage());
        // The thread name changes based on which test runner is used.
        // Assert.assertEquals("Test worker", agentHealthException.getThreadName());
        Assert.assertTrue(agentHealthException.getExceptionClass().contains(MalformedJsonException.class.getSimpleName()));

        // only 1 of these additional method calls would result in a metric.
        ExceptionHelper.exceptionToErrorCode(new EOFException(EXCEPTION_MSG));
        ExceptionHelper.exceptionToErrorCode(new SSLPeerUnverifiedException(EXCEPTION_MSG));
        ExceptionHelper.exceptionToErrorCode(new FileLockInterruptionException());

        TaskQueue.synchronousDequeue();

        agentHealthExceptions = testHarvest.getAgentHealthExceptions();
        Assert.assertEquals(2, agentHealthExceptions.size());

    }

    @Test
    public void testRuntimeExceptionLogging() {
        // test a RuntimeException subclass
        Exception exception = new IllegalStateException(EXCEPTION_MSG);
        ExceptionHelper.exceptionToErrorCode(exception);

        TaskQueue.synchronousDequeue();

        Map<String, AgentHealthException> agentHealthExceptions = testHarvest.getAgentHealthExceptions();
        Assert.assertEquals(1, agentHealthExceptions.size());

        ExceptionHelper.exceptionToErrorCode(exception);
        ExceptionHelper.exceptionToErrorCode(new IllegalArgumentException(EXCEPTION_MSG));
        ExceptionHelper.exceptionToErrorCode(new NullPointerException(EXCEPTION_MSG));
        ExceptionHelper.exceptionToErrorCode(new NoSuchElementException(EXCEPTION_MSG));

        TaskQueue.synchronousDequeue();

        agentHealthExceptions = testHarvest.getAgentHealthExceptions();
        Assert.assertEquals(4, agentHealthExceptions.size());

        final String key = testHarvest.getKey(exception);
        AgentHealthException agentHealthException = agentHealthExceptions.get(key);

        // IllegalStateException was called twice
        Assert.assertEquals(2, agentHealthException.getCount());

    }


    private class TestHarvest extends Harvest {
        public TestHarvest() {
        }

        public HarvestData getHarvestData() {
            return harvestData;
        }

        public Map<String, AgentHealthException> getAgentHealthExceptions() {
            return getHarvestData().getAgentHealth().agentHealthExceptions.getAgentHealthExceptions();
        }

        public final String getKey(Exception exception) {
            return getHarvestData().getAgentHealth().agentHealthExceptions.getKey(new AgentHealthException(exception));

        }

    }


}
