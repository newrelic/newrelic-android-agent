/**
 * Copyright 2023-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay;

/**
 * Defines the recording modes for Session Replay.
 * These modes control when and how session replay data is captured and sent.
 */
public enum SessionReplayMode {
    /**
     * OFF mode - Session Replay is completely disabled.
     * No data is captured or stored.
     */
    OFF("off"),

    /**
     * ERROR mode - Session Replay runs in buffered mode, continuously recording
     * but only preserving and sending data when an error/crash occurs.
     * Uses a sliding window buffer to maintain the last 15-30 seconds of activity.
     *
     * Mode transitions:
     * - Switches to FULL mode when MobileCrash, MobileHandledException, or
     *   error-level HTTP responses (4xx, 5xx) are detected
     */
    ERROR("error"),

    /**
     * FULL mode - Session Replay continuously records and sends all data at regular
     * harvest intervals. This provides complete session coverage but uses more
     * bandwidth and storage.
     *
     * Once in FULL mode, the session remains in FULL mode for its duration.
     */
    FULL("full");

    private final String value;

    SessionReplayMode(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * Converts a string value to a SessionReplayMode enum.
     *
     * @param value The string value to convert
     * @return The corresponding SessionReplayMode, or ERROR as default if not recognized
     */
    public static SessionReplayMode fromString(String value) {
        if (value == null) {
            return ERROR; // Default to ERROR mode
        }

        for (SessionReplayMode mode : SessionReplayMode.values()) {
            if (mode.value.equalsIgnoreCase(value)) {
                return mode;
            }
        }

        return ERROR; // Default to ERROR mode if unknown
    }

    @Override
    public String toString() {
        return value;
    }
}