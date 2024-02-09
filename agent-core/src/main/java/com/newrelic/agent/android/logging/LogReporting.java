/*
 * Copyright (c) 2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.logging;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.analytics.AnalyticsAttribute;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * LogReporting public interface, exposed to NewRelic API
 */
public abstract class LogReporting {

    // Logging payload attributes
    protected static final String LOG_TIMESTAMP_ATTRIBUTE = AnalyticsAttribute.EVENT_TIMESTAMP_ATTRIBUTE;
    protected static final String LOG_LEVEL_ATTRIBUTE = "level";
    protected static final String LOG_MESSAGE_ATTRIBUTE = "message";
    protected static final String LOG_ATTRIBUTES_ATTRIBUTE = "attributes";
    protected static final String LOG_ENTITY_ATTRIBUTE = "entity.guid";
    protected static final String LOG_ERROR_MESSAGE_ATTRIBUTE = "error.message";
    protected static final String LOG_ERROR_STACK_ATTRIBUTE = "error.stack";
    protected static final String LOG_ERROR_CLASS_ATTRIBUTE = "error.class";
    protected static final Type gtype = new TypeToken<Map<String, Object>>() {}.getType();
    protected static final Gson gson = new GsonBuilder()
            .enableComplexMapKeySerialization()
            .create();

    protected static LogLevel logLevel = LogLevel.WARN;
    protected static AtomicReference<LogReporting> instance = new AtomicReference<>(new LocalLogger());

    protected static String entityGuid = "";

    public static LogReporting getLogger() {
        return instance.get();
    }

    public static LogReporting setLogger(LogReporting logger) {
        LogReporting.instance.set(logger);
        return LogReporting.instance.get();
    }

    /**
     * Return the current log level
     *
     * @return LogLevel enum @link {LogLevel()}
     */
    static LogLevel getLogLevel() {
        return logLevel;
    }

    /**
     * Return ordinal value of log level
     *
     * @return LogLevel enum @link {LogLevel#ordinal()}
     */
    static int getLogLevelAsInt() {
        return logLevel.ordinal();
    }

    /**
     * Set log level by name
     *
     * @param logLevelAsString {@link LogLevel#name()}
     */
    public static void setLogLevel(String logLevelAsString) {
        setLogLevel(LogLevel.valueOf(logLevelAsString.toUpperCase()));
    }

    /**
     * Set log level by ordinal value
     *
     * @param logLevelAsValue @link {LogLevel()#ordinal()}
     */
    static void setLogLevel(int logLevelAsValue) {
        logLevel = LogLevel.levels[logLevelAsValue];
    }

    /**
     * Set log level by enum
     *
     * @param level {@link LogLevel#name()}
     */
    public static void setLogLevel(LogLevel level) {
        logLevel = level;
    }

    public static boolean isLevelEnabled(LogLevel level) {
        return logLevel.value >= level.value;
    }

    public boolean isRemoteLoggingEnabled() {
        return FeatureFlag.featureEnabled(FeatureFlag.LogReporting) &&
                LogLevel.NONE != getLogLevel();
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
        String level = (String) attributes.getOrDefault(LOG_LEVEL_ATTRIBUTE, LogLevel.INFO.name());
        attributes.remove(LOG_LEVEL_ATTRIBUTE);
        msgAttributes.put(LOG_ATTRIBUTES_ATTRIBUTE, attributes);
        logToAgent(LogLevel.valueOf(level.toUpperCase()), gson.toJson(msgAttributes, gtype));
    }

    public void logAll(Throwable throwable, Map<String, Object> attributes) {
        String level = (String) attributes.getOrDefault(LOG_LEVEL_ATTRIBUTE, LogLevel.INFO.name());
        Map<String, Object> msgAttributes = new HashMap<>();

        attributes.remove(LOG_LEVEL_ATTRIBUTE);

        msgAttributes.put(LOG_LEVEL_ATTRIBUTE, level.toUpperCase());
        msgAttributes.putAll(getCommonBlockAttributes());
        
        if (throwable != null) {
            msgAttributes.put(LOG_ERROR_MESSAGE_ATTRIBUTE, throwable.getLocalizedMessage());
            msgAttributes.put(LOG_ERROR_STACK_ATTRIBUTE, throwable.getStackTrace()[0].toString());
            msgAttributes.put(LOG_ERROR_CLASS_ATTRIBUTE, throwable.getClass().getSimpleName());
        }
        
        msgAttributes.put(LOG_ATTRIBUTES_ATTRIBUTE, attributes);
        
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
        Map<String, Object> attrs = new HashMap<>();

        attrs.put(LogReporting.LOG_TIMESTAMP_ATTRIBUTE, System.currentTimeMillis());
        attrs.put(LogReporting.LOG_ENTITY_ATTRIBUTE, LogReporting.getEntityGuid());

        return attrs;
    }

    static class LocalLogger extends LogReporting {
        @Override
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
    }

    // TODO
    public interface LogMessageValidator {
        String INVALID_KEYSET = "{}\\[\\]]";
        String[] ANONYMIZATION_TARGETS = {
                "http?//{.*}/{.*}",
                "{.*}\\@{.*}\\.{.*}"
        };

        boolean validateAttributes(Map<String, Object> attributes);

        default boolean anonymize(Map<String, Object> attributes) {
            return true;
        }

        default boolean validateThrowable(final Throwable throwable) {
            return true;
        }
    }

    /**
     * Validate and sanitize key/value data pairs
     *
     * @link https://docs.newrelic.com/docs/logs/log-api/introduction-log-api/#supported-types
     */
    protected Map<String, Object> validateLogData(LogMessageValidator validator, Map<String, Object> logDataMap) {
        logDataMap.forEach((key, value) -> {
            if (value instanceof String) {
                // TODO https://docs.newrelic.com/docs/logs/log-api/introduction-log-api/#message-attribute-parsin
                // Enforce log message constraints:
                //  static int MAX_ATTRIBUTES_PER_EVENT = 255;
                //  static int MAX_ATTRIBUTES_NAME_SIZE = 255;
                //  static int MAX_ATTRIBUTES_VALUE_SIZE = 4096;
            }
        });

        return logDataMap;
    }

    /**
     * Final decoration of log data attribute set
     *
     * @link https://docs.newrelic.com/docs/logs/log-api/introduction-log-api/#supported-types
     */
    protected Map<String, Object> decorateLogData(LogMessageValidator validator, Map<String, Object> logDataMap) {
        // TODO
        return logDataMap;
    }


}
