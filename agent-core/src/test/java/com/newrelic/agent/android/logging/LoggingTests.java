/*
 * Copyright (c) 2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.logging;

import com.google.gson.JsonArray;
import com.newrelic.agent.android.AgentConfiguration;

import org.junit.Assert;
import org.junit.BeforeClass;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class LoggingTests {
    protected static File reportsDir;
    long tStart;

    @BeforeClass
    public static void beforeClass() throws Exception {
        reportsDir = Files.createTempDirectory("LogReporting-").toFile();
        reportsDir.mkdirs();

        AgentLogManager.setAgentLog(new ConsoleAgentLog());
        AgentLogManager.getAgentLog().setLevel(AgentLog.DEBUG);
        AgentConfiguration.getInstance().setApplicationToken("<APP-TOKEN>>");
        AgentConfiguration.getInstance().setEntityGuid("<ENTITY-GUID>");
    }

    public LoggingTests() {
        this.tStart = System.currentTimeMillis();
    }

    /**
     * Generate a message of a minimum length with at least 12 words, each 1 to 15 chars in length
     */
    static String getRandomMsg(int msgLength) {
        StringBuilder msg = new StringBuilder();

        while (msg.length() < msgLength) {
            new Random().ints(new Random().nextInt(12), 1, 15)
                    .forEach(wordLength -> msg.append(new Random().ints('a', '~' + 1)
                                    .limit(wordLength)
                                    .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                                    .append(" ").toString().replace("\"", ":").trim())
                            .append(". "));
        }

        return msg.toString().trim();
    }

    JsonArray verifyWorkingLogfile(int expectedRecordCount) throws IOException {
        LogReporter logReporter = LogReporter.getInstance();

        Assert.assertNotNull(logReporter);
        logReporter.finalizeWorkingLogfile();

        return verifyLogfile(logReporter.workingLogfile, expectedRecordCount);
    }

    JsonArray verifyLogfile(File logFile, int expectedRecordCount) throws IOException {
        List<String> lines = Files.readAllLines(logFile.toPath());
        lines.removeIf(s -> s.isEmpty() || ("[".equals(s) || "]".equals(s)));
        Assert.assertEquals("Expected records lines", expectedRecordCount, lines.size(), 1);

        JsonArray jsonArray = LogReporter.logfileToJsonArray(logFile);
        Assert.assertEquals("Expected JSON records", expectedRecordCount, jsonArray.size());

        return jsonArray;
    }

    JsonArray verifySpannedLogfiles(Set<File> logFiles, int expectedRecordCount) throws IOException {
        JsonArray jsonArray = new JsonArray();
        for (File logFile : logFiles) {
            jsonArray.addAll(LogReporter.logfileToJsonArray(logFile));
        }
        Assert.assertEquals("Expected JSON records", expectedRecordCount, jsonArray.size());

        return jsonArray;
    }

    Set<File> seedLogData(int numFiles) throws Exception {
        final HashSet<File> reportSet = new HashSet<>();
        final LogReporter logReporter = LogReporter.getInstance();

        for (int file = 0; file < numFiles; file++) {
            RemoteLogger remoteLogger = new RemoteLogger();
            logReporter.resetWorkingLogfile();

            remoteLogger.log(LogLevel.ERROR, getRandomMsg((int) (Math.random() * 30) + 12));
            remoteLogger.log(LogLevel.WARN, getRandomMsg((int) (Math.random() * 30) + 12));
            remoteLogger.log(LogLevel.INFO, getRandomMsg((int) (Math.random() * 30) + 12));
            remoteLogger.log(LogLevel.DEBUG, getRandomMsg((int) (Math.random() * 30) + 12));
            remoteLogger.log(LogLevel.VERBOSE, getRandomMsg((int) (Math.random() * 30) + 12));
            remoteLogger.flush();

            logReporter.finalizeWorkingLogfile();
            reportSet.add(logReporter.rollWorkingLogfile());
        }

        return reportSet;
    }

    Set<File> seedLogData(int numFiles, int minFileSize) throws Exception {
        final HashSet<File> reportSet = new HashSet<>();
        final LogReporter logReporter = LogReporter.getInstance();

        for (int file = 0; file < numFiles; file++) {
            final RemoteLogger remoteLogger = new RemoteLogger();

            logReporter.resetWorkingLogfile();
            logReporter.payloadBudget = minFileSize;
            while (logReporter.payloadBudget > 512) {   //  break before teh logReporter rolls the file
                remoteLogger.log(LogLevel.ERROR, getRandomMsg((int) (Math.random() * 30) + 12));
                remoteLogger.log(LogLevel.WARN, getRandomMsg((int) (Math.random() * 30) + 12));
                remoteLogger.log(LogLevel.INFO, getRandomMsg((int) (Math.random() * 30) + 12));
                remoteLogger.log(LogLevel.VERBOSE, getRandomMsg((int) (Math.random() * 30) + 12));
                remoteLogger.log(LogLevel.DEBUG, getRandomMsg((int) (Math.random() * 30) + 12));
                remoteLogger.flush();
            }

            logReporter.finalizeWorkingLogfile();
            reportSet.add(logReporter.rollWorkingLogfile());
        }
        return reportSet;
    }
}
