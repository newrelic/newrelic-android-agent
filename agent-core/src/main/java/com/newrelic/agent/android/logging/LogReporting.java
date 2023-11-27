/*
 * Copyright (c) 2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.logging;

import com.google.gson.annotations.Expose;

/**
 * LogReporting public interface, exposed to static API
 */
public interface LogReporting {

    /**
     * Level names should correspond to iOS values.
     * The ordinal values are not shared and are used primarily for priority ordering
     */
    enum LogLevel {

        NONE(0),        // All logging disabled, not advised
        ERROR(1),       // App and system errors
        WARN(2),        // Errors and app warnings
        INFO(3),        // Useful app messages
        DEBUG(4),       // Messaging to assist static analysis
        VERBOSE(5);     // When too much is just not enough

        int level;

        LogLevel(final int level) {
            this.level = level;
        }

    }

    String TAG = "newrelic";
    LogLevel logLevel = LogLevel.WARN;

    static AgentLog getLogger() {
        return AgentLogManager.getAgentLog();
    }

    static int getLogLevel() {
        return logLevel.level;
    }

    static void setLogLevel(String logLevelAsString) {
        setLogLevel(LogLevel.valueOf(logLevelAsString));
    }

    static void setLogLevel(int logLevelAsInt) {
        logLevel.level = logLevelAsInt;
    }

    static void setLogLevel(LogLevel level) {
        logLevel.level = level.level;
        AgentLogManager.getAgentLog().setLevel(level.level);
    }

    static boolean isLevelEnabled(LogLevel level) {
        return getLogLevel() >= level.level;
    }

    /**
     * Writes a message to the log using the porvided log level
     */

    static void notice(LogLevel level, String message) {
        if (isLevelEnabled(level)) {
            // TODO
        }
    }

}
