/*
 * Copyright (c) 2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.logging;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.newrelic.agent.android.FeatureFlag;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * LogReporting public interface, exposed to NewRelic API
 */
public abstract class LogReporting {

    private static Gson gson = new GsonBuilder().enableComplexMapKeySerialization().create();
    private static Type gtype = new TypeToken<Map<String, Object>>(){}.getType();

    static LogLevel logLevel = LogLevel.INFO;
    static LogReporting instance = new LogReporting() {};

    public static LogReporting getLogger() {
        return instance;
    }

    public static LogReporting setLogger(LogReporting logger) {
        LogReporting.instance = logger;
        return LogReporting.instance;
    }

    static LogLevel getLogLevel() {
        return logLevel;
    }

    /**
     * Return ordinal value of log level
     *
     * @return LogLevel enum
     */
    static int getLogLevelAsInt() {
        return logLevel.ordinal();
    }

    /**
     * Set log level by name
     *
     * @param logLevelAsString
     */
    public static void setLogLevel(String logLevelAsString) {
        setLogLevel(LogLevel.valueOf(logLevelAsString.toUpperCase()));
    }

    /**
     * Set log level by ordinal value
     *
     * @param logLevelAsValue
     */
    static void setLogLevel(int logLevelAsValue) {
        logLevel = LogLevel.levels[logLevelAsValue];
    }

    /**
     * Set log level by enum
     *
     * @param level
     */
    public static void setLogLevel(LogLevel level) {
        logLevel = level;
    }

    public static boolean isLevelEnabled(LogLevel level) {
        return logLevel.value >= level.value;
    }

    public boolean isRemoteLoggingEnabled() {
        return FeatureFlag.featureEnabled(FeatureFlag.LogReporting) &&
                getLogLevel() != LogLevel.NONE;
    }

    /**
     * Writes a message to the agent log using the provided log level
     */

    public void log(LogLevel level, String message) {
        if (isLevelEnabled(level)) {
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

    public void logThrowable(LogLevel logLevel, String message, Throwable throwable) {
        log(logLevel, message + ": " + throwable.getLocalizedMessage());
    }

    public void logAttributes(Map<String, Object> attributes) {
        Map<String, Object> msgAttributes = getDefaultAttributes();
        String level = (String) attributes.getOrDefault("level", "NONE");
        log(LogLevel.valueOf(level.toUpperCase()), gson.toJson(msgAttributes, gtype));
    }

    public void logAll(Throwable throwable, Map<String, Object> attributes) {
        String level = (String) attributes.getOrDefault("level", "NONE");
        Map<String, Object> msgAttributes = new HashMap<>() {{
            put("level", level.toUpperCase());
            putAll(getDefaultAttributes());
            if (throwable != null) {
                put("error.message", throwable.getLocalizedMessage());
                put("error.stack", throwable.getStackTrace()[0].toString());
                put("error.class", throwable.getClass().getSimpleName());
            }
            putAll(attributes);
        }};
        log(LogLevel.valueOf(level.toUpperCase()), gson.toJson(msgAttributes, gtype));
    }

    /**
     *  WIP: return the collection of NR default attributes to add to log request
     * @return
     */
    Map<String, Object> getDefaultAttributes() {
        return new HashMap<>() {{
            put("timestamp", System.currentTimeMillis());
            put("entity-id", "** FIXME **");
        }};
    }

}
