/*
 * Copyright (c) 2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.logging;

import com.google.gson.annotations.SerializedName;
import com.newrelic.agent.android.AgentConfiguration;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class LoggingConfiguration {

    @SerializedName("enabled")
    final AtomicBoolean enabled = new AtomicBoolean(false);

    @SerializedName("level")
    final AtomicReference<LogReporting.LogLevel> level = new AtomicReference<>(LogReporting.LogLevel.WARN);

    public LoggingConfiguration() {
        this(false, LogReporting.LogLevel.NONE);
    }

    public LoggingConfiguration(boolean enabled, LogReporting.LogLevel level) {
        this.enabled.set(enabled);
        this.level.set(level);
    }

    public void setConfiguration(AgentConfiguration agentConfiguration) {
        setConfiguration(agentConfiguration.getLogReportingConfiguration());
    }

    public void setConfiguration(LoggingConfiguration loggingConfiguration) {
        enabled.set(loggingConfiguration.enabled.get());
        level.set(loggingConfiguration.level.get());
    }

    /**
     * Returns current logging enabled value
     *
     * @return true if logging is enabled
     */
    public boolean getLoggingEnabled() {
        return enabled.get();
    }

    /**
     * Sets enabled flag to new state
     *
     * @param state enabled flag
     * @return Previous enabled atate
     */
    public boolean setLoggingEnabled(final boolean state) {
        return enabled.getAndSet(state);
    }

    /**
     * Returns current logging level
     *
     * @return
     */
    public LogReporting.LogLevel getLogLevel() {
        return level.get();
    }

    /**
     * Set the log reporting level
     *
     * @param level
     */
    public void setLogLevel(LogReporting.LogLevel level) {
        this.level.set(level);
    }

    @Override
    public String toString() {
        return "\"logging\" {" + "\"enabled\"=" + enabled + ", \"level\"=\"" + level.get().name().toUpperCase() + "\"}";
    }
}
