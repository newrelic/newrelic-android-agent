/*
 * Copyright (c) 2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.logging;

import com.google.gson.annotations.SerializedName;
import com.newrelic.agent.android.AgentConfiguration;

public class LoggingConfiguration {

    @SerializedName("enabled")
    boolean enabled = false;

    @SerializedName("level")
    LogLevel level;

    public LoggingConfiguration() {
        this(false, LogLevel.NONE);
    }

    public LoggingConfiguration(boolean enabled, LogLevel level) {
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
    public LogLevel getLogLevel() {
        return level;
    }

    /**
     * Set the log reporting level
     *
     * @param level
     */
    public void setLogLevel(LogLevel level) {
        this.level = level;
    }

    @Override
    public String toString() {
        return "\"LoggingConfiguration\" {" + "\"enabled\"=" + enabled + ", \"level\"=\"" + level + "\"}";
    }

}
