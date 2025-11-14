package com.newrelic.agent.android.sessionReplay.compose

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics

/**
 * NewRelic Compose modifiers for controlling session replay privacy behavior
 *
 * Privacy tags are hierarchical and automatically apply to all child elements within
 * a container. Child elements can override parent privacy settings by applying their
 * own privacy modifier.
 *
 * Privacy tags affect both visual content and touch interactions:
 * - Masked elements will have their text/content hidden in session replay
 * - Touch events on masked elements will use a generic ID (preventing individual tracking)
 *
 * Example:
 * ```
 * Column(modifier = Modifier.newRelicMask()) {
 *     Text("This is masked") // Inherits mask from parent Column
 *     Text("Also masked")    // Inherits mask from parent Column
 *     Button(onClick = {}) { Text("Sensitive Button") } // Touches are masked too
 *     Text("Visible!", modifier = Modifier.newRelicUnmask()) // Overrides parent mask
 * }
 * ```
 */
object NewRelicModifiers {

    /**
     * Modifier that marks a Compose element to be masked in session replay.
     * This will prevent the content from being visible in session replay recordings
     * and mask touch interactions on this element.
     *
     * When applied to a container (Column, Row, Box), all child elements will
     * automatically be masked unless they explicitly use newRelicUnmask().
     *
     * Effects:
     * - Visual content (text, images) will be hidden or redacted
     * - Touch events will use a generic masked ID instead of the element's unique ID
     *
     * @return Modified Modifier with masking semantics
     */
    fun Modifier.newRelicMask(): Modifier = this.semantics {
        newRelicPrivacy = ComposeSessionReplayConstants.PrivacyTags.MASK
    }

    /**
     * Modifier that marks a Compose element to be unmasked in session replay.
     * This will ensure the content and touch interactions are visible even when:
     * - A parent container has masking enabled
     * - Global masking is enabled
     *
     * Effects:
     * - Visual content will be captured normally
     * - Touch events will use the element's actual ID for tracking
     *
     * @return Modified Modifier with unmasking semantics
     */
    fun Modifier.newRelicUnmask(): Modifier = this.semantics {
        newRelicPrivacy = ComposeSessionReplayConstants.PrivacyTags.UNMASK
    }

    /**
     * Modifier that applies custom NewRelic privacy behavior.
     *
     * @param privacyValue Custom privacy value ("nr-mask", "nr-unmask", or custom tag)
     * @return Modified Modifier with custom privacy semantics
     */
    fun Modifier.newRelicPrivacy(privacyValue: String): Modifier = this.semantics {
        newRelicPrivacy = privacyValue
    }
}