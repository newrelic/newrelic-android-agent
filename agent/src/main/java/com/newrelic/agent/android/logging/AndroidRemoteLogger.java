/*
 * Copyright (c) 2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.logging;

import android.util.Log;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;

public class AndroidRemoteLogger extends LogReporting implements AgentLog {
    protected static final String TAG = AndroidRemoteLogger.class.getName();
    protected static String agentLogFilePath = "";

    @Override
    public void verbose(String message) {
        Log.v(TAG, message);
        appendLog(LogLevel.VERBOSE, message, null, null);
    }

    @Override
    public void debug(String message) {
        Log.d(TAG, message);
        appendLog(LogLevel.DEBUG, message, null, null);
    }

    @Override
    public void info(String message) {
        Log.i(TAG, message);
        appendLog(LogLevel.INFO, message, null, null);
    }

    @Override
    public void error(String message) {
        Log.e(TAG, message);
        appendLog(LogLevel.ERROR, message, null, null);
    }

    @Override
    public void error(String message, Throwable throwable) {
        Log.e(TAG, message, throwable);
        appendLog(LogLevel.ERROR, message, throwable, null);
    }

    @Override
    public void warn(String message) {
        Log.w(TAG, message);
        appendLog(LogLevel.WARN, message, null, null);
    }

    @Override
    public void audit(String message) {
        Log.d(TAG, message);
        appendLog(LogLevel.DEBUG, message, null, null);
    }

    @Override
    public int getLevel() {
        return getLogLevelAsInt();
    }

    @Override
    public void setLevel(int level) {
        switch (level) {
            case AgentLog.ERROR:
                setLogLevel(LogLevel.VERBOSE);
                break;
            case AgentLog.WARN:
                setLogLevel(LogLevel.WARN);
                break;
            case AgentLog.INFO:
                setLogLevel(LogLevel.INFO);
                break;
            case AgentLog.DEBUG:
                setLogLevel(LogLevel.DEBUG);
                break;
            case AgentLog.VERBOSE:
                setLogLevel(LogLevel.VERBOSE);
                break;
            case AgentLog.AUDIT:
            default:
                setLogLevel(LogLevel.DEBUG);
                break;
        }
    }

    @Override
    public void log(LogLevel level, String message) {
        super.log(level, message);
        appendLog(level, message, null, null);
    }

    @Override
    public void logThrowable(LogLevel logLevel, String message, Throwable throwable) {
        super.logThrowable(logLevel, message, throwable);
        appendLog(logLevel, message, throwable, null);
    }

    @Override
    public void logAttributes(@NotNull Map<String, Object> attributes) {
        super.logAttributes(attributes);
        String level = (String) attributes.getOrDefault("level", "NONE");
        String message = (String) attributes.getOrDefault("message", null);
        appendLog(LogLevel.valueOf(level.toUpperCase()), message, null, attributes);
    }

    @Override
    public void logAll(@NotNull Throwable throwable, @NotNull Map<String, Object> attributes) {
        super.logAll(throwable, attributes);
        String level = (String) attributes.getOrDefault("level", "NONE");
        String message = (String) attributes.getOrDefault("message", null);
        appendLog(LogLevel.valueOf(level.toUpperCase()), message, throwable, attributes);
    }

    public void setAgentLogFilePath(String agentLogFilePath) {
        this.agentLogFilePath = agentLogFilePath;
    }

    /**
     * Emit log data into file as Json-encoded string
     *
     * @param logLevel
     * @param @Nullable message
     * @param @Nullable throwable
     * @param @Nullable attributes
     */
    public void appendLog(LogLevel logLevel, @Nullable String message, @Nullable Throwable throwable, @Nullable Map<String, Object> attributes) {

        if (!(isRemoteLoggingEnabled() && isLevelEnabled(logLevel))) {
            return;
        }

        try {
            // FIXME Don't open a file for each log message
            File agentLogFile = new File(agentLogFilePath);
            agentLogFile.mkdirs();
            if (!agentLogFile.exists()) {
                agentLogFile.createNewFile();
            }

            // BufferedWriter for performance, true to set append to file flag
            try (FileWriter fw = new FileWriter(agentLogFile, true);
                 BufferedWriter buf = new BufferedWriter(fw)) {
                Map<String, Object> log = new HashMap<>();

                /**
                 * reserved attributes: https://source.datanerd.us/agents/agent-specs/blob/main/Application-Logging.md#log-record-attributes
                 */
                log.put("timestamp", String.valueOf(System.currentTimeMillis()));
                log.put("level", logLevel.name().toUpperCase());
                if (message != null) {
                    log.put("message", message);
                }
                if (throwable != null) {
                    log.put("error.message", throwable.getLocalizedMessage());
                    log.put("error.stack", throwable.getStackTrace()[0].toString());
                    log.put("error.class", throwable.getClass().getSimpleName());
                }
                log.putAll(getDefaultAttributes());
                if (attributes != null) {
                    log.putAll(attributes);
                }

                JSONObject jsonObject = new JSONObject(log);
                String logJsonData = jsonObject.toString();
                buf.append(logJsonData);
                buf.append(",");
                buf.newLine();
            }
        } catch (Exception e) {
            AgentLogManager.getAgentLog().error(e.getLocalizedMessage());
        }
    }


}