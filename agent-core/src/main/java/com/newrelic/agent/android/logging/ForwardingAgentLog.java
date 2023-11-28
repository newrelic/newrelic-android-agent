/*
 * Copyright (c) 2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.logging;

import com.newrelic.agent.android.AgentConfiguration;

/**
 * The Log forwarder delegates to the default AgentLog, then queues the message for delivery to
 * Logging ingest.
 */
public class ForwardingAgentLog implements LogReporting, AgentLog {

    public ForwardingAgentLog(LoggingConfiguration loggingConfiguration) {
    }

    public ForwardingAgentLog(AgentConfiguration agentConfiguration) {
    }

    @Override
    public void error(String message) {
        if (LogReporting.isLevelEnabled(LogLevel.ERROR)) {
            AgentLogManager.getAgentLog().error(message);
        }
    }

    @Override
    public void error(String message, Throwable cause) {
        if (LogReporting.isLevelEnabled(LogLevel.ERROR)) {
            AgentLogManager.getAgentLog().error(message);
        }
    }

    @Override
    public void warn(String message) {
        if (LogReporting.isLevelEnabled(LogLevel.WARN)) {
            AgentLogManager.getAgentLog().warn(message);
        }
    }

    @Override
    public void info(String message) {
        if (LogReporting.isLevelEnabled(LogLevel.INFO)) {
            AgentLogManager.getAgentLog().info(message);
        }
    }

    @Override
    public void debug(String message) {
        if (LogReporting.isLevelEnabled(LogLevel.DEBUG)) {
            AgentLogManager.getAgentLog().debug(message);
        }
    }

    @Override
    public void verbose(String message) {
        if (LogReporting.isLevelEnabled(LogLevel.VERBOSE)) {
            AgentLogManager.getAgentLog().verbose(message);
        }
    }

    @Override
    public void audit(String message) {
        // not supported for remote loggiing
    }

    @Override
    public int getLevel() {
        return LogReporting.getLogLevel();
    }

    @Override
    public void setLevel(int level) {
        LogReporting.setLogLevel(level);
    }

}
