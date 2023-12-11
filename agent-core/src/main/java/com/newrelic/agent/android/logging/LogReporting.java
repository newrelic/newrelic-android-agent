/*
 * Copyright (c) 2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.logging;

/**
 * LogReporting public interface, exposed to static API
 */
public abstract class LogReporting {

    static String TAG = "newrelic";
    static LogLevel logLevel = LogLevel.WARN;

    static AgentLog getLogger() {
        return AgentLogManager.getAgentLog();
    }

    static LogLevel getLogLevel() {
        return logLevel;
    }

    static int getLogLevelAsInt() {
        return logLevel.ordinal();
    }

    static void setLogLevel(String logLevelAsString) {
        setLogLevel(LogLevel.valueOf(logLevelAsString.toUpperCase()));
    }

    static void setLogLevel(int logLevelAsValue) {
        logLevel = LogLevel.levels[logLevelAsValue];
    }

    static void setLogLevel(LogLevel level) {
        logLevel = level;
    }

    static boolean isLevelEnabled(LogLevel level) {
        return logLevel.value >= level.value;
    }

    /**
     * Writes a message to the log using the provided log level
     */

    static void notice(LogLevel level, String message) {
        if (isLevelEnabled(level)) {
            // TODO
        }
    }

}
