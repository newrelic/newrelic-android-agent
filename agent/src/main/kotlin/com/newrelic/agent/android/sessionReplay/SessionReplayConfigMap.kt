/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay

import java.util.Collections

/**
 * Builds the map handed to [SessionReplayConfigListener]s and returned by
 * `NewRelic.getSessionReplayConfiguration()`. This is the only place it is built, so the getter
 * and the callback cannot disagree. Same contract as the iOS agent.
 */
object SessionReplayConfigMap {

    const val ENABLED = "enabled"
    const val MODE = "mode"
    const val RECORDING_MODE = "recordingMode"
    const val MASK_APPLICATION_TEXT = "maskApplicationText"
    const val MASK_USER_INPUT_TEXT = "maskUserInputText"
    const val MASK_ALL_IMAGES = "maskAllImages"
    const val MASK_ALL_USER_TOUCHES = "maskAllUserTouches"

    // a configuration parsed from a response with "mode": null has no mode
    private const val DEFAULT_MODE = "default"

    /**
     * @param configuration the resolved configuration, or null when nothing has resolved
     * @param mode the mode that is recording right now, or null when nothing is
     * @return an unmodifiable map. `recordingMode` is `"off"` whenever the configuration is not
     * enabled, otherwise the mode that is currently recording. Exactly `{enabled: false, recordingMode: "off"}` when the
     * configuration is null or not enabled, whatever [mode] says: a later config can disable
     * replay without anything resetting the mode manager, and listeners follow `recordingMode`.
     */
    @JvmStatic
    fun create(configuration: SessionReplayConfiguration?, mode: SessionReplayMode?): Map<String, Any> {
        if (configuration == null || !configuration.isEnabled) return off()
        return Collections.unmodifiableMap(
            linkedMapOf<String, Any>(
                ENABLED to true,
                MODE to (configuration.mode ?: DEFAULT_MODE),
                RECORDING_MODE to (mode ?: SessionReplayMode.OFF).value,
                MASK_APPLICATION_TEXT to configuration.isMaskApplicationText,
                MASK_USER_INPUT_TEXT to configuration.isMaskUserInputText,
                MASK_ALL_IMAGES to configuration.isMaskAllImages,
                MASK_ALL_USER_TOUCHES to configuration.isMaskAllUserTouches,
            )
        )
    }

    /** What the agent reports before it starts, when nothing has resolved, and when replay is disabled. */
    @JvmStatic
    fun off(): Map<String, Any> = Collections.unmodifiableMap(
        linkedMapOf<String, Any>(ENABLED to false, RECORDING_MODE to SessionReplayMode.OFF.value)
    )
}
