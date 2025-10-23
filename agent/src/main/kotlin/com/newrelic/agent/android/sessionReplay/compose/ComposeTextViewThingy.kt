package com.newrelic.agent.android.sessionReplay.compose

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.TextLayoutInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnitType
import com.newrelic.agent.android.AgentConfiguration
import com.newrelic.agent.android.sessionReplay.NewRelicIdGenerator
import com.newrelic.agent.android.sessionReplay.SessionReplayConfiguration
import com.newrelic.agent.android.sessionReplay.SessionReplayViewThingyInterface
import com.newrelic.agent.android.sessionReplay.models.Attributes
import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.MutationRecord
import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.RRWebMutationData
import com.newrelic.agent.android.sessionReplay.models.RRWebElementNode
import com.newrelic.agent.android.sessionReplay.models.RRWebTextNode

open class ComposeTextViewThingy(
    private val viewDetails: ComposeViewDetails,
    private val semanticsNode: SemanticsNode,
    agentConfiguration: AgentConfiguration
) : SessionReplayViewThingyInterface {

    private var subviews: List<SessionReplayViewThingyInterface> = emptyList()

    var shouldRecordSubviews = false
    private val labelText: String
    private val fontSize: Float
    private val fontFamily: String
    private val textColor: String
    private val textAlign: String
    private val fontWeight:String
    private val fontStyle:String

    protected val sessionReplayConfiguration: SessionReplayConfiguration = agentConfiguration.sessionReplayConfiguration

    init {
        val textLayoutResults = mutableListOf<TextLayoutResult>()
        semanticsNode.config[SemanticsActions.GetTextLayoutResult].action?.invoke(textLayoutResults)

        val layoutResult = textLayoutResults.firstOrNull()
        val layoutInput = layoutResult?.layoutInput
        val textStyle = layoutInput?.style
        val rawText = extractTextFromSemantics(layoutInput)
        val shouldMaskText = shouldMaskComposeText(semanticsNode)
        labelText = getMaskedTextIfNeeded(semanticsNode, rawText, shouldMaskText)

        val textStyling = extractTextStyling(textStyle)
        fontSize = textStyling.fontSize
        fontFamily = textStyling.fontFamily
        textColor = textStyling.textColor
        textAlign = textStyling.textAlign
        fontStyle = textStyling.fontStyle
        fontWeight = textStyling.fontWeight

    }

    private fun extractTextFromSemantics(layoutInput: TextLayoutInput?): String {
        return layoutInput?.text.toString()
    }

    private fun shouldMaskComposeText(node: SemanticsNode): Boolean {
        if (node.config.contains(SemanticsProperties.Password)) {
            return true
        }

        val isInput = node.config.contains(SemanticsProperties.EditableText)
        return if (isInput) sessionReplayConfiguration.isMaskUserInputText else sessionReplayConfiguration.isMaskApplicationText
    }



    private fun extractTextStyling(textStyle: TextStyle?): TextStyling {
        val fontSize = textStyle?.fontSize?.let { fontSizeUnit ->
            when (fontSizeUnit.type) {
                TextUnitType.Companion.Sp -> fontSizeUnit.value
                TextUnitType.Companion.Em -> fontSizeUnit.value * ComposeSessionReplayConstants.Defaults.EM_TO_PX_MULTIPLIER
                else -> ComposeSessionReplayConstants.Defaults.DEFAULT_FONT_SIZE
            }
        } ?: ComposeSessionReplayConstants.Defaults.DEFAULT_FONT_SIZE

        // Better: Inline in extractTextStyling
        val textAlign = textStyle?.textAlign?.let { align ->
            when (align) {
                TextAlign.Companion.Center -> "center"
                TextAlign.Companion.End, TextAlign.Companion.Right -> "right"
                TextAlign.Companion.Start, TextAlign.Companion.Left -> "left"
                else -> "left"
            }
        } ?: "left"


        var txtColor = ComposeSessionReplayConstants.Defaults.DEFAULT_TEXT_COLOR
        textStyle?.color?.let { color ->
            val argb = Color(color.value).toArgb()
            val colorString = Integer.toHexString(argb)
            txtColor = if (colorString.length > 2) {
                colorString.substring(2)
            } else {
                ComposeSessionReplayConstants.Defaults.DEFAULT_TEXT_COLOR
            }
        }

        // Build CSS directly instead of string manipulation

        val family = when (textStyle?.fontFamily) {
            FontFamily.Companion.Serif -> "serif"
            FontFamily.Companion.Monospace -> "monospace"
            FontFamily.Companion.Cursive -> "cursive"
            else -> "sans-serif"
        }

        val weight = when (textStyle?.fontWeight) {
            FontWeight.Companion.Bold -> "bold"
            FontWeight.Companion.Light -> "300"
            FontWeight.Companion.Medium -> "500"
            FontWeight.Companion.SemiBold -> "600"
            FontWeight.Companion.ExtraBold -> "800"
            FontWeight.Companion.Black -> "900"
            else -> "normal"
        }

        val style = if (textStyle?.fontStyle == FontStyle.Companion.Italic) "italic" else "normal"

        return TextStyling(fontSize, family, txtColor, textAlign,style,weight)
    }
    private data class TextStyling(
        val fontSize: Float,
        val fontFamily: String,
        val textColor: String,
        val textAlign: String,
        val fontStyle: String = "normal",
        val fontWeight: String = "normal"
    )

    override fun getSubviews(): List<SessionReplayViewThingyInterface> = subviews

    override fun setSubviews(subviews: List<SessionReplayViewThingyInterface>) {
        this.subviews = subviews
    }

    override fun getViewDetails(): Any? = this.viewDetails

    override fun shouldRecordSubviews(): Boolean = shouldRecordSubviews

    override fun getCssSelector(): String = viewDetails.cssSelector

    override fun generateCssDescription(): String {
        val cssBuilder = StringBuilder(viewDetails.generateCssDescription())
        generateTextCss(cssBuilder)
        return cssBuilder.toString()
    }

    override fun generateInlineCss(): String {
        val cssBuilder = StringBuilder(viewDetails.generateInlineCSS())
        cssBuilder.append(" ")
        generateTextCss(cssBuilder)
        return cssBuilder.toString()
    }

    private fun generateTextCss(cssBuilder: StringBuilder) {
        cssBuilder.append("white-space: pre-wrap;")
        cssBuilder.append("word-wrap: break-word;")
        cssBuilder.append(" ")
        cssBuilder.append("font-size: ")
        cssBuilder.append(formattedFontSize)
        cssBuilder.append("px; ")
        cssBuilder.append("color: #")
        cssBuilder.append(textColor)
        cssBuilder.append("; ")
        cssBuilder.append("text-align: ")
        cssBuilder.append(textAlign)
        cssBuilder.append("; ")
        cssBuilder.append("line-height: normal; ")
        cssBuilder.append("font-family: ")
        cssBuilder.append(fontFamily)
        cssBuilder.append("; ")
        cssBuilder.append("font-style: ")
        cssBuilder.append(fontStyle)
        cssBuilder.append("; ")
        cssBuilder.append("font-weight: ")
        cssBuilder.append(fontWeight)
        cssBuilder.append("; ")

    }

    override fun generateRRWebNode(): RRWebElementNode {
        val textNode = RRWebTextNode(labelText, false, NewRelicIdGenerator.generateId())
        val attributes = Attributes(viewDetails.cssSelector)
        return RRWebElementNode(
            attributes,
            RRWebElementNode.TAG_TYPE_DIV,
            viewDetails.viewId,
            arrayListOf(textNode)
        )
    }

    override fun generateDifferences(other: SessionReplayViewThingyInterface): List<MutationRecord>? {
        if (other !is ComposeTextViewThingy) {
            return null
        }

        // Early return if nothing changed
        if (!hasChanged(other)) {
            return null
        }
        val styleDifferences = HashMap<String, String>(10)
        val otherComposeViewDetails = other.viewDetails
        if (viewDetails.frame != otherComposeViewDetails.frame) {
            styleDifferences["left"] = "${otherComposeViewDetails.frame.left}px"
            styleDifferences["top"] = "${otherComposeViewDetails.frame.top}px"
            styleDifferences["width"] = "${otherComposeViewDetails.frame.width()}px"
            styleDifferences["height"] = "${otherComposeViewDetails.frame.height()}px"
            styleDifferences["line-height"] = "${otherComposeViewDetails.frame.height()}px"
        }

        if (viewDetails.backgroundColor != otherComposeViewDetails.backgroundColor) {
            otherComposeViewDetails.backgroundColor?.let {
                styleDifferences["background-color"] = it
            }
        }

        if (textColor != other.textColor) {
            styleDifferences["color"] = "#${other.textColor}"
        }

        if (fontFamily != other.fontFamily) {
            styleDifferences["font-family"] = other.fontFamily
        }

        if(fontStyle != other.fontStyle){
            styleDifferences["font-style"] = other.fontStyle
        }

        if (fontWeight != other.fontWeight) {
            styleDifferences["font-weight"] = other.fontWeight
        }

        if (textAlign != other.textAlign) {
            styleDifferences["text-align"] = other.textAlign
        }

        if (fontSize != other.fontSize) {
            styleDifferences["font-size"] = String.format("%.2fpx", other.fontSize)
        }

        if (viewDetails.isHidden() != other.viewDetails.isHidden()) {
            styleDifferences.put(
                "visibility",
                if (other.viewDetails.isHidden()) "hidden" else "visible"
            )
        }

        val mutations = ArrayList<MutationRecord>(2)
        val attributes = Attributes(viewDetails.cssSelector)
        attributes.metadata = styleDifferences
        mutations.add(RRWebMutationData.AttributeRecord(viewDetails.viewId, attributes))

        if (labelText != other.labelText) {
            mutations.add(RRWebMutationData.TextRecord(viewDetails.viewId, other.labelText))
        }

        return mutations.takeIf { styleDifferences.isNotEmpty() || labelText != other.labelText }
    }

    override fun generateAdditionNodes(parentId: Int): List<RRWebMutationData.AddRecord> {
        val attributes = Attributes(viewDetails.cssSelector)
        val viewNode = RRWebElementNode(
            attributes,
            RRWebElementNode.TAG_TYPE_DIV,
            viewDetails.viewId,
            ArrayList()
        )

        viewNode.attributes.metadata["style"] = generateInlineCss()

        val textNode = RRWebTextNode(labelText, false, NewRelicIdGenerator.generateId())

        val viewAddRecord = RRWebMutationData.AddRecord(parentId, null, viewNode)
        val textAddRecord = RRWebMutationData.AddRecord(viewDetails.viewId, null, textNode)

        return listOf(viewAddRecord, textAddRecord)
    }

    override fun getViewId(): Int = viewDetails.viewId
    override fun getParentViewId(): Int {
        return viewDetails.parentId
    }

    override fun hasChanged(other: SessionReplayViewThingyInterface?): Boolean {
        // Quick check: if it's not the same type, it has changed
        if (other == null || other !is ComposeTextViewThingy) {
            return true
        }

        return viewDetails != other.viewDetails ||
                labelText != other.labelText ||
                fontSize != other.fontSize ||
                textColor != other.textColor ||
                textAlign != other.textAlign ||
                fontFamily != other.fontFamily ||
                fontStyle != other.fontStyle ||
                fontWeight != other.fontWeight
    }

    protected fun getMaskedTextIfNeeded(node: SemanticsNode, text: String, shouldMask: Boolean): String {
        if (text.isEmpty()) return text

        // Check current node and all parent nodes for privacy tags
        val privacyTag = ComposePrivacyUtils.getEffectivePrivacyTag(node)
        val isCustomMode = sessionReplayConfiguration.getMode() == ComposeSessionReplayConstants.Modes.CUSTOM
        val hasUnmaskTag = isCustomMode && privacyTag == ComposeSessionReplayConstants.PrivacyTags.UNMASK
        val hasMaskTag = privacyTag == ComposeSessionReplayConstants.PrivacyTags.MASK

        return if ((shouldMask && !hasUnmaskTag) || (!shouldMask && hasMaskTag)) {
            ComposeSessionReplayConstants.Masking.MASK_CHARACTER.repeat(text.length)
        } else {
            text
        }
    }


    fun isEditableText(): Boolean {
        return semanticsNode.config.contains(SemanticsProperties.EditableText)
    }


    private val formattedFontSize: String by lazy {
        String.format("%.2f", fontSize)
    }
}