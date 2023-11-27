/*
 * Copyright (c) 2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.logging;


/**
 * LogReporting public interface, exposed to static API
 */

public interface LogReporting {

    /**
     * Level names should correspond to iOS values.
     * The ordinal values are not shared and are used for priority ordering
     */
    enum LogLevel {

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

}
