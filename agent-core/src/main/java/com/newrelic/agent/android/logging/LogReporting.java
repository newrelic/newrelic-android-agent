/*
 * Copyright (c) 2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.logging;

/**
 * Level names should correspond to iOS values.
 * The ordinal values are not shared and are used for priority ordering
 */
enum LogLevel {

    NONE(0),        // All logging disabled, not advised
    ERROR(1),       // App and system errors
    WARN(2),        // Errors and app warnings
    INFO(3),        // Useful app messages
    DEBUG(4),       // Messaging to assist static analysis
    VERBOSE(5);     // When too much is just not enough

    final int value;
    static final LogLevel levels[] = values();

    LogLevel(final int value) {
        this.value = value;
    }

}

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
