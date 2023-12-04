/*
 * Copyright (c) 2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.logging;

import com.google.gson.annotations.SerializedName;

import java.util.concurrent.TimeUnit;

public class LogReportingConfiguration extends LoggingConfiguration {
    static final long DEFAULT_HARVEST_PERIOD = TimeUnit.SECONDS.convert(30, TimeUnit.SECONDS);
    static final long DEFAULT_EXPIRATION_PERIOD = TimeUnit.SECONDS.convert(2, TimeUnit.DAYS);

    @SerializedName("data_report_period")
    final Long harvestPeriod;

    @SerializedName("expiration_period")
    final Long expirationPeriod;

    public LogReportingConfiguration() {
        this(false, LogReporting.LogLevel.NONE);
    }

    public LogReportingConfiguration(boolean enabled, LogReporting.LogLevel level) {
        super(enabled, level);
        this.harvestPeriod = DEFAULT_HARVEST_PERIOD;
        this.expirationPeriod = DEFAULT_EXPIRATION_PERIOD;
    }

    public LogReportingConfiguration(long harvestPeriod, long expirationPeriod) {
        this.harvestPeriod = harvestPeriod;
        this.expirationPeriod = expirationPeriod;
    }

    public LogReportingConfiguration(boolean enabled, LogReporting.LogLevel level, long harvestPeriod, long expirationPeriod) {
        this(harvestPeriod, expirationPeriod);
        this.enabled = enabled;
        this.level = level;
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
                + ",\"data_report_period\"=" + harvestPeriod
                + ",\"expiration_period\"=" + expirationPeriod
                + "}";
    }

}
