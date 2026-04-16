package com.newrelic.agent.android.sessionReplay.viewMapper

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import com.newrelic.agent.android.AgentConfiguration
import com.newrelic.agent.android.sessionReplay.compose.ComposeViewDetails
import com.newrelic.agent.android.sessionReplay.compose.SessionReplayComposeViewThingy
import com.newrelic.agent.android.sessionReplay.models.Attributes
import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.InputCapable
import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.MutationRecord
import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.RRWebInputData
import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.RRWebMutationData
import com.newrelic.agent.android.sessionReplay.models.RRWebElementNode

class ComposeRadioButtonThingy(
    private val viewDetails: ComposeViewDetails,
    semanticsNode: SemanticsNode,
    agentConfiguration: AgentConfiguration
) : SessionReplayComposeViewThingy(viewDetails, semanticsNode, agentConfiguration), InputCapable {

    private val isChecked: Boolean

    init {
        val selected = semanticsNode.config.getOrNull(SemanticsProperties.Selected)
        isChecked = selected ?: false
    }

    override fun generateRRWebNode(): RRWebElementNode {
        val attributes = Attributes(viewDetails.cssSelector)
        attributes.type = "radio"
        attributes.inputType = "radio"
        if (isChecked) {
            attributes.checked = true
        }
        return RRWebElementNode(
            attributes,
            RRWebElementNode.TAG_TYPE_INPUT,
            viewDetails.viewId,
            ArrayList()
        )
    }

    override fun generateDifferences(other: SessionReplayViewThingyInterface): List<MutationRecord>? {
        if (other !is ComposeRadioButtonThingy) {
            return emptyList()
        }

        val parentDifferences = super.generateDifferences(other)
        val hasCheckedChange = isChecked != other.isChecked

        if (parentDifferences.isNullOrEmpty() && !hasCheckedChange) {
            return emptyList()
        }

        val mutations = ArrayList<MutationRecord>(
            (parentDifferences?.size ?: 0) + 1
        )

        parentDifferences?.let { mutations.addAll(it) }

        if (hasCheckedChange) {
            val attributes = Attributes(viewDetails.cssSelector)
            attributes.type = "radio"
            attributes.inputType = "radio"
            if (other.isChecked) {
                attributes.checked = true
            }
            mutations.add(RRWebMutationData.AttributeRecord(viewDetails.viewId, attributes))
        }

        return mutations.ifEmpty { null }
    }

    override fun generateAdditionNodes(parentId: Int): List<RRWebMutationData.AddRecord> {
        val attributes = Attributes(viewDetails.cssSelector)
        attributes.type = "radio"
        attributes.inputType = "radio"
        if (isChecked) {
            attributes.checked = true
        }

        val viewNode = RRWebElementNode(
            attributes,
            RRWebElementNode.TAG_TYPE_INPUT,
            viewDetails.viewId,
            ArrayList()
        )

        viewNode.attributes.metadata["style"] = generateInlineCss()

        val viewAddRecord = RRWebMutationData.AddRecord(parentId, null, viewNode)
        return listOf(viewAddRecord)
    }

    override fun generateInputData(other: SessionReplayViewThingyInterface): RRWebInputData? {
        if (other !is ComposeRadioButtonThingy) return null
        if (isChecked == other.isChecked) return null
        return RRWebInputData(viewDetails.viewId, "", other.isChecked)
    }

    override fun hasChanged(other: SessionReplayViewThingyInterface?): Boolean {
        if (other == null || other !is ComposeRadioButtonThingy) {
            return true
        }
        return !this.equals(other)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ComposeRadioButtonThingy) return false

        return viewDetails == other.viewDetails &&
                isChecked == other.isChecked
    }

    override fun hashCode(): Int {
        var result = viewDetails.hashCode()
        result = 31 * result + isChecked.hashCode()
        return result
    }
}