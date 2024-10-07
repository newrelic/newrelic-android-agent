/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.logging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.ApplicationFramework;
import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.stats.TicToc;
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

public class RemoteLoggerTest extends LoggingTests {

    private RemoteLogger logger;
    private LogReporter logReporter;
    private AgentConfiguration agentConfig;

    @BeforeClass
    public static void beforeClass() throws Exception {
        LoggingTests.beforeClass();

        AgentLogManager.setAgentLog(new ConsoleAgentLog());

    }

    @Before
    public void setUp() throws Exception {
        FeatureFlag.enableFeature(FeatureFlag.LogReporting);
        LogReporting.setLogLevel(LogLevel.INFO);
        LogReporter.MIN_PAYLOAD_THRESHOLD = 0;      // Disable for testing
        logReporter = Mockito.spy(LogReporter.initialize(reportsDir, new AgentConfiguration()));

        Assert.assertTrue("LogReporter should create RemoteLogger()", LogReporting.getLogger() instanceof RemoteLogger);
        logger = (RemoteLogger) Mockito.spy(LogReporting.getLogger());
        agentConfig = AgentConfiguration.getInstance();

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
        logger.flush();

        verify(logger, times(5)).log(any(LogLevel.class), anyString());
        verify(logger, times(0)).appendToWorkingLogfile(any(LogLevel.class), anyString(), any(), any());

        JsonArray jsonArray = verifyWorkingLogfile(0);
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
        logger.flush();

        verify(logger, times(5)).log(any(LogLevel.class), anyString());
        verify(logger, times(3)).appendToWorkingLogfile(any(LogLevel.class), anyString(), any(), any());

        JsonArray jsonArray = verifyWorkingLogfile(3);
        JsonObject jsonObject = jsonArray.get(0).getAsJsonObject();
        Assert.assertTrue(jsonObject.get(LogReporting.LOG_TIMESTAMP_ATTRIBUTE).getAsLong() >= tStart);
        Assert.assertFalse(jsonObject.has(LogReporting.LOG_ATTRIBUTES_ATTRIBUTE));
        Assert.assertTrue(jsonObject.has(LogReporting.LOG_SESSION_ID));
        Assert.assertEquals(jsonObject.get(LogReporting.LOG_SESSION_ID).getAsString(), AgentConfiguration.getInstance().getSessionID());
    }

    @Test
    public void logThrowable() throws Exception {
        final Throwable throwable = new RuntimeException("logThrowable: " + getRandomMsg(127));

        logger.logThrowable(LogLevel.WARN, throwable.getMessage(), throwable);
        logger.flush();

        verify(logger, times(1)).appendToWorkingLogfile(LogLevel.WARN, throwable.getMessage(), throwable, null);

        JsonArray jsonArray = verifyWorkingLogfile(1);
        JsonObject jsonObject = jsonArray.get(0).getAsJsonObject();
        Assert.assertTrue(jsonObject.get(LogReporting.LOG_TIMESTAMP_ATTRIBUTE).getAsLong() >= tStart);
        Assert.assertEquals(jsonObject.get(LogReporting.LOG_MESSAGE_ATTRIBUTE).getAsString(), throwable.getLocalizedMessage());
        Assert.assertFalse(jsonObject.has(LogReporting.LOG_ATTRIBUTES_ATTRIBUTE));
        Assert.assertTrue(jsonObject.has(LogReporting.LOG_SESSION_ID));
        Assert.assertTrue(jsonObject.has(LogReporting.LOG_INSTRUMENTATION_NAME));
        Assert.assertTrue(jsonObject.has(LogReporting.LOG_INSTRUMENTATION_PROVIDER));
        Assert.assertEquals(jsonObject.get(LogReporting.LOG_SESSION_ID).getAsString(), AgentConfiguration.getInstance().getSessionID());
    }

    @Test
    public void logAttributes() throws Exception {
        final String msg = "logAttributes: " + getRandomMsg(17);
        final HashMap<String, Object> attrs = new HashMap<>();

        attrs.put("level", "INFO");
        attrs.put("message", msg);

        logger.logAttributes(attrs);
        logger.flush();
        verify(logger, times(1)).appendToWorkingLogfile(LogLevel.INFO, msg, null, attrs);

        JsonArray jsonArray = verifyWorkingLogfile(1);
        JsonObject jsonObject = jsonArray.get(0).getAsJsonObject();
        Assert.assertTrue(jsonObject.get(LogReporting.LOG_TIMESTAMP_ATTRIBUTE).getAsLong() >= tStart);
        Assert.assertEquals(jsonObject.get(LogReporting.LOG_MESSAGE_ATTRIBUTE).getAsString(), msg);
        Assert.assertTrue(jsonObject.has(LogReporting.LOG_SESSION_ID));
        Assert.assertTrue(jsonObject.has(LogReporting.LOG_INSTRUMENTATION_NAME));
        Assert.assertTrue(jsonObject.has(LogReporting.LOG_INSTRUMENTATION_PROVIDER));
        Assert.assertEquals(jsonObject.get(LogReporting.LOG_SESSION_ID).getAsString(), AgentConfiguration.getInstance().getSessionID());
    }

    @Test
    public void logAll() throws Exception {
        final String msg = "logAll msg: " + getRandomMsg(12);
        final Throwable throwable = new RuntimeException(msg);
        final HashMap<String, Object> attrs = new HashMap<>();

        attrs.put("level", "INFO");
        attrs.put("message", msg);

        logger.logAll(throwable, attrs);
        verify(logger, times(1)).appendToWorkingLogfile(LogLevel.INFO, msg, throwable, attrs);

        JsonArray jsonArray = verifyWorkingLogfile(1);
        JsonObject jsonObject = jsonArray.get(0).getAsJsonObject();
        Assert.assertTrue(jsonObject.get(LogReporting.LOG_TIMESTAMP_ATTRIBUTE).getAsLong() >= tStart);
        Assert.assertEquals(jsonObject.get(LogReporting.LOG_MESSAGE_ATTRIBUTE).getAsString(), msg);
        Assert.assertTrue(jsonObject.has(LogReporting.LOG_SESSION_ID));
        Assert.assertTrue(jsonObject.has(LogReporting.LOG_INSTRUMENTATION_NAME));
        Assert.assertTrue(jsonObject.has(LogReporting.LOG_INSTRUMENTATION_PROVIDER));
        Assert.assertEquals(jsonObject.get(LogReporting.LOG_SESSION_ID).getAsString(), AgentConfiguration.getInstance().getSessionID());
    }

    @Test
    public void appendLog() throws Exception {
        final String msg = "appendLog: " + getRandomMsg(24);

        logger.appendToWorkingLogfile(LogLevel.INFO, msg, null, null);
        logger.flush();
        verify(logger, times(1)).appendToWorkingLogfile(LogLevel.INFO, msg, null, null);

        JsonArray jsonArray = verifyWorkingLogfile(1);
        JsonObject jsonObject = jsonArray.get(0).getAsJsonObject();
        Assert.assertTrue(jsonObject.get(LogReporting.LOG_TIMESTAMP_ATTRIBUTE).getAsLong() >= tStart);
        Assert.assertEquals(jsonObject.get(LogReporting.LOG_MESSAGE_ATTRIBUTE).getAsString(), msg);
        Assert.assertFalse(jsonObject.has(LogReporting.LOG_ATTRIBUTES_ATTRIBUTE));
        Assert.assertTrue(jsonObject.has(LogReporting.LOG_SESSION_ID));
        Assert.assertTrue(jsonObject.has(LogReporting.LOG_INSTRUMENTATION_NAME));
        Assert.assertTrue(jsonObject.has(LogReporting.LOG_INSTRUMENTATION_PROVIDER));
        Assert.assertEquals(jsonObject.get(LogReporting.LOG_SESSION_ID).getAsString(), AgentConfiguration.getInstance().getSessionID());
    }

    // @Test  FIXME flakey test
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
        logger.flush();

        JsonArray jsonArray = verifyWorkingLogfile(2);
        for (JsonElement jsonElement : jsonArray) {
            JsonObject json = jsonElement.getAsJsonObject();
            Assert.assertTrue("Log json should contain timestamp", json.has(LogReporting.LOG_TIMESTAMP_ATTRIBUTE));
            Assert.assertTrue("Log json should contain log level", json.has(LogReporting.LOG_LEVEL_ATTRIBUTE));
            Assert.assertTrue(json.has(LogReporting.LOG_SESSION_ID));
            Assert.assertTrue(json.has(LogReporting.LOG_INSTRUMENTATION_NAME));
            Assert.assertTrue(json.has(LogReporting.LOG_INSTRUMENTATION_PROVIDER));
            Assert.assertEquals(json.get(LogReporting.LOG_SESSION_ID).getAsString(), AgentConfiguration.getInstance().getSessionID());
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

        Mockito.reset(logger);
        logger.log(LogLevel.INFO, null);
        verify(logger, times(1)).appendToWorkingLogfile(LogLevel.INFO, LogReporting.INVALID_MSG, null, null);

        Mockito.reset(logger);
        logger.logAttributes(null);
        verify(logger, times(1)).appendToWorkingLogfile(any(LogLevel.class), anyString(), isNull(), anyMap());

        Mockito.reset(logger);
        logger.logThrowable(LogLevel.ERROR, "", null);
        verify(logger, times(1)).appendToWorkingLogfile(any(LogLevel.class), anyString(), any(Throwable.class), isNull());

        Mockito.reset(logger);
        logger.logAll(null, null);
        verify(logger, times(1)).appendToWorkingLogfile(any(LogLevel.class), anyString(), isNull(), anyMap());
    }

    @Test
    public void testEntityGuid() throws IOException {
        Assert.assertNotNull(AgentConfiguration.getInstance().getEntityGuid());
        Assert.assertFalse(AgentConfiguration.getInstance().getEntityGuid().isEmpty());

        final String msg = "testEntityGuid: " + getRandomMsg(33);

        logger.log(LogLevel.ERROR, msg);
        logger.flush();

        JsonArray jsonArray = verifyWorkingLogfile(1);
        JsonObject jsonObject = jsonArray.get(0).getAsJsonObject();
        Assert.assertTrue(jsonObject.get(LogReporting.LOG_TIMESTAMP_ATTRIBUTE).getAsLong() >= tStart);
        Assert.assertEquals(jsonObject.get(LogReporting.LOG_ENTITY_ATTRIBUTE).getAsString(), AgentConfiguration.getInstance().getEntityGuid());
        Assert.assertTrue(jsonObject.has(LogReporting.LOG_SESSION_ID));
        Assert.assertEquals(jsonObject.get(LogReporting.LOG_SESSION_ID).getAsString(), AgentConfiguration.getInstance().getSessionID());
    }

    /**
     * Not a test per se, but try to determine an anecdotal throughput time
     */
    @Test
    public void testLoggingThroughput() throws Exception {
        final AgentLog agentLog = AgentLogManager.getAgentLog();

        float baseLine = 0f;

        // too many of any of these will blow out the heap
        int iterations = 10;
        int msgPerIteration = 10;
        int msgSize = 128;
        int testCnt = 5;

        for (int testRun = 1; testRun <= testCnt; testRun++) {
            agentLog.setLevel(AgentLog.WARN);   // shush

            TicToc timer = new TicToc().tic();

            for (int i = 1; i <= iterations; i++) {
                for (int perIteration = 0; perIteration < msgPerIteration; perIteration++) {
                    String msg = getRandomMsg(msgSize);

                    Map<String, Object> attrs = new HashMap<>();
                    attrs.put("level", LogLevel.values()[(int) (Math.random() * LogLevel.values().length)].name());
                    attrs.put("message", msg);
                    attrs.put("name", getRandomMsg(8));
                    attrs.put("age", (double) Math.random() * 117);
                    attrs.put("fun", (Math.random() * 2) > 1);
                    attrs.put(getRandomMsg(128), getRandomMsg(1024));

                    logger.logAttributes(attrs);

                    Throwable throwable = new RuntimeException(msg);
                    logger.logAll(throwable, attrs);
                }
            }

            long tIteration = timer.toc();
            int nMsgs = (iterations * msgPerIteration);
            float tPerIteration = (float) tIteration / iterations;
            float tPerMsg = (float) tIteration / nMsgs;

            if (0 == baseLine) baseLine = tPerMsg;

            TicToc fileIOTimer = new TicToc().tic();
            logger.flush();
            logReporter.finalizeWorkingLogfile();
            File rolledLog = logReporter.rollWorkingLogfile();
            logReporter.resetWorkingLogfile();
            agentLog.warn("Run[" + testRun + "] File finalization[" + fileIOTimer.peek() + "] ms");

            logReporter.expire(0);
            logReporter.cleanup();
            agentLog.warn("Run[" + testRun + "] File cleanup[" + fileIOTimer.toc() + "] ms");

            agentLog.setLevel(AgentLog.VERBOSE);
            agentLog.info("Run[" + testRun + "] Iterations[" + iterations + "] Msgs[" + nMsgs + "]");
            agentLog.info("Run[" + testRun + "] Iteration service time[" + tPerIteration + "] ms");
            agentLog.info("Run[" + testRun + "] Service time per record[" + tPerMsg + "] ms");
            agentLog.warn("Run[" + testRun + "] Logs reported [" + rolledLog.length() + "] bytes");

            iterations *= 2;
            msgPerIteration *= 2;

            // throughput time should scale (+-50 ms)
            Assert.assertEquals(baseLine, tPerMsg, 50f);   // 50 ms drift
        }

        agentLog.info("Baseline service time per record[" + baseLine + "] ms");
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

        logger.flush();
        logReporter.finalizeWorkingLogfile();

        Set<File> files = logReporter.getCachedLogReports(LogReporter.LogReportState.ALL);

        // the working logfile may rollover, so we need to count all generated files
        Assert.assertFalse(files.isEmpty());

        JsonArray jsonArray = verifySpannedLogfiles(files, N_THREADS * N_MSGS);
        for (int i = 0; i < (N_THREADS * N_MSGS); i++) {
            JsonObject jsonObject = jsonArray.get(i).getAsJsonObject();
            Assert.assertTrue(jsonObject.get(LogReporting.LOG_TIMESTAMP_ATTRIBUTE).getAsLong() >= tStart);
            Assert.assertTrue(jsonObject.has(LogReporting.LOG_MESSAGE_ATTRIBUTE));
            Assert.assertFalse(jsonObject.has(LogReporting.LOG_ATTRIBUTES_ATTRIBUTE));
            Assert.assertTrue(jsonObject.has(LogReporting.LOG_INSTRUMENTATION_NAME));
            Assert.assertTrue(jsonObject.has(LogReporting.LOG_INSTRUMENTATION_PROVIDER));
            Assert.assertTrue(jsonObject.has(LogReporting.LOG_SESSION_ID));
            Assert.assertEquals(jsonObject.get(LogReporting.LOG_SESSION_ID).getAsString(), AgentConfiguration.getInstance().getSessionID());
        }
    }

    @Test
    public void checkLogInstrumentationInsightsForHybridApps() throws Exception {
        final String msg = "Log: ";


        agentConfig.setApplicationFramework(ApplicationFramework.Capacitor);
        agentConfig.setApplicationFrameworkVersion("1.2.3");


        logger.log(LogLevel.ERROR, msg + getRandomMsg(19));
        logger.log(LogLevel.INFO, msg + getRandomMsg(7));
        logger.log(LogLevel.WARN, msg + getRandomMsg(31));
        logger.log(LogLevel.VERBOSE, msg + getRandomMsg(11));
        logger.log(LogLevel.DEBUG, msg + getRandomMsg(23));
        logger.flush();


        verify(logger, times(5)).log(any(LogLevel.class), anyString());
        verify(logger, times(3)).appendToWorkingLogfile(any(LogLevel.class), anyString(), any(), any());

        JsonArray jsonArray = verifyWorkingLogfile(3);
        JsonObject jsonObject = jsonArray.get(0).getAsJsonObject();
        Assert.assertTrue(jsonObject.get(LogReporting.LOG_TIMESTAMP_ATTRIBUTE).getAsLong() >= tStart);
        Assert.assertFalse(jsonObject.has(LogReporting.LOG_ATTRIBUTES_ATTRIBUTE));
        Assert.assertTrue(jsonObject.has(LogReporting.LOG_SESSION_ID));
        Assert.assertTrue(jsonObject.has(LogReporting.LOG_INSTRUMENTATION_NAME));
        Assert.assertTrue(jsonObject.has(LogReporting.LOG_INSTRUMENTATION_PROVIDER));
        Assert.assertTrue(jsonObject.has(LogReporting.LOG_INSTRUMENTATION_VERSION));
        Assert.assertEquals(jsonObject.get(LogReporting.LOG_SESSION_ID).getAsString(), AgentConfiguration.getInstance().getSessionID());
        Assert.assertEquals(jsonObject.get(LogReporting.LOG_INSTRUMENTATION_NAME).getAsString(), AgentConfiguration.getInstance().getApplicationFramework().toString());
        Assert.assertEquals(jsonObject.get(LogReporting.LOG_INSTRUMENTATION_VERSION).getAsString(), AgentConfiguration.getInstance().getApplicationFrameworkVersion());
        Assert.assertEquals(jsonObject.get(LogReporting.LOG_INSTRUMENTATION_PROVIDER).getAsString(), LogReporting.LOG_INSTRUMENTATION_PROVIDER_ATTRIBUTE);
        Assert.assertEquals(jsonObject.get(LogReporting.LOG_INSTRUMENTATION_COLLECTOR_NAME).getAsString(), LogReporting.LOG_INSTRUMENTATION_ANDROID_NAME);

    }

    @Test
    public void checkLogInstrumentationInsightsForNativeApps() throws Exception {
        final String msg = "Log: ";

        agentConfig.setApplicationFrameworkVersion("7.6.1");


        logger.log(LogLevel.ERROR, msg + getRandomMsg(19));
        logger.log(LogLevel.INFO, msg + getRandomMsg(7));
        logger.log(LogLevel.WARN, msg + getRandomMsg(31));
        logger.log(LogLevel.VERBOSE, msg + getRandomMsg(11));
        logger.log(LogLevel.DEBUG, msg + getRandomMsg(23));
        logger.flush();

        verify(logger, times(5)).log(any(LogLevel.class), anyString());
        verify(logger, times(3)).appendToWorkingLogfile(any(LogLevel.class), anyString(), any(), any());

        JsonArray jsonArray = verifyWorkingLogfile(3);
        JsonObject jsonObject = jsonArray.get(0).getAsJsonObject();
        Assert.assertTrue(jsonObject.get(LogReporting.LOG_TIMESTAMP_ATTRIBUTE).getAsLong() >= tStart);
        Assert.assertFalse(jsonObject.has(LogReporting.LOG_ATTRIBUTES_ATTRIBUTE));
        Assert.assertTrue(jsonObject.has(LogReporting.LOG_SESSION_ID));
        Assert.assertTrue(jsonObject.has(LogReporting.LOG_INSTRUMENTATION_NAME));
        Assert.assertTrue(jsonObject.has(LogReporting.LOG_INSTRUMENTATION_PROVIDER));
        Assert.assertTrue(jsonObject.has(LogReporting.LOG_INSTRUMENTATION_VERSION));
        Assert.assertEquals(jsonObject.get(LogReporting.LOG_SESSION_ID).getAsString(), AgentConfiguration.getInstance().getSessionID());
        Assert.assertEquals(jsonObject.get(LogReporting.LOG_INSTRUMENTATION_NAME).getAsString(), AgentConfiguration.getInstance().getApplicationFramework().toString());
        Assert.assertEquals(jsonObject.get(LogReporting.LOG_INSTRUMENTATION_VERSION).getAsString(), AgentConfiguration.getInstance().getApplicationFrameworkVersion());
        Assert.assertEquals(jsonObject.get(LogReporting.LOG_INSTRUMENTATION_PROVIDER).getAsString(), LogReporting.LOG_INSTRUMENTATION_PROVIDER_ATTRIBUTE);
        Assert.assertEquals(jsonObject.get(LogReporting.LOG_INSTRUMENTATION_COLLECTOR_NAME).getAsString(), LogReporting.LOG_INSTRUMENTATION_ANDROID_NAME);

    }

    @Test
    public void testExecutor() {
        Assert.assertFalse(logger.executor.isShutdown());
        logger.shutdown();
        Assert.assertTrue(logger.executor.isShutdown());
    }

}