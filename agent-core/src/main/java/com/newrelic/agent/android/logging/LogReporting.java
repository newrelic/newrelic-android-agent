/*
 * Copyright (c) 2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.logging;

import static com.newrelic.agent.android.analytics.AnalyticsAttribute.EVENT_TIMESTAMP_ATTRIBUTE;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.analytics.AnalyticsAttribute;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * LogReporting public interface, exposed to NewRelic API
 */
public abstract class LogReporting {

    private static Gson gson = new GsonBuilder().enableComplexMapKeySerialization().create();
    private static Type gtype = new TypeToken<Map>() {}.getType();

    // Logging payload attributes
    static final String LOG_TIMESTAMP_ATTRIBUTE = EVENT_TIMESTAMP_ATTRIBUTE;
    static final String LOG_LEVEL_ATTRIBUTE = "level";
    static final String LOG_MESSAGE_ATTRIBUTE = "message";
    static final String LOG_ATTRIBUTES_ATTRIBUTE = "attributes";

    static final String LOG_ERROR_MESSAGE_ATTRIBUTE = "error.message";
    static final String LOG_ERROR_STACK_ATTRIBUTE = "error.stack";
    static final String LOG_ERROR_CLASS_ATTRIBUTE = "error.class";

    static LogLevel logLevel = LogLevel.INFO;

    static LogReporting instance = new LogReporting() {
        @Override
        public void log(LogLevel level, String message) {
            logToAgent(level, message);
        }
    };

    private static String entityGuid = "";

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
     * Writes a message to the current agent log using the provided log level.
     * At runtime, this will be the Android logger (Log) instance.
     */

    public void logToAgent(LogLevel level, String message) {
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

    /**
     * derived classes must implement a basic message logger
     */
    public abstract void log(LogLevel level, String message);

    public void logThrowable(LogLevel logLevel, String message, Throwable throwable) {
        logToAgent(logLevel, message + ": " + throwable.getLocalizedMessage());
    }

    public void logAttributes(Map<String, Object> attributes) {
        Map<String, Object> msgAttributes = getCommonBlockAttributes();
        String level = (String) attributes.getOrDefault(LOG_LEVEL_ATTRIBUTE, "NONE");
        msgAttributes.put(LogReporting.LOG_ATTRIBUTES_ATTRIBUTE, attributes);
        logToAgent(LogLevel.valueOf(level.toUpperCase()), gson.toJson(msgAttributes, gtype));
    }

    public void logAll(Throwable throwable, Map<String, Object> attributes) {
        String level = (String) attributes.getOrDefault(LogReporting.LOG_LEVEL_ATTRIBUTE, "NONE");
        Map<String, Object> msgAttributes = new HashMap<>() {{
            put(LogReporting.LOG_LEVEL_ATTRIBUTE, level.toUpperCase());
            putAll(getCommonBlockAttributes());
            if (throwable != null) {
                put(LogReporting.LOG_ERROR_MESSAGE_ATTRIBUTE, throwable.getLocalizedMessage());
                put(LogReporting.LOG_ERROR_STACK_ATTRIBUTE, throwable.getStackTrace()[0].toString());
                put(LogReporting.LOG_ERROR_CLASS_ATTRIBUTE, throwable.getClass().getSimpleName());
            }
            put(LogReporting.LOG_ATTRIBUTES_ATTRIBUTE, attributes);
        }};
        logToAgent(LogLevel.valueOf(level.toUpperCase()), gson.toJson(msgAttributes, gtype));
    }

    public static String getEntityGuid() {
        return entityGuid != null ? entityGuid : "";
    }

    public static void setEntityGuid(String entityGuid) {
        if (entityGuid == null) {
            AgentLogManager.getAgentLog().error("setEntityGuid: invalid entity guid value!");
        } else {
            LogReporting.entityGuid = entityGuid;
        }
    }

    /**
     * Return the collection of NR common (root level) log attributes to add to the log data entry
     *
     * @return Map of common block attributes
     */
    Map<String, Object> getCommonBlockAttributes() {
        return new HashMap<>() {{
            put(EVENT_TIMESTAMP_ATTRIBUTE, System.currentTimeMillis());
        //  put(EVENT_ENTITY_ID_ATTRIBUTE, LogReporting.getEntityGuid());
        }};
    }

}
