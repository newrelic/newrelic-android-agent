package com.newrelic.agent.android.logging;

/**
 * Level names should correspond to iOS values.
 * The ordinal values are not shared and are used for priority ordering.
 * These values represent RFC 5424 (The Syslog Protocol) levels
 */
public enum LogLevel {

    NONE(0),        // All logging disabled, not advised
    ERROR(1),       // App and system errors
    WARN(2),        // Errors and app warnings
    INFO(3),        // Useful app messages
    VERBOSE(4),     // When too much is just not enough
    DEBUG(5);       // Messaging to assist static analysis

    static final LogLevel[] levels = values();
    final int value;

    LogLevel(final int value) {
        this.value = value;
    }

}
