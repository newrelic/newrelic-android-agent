package com.newrelic.agent.android.sessionReplay.compose

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import com.newrelic.agent.android.AgentConfiguration
import com.newrelic.agent.android.sessionReplay.NewRelicIdGenerator
import com.newrelic.agent.android.sessionReplay.SessionReplayViewThingyInterface
import com.newrelic.agent.android.sessionReplay.ViewDetails
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
    semanticsNode: SemanticsNode,
    agentConfiguration: AgentConfiguration
) : ComposeTextViewThingy(viewDetails, semanticsNode, agentConfiguration),SessionReplayViewThingyInterface {

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
     * Extracts editable text from SemanticsProperties.EditableText
     */
    private fun extractEditableText(node: SemanticsNode): String {
        if (node.config.contains(SemanticsProperties.EditableText)) {
            val editableTextValue = node.config[SemanticsProperties.EditableText]
            val rawText = when (editableTextValue) {
                else -> editableTextValue.text
            }

            // Apply masking for user input text
            val shouldMaskText = sessionReplayConfiguration.isMaskUserInputText
            return getMaskedTextIfNeeded(node, rawText, shouldMaskText)
        }
        return ""
    }

    /**
     * Extracts regular text content for hint/placeholder purposes
     */
    private fun extractRegularText(node: SemanticsNode): String {
        if (node.config.contains(SemanticsProperties.Text)) {
            val textList = node.config[SemanticsProperties.Text]
            val rawText = textList.joinToString(" ")

            // Apply masking for application text
            val shouldMaskText = sessionReplayConfiguration.isMaskApplicationText
            return getMaskedTextIfNeeded(node, rawText, shouldMaskText)
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
        val textNode = RRWebTextNode(displayText, false, NewRelicIdGenerator.generateId())

        val attributes = Attributes(viewDetails.cssSelector)

        // Set input type for editable text fields
        attributes.type = "text"


        return RRWebElementNode(
            attributes,
            RRWebElementNode.TAG_TYPE_DIV, // Use input tag for editable text
            viewDetails.viewId,
            arrayListOf(textNode)
        )
    }

    override fun generateDifferences(other: SessionReplayViewThingyInterface): List<MutationRecord>? {
        if (other !is ComposeEditTextThingy) {
            return null
        }

        val mutations = mutableListOf<MutationRecord>()

        // Get style differences from parent class
        val parentDifferences = super.generateDifferences(other)
        parentDifferences?.let { mutations.addAll(it) }

        // Check for editable text content changes
        val currentDisplayText = getCurrentDisplayText()
        val otherDisplayText = other.getCurrentDisplayText()

        if (currentDisplayText != otherDisplayText) {
            mutations.add(RRWebMutationData.TextRecord(viewDetails.viewId, otherDisplayText))
        }

        // Check for editable text vs hint text state changes
        val currentHasEditableText = editableText.isNotEmpty()
        val otherHasEditableText = other.editableText.isNotEmpty()

        if (currentHasEditableText != otherHasEditableText) {
            // State changed between showing editable content vs hint
            val attributes = Attributes(viewDetails.cssSelector)
            if (otherHasEditableText) {
                // Changed to showing editable text, remove placeholder
                attributes.metadata["placeholder"] = ""
            } else {
                // Changed to showing hint text, add placeholder
                attributes.metadata["placeholder"] = other.hintText
            }
            mutations.add(RRWebMutationData.AttributeRecord(viewDetails.viewId, attributes))
        }

        return if (mutations.isEmpty()) null else mutations
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

        val textNode = RRWebTextNode(displayText, false, NewRelicIdGenerator.generateId())

        val viewAddRecord = RRWebMutationData.AddRecord(parentId, null, viewNode)
        val textAddRecord = RRWebMutationData.AddRecord(viewDetails.viewId, null, textNode)

        return listOf(viewAddRecord, textAddRecord)
    }

    /**
     * Checks if this compose element represents an editable text field
     */
    fun hasEditableText(): Boolean {
        return getSemanticsNode().config.contains(SemanticsProperties.EditableText)
    }

    // Override interface methods for proper implementation
    override fun getViewDetails(): ViewDetails? = null


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
}