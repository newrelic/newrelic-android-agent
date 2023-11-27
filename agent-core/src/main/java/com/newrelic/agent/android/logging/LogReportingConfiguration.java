/*
 * Copyright (c) 2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.logging;

import com.google.gson.annotations.SerializedName;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class LogReportingConfiguration extends LoggingConfiguration {
    @SerializedName("entity_guid")
    final String entityGuid;
    @SerializedName("data_report_period")
    final AtomicLong harvestPeriod = new AtomicLong(TimeUnit.SECONDS.convert(30, TimeUnit.SECONDS));

    @SerializedName("expiration_period")
    final AtomicLong expirationPeriod = new AtomicLong(TimeUnit.SECONDS.convert(2, TimeUnit.DAYS));

    public LogReportingConfiguration(String entityGuid, boolean enabled, LogReporting.LogLevel level) {
        this.entityGuid = entityGuid;
        this.enabled.set(enabled);
        this.level.set(level);
    }

    public LogReportingConfiguration(String entityGuid, long harvestPeriod, long expirationPeriod) {
        this.entityGuid = entityGuid;
        this.harvestPeriod.set(harvestPeriod);
        this.expirationPeriod.set(expirationPeriod);
    }

    public LogReportingConfiguration(boolean enabled, LogReporting.LogLevel level, String entityGuid, long harvestPeriod, long expirationPeriod) {
        this(entityGuid, harvestPeriod, expirationPeriod);
        this.enabled.set(enabled);
        this.level.set(level);
    }

    public String getEntityGuid() {
        return entityGuid;
    }

    public long getHarvestPeriod() {
        return harvestPeriod.get();
    }

    public long getExpirationPeriod() {
        return expirationPeriod.get();
    }

    @Override
    public String toString() {
        return "\"log_reporting\" {"
                + "\"enabled\"=" + enabled
                + ",\"level\"=" + "\"" + level.get().name().toUpperCase() + "\""
                + ",\"entity_guid\"=" + "\"" + entityGuid + "\""
                + ",\"data_report_period\"=" + harvestPeriod
                + ",\"expiration_period\"=" + expirationPeriod
                + "}";
    }
}
