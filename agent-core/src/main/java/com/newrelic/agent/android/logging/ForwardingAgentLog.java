/*
 * Copyright (c) 2022-2024. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.logging;

import com.newrelic.agent.android.Agent;

import java.util.HashMap;
import java.util.Map;

/**
 * AgentLog class that emits a log message to its provided delegate, then forwards to the Remote logger.
 */
public class ForwardingAgentLog implements AgentLog {

    private final AgentLog delegate;

    public ForwardingAgentLog(AgentLog agentLog) {
        this.delegate = agentLog;
    }

    public void audit(String message) {
        delegate.audit(message);

        if (delegate.getLevel() == AgentLog.AUDIT) {
            if (LogReporting.getLogger() instanceof RemoteLogger) {
                LogReporting.getLogger().logAttributes(asAttributes(LogLevel.DEBUG, message));
            }
        }
    }

    public void debug(final String message) {
        delegate.debug(message);

        if (delegate.getLevel() >= DEBUG) {
            if (LogReporting.getLogger() instanceof RemoteLogger) {
                LogReporting.getLogger().logAttributes(asAttributes(LogLevel.DEBUG, message));
            }
        }
    }

    public void verbose(final String message) {
        delegate.verbose(message);

        if (delegate.getLevel() >= VERBOSE) {
            if (LogReporting.getLogger() instanceof RemoteLogger) {
                LogReporting.getLogger().logAttributes(asAttributes(LogLevel.VERBOSE, message));
            }
        }
    }

    public void info(final String message) {
        delegate.info(message);

        if (delegate.getLevel() >= INFO) {
            if (LogReporting.getLogger() instanceof RemoteLogger) {
                LogReporting.getLogger().logAttributes(asAttributes(LogLevel.INFO, message));
            }
        }
    }

    public void warn(final String message) {
        delegate.warn(message);

        if (delegate.getLevel() >= WARN) {
            if (LogReporting.getLogger() instanceof RemoteLogger) {
                LogReporting.getLogger().logAttributes(asAttributes(LogLevel.WARN, message));
            }
        }
    }

    public void error(final String message) {
        delegate.error(message);

        if (delegate.getLevel() >= ERROR) {
            if (LogReporting.getLogger() instanceof RemoteLogger) {
                LogReporting.getLogger().logAttributes(asAttributes(LogLevel.ERROR, message));
            }
        }
    }

    public void error(final String message, Throwable throwable) {
        delegate.error(message, throwable);

        if (delegate.getLevel() >= ERROR) {
            if (LogReporting.getLogger() instanceof RemoteLogger) {
                Map<String, Object> attributes = asAttributes(LogLevel.ERROR, message);

                attributes.put(LogReporting.LOG_ERROR_MESSAGE_ATTRIBUTE, throwable.toString());
                attributes.put(LogReporting.LOG_ERROR_STACK_ATTRIBUTE, throwable.getStackTrace()[0].toString());
                attributes.put(LogReporting.LOG_ERROR_CLASS_ATTRIBUTE, throwable.getClass().getSimpleName());
                LogReporting.getLogger().logAttributes(attributes);
            }
        }
    }

    @Override
    public int getLevel() {
        return delegate.getLevel();
    }

    @Override
    public void setLevel(int level) {
        delegate.setLevel(level);
    }

    Map<String, Object> asAttributes(LogLevel level, String message) {
        Map<String, Object> attributes = new HashMap<>();

        attributes.put(LogReporting.LOG_LEVEL_ATTRIBUTE, level.name());
        attributes.put(LogReporting.LOG_MESSAGE_ATTRIBUTE, message);
        attributes.put(LogReporting.LOG_LOGGER_ATTRIBUTE, "Android agent " + Agent.getVersion());

        return attributes;
    }

}
