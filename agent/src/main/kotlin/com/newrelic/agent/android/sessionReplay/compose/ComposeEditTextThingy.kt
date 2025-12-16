package com.newrelic.agent.android.sessionReplay.compose

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.newrelic.agent.android.AgentConfiguration
import com.newrelic.agent.android.sessionReplay.NewRelicIdGenerator
import com.newrelic.agent.android.sessionReplay.SessionReplayViewThingyInterface
import com.newrelic.agent.android.sessionReplay.models.Attributes
import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.MutationRecord
import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.RRWebMutationData
import com.newrelic.agent.android.sessionReplay.models.RRWebElementNode
import com.newrelic.agent.android.sessionReplay.models.RRWebTextNode

/**
 * ComposeEditTextThingy handles editable text input fields in Compose UI
 * Extends ComposeTextViewThingy to inherit text styling and display logic
 */
class ComposeEditTextThingy(
    private val viewDetails: ComposeViewDetails,
    private val semanticsNode: SemanticsNode,
    private val agentConfiguration: AgentConfiguration
) : ComposeTextViewThingy(viewDetails, semanticsNode, agentConfiguration),
    SessionReplayViewThingyInterface {

    private val editableText: String
    private val hintText: String

    init {
        // Extract editable text content with priority over regular text
        editableText = extractEditableText(semanticsNode)

        // Extract hint/placeholder text (fallback to regular text if no editable text)
        hintText = if (editableText.isEmpty()) {
            extractRegularText(semanticsNode)
        } else {
            ""
        }
    }

    /**
     * Determines if the text content of this Composable should be masked.
     * The decision is based on the agent's configuration for user input text
     * and any specific masking/unmasking rules for this view's testTag.
     */
    private fun shouldMaskInputText(): Boolean {
        val sessionReplayConfig = agentConfiguration.sessionReplayConfiguration
        val sessionReplayLocalConfig = agentConfiguration.sessionReplayLocalConfiguration
        val testTag = semanticsNode.config.getOrNull(SemanticsProperties.TestTag)

        if (semanticsNode.config.getOrNull(SemanticsProperties.Password) != null) {
            return true
        }

        if (testTag == "nr-unmask") {
            return false
        }

        if (testTag == "nr-mask") {
            return true
        }

        val privacyTag = ComposePrivacyUtils.getEffectivePrivacyTag(semanticsNode)
        if (privacyTag.isNotEmpty() && ComposeSessionReplayConstants.PrivacyTags.MASK.equals(privacyTag)) {
            return true
        } else if (privacyTag.isNotEmpty() && ComposeSessionReplayConstants.PrivacyTags.UNMASK.equals(privacyTag)) {
            return false
        }

        if (testTag != null && sessionReplayConfig.unmaskedViewTags.contains(testTag)) {
            return false
        }

        if (testTag != null && sessionReplayConfig.maskedViewTags.contains(testTag)) {
            return true
        }

        if (testTag != null && sessionReplayLocalConfig.unmaskedViewTags.contains(testTag)) {
            return false
        }

        if (testTag != null && sessionReplayLocalConfig.maskedViewTags.contains(testTag)) {
            return true
        }

        return sessionReplayConfig.isMaskUserInputText
    }

    /**
     * Extracts editable text from SemanticsProperties.EditableText
     */
    private fun extractEditableText(node: SemanticsNode): String {
        val editableTextValue = node.config.getOrNull(SemanticsProperties.EditableText) ?: return ""

        val shouldMaskText = shouldMaskInputText()
        return getMaskedTextIfNeeded(node, editableTextValue.text, shouldMaskText)
    }

    /**
     * Extracts regular text content for hint/placeholder purposes
     */
    private fun extractRegularText(node: SemanticsNode): String {
        if (node.config.contains(SemanticsProperties.Text)) {
            val textList = node.config.getOrNull(SemanticsProperties.Text)
            if (textList != null && textList.isNotEmpty()) {
                val rawText = textList.joinToString(" ")

                // Apply masking for application text
                val shouldMaskText = sessionReplayConfiguration.isMaskApplicationText
                return getMaskedTextIfNeeded(node, rawText, shouldMaskText)
            }
        }
        return ""
    }

    /**
     * Gets the current display text (editable text takes priority over hint)
     */
    private fun getCurrentDisplayText(): String {
        return editableText.ifEmpty { hintText }
    }

    override fun generateRRWebNode(): RRWebElementNode {
        val displayText = getCurrentDisplayText()
        val textNode = createTextNode(displayText)

        val attributes = Attributes(viewDetails.cssSelector)

        // Set input type for editable text fields
        attributes.type = "text"


        return RRWebElementNode(
            attributes,
            RRWebElementNode.TAG_TYPE_DIV, // Use div tag (styled as input via type attribute)
            viewDetails.viewId,
            arrayListOf(textNode)
        )
    }

    override fun generateDifferences(other: SessionReplayViewThingyInterface): List<MutationRecord>? {
        if (other !is ComposeEditTextThingy) {
            return emptyList()
        }

        // Check if anything actually changed
        val currentDisplayText = getCurrentDisplayText()
        val otherDisplayText = other.getCurrentDisplayText()
        val currentHasEditableText = editableText.isNotEmpty()
        val otherHasEditableText = other.editableText.isNotEmpty()

        val parentDifferences = super.generateDifferences(other)
        val hasTextChange = currentDisplayText != otherDisplayText
        val hasStateChange = currentHasEditableText != otherHasEditableText

        // Early return if nothing changed
        if (parentDifferences.isNullOrEmpty() && !hasTextChange && !hasStateChange) {
            return emptyList()
        }

        // Now build mutations list with known size
        val mutations = ArrayList<MutationRecord>(
            (parentDifferences?.size ?: 0) + 2
        )

        parentDifferences?.let { mutations.addAll(it) }

        if (hasTextChange) {
            mutations.add(RRWebMutationData.TextRecord(viewDetails.viewId, otherDisplayText))
        }

        if (hasStateChange) {
            val attributes = Attributes(viewDetails.cssSelector)
            if (otherHasEditableText) {
                attributes.metadata["placeholder"] = ""
            } else {
                attributes.metadata["placeholder"] = other.hintText
            }
            mutations.add(RRWebMutationData.AttributeRecord(viewDetails.viewId, attributes))
        }

        return mutations.ifEmpty { null }
    }

    override fun generateAdditionNodes(parentId: Int): List<RRWebMutationData.AddRecord> {
        val attributes = Attributes(viewDetails.cssSelector)
        attributes.type = "text"

        val displayText = getCurrentDisplayText()

        // Add placeholder if showing hint text
        if (editableText.isEmpty() && hintText.isNotEmpty()) {
            attributes.metadata["placeholder"] = hintText
        }

        val viewNode = RRWebElementNode(
            attributes,
            RRWebElementNode.TAG_TYPE_DIV,
            viewDetails.viewId,
            ArrayList()
        )

        viewNode.attributes.metadata["style"] = generateInlineCss()

        val textNode = createTextNode(displayText)

        val viewAddRecord = RRWebMutationData.AddRecord(parentId, null, viewNode)
        val textAddRecord = RRWebMutationData.AddRecord(viewDetails.viewId, null, textNode)

        return listOf(viewAddRecord, textAddRecord)
    }

    override fun hasChanged(other: SessionReplayViewThingyInterface?): Boolean {
        if (other == null || other !is ComposeEditTextThingy) {
            return true
        }
        return !this.equals(other)  // Use proper equals comparison
    }


    // Override interface methods for proper implementation
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ComposeEditTextThingy) return false

        return viewDetails == other.viewDetails &&
                editableText == other.editableText &&
                hintText == other.hintText
    }

    override fun hashCode(): Int {
        var result = viewDetails.hashCode()
        result = 31 * result + editableText.hashCode()
        result = 31 * result + hintText.hashCode()
        return result
    }

    private fun createTextNode(text: String): RRWebTextNode {
        return RRWebTextNode(text, false, NewRelicIdGenerator.generateId())
    }
}