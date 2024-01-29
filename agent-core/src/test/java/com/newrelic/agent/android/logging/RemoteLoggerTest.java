/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.logging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atMostOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.FeatureFlag;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RemoteLoggerTest {

    private static Gson gson = new GsonBuilder()
            .setLenient()
            .enableComplexMapKeySerialization()
            .create();

    private static File reportsDir;

    private RemoteLogger logger;
    private long tStart;

    @BeforeClass
    public static void beforeClass() throws Exception {
        LogReporting.setEntityGuid(UUID.randomUUID().toString());
        AgentLogManager.setAgentLog(new ConsoleAgentLog());
        reportsDir = Files.createTempDirectory("LogReporting-").toFile();
        reportsDir.mkdirs();
    }

    @Before
    public void setUp() throws Exception {
        LogReporter.initialize(reportsDir, new AgentConfiguration());
        LogReporting.setLogLevel(LogLevel.INFO);
        LogReporter.getWorkingLogfile().delete();
        logger = Mockito.spy(new RemoteLogger());
        tStart = System.currentTimeMillis();
        FeatureFlag.enableFeature(FeatureFlag.LogReporting);
    }

    @After
    public void tearDown() throws Exception {
        FeatureFlag.disableFeature(FeatureFlag.LogReporting);
    }

    @AfterClass
    public static void afterClass() throws Exception {
        Path path = Paths.get(LogReporter.logDataStore.getAbsolutePath());
        try (Stream<Path> stream = Files.list(Paths.get(path.toUri()))) {
            String logFileMask = String.format(Locale.getDefault(),
                    LogReporter.LOG_FILE_MASK, ".*\\", LogReporter.LogReportState.CLOSED.value);

            Set<File> files = stream
                    .filter(file -> !Files.isDirectory(file) && file.getFileName().toString().matches(logFileMask))
                    .map(Path::toFile)
                    .collect(Collectors.toSet());

            files.stream().forEach(s -> s.delete());
        }
    }

    @Test
    public void log() throws Exception {
        final String msg = "Log msg";

        logger.log(LogLevel.INFO, msg);
        logger.log(LogLevel.WARN, msg);
        logger.log(LogLevel.ERROR, msg);
        logger.log(LogLevel.VERBOSE, msg);
        logger.log(LogLevel.DEBUG, msg);

        verify(logger, times(5)).log(any(LogLevel.class), anyString());
        verify(logger, times(3)).appendLog(any(LogLevel.class), anyString(), any(), any());

        JsonArray jsonArray = verifyWorkingLogFile(3);
        JsonObject jsonObject = jsonArray.get(0).getAsJsonObject();
        Assert.assertTrue(jsonObject.get(LogReporting.LOG_TIMESTAMP_ATTRIBUTE).getAsLong() >= tStart);
        Assert.assertEquals(jsonObject.get(LogReporting.LOG_MESSAGE_ATTRIBUTE).getAsString(), msg);
        Assert.assertFalse(jsonObject.has(LogReporting.LOG_ATTRIBUTES_ATTRIBUTE));
    }

    @Test
    public void logThrowable() throws Exception {
        final Throwable throwable = new RuntimeException("logThrowable");

        logger.logThrowable(LogLevel.WARN, throwable.getMessage(), throwable);
        verify(logger, times(1)).appendLog(LogLevel.WARN, throwable.getMessage(), throwable, null);

        JsonArray jsonArray = verifyWorkingLogFile(1);
        JsonObject jsonObject = jsonArray.get(0).getAsJsonObject();
        Assert.assertTrue(jsonObject.get(LogReporting.LOG_TIMESTAMP_ATTRIBUTE).getAsLong() >= tStart);
        Assert.assertEquals(jsonObject.get(LogReporting.LOG_MESSAGE_ATTRIBUTE).getAsString(), throwable.getLocalizedMessage());
        Assert.assertFalse(jsonObject.has(LogReporting.LOG_ATTRIBUTES_ATTRIBUTE));
    }

    @Test
    public void logAttributes() throws Exception {
        final String msg = "logAttributes message";
        final HashMap<String, Object> attrs = new HashMap<String, Object>() {{
            put("level", "INFO");
            put("message", msg);
        }};

        logger.logAttributes(attrs);
        logger.flushPendingRequests();

        verify(logger, times(1)).appendLog(LogLevel.INFO, msg, null, attrs);
        JsonArray jsonArray = verifyWorkingLogFile(1);
        JsonObject jsonObject = jsonArray.get(0).getAsJsonObject();
        Assert.assertTrue(jsonObject.get(LogReporting.LOG_TIMESTAMP_ATTRIBUTE).getAsLong() >= tStart);
        Assert.assertEquals(jsonObject.get(LogReporting.LOG_MESSAGE_ATTRIBUTE).getAsString(), msg);
    }

    @Test
    public void logAll() throws Exception {
        final String msg = "logAll msg";
        final Throwable throwable = new RuntimeException("logAll");
        final HashMap<String, Object> attrs = new HashMap<String, Object>() {{
            put("level", "INFO");
            put("message", msg);
        }};

        logger.logAll(throwable, attrs);
        verify(logger, times(1)).appendLog(LogLevel.INFO, msg, throwable, attrs);

        JsonArray jsonArray = verifyWorkingLogFile(1);
        JsonObject jsonObject = jsonArray.get(0).getAsJsonObject();
        Assert.assertTrue(jsonObject.get(LogReporting.LOG_TIMESTAMP_ATTRIBUTE).getAsLong() >= tStart);
        Assert.assertEquals(jsonObject.get(LogReporting.LOG_MESSAGE_ATTRIBUTE).getAsString(), msg);
    }

    @Test
    public void appendLog() throws Exception {
        final String msg = "appendLog msg";

        logger.appendLog(LogLevel.INFO, msg, null, null);
        verify(logger, times(1)).appendLog(LogLevel.INFO, msg, null, null);

        JsonArray jsonArray = verifyWorkingLogFile(1);
        JsonObject jsonObject = jsonArray.get(0).getAsJsonObject();
        Assert.assertTrue(jsonObject.get(LogReporting.LOG_TIMESTAMP_ATTRIBUTE).getAsLong() >= tStart);
        Assert.assertEquals(jsonObject.get(LogReporting.LOG_MESSAGE_ATTRIBUTE).getAsString(), msg);
        Assert.assertFalse(jsonObject.has(LogReporting.LOG_ATTRIBUTES_ATTRIBUTE));
    }

    @Test
    public void rollWorkingLogFile() throws IOException {
        Assert.assertNotNull(logger.workingLogFile);
        Assert.assertTrue(logger.workingLogFile.exists());

        Assert.assertNotNull(logger.workingLogFileWriter.get());
        BufferedWriter writer = logger.workingLogFileWriter.get();

        File finalizedLogFile = logger.rollWorkingLogFile();
        Assert.assertTrue(finalizedLogFile.exists());
        Assert.assertTrue(logger.workingLogFile.exists());
        Assert.assertTrue("Should create a new log data file", finalizedLogFile.length() > 0);
        Assert.assertTrue("Should create a new working file", logger.workingLogFile.length() == 0);
        Assert.assertNotEquals("Should create a new buffered writer", writer, logger.workingLogFileWriter.get());
    }

    @Test
    public void testConcurrentLoad() throws Exception {
        ArrayList<Thread> threadArray = new ArrayList<Thread>() {
            {
                for (int t = 0; t < 10; t++) {
                    add(new Thread(() -> {
                        Thread thread = Thread.currentThread();
                        for (int i = 0; i < 100; i++) {
                            logger.log(LogLevel.INFO, "Called from thread " + thread.getName());
                            thread.yield();
                        }
                    }));
                }
            }
        };

        for (Thread thread : threadArray) {
            thread.start();
        }
        for (Thread thread : threadArray) {
            thread.join();
        }

        logger.flushPendingRequests();

        JsonArray jsonArray = verifyWorkingLogFile(10 * 100);
        for (int i = 0; i < 10 * 100; i++) {
            JsonObject jsonObject = jsonArray.get(i).getAsJsonObject();
            Assert.assertTrue(jsonObject.get(LogReporting.LOG_TIMESTAMP_ATTRIBUTE).getAsLong() >= tStart);
            Assert.assertTrue(jsonObject.has(LogReporting.LOG_MESSAGE_ATTRIBUTE));
            Assert.assertFalse(jsonObject.has(LogReporting.LOG_ATTRIBUTES_ATTRIBUTE));
        }
    }

    @Test
    public void testExecutor() throws Exception {
        final String msg = "Log msg";

        Assert.assertFalse(logger.executor.isShutdown());
        logger.shutdown();
        Assert.assertTrue(logger.executor.isShutdown());
    }

    @Test
    public void testLoggingThroughput() {
        // TODO
    }

    @Test
    public void testLogIngestLimits() {
        /**
         * Restrictions on logs sent to the Log API:
         *
         * Payload total size: 1MB(10^6 bytes) maximum per POS
         * The payload must be encoded as UTF-8.
         * Number of attributes per event: 255 maximum.
         * Length of attribute name: 255 characters.
         * Length of attribute value: 4,094 characters are stored in NRDB as a Log event field
         **/

        // TODO
    }

    @Test
    public void testPayloadBudget() throws Exception {
        logger.uploadBudget = 1 + 1024;
        while (logger.getBytesRemaining() > 0) {
            logger.log(LogLevel.INFO, String.format("Log message #", logger.uploadBudget, '.'));
        }
        logger.flushPendingRequests();

        verify(logger, atMostOnce()).rollWorkingLogFile();
    }

    @Test
    public void testHarvestLifecycle() {
        Assert.assertNotNull(logger.workingLogFile);
        Assert.assertTrue(logger.workingLogFile.exists());

        logger.log(LogLevel.INFO, "Before onHarvestStart()");
        logger.onHarvestStart();
        Assert.assertNotNull(logger.workingLogFileWriter.get());

        BufferedWriter writer = logger.workingLogFileWriter.get();
        logger.log(LogLevel.INFO, "After onHarvestStart()");

        logger.log(LogLevel.INFO, "Before onHarvest()");
        logger.onHarvest();
        Assert.assertTrue(logger.workingLogFile.exists());
        Assert.assertTrue("Should create a new working file", logger.workingLogFile.length() == 0);
        Assert.assertNotEquals("Should create a new buffered writer", writer, logger.workingLogFileWriter.get());
        logger.log(LogLevel.INFO, "After onHarvest()");
        logger.log(LogLevel.INFO, "Before onHarvestStop()");

        logger.onHarvestStop();
        logger.flushPendingRequests();
        Assert.assertTrue(logger.workingLogFile.exists());
        Assert.assertTrue("Should create a new working file", logger.workingLogFile.length() == 3); // '[\n']
        Assert.assertNull("Should not create a new buffered writer", logger.workingLogFileWriter.get());
        Assert.assertTrue("Should shutdown executor", logger.executor.isShutdown());

        // logging after harvestStop()?
        logger.log(LogLevel.INFO, "After onHarvestStop()");
        logger.finalizeWorkingLogFile();
    }

    @Test
    public void testEntityGuid() {
        // TODO
    }

    @Test
    public void testPayloadFormat() throws IOException, InterruptedException {
        // https://docs.newrelic.com/docs/logs/log-api/introduction-log-api/#payload-format

        final String msg = "{\"service-name\": \"login-service\", \"user\": {\"id\": 123, \"name\": \"alice\"}}";
        logger.log(LogLevel.INFO, msg);

        final HashMap<String, Object> attrs = new HashMap<String, Object>() {{
            put("level", "INFO");
            put("action", "login");
            put("user", new HashMap<String, Object>() {{
                put("id", 123);
                put("name", "alice");
            }});
        }};
        logger.logAttributes(attrs);

        JsonArray jsonArray = verifyWorkingLogFile(2);
        for (Iterator<JsonElement> it = jsonArray.iterator(); it.hasNext(); ) {
            JsonObject json = it.next().getAsJsonObject();
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
        Assert.assertFalse(elem2.has(LogReporting.LOG_ATTRIBUTES_ATTRIBUTE));
    }

    @Test
    public void testIncompleteWorkingfile() throws InterruptedException, IOException {
        final String msg = "Log msg";

        logger.log(LogLevel.INFO, msg);
        logger.log(LogLevel.INFO, msg);
        logger.log(LogLevel.INFO, msg);
        logger.flushPendingRequests();

        // close working filewriter without finalizing
        logger.workingLogFileWriter.get().flush();
        logger.workingLogFileWriter.get().close();

        try {
            verifyWorkingLogFile(3);
            Assert.fail("Open Json should not parse!");
        } catch (Exception e) {
            logger = new RemoteLogger();
            JsonArray jsonArray = verifyWorkingLogFile(3);
            Assert.assertNotNull(jsonArray);
        }
    }

    @Test
    public void finalizeLogData() {
        final String msg = "Log msg";

        Assert.assertFalse(logger.executor.isShutdown());
        logger.log(LogLevel.INFO, msg);
        logger.log(LogLevel.INFO, msg);
        logger.log(LogLevel.INFO, msg);
        logger.finalizeWorkingLogFile();

        Assert.assertNull(logger.workingLogFileWriter.get());
    }

    @Test
    public void shutdown() {
        final String msg = "Log msg";

        Assert.assertFalse(logger.executor.isShutdown());
        logger.log(LogLevel.INFO, msg);
        logger.log(LogLevel.INFO, msg);
        logger.log(LogLevel.INFO, msg);
        logger.log(LogLevel.INFO, msg);
        logger.log(LogLevel.INFO, msg);
        logger.shutdown();

        Assert.assertTrue(logger.executor.isShutdown());
    }

    private JsonArray verifyWorkingLogFile(int expectedRecordCount) throws IOException {
        logger.finalizeWorkingLogFile();

        return verifyLogFile(logger.workingLogFile, expectedRecordCount);
    }

    private JsonArray verifyLogFile(File logFile, int expectedRecordCount) throws IOException {
        Path p = Paths.get(logFile.getAbsolutePath());
        List<String> lines = Files.readAllLines(p);
        lines.removeIf(s -> "[".equals(s) || "]".equals(s));
        Assert.assertEquals("Expected records lines", expectedRecordCount, lines.size());

        JsonArray jsonArray = gson.fromJson(new String(Files.readAllBytes(p)), JsonArray.class);
        jsonArray.remove(jsonArray.size() - 1);   // FIXME remove last null record
        Assert.assertEquals("Expected JSON records", expectedRecordCount, jsonArray.size());

        return jsonArray;
    }
}