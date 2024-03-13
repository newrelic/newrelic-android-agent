/*
 * Copyright (c) 2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.logging;

import java.util.Map;

/**
 * LogReporting public interface, exposed to NewRelic API
 */
public interface Logger {
    String TAG = "newrelic";

    default boolean isLevelEnabled(LogLevel logLevel) {
        return LogReporting.isLevelEnabled(logLevel);
    }

    default void log(LogLevel level, String message) {
        if (LogReporting.isLevelEnabled(level)) {
            final AgentLog agentLog = AgentLogManager.getAgentLog();
            switch (level) {
                case ERROR:
                    agentLog.error(message);
                    break;
                case WARN:
                    agentLog.warn(message);
                    break;
                case INFO:
                    agentLog.info(message);
                    break;
                case VERBOSE:
                    agentLog.verbose(message);
                    break;
                case DEBUG:
                    agentLog.debug(message);
                    break;
            }
        }
    }

    default void logThrowable(LogLevel logLevel, String message, Throwable throwable) {
    }

    default void logAttributes(Map<String, Object> attributes) {
    }

    default void logAll(Throwable throwable, Map<String, Object> attributes) {
    }

}
