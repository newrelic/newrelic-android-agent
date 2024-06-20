package com.newrelic.agent.android.logging;

import com.google.gson.annotations.SerializedName;

/**
 * Level names should correspond to iOS values.
 * The ordinal values are not shared and are used for priority ordering.
 * These values represent RFC 5424 (The Syslog Protocol) levels
 */
public enum LogLevel {

    @SerializedName(value = "NONE", alternate = {"none"})
    NONE(0),        // All logging disabled, not advised

    @SerializedName(value = "ERROR", alternate = {"error"})
    ERROR(1),       // App and system errors

    @SerializedName(value = "WARN", alternate = {"warn"})
    WARN(2),        // Errors and app warnings

    @SerializedName(value = "INFO", alternate = {"info"})
    INFO(3),        // Useful app messages

    @SerializedName(value = "VERBOSE", alternate = {"verbose"})
    VERBOSE(4),     // When too much is just not enough

    @SerializedName(value = "DEBUG", alternate = {"debug"})
    DEBUG(5);       // Messaging to assist static analysis

    static final LogLevel[] levels = values();
    final int value;

    LogLevel(final int value) {
        this.value = value;
    }

}
