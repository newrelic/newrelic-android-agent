package com.newrelic.agent.android.sessionReplay.compose

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics

/**
 * NewRelic Compose modifiers for controlling session replay privacy behavior
 */
object NewRelicModifiers {

    /**
     * Modifier that marks a Compose element to be masked in session replay
     * This will prevent the content from being visible in session replay recordings
     */
    fun Modifier.newRelicMask(): Modifier = this.semantics {
        newRelicPrivacy = "nr-mask"
    }

    /**
     * Modifier that marks a Compose element to be unmasked in session replay
     * This will ensure the content is visible even when global masking is enabled
     */
    fun Modifier.newRelicUnmask(): Modifier = this.semantics {
        newRelicPrivacy = "nr-unmask"
    }

    /**
     * Modifier that applies custom NewRelic privacy behavior
     * @param privacyValue Custom privacy value ("nr-mask", "nr-unmask", or custom tag)
     */
    fun Modifier.newRelicPrivacy(privacyValue: String): Modifier = this.semantics {
        newRelicPrivacy = privacyValue
    }
}