package com.newrelic.agent.android.sessionReplay.compose

import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver

/**
 * NewRelic privacy semantics property key for controlling session replay masking behavior
 */
@JvmField
val NewRelicPrivacyKey = SemanticsPropertyKey<String>(ComposeSessionReplayConstants.SemanticsKeys.NEW_RELIC_PRIVACY)

/**
 * Sets the NewRelic privacy behavior for session replay
 * @param value Either "nr-mask" to force masking or "nr-unmask" to prevent masking
 */
var SemanticsPropertyReceiver.newRelicPrivacy by NewRelicPrivacyKey