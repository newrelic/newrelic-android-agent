/*
 * Copyright (c) 2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.logging;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.annotations.SerializedName;
import com.newrelic.agent.android.AgentConfiguration;

import java.lang.reflect.Type;

public class LoggingConfiguration {
    static final AgentLog log = AgentLogManager.getAgentLog();

    @SerializedName("enabled")
    boolean enabled = false;

    @SerializedName("level")
    LogReporting.LogLevel level;

    public LoggingConfiguration() {
        this(false, LogReporting.LogLevel.NONE);
    }

    public LoggingConfiguration(boolean enabled, LogReporting.LogLevel level) {
        this.enabled = enabled;
        this.level = level;
    }

    public void setConfiguration(AgentConfiguration agentConfiguration) {
        setConfiguration(agentConfiguration.getLogReportingConfiguration());
    }

    public void setConfiguration(LoggingConfiguration loggingConfiguration) {
        enabled = loggingConfiguration.enabled;
        level = loggingConfiguration.level;
    }

    /**
     * Returns current logging enabled value
     *
     * @return true if logging is enabled
     */
    public boolean getLoggingEnabled() {
        return enabled;
    }

    /**
     * Sets enabled flag to new state
     *
     * @param state enabled flag
     * @return Previous enabled atate
     */
    public boolean setLoggingEnabled(final boolean state) {
        return enabled = state;
    }

    /**
     * Returns current logging level
     *
     * @return
     */
    public LogReporting.LogLevel getLogLevel() {
        return level;
    }

    /**
     * Set the log reporting level
     *
     * @param level
     */
    public void setLogLevel(LogReporting.LogLevel level) {
        this.level = level;
    }

    @Override
    public String toString() {
        return "\"LoggingConfiguration\" {" + "\"enabled\"=" + enabled + ", \"level\"=\"" + level + "\"}";
    }

    public static class LogLevelSerializer implements JsonSerializer<LogReporting.LogLevel> {
        @Override
        public JsonElement serialize(LogReporting.LogLevel src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src.name());
        }
    }

    public static class LogLevelDeserializer implements JsonDeserializer<LogReporting.LogLevel> {
        @Override
        public LogReporting.LogLevel deserialize(JsonElement root, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            return LogReporting.LogLevel.valueOf(root.getAsString());
        }
    }

    public static class ConfigurationSerializer implements JsonSerializer<LoggingConfiguration> {
        @Override
        public JsonElement serialize(LoggingConfiguration src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive("{\"enabled\"=" + src.enabled + ",\"level\"=\"" + src.getLogLevel() + "\"}");
        }
    }

    public static class ConfigurationDeserializer implements JsonDeserializer<LoggingConfiguration> {
        @Override
        public LoggingConfiguration deserialize(JsonElement root, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (!root.isJsonObject()) {
                log.error("Expected root element to be an object.");
                return new LoggingConfiguration();
            }

            JsonObject jsonObject = root.getAsJsonObject();
            return new LoggingConfiguration(
                    jsonObject.get("enabled").getAsBoolean(),
                    LogReporting.LogLevel.valueOf(jsonObject.get("level").getAsString()));
        }
    }

}
