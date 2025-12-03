/**
 * Copyright 2023-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay;

import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.stats.StatsEngine;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages the current Session Replay recording mode.
 * This class is thread-safe and simply tracks the current mode.
 * Business logic for mode transitions should be handled by the calling code.
 *
 * This is a singleton class to ensure only one mode manager exists per application lifecycle.
 */
public class SessionReplayModeManager {
    private static final AgentLog log = AgentLogManager.getAgentLog();
    private static volatile SessionReplayModeManager instance;
    private static final Object lock = new Object();

    private final AtomicReference<SessionReplayMode> currentMode;
    private final SessionReplayConfiguration configuration;

    private SessionReplayModeManager(SessionReplayConfiguration configuration) {
        this.configuration = configuration;

        // Initialize based on configuration
        SessionReplayMode initialMode = SessionReplayMode.fromString(configuration.getMode());

        // If disabled, start in OFF mode regardless of config
        if (!configuration.isEnabled()) {
            initialMode = SessionReplayMode.OFF;
        }

        this.currentMode = new AtomicReference<>(initialMode);

        log.debug("SessionReplayModeManager initialized with mode: " + initialMode);
        StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_SESSION_REPLAY_MODE + "/" + initialMode.getValue());
    }

    /**
     * Gets the singleton instance of SessionReplayModeManager.
     * Creates a new instance if one doesn't exist yet.
     *
     * @param configuration The SessionReplayConfiguration to use (only used on first initialization)
     * @return The singleton instance of SessionReplayModeManager
     */
    public static SessionReplayModeManager getInstance(SessionReplayConfiguration configuration) {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new SessionReplayModeManager(configuration);
                }
            }
        }
        return instance;
    }

    /**
     * Gets the existing singleton instance of SessionReplayModeManager.
     * Returns null if not yet initialized.
     *
     * @return The singleton instance, or null if not initialized
     */
    public static SessionReplayModeManager getInstance() {
        return instance;
    }

    /**
     * Resets the singleton instance. Should only be used for testing or cleanup.
     * This is package-private to prevent misuse.
     */
    static void resetInstance() {
        synchronized (lock) {
            instance = null;
        }
    }

    /**
     * Gets the current recording mode.
     *
     * @return The current SessionReplayMode
     */
    public SessionReplayMode getCurrentMode() {
        return currentMode.get();
    }

    /**
     * Checks if Session Replay is currently recording (not in OFF mode).
     *
     * @return true if recording (ERROR or FULL mode), false if OFF
     */
    public boolean isRecording() {
        return currentMode.get() != SessionReplayMode.OFF;
    }

    /**
     * Checks if in FULL recording mode.
     *
     * @return true if in FULL mode, false otherwise
     */
    public boolean isFullMode() {
        return currentMode.get() == SessionReplayMode.FULL;
    }

    /**
     * Checks if in ERROR (buffered) mode.
     *
     * @return true if in ERROR mode, false otherwise
     */
    public boolean isErrorMode() {
        return currentMode.get() == SessionReplayMode.ERROR;
    }

    /**
     * Transitions to a new mode.
     * Simply sets the mode to the requested value without validation.
     * The calling code is responsible for enforcing any business rules.
     *
     * @param newMode The mode to transition to
     * @param trigger The reason/trigger for the transition (for logging)
     * @return true if the transition was successful, false if already in target mode
     */
    public boolean transitionTo(SessionReplayMode newMode, String trigger) {
        SessionReplayMode oldMode = currentMode.get();

        // If already in the target mode, no transition needed
        if (oldMode == newMode) {
            log.debug("SessionReplay: Already in " + newMode + " mode. Trigger: " + trigger);
            return false;
        }

        // Perform the transition
        if (currentMode.compareAndSet(oldMode, newMode)) {
            log.info("SessionReplay: Mode transition: " + oldMode + " → " + newMode + ". Trigger: " + trigger);
            StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_SESSION_REPLAY_MODE_TRANSITION + "/" + oldMode.getValue() + "_to_" + newMode.getValue());
            return true;
        }

        // CAS failed, another thread changed the mode
        log.debug("SessionReplay: Mode transition failed due to concurrent modification. Trigger: " + trigger);
        return false;
    }
    /**
     * Gets configuration for the current mode manager.
     *
     * @return The SessionReplayConfiguration
     */
    public SessionReplayConfiguration getConfiguration() {
        return configuration;
    }
}