/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.compile;

import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.helpers.LegacyAbstractLogger;

import java.util.HashMap;
import java.util.Map;

public class Log extends LegacyAbstractLogger {

    public enum LogLevel {

        ERROR(0),       // Error messages
        WARN(1),        // Warning messages
        INFO(2),        // Information messages
        DEBUG(3),       // Debug messages
        TRACE(4);       // Trace messages

        final int value;

        LogLevel(final int newValue) {
            value = newValue;
        }
    }

    public static Log LOGGER = new NullLogger();

    protected final int logLevel;

    public Log(Map<String, String> agentOptions) {
        String logLevelOpt = agentOptions.get("loglevel");
        logLevel = (logLevelOpt != null) ? LogLevel.valueOf(logLevelOpt).value : LogLevel.WARN.value;
        LOGGER = this;
    }

    protected void log(String level, String message) {
        // no-op
    }

    protected void log(LogLevel level, String message) {
        if (logLevelEnabled(level)) {
            synchronized (this) {
                log(level.name(), message);
            }
        }
    }

    boolean logLevelEnabled(LogLevel level) {
        return (logLevel >= level.value);
    }

    @Override
    public boolean isErrorEnabled() {
        return logLevelEnabled(LogLevel.ERROR);
    }

    @Override
    public boolean isErrorEnabled(Marker marker) {
        return isErrorEnabled();
    }

    @Override
    public boolean isWarnEnabled() {
        return logLevelEnabled(LogLevel.WARN);
    }

    @Override
    public boolean isWarnEnabled(Marker marker) {
        return isWarnEnabled();
    }

    @Override
    public boolean isInfoEnabled() {
        return (logLevelEnabled(LogLevel.INFO));
    }

    @Override
    public boolean isInfoEnabled(Marker marker) {
        return isInfoEnabled();
    }

    @Override
    public boolean isDebugEnabled() {
        return logLevelEnabled(LogLevel.DEBUG);
    }

    @Override
    public boolean isDebugEnabled(Marker marker) {
        return isDebugEnabled();
    }

    @Override
    public boolean isTraceEnabled() {
        return logLevelEnabled(LogLevel.TRACE);
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

    static class NullLogger extends Log {
        public NullLogger() {
            super(new HashMap<>());
        }
    }

}
