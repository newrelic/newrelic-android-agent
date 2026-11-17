/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay

/**
 * Resets the process-wide state the config listener tests share. Lives in this package because
 * the things it resets are package-private.
 */
object SessionReplayConfigListenerTestSupport {

    // Deliberately not reset: SessionReplay.application, agentConfiguration, isFirstChunk and
    // listenerRegistered. Every test calls initialize() or never touches them, and none of the
    // listener assertions depend on their previous values.
    @JvmStatic
    fun reset() {
        SessionReplay.resetConfigListeners()
        SessionReplayModeManager.resetInstance()
    }
}
