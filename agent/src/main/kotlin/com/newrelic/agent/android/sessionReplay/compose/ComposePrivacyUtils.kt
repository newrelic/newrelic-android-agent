package com.newrelic.agent.android.sessionReplay.compose

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull

/**
 * Utility class for handling privacy-related operations in Compose session replay
 */
object ComposePrivacyUtils {

    /**
     * Gets the effective privacy tag by checking the node and all its parents.
     * Returns the first privacy tag found when walking up the tree.
     * Child tags take precedence over parent tags.
     *
     * @param node The SemanticsNode to check for privacy tags
     * @return The privacy tag string ("nr-mask", "nr-unmask", or custom value), or empty string if none found
     */
    fun getEffectivePrivacyTag(node: SemanticsNode): String {
        // Check current node first
        val currentTag = node.config.getOrNull(NewRelicPrivacyKey)
        if (!currentTag.isNullOrEmpty()) {
            return currentTag
        }

        // Walk up the parent chain
        var parent = node.parent
        while (parent != null) {
            val parentTag = parent.config.getOrNull(NewRelicPrivacyKey)
            if (!parentTag.isNullOrEmpty()) {
                return parentTag
            }
            parent = parent.parent
        }

        return ""
    }

    /**
     * Checks if a node or any of its parents has a mask tag
     *
     * @param node The SemanticsNode to check
     * @return true if the effective privacy tag is "nr-mask"
     */
    fun hasEffectiveMaskTag(node: SemanticsNode): Boolean {
        return getEffectivePrivacyTag(node) == "nr-mask"
    }

    /**
     * Checks if a node or any of its parents has an unmask tag
     *
     * @param node The SemanticsNode to check
     * @return true if the effective privacy tag is "nr-unmask"
     */
    fun hasEffectiveUnmaskTag(node: SemanticsNode): Boolean {
        return getEffectivePrivacyTag(node) == "nr-unmask"
    }
}