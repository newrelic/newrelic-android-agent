/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.logging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.util.Streams;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class RemoteLoggerTest extends LoggingTests {

    private RemoteLogger logger;
    private LogReporter logReporter;

    @BeforeClass
    public static void beforeClass() throws Exception {
        LoggingTests.beforeClass();

        AgentLogManager.setAgentLog(new ConsoleAgentLog());
    }

    @Before
    public void setUp() throws Exception {
        FeatureFlag.enableFeature(FeatureFlag.LogReporting);
        LogReporting.setLogLevel(LogLevel.INFO);
        logReporter = Mockito.spy(LogReporter.initialize(reportsDir, new AgentConfiguration()));

        Assert.assertTrue("LogReporter should create RemoteLogger()", LogReporting.getLogger() instanceof RemoteLogger);
        logger = (RemoteLogger) Mockito.spy(LogReporting.getLogger());

        logReporter.getWorkingLogfile().createNewFile();
    }

    @After
    public void tearDown() throws Exception {
        logReporter.shutdown();
        FeatureFlag.disableFeature(FeatureFlag.LogReporting);
        Streams.list(LogReporter.logDataStore).forEach(file -> file.delete());
    }

    @Test
    public void logNone() throws Exception {
        final String msg = "Log: ";

        LogReporting.setLogLevel(LogLevel.NONE);
        logger.log(LogLevel.ERROR, msg + getRandomMsg(19));
        logger.log(LogLevel.INFO, msg + getRandomMsg(7));
        logger.log(LogLevel.WARN, msg + getRandomMsg(31));
        logger.log(LogLevel.VERBOSE, msg + getRandomMsg(11));
        logger.log(LogLevel.DEBUG, msg + getRandomMsg(23));
        logger.flushPendingRequests();

        verify(logger, times(5)).log(any(LogLevel.class), anyString());
        verify(logger, times(0)).appendToWorkingLogFile(any(LogLevel.class), anyString(), any(), any());

        JsonArray jsonArray = verifyWorkingLogFile(0);
        Assert.assertEquals(0, jsonArray.size());
    }

    @Test
    public void log() throws Exception {
        final String msg = "Log: ";

        logger.log(LogLevel.ERROR, msg + getRandomMsg(19));
        logger.log(LogLevel.INFO, msg + getRandomMsg(7));
        logger.log(LogLevel.WARN, msg + getRandomMsg(31));
        logger.log(LogLevel.VERBOSE, msg + getRandomMsg(11));
        logger.log(LogLevel.DEBUG, msg + getRandomMsg(23));
        logger.flushPendingRequests();

        verify(logger, times(5)).log(any(LogLevel.class), anyString());
        verify(logger, times(3)).appendToWorkingLogFile(any(LogLevel.class), anyString(), any(), any());

        JsonArray jsonArray = verifyWorkingLogFile(3);
        JsonObject jsonObject = jsonArray.get(0).getAsJsonObject();
        Assert.assertTrue(jsonObject.get(LogReporting.LOG_TIMESTAMP_ATTRIBUTE).getAsLong() >= tStart);
        Assert.assertFalse(jsonObject.has(LogReporting.LOG_ATTRIBUTES_ATTRIBUTE));
    }

    @Test
    public void logThrowable() throws Exception {
        final Throwable throwable = new RuntimeException("logThrowable: " + getRandomMsg(127));

        logger.logThrowable(LogLevel.WARN, throwable.getMessage(), throwable);
        logger.flushPendingRequests();

        verify(logger, times(1)).appendToWorkingLogFile(LogLevel.WARN, throwable.getMessage(), throwable, null);

        JsonArray jsonArray = verifyWorkingLogFile(1);
        JsonObject jsonObject = jsonArray.get(0).getAsJsonObject();
        Assert.assertTrue(jsonObject.get(LogReporting.LOG_TIMESTAMP_ATTRIBUTE).getAsLong() >= tStart);
        Assert.assertEquals(jsonObject.get(LogReporting.LOG_MESSAGE_ATTRIBUTE).getAsString(), throwable.getLocalizedMessage());
        Assert.assertFalse(jsonObject.has(LogReporting.LOG_ATTRIBUTES_ATTRIBUTE));
    }

    @Test
    public void logAttributes() throws Exception {
        final String msg = "logAttributes: " + getRandomMsg(17);
        final HashMap<String, Object> attrs = new HashMap<>();

        attrs.put("level", "INFO");
        attrs.put("message", msg);

        logger.logAttributes(attrs);
        logger.flushPendingRequests();
        verify(logger, times(1)).appendToWorkingLogFile(LogLevel.INFO, msg, null, attrs);

        JsonArray jsonArray = verifyWorkingLogFile(1);
        JsonObject jsonObject = jsonArray.get(0).getAsJsonObject();
        Assert.assertTrue(jsonObject.get(LogReporting.LOG_TIMESTAMP_ATTRIBUTE).getAsLong() >= tStart);
        Assert.assertEquals(jsonObject.get(LogReporting.LOG_MESSAGE_ATTRIBUTE).getAsString(), msg);
    }

    @Test
    public void logAll() throws Exception {
        final String msg = "logAll msg: " + getRandomMsg(12);
        final Throwable throwable = new RuntimeException(msg);
        final HashMap<String, Object> attrs = new HashMap<>();

        attrs.put("level", "INFO");
        attrs.put("message", msg);

        logger.logAll(throwable, attrs);
        verify(logger, times(1)).appendToWorkingLogFile(LogLevel.INFO, msg, throwable, attrs);

        JsonArray jsonArray = verifyWorkingLogFile(1);
        JsonObject jsonObject = jsonArray.get(0).getAsJsonObject();
        Assert.assertTrue(jsonObject.get(LogReporting.LOG_TIMESTAMP_ATTRIBUTE).getAsLong() >= tStart);
        Assert.assertEquals(jsonObject.get(LogReporting.LOG_MESSAGE_ATTRIBUTE).getAsString(), msg);
    }

    @Test
    public void appendLog() throws Exception {
        final String msg = "appendLog: " + getRandomMsg(24);

        logger.appendToWorkingLogFile(LogLevel.INFO, msg, null, null);
        logger.flushPendingRequests();
        verify(logger, times(1)).appendToWorkingLogFile(LogLevel.INFO, msg, null, null);

        JsonArray jsonArray = verifyWorkingLogFile(1);
        JsonObject jsonObject = jsonArray.get(0).getAsJsonObject();
        Assert.assertTrue(jsonObject.get(LogReporting.LOG_TIMESTAMP_ATTRIBUTE).getAsLong() >= tStart);
        Assert.assertEquals(jsonObject.get(LogReporting.LOG_MESSAGE_ATTRIBUTE).getAsString(), msg);
        Assert.assertFalse(jsonObject.has(LogReporting.LOG_ATTRIBUTES_ATTRIBUTE));
    }

    // @Test FIXME Flakey failure on GHA jobs
    public void testPayloadFormat() throws IOException {
        // https://docs.newrelic.com/docs/logs/log-api/introduction-log-api/#payload-format

        final String msg = "{\"service-name\": \"login-service\", \"user\": {\"id\": 123, \"name\": \"alice\"}}";
        logger.log(LogLevel.INFO, msg);

        // Gson does not serialize anonymous nested classes
        final Map<String, Object> attrs = new HashMap<>();
        final Map<String, Object> userAttributes = new HashMap<>();

        attrs.put("level", "INFO");
        attrs.put("service-name", "login-service");
        attrs.put("action", "login");
        attrs.put("user", userAttributes);

        userAttributes.put("id", 123);
        userAttributes.put("name", "alice");
        userAttributes.put("login-result", false);

        logger.logAttributes(attrs);
        logger.flushPendingRequests();

        JsonArray jsonArray = verifyWorkingLogFile(2);
        for (JsonElement jsonElement : jsonArray) {
            JsonObject json = jsonElement.getAsJsonObject();
            Assert.assertTrue("Log json should contain timestamp", json.has(LogReporting.LOG_TIMESTAMP_ATTRIBUTE));
            Assert.assertTrue("Log json should contain log level", json.has(LogReporting.LOG_LEVEL_ATTRIBUTE));
        }

        // Element 1:
        JsonObject elem1 = jsonArray.get(0).getAsJsonObject();
        Assert.assertTrue(elem1.has(LogReporting.LOG_MESSAGE_ATTRIBUTE));
        Assert.assertFalse(elem1.has(LogReporting.LOG_ATTRIBUTES_ATTRIBUTE));

        // Element 2:
        JsonObject elem2 = jsonArray.get(1).getAsJsonObject();
        Assert.assertFalse(elem2.has(LogReporting.LOG_MESSAGE_ATTRIBUTE));
        Assert.assertTrue(elem2.has(LogReporting.LOG_ATTRIBUTES_ATTRIBUTE));
        JsonObject attributes = elem2.get(LogReporting.LOG_ATTRIBUTES_ATTRIBUTE).getAsJsonObject();
        Assert.assertTrue(attributes.has("action"));
        Assert.assertTrue(attributes.has("user"));
        Assert.assertNotNull(attributes.get("user").getAsJsonObject());
        JsonObject user = attributes.get("user").getAsJsonObject();
        Assert.assertEquals("alice", user.get("name").getAsString());
    }

    @Test
    public void testLogValidation() {
        /*
         * Restrictions on logs sent to the Log API:
         *
         * Payload total size: 1MB(10^6 bytes) maximum per POS
         * The payload must be encoded as UTF-8.
         * Number of attributes per event: 255 maximum.
         * Length of attribute name: 255 characters.
         * Length of attribute value: 4,094 characters are stored in NRDB as a Log event field
         */

        // TODO
    }

    @Test
    public void testEntityGuid() {
        // TODO
    }

    @Test
    public void testLoggingThroughput() {
        // TODO
    }

    @Test
    public void testConcurrentLoad() throws Exception {
        int N_THREADS = RemoteLogger.POOL_SIZE * RemoteLogger.POOL_SIZE;
        int N_MSGS = 1000;
        ArrayList<Thread> threadArray = new ArrayList<>() {{
            for (int t = 0; t < N_THREADS; t++) {
                add(new Thread(() -> {
                    Thread thread = Thread.currentThread();
                    for (int i = 0; i < N_MSGS; i++) {
                        logger.log(LogLevel.INFO, "Called from " + thread.getName() + ": " + RemoteLoggerTest.getRandomMsg((int) (Math.random() * 256)));
                        Thread.yield();
                    }
                }));
            }
        }};

        for (Thread thread : threadArray) {
            thread.start();
        }
        for (Thread thread : threadArray) {
            thread.join();
        }

        logger.flushPendingRequests();
        logReporter.finalizeWorkingLogFile();

        Set<File> files = logReporter.getCachedLogReports(LogReporter.LogReportState.ALL);

        // the working logfile may rollover, so we need to count all generated files
        Assert.assertFalse(files.isEmpty());

        JsonArray jsonArray = verifySpannedLogFiles(files, N_THREADS * N_MSGS);
        for (int i = 0; i < (N_THREADS * N_MSGS); i++) {
            JsonObject jsonObject = jsonArray.get(i).getAsJsonObject();
            Assert.assertTrue(jsonObject.get(LogReporting.LOG_TIMESTAMP_ATTRIBUTE).getAsLong() >= tStart);
            Assert.assertTrue(jsonObject.has(LogReporting.LOG_MESSAGE_ATTRIBUTE));
            Assert.assertFalse(jsonObject.has(LogReporting.LOG_ATTRIBUTES_ATTRIBUTE));
        }
    }

    @Test
    public void testExecutor() {
        Assert.assertFalse(logger.executor.isShutdown());
        logger.shutdown();
        Assert.assertTrue(logger.executor.isShutdown());
    }

}