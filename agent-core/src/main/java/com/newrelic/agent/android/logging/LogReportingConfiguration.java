/*
 * Copyright (c) 2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.logging;

import com.google.gson.annotations.SerializedName;

import java.util.concurrent.TimeUnit;

public class LogReportingConfiguration extends LoggingConfiguration {
    /**
     * The sample seed is the coin flip that determines whether logging is enabled during
     * this app lifetime. It should only be set once. Range is [1...100];
     */
    static int sampleSeed = 100;

    static final long DEFAULT_HARVEST_PERIOD = TimeUnit.SECONDS.convert(30, TimeUnit.SECONDS);
    static final long DEFAULT_EXPIRATION_PERIOD = TimeUnit.SECONDS.convert(2, TimeUnit.DAYS);

    @SerializedName("data_report_period")
    Long harvestPeriod;

    @SerializedName("expiration_period")
    Long expirationPeriod;

    @SerializedName("sampling_rate")
    int sampleRate;

    public LogReportingConfiguration() {
        this(false, LogLevel.NONE);
    }

    public LogReportingConfiguration(boolean enabled, LogLevel level) {
        this(enabled, level, DEFAULT_HARVEST_PERIOD, DEFAULT_EXPIRATION_PERIOD, sampleSeed);
    }

    public LogReportingConfiguration(boolean enabled, LogLevel level, long harvestPeriod, long expirationPeriod, int sampleRate) {
        super(enabled, level);
        this.harvestPeriod = harvestPeriod;
        this.expirationPeriod = expirationPeriod;
        this.sampleRate = Math.min(100, Math.max(0, sampleRate));
    }

    public void setConfiguration(LogReportingConfiguration logReportingConfiguration) {
        super.setConfiguration(logReportingConfiguration);
        enabled = logReportingConfiguration.enabled;
        level = logReportingConfiguration.level;
        sampleRate = logReportingConfiguration.sampleRate;
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
                + ",\"sampling_rate\"=" + sampleRate
                + "}";
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof LogReportingConfiguration) {
            LogReportingConfiguration rhs = (LogReportingConfiguration) obj;
            return enabled == rhs.enabled &&
                    level.equals(rhs.level) &&
                    sampleRate == rhs.sampleRate;
        }
        return false;
    }

    /**
     * @return true is remote logging is enabled AND this session is sampling
     */
    @Override
    public boolean getLoggingEnabled() {
        return enabled && isSampled();
    }

    /**
     * @return true if the generated sample seed is less than or equal to the configured sampling rate
     */
    public boolean isSampled() {
        return sampleSeed <= sampleRate;
    }

    /**
     * Generate a suitable seed. Range is [1...100];
     */
    protected static int reseed() {
        sampleSeed = (int) (Math.random() * 100.0) + 1;
        return sampleSeed;
    }
}