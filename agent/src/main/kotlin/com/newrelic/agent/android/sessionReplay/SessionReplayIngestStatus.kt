/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay

/**
 * What [com.newrelic.agent.android.NewRelic.recordSessionReplayEvents] returns. A status never
 * starts or stops capture.
 */
object SessionReplayIngestStatus {
    /** The agent took the batch and stored it for its next upload. */
    const val ACCEPTED = "accepted"

    /**
     * Recording is off, in error mode, or the agent has not started, or the application framework
     * is not Flutter (for example an add-to-app host that starts the agent itself). Also returned
     * if the agent could not queue the batch. Drop the batch, don't retry.
     */
    const val DROPPED_DISABLED = "dropped_disabled"

    /** The batch is empty or cannot be encoded as UTF-8. Drop the batch, don't retry. */
    const val DROPPED_INVALID = "dropped_invalid"
}
