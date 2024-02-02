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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
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
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RemoteLoggerTest {

    private static File reportsDir;

    private RemoteLogger logger;
    private long tStart;
    private LogReporter logReporter;

    @BeforeClass
    public static void beforeClass() throws Exception {
        LogReporting.setEntityGuid(UUID.randomUUID().toString());
        AgentLogManager.setAgentLog(new ConsoleAgentLog());
        reportsDir = Files.createTempDirectory("LogReporting-").toFile();
        reportsDir.mkdirs();
    }

    @Before
    public void setUp() throws Exception {
        FeatureFlag.enableFeature(FeatureFlag.LogReporting);
        LogReporting.setLogLevel(LogLevel.INFO);
        logReporter = LogReporter.initialize(reportsDir, new AgentConfiguration());
        logReporter.getWorkingLogfile().delete();
        logger = Mockito.spy(new RemoteLogger());
        tStart = System.currentTimeMillis();
    }

    @After
    public void tearDown() throws Exception {
        logger.shutdown();
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
        final String msg = "Log: ";

        logger.log(LogLevel.ERROR, msg + getRandomMsg(19));
        logger.log(LogLevel.INFO, msg + getRandomMsg(7));
        logger.log(LogLevel.WARN, msg + getRandomMsg(31));
        logger.log(LogLevel.VERBOSE, msg + getRandomMsg(11));
        logger.log(LogLevel.DEBUG, msg + getRandomMsg(23));

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
        final HashMap<String, Object> attrs = new HashMap<String, Object>() {{
            put("level", "INFO");
            put("message", msg);
        }};

        logger.logAttributes(attrs);

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
        final HashMap<String, Object> attrs = new HashMap() {{
            put("level", "INFO");
            put("message", msg);
        }};

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
        verify(logger, times(1)).appendToWorkingLogFile(LogLevel.INFO, msg, null, null);

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

        logger.log(LogLevel.INFO, getRandomMsg(10));
        logger.flushPendingRequests();

        File finalizedLogFile = logger.rollWorkingLogFile();
        Assert.assertTrue(finalizedLogFile.exists());
        Assert.assertTrue(logger.workingLogFile.exists());
        Assert.assertNotEquals("Should create a new buffered writer", writer, logger.workingLogFileWriter.get());
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
                        logger.log(LogLevel.INFO, "Called from " + thread.getName() + ": " + getRandomMsg((int) (Math.random() * 256)));
                        thread.yield();
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

        logger.finalizeWorkingLogFile();

        // the working logfile may rollover, so we need to cunt all generated files
        try (Stream<Path> stream = Files.list(LogReporter.logDataStore.toPath())) {
            Set<File> files = stream
                    .filter(file -> !Files.isDirectory(file))
                    .map(Path::toFile)
                    .collect(Collectors.toSet());

            Assert.assertFalse(files.isEmpty());

            JsonArray jsonArray = verifySpannedLogFiles(files, N_THREADS * N_MSGS);
            for (int i = 0; i < (N_THREADS * N_MSGS); i++) {
                JsonObject jsonObject = jsonArray.get(i).getAsJsonObject();
                Assert.assertTrue(jsonObject.get(LogReporting.LOG_TIMESTAMP_ATTRIBUTE).getAsLong() >= tStart);
                Assert.assertTrue(jsonObject.has(LogReporting.LOG_MESSAGE_ATTRIBUTE));
                Assert.assertFalse(jsonObject.has(LogReporting.LOG_ATTRIBUTES_ATTRIBUTE));
            }
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
        logger.uploadBudget = 1000;
        logger.log(LogLevel.INFO, getRandomMsg(300));
        logger.log(LogLevel.INFO, getRandomMsg(300));
        logger.log(LogLevel.INFO, getRandomMsg(400));
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
        Assert.assertEquals("Should create a new working file", 0, logger.workingLogFile.length());
        Assert.assertNotEquals("Should create a new buffered writer", writer, logger.workingLogFileWriter.get());
        logger.log(LogLevel.INFO, "After onHarvest()");
        logger.log(LogLevel.INFO, "Before onHarvestStop()");

        logger.onHarvestStop();
        logger.flushPendingRequests();
        Assert.assertTrue(logger.workingLogFile.exists());
        Assert.assertEquals("Should create a new working file", 0, logger.workingLogFile.length());
        Assert.assertNull("Should not create a new buffered writer", logger.workingLogFileWriter.get());
        Assert.assertFalse("Should not shutdown executor", logger.executor.isShutdown());
        logger.log(LogLevel.INFO, "After onHarvestStop()");
    }

    @Test
    public void testEntityGuid() {
        // TODO
    }

    @Test
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
        Assert.assertTrue(elem2.has(LogReporting.LOG_ATTRIBUTES_ATTRIBUTE));
        JsonObject attributes = elem2.get(LogReporting.LOG_ATTRIBUTES_ATTRIBUTE).getAsJsonObject();
        Assert.assertTrue(attributes.has("action"));
        Assert.assertTrue(attributes.has("user"));
        Assert.assertTrue(attributes.get("user").getAsJsonObject() instanceof JsonObject);
        JsonObject user = attributes.get("user").getAsJsonObject();
        Assert.assertEquals("alice", user.get("name").getAsString());
    }

    @Test
    public void testWorkingfileIOFailure() throws IOException {
        final String msg = "";

        logger.log(LogLevel.INFO, msg + getRandomMsg(8));
        logger.log(LogLevel.INFO, msg + getRandomMsg(5));
        logger.log(LogLevel.INFO, msg + getRandomMsg(11));

        // close working filewriter without finalizing
        logger.workingLogFileWriter.get().flush();
        logger.workingLogFileWriter.get().close();

        verifyWorkingLogFile(3);
        logger = new RemoteLogger();

        JsonArray jsonArray = verifyWorkingLogFile(3);
        Assert.assertNotNull(jsonArray);
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
        Assert.assertFalse(logger.executor.isShutdown());
        logger.log(LogLevel.INFO, getRandomMsg(128));
        logger.log(LogLevel.INFO, getRandomMsg(64));
        logger.shutdown();

        Assert.assertTrue(logger.executor.isShutdown());
    }


    private JsonArray slurpLogDataFile(File logfile) throws IOException {
        JsonArray jsonArray = new JsonArray();

        Files.newBufferedReader(logfile.toPath()).lines().forEach(s -> {
            if (!(s == null || s.isEmpty())) {
                try {
                    JsonObject messageAsJson = LogReporting.gson.fromJson(s, JsonObject.class);
                    jsonArray.add(messageAsJson);
                } catch (JsonSyntaxException e) {
                    Assert.fail("Invalid Json entry!");
                }
            }
        });

        return jsonArray;
    }

    private JsonArray verifyWorkingLogFile(int expectedRecordCount) throws IOException {
        logger.finalizeWorkingLogFile();

        return verifyLogFile(logger.workingLogFile, expectedRecordCount);
    }

    private JsonArray verifyLogFile(File logFile, int expectedRecordCount) throws IOException {
        List<String> lines = Files.readAllLines(logFile.toPath());
        lines.removeIf(s -> s.isEmpty() || ("[".equals(s) || "]".equals(s)));
        Assert.assertEquals("Expected records lines", expectedRecordCount, lines.size());

        JsonArray jsonArray = slurpLogDataFile(logFile);
        Assert.assertEquals("Expected JSON records", expectedRecordCount, jsonArray.size());

        return jsonArray;
    }

    private JsonArray verifySpannedLogFiles(Set<File> logFiles, int expectedRecordCount) throws IOException {
        JsonArray jsonArray = new JsonArray();
        for (File logFile : logFiles) {
            jsonArray.addAll(slurpLogDataFile(logFile));
        }
        Assert.assertEquals("Expected JSON records", expectedRecordCount, jsonArray.size());

        return jsonArray;
    }

    /**
     * Generate a message of a minimum length with at least 12 words, each 1 to 15 chars in length
     */
    static String getRandomMsg(int msgLength) {
        StringBuilder msg = new StringBuilder();

        while (msg.length() < msgLength) {
            new Random().ints(new Random().nextInt(12), 1, 15)
                    .forEach(wordLength -> {
                        msg.append(new Random().ints((int) 'a', (int) '~' + 1)
                                        .limit(wordLength)
                                        .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                                        .append(" ").toString().replace("\"", ":").trim())
                                .append(". ");
                    });
        }

        return msg.toString().trim();
    }

};