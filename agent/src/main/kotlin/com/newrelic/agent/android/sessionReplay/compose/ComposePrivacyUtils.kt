package com.newrelic.agent.android.sessionReplay.compose

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull

/**
 * Utility class for handling privacy-related operations in Compose session replay
 */
object ComposePrivacyUtils {

    fun getEffectivePrivacyTag(node: SemanticsNode): String {
        val currentTag = node.config.getOrNull(NewRelicPrivacyKey)
        if (!currentTag.isNullOrEmpty()) {
            return currentTag
        }
        return ""
    }
}