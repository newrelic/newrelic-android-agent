package com.newrelic.agent.android.sessionReplay.compose

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.newrelic.agent.android.AgentConfiguration
import com.newrelic.agent.android.sessionReplay.SessionReplayViewThingyInterface
import com.newrelic.agent.android.sessionReplay.models.Attributes
import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.MutationRecord
import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.RRWebMutationData
import com.newrelic.agent.android.sessionReplay.models.RRWebElementNode

class ComposeSliderThingy(
    private val viewDetails: ComposeViewDetails,
    semanticsNode: SemanticsNode,
    agentConfiguration: AgentConfiguration
) : SessionReplayComposeViewThingy(viewDetails, semanticsNode, agentConfiguration) {

    private val currentValue: Float
    private val minValue: Float
    private val maxValue: Float
    private val steps: Int
    private val stepSize: String

    init {
        val rangeInfo = semanticsNode.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)
        currentValue = rangeInfo?.current ?: 0f
        minValue = rangeInfo?.range?.start ?: 0f
        maxValue = rangeInfo?.range?.endInclusive ?: 100f
        steps = rangeInfo?.steps ?: 0
        stepSize = if (steps > 0) {
            ((maxValue - minValue) / (steps + 1)).toString()
        } else {
            "any"
        }
    }

    private fun buildAttributes(): Attributes {
        val attributes = Attributes(viewDetails.cssSelector)
        attributes.type = "range"
        attributes.inputType = "range"
        attributes.value = currentValue.toString()
        attributes.min = minValue.toString()
        attributes.max = maxValue.toString()
        attributes.step = stepSize
        return attributes
    }

    override fun generateRRWebNode(): RRWebElementNode {
        return RRWebElementNode(
            buildAttributes(),
            RRWebElementNode.TAG_TYPE_INPUT,
            viewDetails.viewId,
            ArrayList()
        )
    }

    override fun generateDifferences(other: SessionReplayViewThingyInterface): List<MutationRecord>? {
        if (other !is ComposeSliderThingy) {
            return emptyList()
        }

        val parentDifferences = super.generateDifferences(other)
        val hasValueChange = currentValue != other.currentValue
                || minValue != other.minValue
                || maxValue != other.maxValue
                || steps != other.steps

        if (parentDifferences.isNullOrEmpty() && !hasValueChange) {
            return emptyList()
        }

        val mutations = ArrayList<MutationRecord>(
            (parentDifferences?.size ?: 0) + 1
        )

        parentDifferences?.let { mutations.addAll(it) }

        if (hasValueChange) {
            mutations.add(RRWebMutationData.AttributeRecord(viewDetails.viewId, other.buildAttributes()))
        }

        return mutations.ifEmpty { null }
    }

    override fun generateAdditionNodes(parentId: Int): List<RRWebMutationData.AddRecord> {
        val viewNode = RRWebElementNode(
            buildAttributes(),
            RRWebElementNode.TAG_TYPE_INPUT,
            viewDetails.viewId,
            ArrayList()
        )

        viewNode.attributes.metadata["style"] = generateInlineCss()

        val viewAddRecord = RRWebMutationData.AddRecord(parentId, null, viewNode)
        return listOf(viewAddRecord)
    }

    override fun hasChanged(other: SessionReplayViewThingyInterface?): Boolean {
        if (other == null || other !is ComposeSliderThingy) {
            return true
        }
        return !this.equals(other)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ComposeSliderThingy) return false

        return viewDetails == other.viewDetails &&
                currentValue == other.currentValue &&
                minValue == other.minValue &&
                maxValue == other.maxValue &&
                steps == other.steps
    }

    override fun hashCode(): Int {
        var result = viewDetails.hashCode()
        result = 31 * result + currentValue.hashCode()
        result = 31 * result + minValue.hashCode()
        result = 31 * result + maxValue.hashCode()
        result = 31 * result + steps
        return result
    }
}