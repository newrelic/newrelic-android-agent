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
    DEBUG(4),       // Messaging to assist static analysis
    VERBOSE(5);     // When too much is just not enough

    final int value;
    static final LogLevel levels[] = values();

    LogLevel(final int value) {
        this.value = value;
    }

}
