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
import com.google.gson.annotations.SerializedName;

import java.lang.reflect.Type;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class LogReportingConfiguration extends LoggingConfiguration {
    static final String NO_VALUE = "";

    @SerializedName("entity_guid")
    final String entityGuid;

    @SerializedName("data_report_period")
    final Long harvestPeriod;

    @SerializedName("expiration_period")
    final Long expirationPeriod;

    public LogReportingConfiguration() {
        this(NO_VALUE, false, LogReporting.LogLevel.NONE);
    }

    public LogReportingConfiguration(String entityGuid, boolean enabled, LogReporting.LogLevel level) {
        super(enabled, level);
        this.entityGuid = entityGuid;
        this.harvestPeriod = TimeUnit.SECONDS.convert(30, TimeUnit.SECONDS);
        this.expirationPeriod = TimeUnit.SECONDS.convert(2, TimeUnit.DAYS);
    }

    public LogReportingConfiguration(String entityGuid, long harvestPeriod, long expirationPeriod) {
        this.entityGuid = entityGuid;
        this.harvestPeriod = harvestPeriod;
        this.expirationPeriod = expirationPeriod;
    }

    public LogReportingConfiguration(boolean enabled, LogReporting.LogLevel level, String entityGuid, long harvestPeriod, long expirationPeriod) {
        this(entityGuid, harvestPeriod, expirationPeriod);
        this.enabled = enabled;
        this.level = level;
    }

    public String getEntityGuid() {
        return entityGuid;
    }

    public long getHarvestPeriod() {
        return harvestPeriod;
    }

    public long getExpirationPeriod() {
        return expirationPeriod;
    }

    @Override
    public String toString() {
        return "{"
                + "\"enabled\"=" + enabled
                + ",\"level\"=" + "\"" + level + "\""
                + ",\"entity_guid\"=" + "\"" + entityGuid + "\""
                + ",\"data_report_period\"=" + harvestPeriod
                + ",\"expiration_period\"=" + expirationPeriod
                + "}";
    }

    public static class ConfigurationDeserializer extends LoggingConfiguration.ConfigurationDeserializer {
        final AgentLog log = AgentLogManager.getAgentLog();

        @Override
        public LogReportingConfiguration deserialize(JsonElement root, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            LoggingConfiguration loggingConfiguration = super.deserialize(root, typeOfT, context);

            if (!root.isJsonObject()) {
                log.error("Expected root element to be an object.");
                return new LogReportingConfiguration();
            }

            JsonObject jsonObject = root.getAsJsonObject();

            return new LogReportingConfiguration(
                    loggingConfiguration.getLoggingEnabled(),
                    loggingConfiguration.getLogLevel(),
                    jsonObject.get("entity_guid").getAsString(),
                    jsonObject.get("data_report_period").getAsLong(),
                    jsonObject.get("expiration_period").getAsLong());
        }
    }

}
