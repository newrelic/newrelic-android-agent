/*
 * Copyright (c) 2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.logging;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.analytics.AnalyticsAttribute;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * LogReporting public interface, exposed to NewRelic API
 */
public abstract class LogReporting {
    static final String NULL_MSG = "<empty message>";

    // Logging payload attributes
    protected static final String LOG_TIMESTAMP_ATTRIBUTE = AnalyticsAttribute.EVENT_TIMESTAMP_ATTRIBUTE;
    protected static final String LOG_LEVEL_ATTRIBUTE = "level";
    protected static final String LOG_MESSAGE_ATTRIBUTE = "message";
    protected static final String LOG_ATTRIBUTES_ATTRIBUTE = "attributes";
    protected static final String LOG_ENTITY_ATTRIBUTE = "entity.guid";
    protected static final String LOG_ERROR_MESSAGE_ATTRIBUTE = "error.message";
    protected static final String LOG_ERROR_STACK_ATTRIBUTE = "error.stack";
    protected static final String LOG_ERROR_CLASS_ATTRIBUTE = "error.class";

    protected static LogLevel logLevel = LogLevel.WARN;
    protected static AgentLogger agentLogger = new AgentLogger();
    protected static AtomicReference<Logger> instance = new AtomicReference<>(agentLogger);

    protected static String entityGuid = "";

    public static Logger getLogger() {
        return instance.get();
    }

    public static Logger setLogger(Logger logger) {
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

    public static boolean isRemoteLoggingEnabled() {
        return FeatureFlag.featureEnabled(FeatureFlag.LogReporting) &&
                LogLevel.NONE != getLogLevel();
    }

    public static String getEntityGuid() {
        return entityGuid != null ? entityGuid : "";
    }

    public static void setEntityGuid(String entityGuid) {
        if (entityGuid == null || entityGuid.isEmpty()) {
            AgentLogManager.getAgentLog().error("setEntityGuid: invalid entity guid value!");
        } else {
            LogReporting.entityGuid = entityGuid;
        }
    }

    public static class AgentLogger implements Logger {
        /**
         * Writes a message to the current agent log using the provided log level.
         * At runtime, this will be the Android logger (Log) instance.
         */
        public void logToAgent(LogLevel level, String message) {
            if (LogReporting.isLevelEnabled(level)) {
                if (null == message || message.isEmpty()) {
                    message = LogReporting.NULL_MSG;
                }

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

        @Override
        public void log(LogLevel logLevel, String message) {
            logToAgent(logLevel, message);
        }

        public void logThrowable(LogLevel logLevel, String message, Throwable throwable) {
            StringWriter sw = new StringWriter();
            throwable.printStackTrace(new PrintWriter(sw));

            logToAgent(logLevel, String.format(Locale.getDefault(), "%s: %s", message, sw.toString()));
        }

        public void logAttributes(Map<String, Object> attributes) {
            String logLevel = (String) attributes.getOrDefault("level", LogLevel.INFO.name());
            String mapAsString = attributes.keySet().stream()
                    .map(key -> key + "=" + attributes.get(key))
                    .collect(Collectors.joining(",", "{", "}"));

            logToAgent(LogLevel.valueOf(logLevel.toUpperCase()), String.format(Locale.getDefault(),
                    "%s: %s", TAG, mapAsString));
        }

        public void logAll(Throwable throwable, Map<String, Object> attributes) {
            String logLevel = (String) attributes.getOrDefault("level", LogLevel.INFO.name());
            StringWriter sw = new StringWriter();
            String mapAsString = attributes.keySet().stream()
                    .map(key -> key + "=" + attributes.get(key))
                    .collect(Collectors.joining(",", "{", "}"));

            throwable.printStackTrace(new PrintWriter(sw));
            logToAgent(LogLevel.valueOf(logLevel.toUpperCase()), String.format(Locale.getDefault(),
                    "%s: %s %s", TAG, sw.toString(), mapAsString));
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

    public static void initialize(File cacheDir, AgentConfiguration agentConfiguration) throws IOException {
        LogReporting.setLogLevel(agentConfiguration.getLogReportingConfiguration().getLogLevel());
        LogReporter.initialize(cacheDir, agentConfiguration);
        if( LogReporter.getInstance().isEnabled()) {
            LogReporter.getInstance().start();
        }
    }

}
