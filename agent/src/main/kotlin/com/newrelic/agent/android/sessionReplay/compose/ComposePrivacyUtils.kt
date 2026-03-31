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

    /**
     * Checks if this node or any ancestor has the BLOCK privacy tag.
     * This walks up the parent chain because block propagation during
     * tree capture may not have run yet (e.g., touch events arrive independently).
     */
    fun hasBlockedAncestor(node: SemanticsNode): Boolean {
        var current: SemanticsNode? = node
        while (current != null) {
            val tag = current.config.getOrNull(NewRelicPrivacyKey)
            if (ComposeSessionReplayConstants.PrivacyTags.BLOCK == tag) {
                return true
            }
            current = current.parent
        }
        return false
    }
}