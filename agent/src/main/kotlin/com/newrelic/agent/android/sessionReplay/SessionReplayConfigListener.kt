/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay

/**
 * Hears when the resolved Session Replay configuration or the current recording mode changes.
 * Register with [SessionReplay.addConfigListener]; unregister with
 * [SessionReplay.removeConfigListener].
 *
 * Called when:
 * - the agent starts with a saved configuration;
 * - the saved configuration or the `/connect` response resolves the Session Replay
 *   configuration, including when it resolves to disabled;
 * - a later harvest updates the configuration;
 * - a new session is sampled, including a session whose recorder is not started because the
 *   app is in the background;
 * - an error switches recording from [SessionReplayMode.ERROR] to [SessionReplayMode.FULL];
 * - `pauseReplay()` or `recordReplay()` changes the mode.
 *
 * Not called when a session ends, when `pauseReplay()` or `recordReplay()` changes nothing, or
 * for an error that changes nothing.
 *
 * Registering works at any time, including before the agent starts. Listeners stay registered
 * across sessions. A listener that registers after something was reported is called once with
 * the latest values.
 *
 * Threading: listeners are called on the main thread, in the order the changes happened.
 * Return quickly. The arguments describe the state at the moment of the change: the
 * configuration is a copy, so it does not change afterwards, and it must be treated as
 * read-only.
 *
 * The same values can arrive more than once (a `/connect` that changes the configuration
 * reports both the connect and the change; a saved configuration is reported at start and
 * again when the harvester connects), so handle repeats.
 *
 * Listeners are held for the life of the process, so remove them with
 * [SessionReplay.removeConfigListener] when the host detaches, and do not capture an Activity.
 * A lambda cannot be removed by a second, identical lambda, so keep a reference to the instance
 * you registered.
 *
 * A listener that throws is logged and ignored.
 */
fun interface SessionReplayConfigListener {

    /**
     * @param configuration the resolved configuration; check [SessionReplayConfiguration.isEnabled]
     * @param mode the current recording mode, [SessionReplayMode.OFF] when nothing is recording
     */
    fun onSessionReplayConfigChanged(configuration: SessionReplayConfiguration, mode: SessionReplayMode)
}
