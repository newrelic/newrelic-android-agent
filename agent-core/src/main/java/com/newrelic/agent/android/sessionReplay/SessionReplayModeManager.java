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
 * Manages the current Session Replay recording mode and handles mode transitions.
 * This class is thread-safe and ensures that mode transitions follow the allowed patterns:
 * - OFF can transition to ERROR or FULL
 * - ERROR can transition to FULL or OFF
 * - FULL cannot transition (session stays in FULL mode once upgraded)
 * - OFF is terminal (once stopped, cannot restart in same session)
 */
public class SessionReplayModeManager {
    private static final AgentLog log = AgentLogManager.getAgentLog();
    private final AtomicReference<SessionReplayMode> currentMode;
    private final SessionReplayConfiguration configuration;
    private volatile boolean hasTransitionedToFull = false;
    private volatile boolean hasStopped = false;

    public SessionReplayModeManager(SessionReplayConfiguration configuration) {
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
     * Attempts to transition to a new mode.
     * This method enforces the allowed transition rules.
     *
     * @param newMode The mode to transition to
     * @param trigger The reason/trigger for the transition (for logging)
     * @return true if the transition was successful, false if not allowed
     */
    public boolean transitionTo(SessionReplayMode newMode, String trigger) {
        SessionReplayMode oldMode = currentMode.get();

        // If already stopped, don't allow any transitions
        if (hasStopped && oldMode == SessionReplayMode.OFF) {
            log.debug("SessionReplay: Cannot transition from OFF after stopping. Trigger: " + trigger);
            return false;
        }

        // If already in the target mode, no transition needed
        if (oldMode == newMode) {
            log.debug("SessionReplay: Already in " + newMode + " mode. Trigger: " + trigger);
            return false;
        }

        // Validate transition rules
        if (!isTransitionAllowed(oldMode, newMode)) {
            log.warn("SessionReplay: Transition from " + oldMode + " to " + newMode + " is not allowed. Trigger: " + trigger);
            return false;
        }

        // Perform the transition
        if (currentMode.compareAndSet(oldMode, newMode)) {
            log.info("SessionReplay: Mode transition: " + oldMode + " → " + newMode + ". Trigger: " + trigger);
            StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_SESSION_REPLAY_MODE_TRANSITION + "/" + oldMode.getValue() + "_to_" + newMode.getValue());

            // Track if we've transitioned to FULL
            if (newMode == SessionReplayMode.FULL) {
                hasTransitionedToFull = true;
            }

            // Track if we've stopped
            if (newMode == SessionReplayMode.OFF) {
                hasStopped = true;
            }

            return true;
        }

        // CAS failed, another thread changed the mode
        log.debug("SessionReplay: Mode transition failed due to concurrent modification. Trigger: " + trigger);
        return false;
    }

    /**
     * Handles a crash event - transitions ERROR mode to FULL mode.
     * This allows the full session replay buffer to be sent and continues recording.
     *
     * @return true if mode changed, false otherwise
     */
    public boolean handleCrash() {
        if (currentMode.get() == SessionReplayMode.ERROR) {
            return transitionTo(SessionReplayMode.FULL, "MobileCrash");
        }
        return false;
    }

    /**
     * Handles a handled exception event - transitions ERROR mode to FULL mode.
     *
     * @return true if mode changed, false otherwise
     */
    public boolean handleHandledException() {
        if (currentMode.get() == SessionReplayMode.ERROR) {
            return transitionTo(SessionReplayMode.FULL, "MobileHandledException");
        }
        return false;
    }

    /**
     * Handles an HTTP error response (4xx, 5xx) - transitions ERROR mode to FULL mode.
     *
     * @param statusCode The HTTP status code
     * @return true if mode changed, false otherwise
     */
    public boolean handleHttpError(int statusCode) {
        if (currentMode.get() == SessionReplayMode.ERROR && isHttpError(statusCode)) {
            return transitionTo(SessionReplayMode.FULL, "MobileRequest/HTTPError/" + statusCode);
        }
        return false;
    }

    /**
     * Stops session replay recording by transitioning to OFF mode.
     *
     * @param reason The reason for stopping
     * @return true if transitioned to OFF, false if already OFF
     */
    public boolean stop(String reason) {
        return transitionTo(SessionReplayMode.OFF, reason);
    }

    /**
     * Checks if an HTTP status code represents an error (4xx or 5xx).
     *
     * @param statusCode The HTTP status code
     * @return true if error status code, false otherwise
     */
    private boolean isHttpError(int statusCode) {
        return statusCode >= 400 && statusCode < 600;
    }

    /**
     * Validates if a mode transition is allowed based on business rules.
     *
     * @param from The current mode
     * @param to The target mode
     * @return true if transition is allowed, false otherwise
     */
    private boolean isTransitionAllowed(SessionReplayMode from, SessionReplayMode to) {
        switch (from) {
            case OFF:
                // OFF can only transition to ERROR or FULL at session start
                // Once stopped (hasStopped=true), no transitions allowed
                return !hasStopped && (to == SessionReplayMode.ERROR || to == SessionReplayMode.FULL);

            case ERROR:
                // ERROR can transition to FULL or OFF
                return to == SessionReplayMode.FULL || to == SessionReplayMode.OFF;

            case FULL:
                // FULL can only transition to OFF (stop)
                // Once in FULL mode, cannot go back to ERROR
                return to == SessionReplayMode.OFF;

            default:
                return false;
        }
    }

    /**
     * Checks if the session has ever been in FULL mode.
     *
     * @return true if session transitioned to FULL at some point, false otherwise
     */
    public boolean hasTransitionedToFull() {
        return hasTransitionedToFull;
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