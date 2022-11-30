/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.compile;

import com.newrelic.agent.InstrumentationAgent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.helpers.LegacyAbstractLogger;

import java.util.Map;

public class Log extends LegacyAbstractLogger {
    public static Log LOGGER = new SLF4JLogger(InstrumentationAgent.getAgentOptions());

    protected final Level logLevel;

    public Log(Map<String, String> agentOptions) {
        String logLevelOpt = agentOptions.getOrDefault("loglevel", Level.WARN.name());
        logLevel = Level.valueOf(logLevelOpt);
        name = getFullyQualifiedCallerName();
        LOGGER = this;
    }

    protected void log(String level, String message) {
        // no-op
    }

    protected void log(Level level, String message) {
        if (isLevelEnabled(level)) {
            synchronized (this) {
                log(level.name(), message);
            }
        }
    }

    boolean isLevelEnabled(Level level) {
        return logLevel.toInt() <= level.toInt();
    }

    @Override
    public boolean isErrorEnabled() {
        return isLevelEnabled(Level.ERROR);
    }

    @Override
    public boolean isErrorEnabled(Marker marker) {
        return isErrorEnabled();
    }

    @Override
    public boolean isWarnEnabled() {
        return isLevelEnabled(Level.WARN);
    }

    @Override
    public boolean isWarnEnabled(Marker marker) {
        return isWarnEnabled();
    }

    @Override
    public boolean isInfoEnabled() {
        return (isLevelEnabled(Level.INFO));
    }

    @Override
    public boolean isInfoEnabled(Marker marker) {
        return isInfoEnabled();
    }

    @Override
    public boolean isDebugEnabled() {
        return isLevelEnabled(Level.DEBUG);
    }

    @Override
    public boolean isDebugEnabled(Marker marker) {
        return isDebugEnabled();
    }

    @Override
    public boolean isTraceEnabled() {
        return isLevelEnabled(Level.TRACE);
    }

    @Override
    public boolean isTraceEnabled(Marker marker) {
        return isTraceEnabled();
    }

    @Override
    protected String getFullyQualifiedCallerName() {
        return "newrelic";
    }

    @Override
    protected void handleNormalizedLoggingCall(Level level, Marker marker, String s, Object[] objects, Throwable throwable) {
        log(level.name(), String.format(s, objects));
    }

    static class SLF4JLogger extends Log {
        Logger logger = LoggerFactory.getLogger(getFullyQualifiedCallerName());

        public SLF4JLogger(Map<String, String> agentOptions) {
            super(agentOptions);
        }

        @Override
        protected void log(String level, String message) {
            logger.atLevel(Level.valueOf(level)).log(message);
        }
    }

}
