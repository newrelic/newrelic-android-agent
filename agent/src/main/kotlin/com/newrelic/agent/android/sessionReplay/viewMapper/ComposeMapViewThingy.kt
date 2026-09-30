package com.newrelic.agent.android.sessionReplay.viewMapper

import androidx.compose.ui.semantics.SemanticsNode
import com.newrelic.agent.android.AgentConfiguration
import com.newrelic.agent.android.logging.AgentLogManager
import com.newrelic.agent.android.sessionReplay.util.MapViewDetectionUtils
import com.newrelic.agent.android.sessionReplay.compose.ComposeViewDetails
import com.newrelic.agent.android.sessionReplay.models.Attributes
import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.MutationRecord
import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.RRWebMutationData
import com.newrelic.agent.android.sessionReplay.models.RRWebElementNode

/**
 * Session replay representation of Jetpack Compose MapView composables.
 *
 * This class handles MapView components for session replay in Compose UI.
 * MapViews are treated as special components that don't record their subviews
 * since they contain complex map rendering that should be handled as a single unit.
 *
 * @param viewDetails The Compose view's layout and styling information
 * @param semanticsNode The Compose SemanticsNode containing map view data
 * @param agentConfiguration Agent configuration including session replay settings
 *
 * @see ComposeViewDetails
 * @see SessionReplayViewThingyInterface
 */
class ComposeMapViewThingy(
    private val viewDetails: ComposeViewDetails,
    private val semanticsNode: SemanticsNode,
    agentConfiguration: AgentConfiguration
) : SessionReplayViewThingyInterface {

    init {
        // Validate required parameters
        require(viewDetails != null) { "ComposeViewDetails cannot be null" }
        require(semanticsNode != null) { "SemanticsNode cannot be null" }
        require(agentConfiguration != null) { "AgentConfiguration cannot be null" }
    }

    companion object {
        private val log = AgentLogManager.getAgentLog()

        /**
         * Checks if a SemanticsNode represents a MapView by examining its properties.
         * This method checks for common MapView indicators in Compose.
         *
         * @param semanticsNode The SemanticsNode to check
         * @return true if the node represents a MapView, false otherwise
         */
        fun isMapView(semanticsNode: SemanticsNode): Boolean {
            // Delegate to centralized utility class
            return MapViewDetectionUtils.isMapView(semanticsNode)
        }

    }

    private var subviews: List<SessionReplayViewThingyInterface> = emptyList()
    private val shouldRecordSubviews = false // MapViews don't record subviews
    private val backgroundColor: String = viewDetails.backgroundColor ?: "#E5E5E5" // Light gray default


    override fun getSubviews(): List<SessionReplayViewThingyInterface> {
        // Return defensive copy to prevent external modification
        return subviews.toList()
    }

    override fun setSubviews(subviews: List<SessionReplayViewThingyInterface>) {
        // Create defensive copy to prevent external modification of internal state
        this.subviews = subviews.toList()
    }

    override fun getViewDetails(): Any = viewDetails

    override fun shouldRecordSubviews(): Boolean = shouldRecordSubviews

    override fun getCssSelector(): String = viewDetails.cssSelector

    override fun generateCssDescription(): String {
        val cssBuilder = StringBuilder(viewDetails.generateCssDescription())
        generateMapViewCss(cssBuilder)
        return cssBuilder.toString()
    }

    override fun generateInlineCss(): String {
        val cssBuilder = StringBuilder(viewDetails.generateInlineCSS())
        cssBuilder.append(" ")
        generateMapViewCss(cssBuilder)
        return cssBuilder.toString()
    }

    /**
     * Generates CSS specific to MapView components in Compose.
     */
    private fun generateMapViewCss(cssBuilder: StringBuilder) {
        cssBuilder.append("background-color: ")
        cssBuilder.append(backgroundColor)
        cssBuilder.append("; ")

        // Add map-specific styling
        cssBuilder.append("overflow: hidden; ")
        cssBuilder.append("position: relative; ")

        // Add a subtle pattern or placeholder for the map
        cssBuilder.append("background-image: ")
        cssBuilder.append("linear-gradient(45deg, #f0f0f0 25%, transparent 25%), ")
        cssBuilder.append("linear-gradient(-45deg, #f0f0f0 25%, transparent 25%), ")
        cssBuilder.append("linear-gradient(45deg, transparent 75%, #f0f0f0 75%), ")
        cssBuilder.append("linear-gradient(-45deg, transparent 75%, #f0f0f0 75%); ")
        cssBuilder.append("background-size: 20px 20px; ")
        cssBuilder.append("background-position: 0 0, 0 10px, 10px -10px, -10px 0px; ")
    }

    override fun generateRRWebNode(): RRWebElementNode {
        val attributes = Attributes(viewDetails.cssSelector)

        // Set the component type to identify this as a map view
        attributes.dataNrType = "mapview"

        // Add map-specific metadata
        attributes.metadata["data-nr-component-type"] = "map"

        return RRWebElementNode(
            attributes,
            RRWebElementNode.TAG_TYPE_DIV,
            viewDetails.viewId,
            ArrayList()
        )
    }

    override fun generateDifferences(other: SessionReplayViewThingyInterface?): List<MutationRecord>? {
        if (other !is ComposeMapViewThingy) {
            return emptyList()
        }

        val styleDifferences = HashMap<String, String>(8)
        val otherViewDetails = other.viewDetails

        // Check for frame changes (position/size)
        if (viewDetails.frame != otherViewDetails.frame) {
            // Only add style differences if the other frame is valid
            otherViewDetails.frame?.let { frame ->
                styleDifferences["left"] = "${frame.left}px"
                styleDifferences["top"] = "${frame.top}px"
                styleDifferences["width"] = "${frame.width()}px"
                styleDifferences["height"] = "${frame.height()}px"
            } ?: run {
                // Handle case where new frame is null (MapView was removed/hidden)
                // For MapViews, null frame indicates the view should be hidden
                styleDifferences["display"] = "none"
            }
        }

        // Check for background color changes
        if (viewDetails.backgroundColor != otherViewDetails.backgroundColor) {
            styleDifferences["background-color"] = otherViewDetails.backgroundColor ?: "transparent"
        }

        // Check for visibility changes
        if (viewDetails.isHidden() != otherViewDetails.isHidden()) {
            styleDifferences["display"] = if (otherViewDetails.isHidden()) "none" else "block"
        }

        if (styleDifferences.isEmpty()) {
            return emptyList()
        }

        val attributes = Attributes(viewDetails.cssSelector)
        attributes.metadata = styleDifferences
        attributes.dataNrType = "mapview"
        attributes.metadata["data-nr-component-type"] = "map"

        return listOf(RRWebMutationData.AttributeRecord(viewDetails.viewId, attributes))
    }

    override fun generateAdditionNodes(parentId: Int): List<RRWebMutationData.AddRecord> {
        val node = generateRRWebNode()
        node.attributes.metadata["style"] = generateInlineCss()

        val addRecord = RRWebMutationData.AddRecord(parentId, null, node)
        return listOf(addRecord)
    }

    override fun getViewId(): Int = viewDetails.viewId

    override fun getParentViewId(): Int = viewDetails.parentId

    override fun hasChanged(other: SessionReplayViewThingyInterface?): Boolean {
        if (other == null || other !is ComposeMapViewThingy) {
            return true
        }

        // Compare view details
        if (viewDetails != other.viewDetails) {
            return true
        }

        // Compare background color
        if (backgroundColor != other.backgroundColor) {
            return true
        }

        return false
    }

    override fun hashCode(): Int {
        var result = viewDetails.hashCode()
        result = 31 * result + backgroundColor.hashCode()
        result = 31 * result + if (shouldRecordSubviews) 1 else 0
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false

        other as ComposeMapViewThingy

        if (shouldRecordSubviews != other.shouldRecordSubviews) return false
        if (viewDetails != other.viewDetails) return false
        if (backgroundColor != other.backgroundColor) return false

        return true
    }
}