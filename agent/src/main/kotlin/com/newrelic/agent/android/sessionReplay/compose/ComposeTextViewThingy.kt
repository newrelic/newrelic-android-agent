package com.newrelic.agent.android.sessionReplay.compose

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
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
import com.newrelic.agent.android.sessionReplay.SessionReplayLocalConfiguration
import com.newrelic.agent.android.sessionReplay.SessionReplayViewThingyInterface
import com.newrelic.agent.android.sessionReplay.ViewDetails
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

    private var subviews: List<SessionReplayViewThingyInterface> = ArrayList()

    var shouldRecordSubviews = false
    private val labelText: String
    private val fontSize: Float
    private val fontName: String
    private val fontFamily: String
    private val textColor: String
    private val textAlign: String
    protected val sessionReplayLocalConfiguration: SessionReplayLocalConfiguration = agentConfiguration.sessionReplayLocalConfiguration
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
        fontSize = textStyling.first
        fontName = textStyling.second
        fontFamily = textStyling.third
        textColor = textStyling.fourth
        textAlign = textStyling.fifth


    }

    private fun extractTextFromSemantics(layoutInput: TextLayoutInput?): String {
        return layoutInput?.text.toString();
    }

    private fun shouldMaskComposeText(node: SemanticsNode): Boolean {
        if (node.config.contains(SemanticsProperties.Password)) {
            return true
        }

        val isInput = node.config.contains(SemanticsProperties.EditableText)
        return if (isInput) sessionReplayConfiguration.isMaskUserInputText else sessionReplayConfiguration.isMaskApplicationText
    }



    private fun extractTextStyling(textStyle: TextStyle?): Tuple5<Float, String, String, String, String> {
        var fontSize = 14.0f
        val fontName = "default"
        var fontFamily = "font-family: sans-serif; font-weight: normal; font-style: normal;"
        val textAlign = extractTextAlignment(textStyle)
        var textColor = "000000"

        textStyle?.fontSize?.let { fontSizeUnit ->
            fontSize = when (fontSizeUnit.type) {
                TextUnitType.Companion.Sp -> fontSizeUnit.value
                TextUnitType.Companion.Em -> fontSizeUnit.value * 16.0f
                else -> fontSize
            }
        }

        textStyle?.fontFamily?.let { family ->
            fontFamily = convertFontFamilyToCss(family)
        }

        textStyle?.fontWeight?.let { weight ->
            fontFamily = updateFontFamilyWithWeight(fontFamily, weight)
        }

        textStyle?.fontStyle?.let { style ->
            fontFamily = updateFontFamilyWithStyle(fontFamily, style)
        }

        textStyle?.color?.let { color ->
            val colorString = Integer.toHexString(Color(color.value).toArgb());
            if(colorString.length > 2 ){
                textColor = colorString.substring(2)
            }
        }

        return Tuple5(fontSize, fontName, fontFamily, textColor, textAlign)
    }

    private fun convertFontFamilyToCss(fontFamily: FontFamily): String {
        return when (fontFamily) {
            FontFamily.Companion.Default -> "font-family: sans-serif; font-weight: normal; font-style: normal;"
            FontFamily.Companion.SansSerif -> "font-family: sans-serif; font-weight: normal; font-style: normal;"
            FontFamily.Companion.Serif -> "font-family: serif; font-weight: normal; font-style: normal;"
            FontFamily.Companion.Monospace -> "font-family: monospace; font-weight: normal; font-style: normal;"
            FontFamily.Companion.Cursive -> "font-family: cursive; font-weight: normal; font-style: normal;"
            else -> "font-family: sans-serif; font-weight: normal; font-style: normal;"
        }
    }

    private fun updateFontFamilyWithWeight(currentFontFamily: String, fontWeight: FontWeight): String {
        val weightValue = when (fontWeight) {
            FontWeight.Companion.Bold -> "bold"
            FontWeight.Companion.Light -> "300"
            FontWeight.Companion.Medium -> "500"
            FontWeight.Companion.SemiBold -> "600"
            FontWeight.Companion.ExtraBold -> "800"
            FontWeight.Companion.Black -> "900"
            else -> "normal"
        }

        return currentFontFamily.replace("font-weight: normal", "font-weight: $weightValue")
    }

    private fun updateFontFamilyWithStyle(currentFontFamily: String, fontStyle: FontStyle): String {
        val styleValue = if (fontStyle == FontStyle.Companion.Italic) "italic" else "normal"
        return currentFontFamily.replace("font-style: normal", "font-style: $styleValue")
    }

    private fun extractTextAlignment( textStyle: TextStyle?): String {
        textStyle?.textAlign?.let { textAlign ->
            return when (textAlign) {
                TextAlign.Companion.Center -> "center"
                TextAlign.Companion.End, TextAlign.Companion.Right -> "right"
                TextAlign.Companion.Start, TextAlign.Companion.Left -> "left"
                else -> "left"
            }
        }

        return "left"
    }

    override fun getSubviews(): List<SessionReplayViewThingyInterface> = subviews

    override fun setSubviews(subviews: List<SessionReplayViewThingyInterface>) {
        this.subviews = subviews
    }

    override fun getViewDetails(): ViewDetails? = null

    override fun shouldRecordSubviews(): Boolean = shouldRecordSubviews

    override fun getCssSelector(): String = viewDetails.cssSelector

    override fun generateCssDescription(): String {
        val cssBuilder = StringBuilder(viewDetails.generateCssDescription())
        cssBuilder.append("")
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
        cssBuilder.append("")
        cssBuilder.append("word-wrap: break-word;")
        cssBuilder.append(" ")
        cssBuilder.append("font-size: ")
        cssBuilder.append(String.format("%.2f", fontSize))
        cssBuilder.append("px; ")
        cssBuilder.append(fontFamily)
        cssBuilder.append("; ")
        cssBuilder.append("color: #")
        cssBuilder.append(textColor)
        cssBuilder.append("; ")
        cssBuilder.append("text-align: ")
        cssBuilder.append(textAlign)
        cssBuilder.append("; ")
        cssBuilder.append("line-height: normal; ")

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

        val styleDifferences = mutableMapOf<String, String>()
        val otherComposeViewDetails = other.viewDetails
        if (viewDetails.frame != otherComposeViewDetails.frame) {
            styleDifferences["left"] = "${otherComposeViewDetails.frame.left}px"
            styleDifferences["top"] = "${otherComposeViewDetails.frame.top}px"
            styleDifferences["width"] = "${otherComposeViewDetails.frame.width()}px"
            styleDifferences["height"] = "${otherComposeViewDetails.frame.height()}px"
            styleDifferences["line-height"] = "${otherComposeViewDetails.frame.height()}px"
        }

        if (viewDetails.backgroundColor != null && otherComposeViewDetails.backgroundColor != null) {
            if (viewDetails.backgroundColor != otherComposeViewDetails.backgroundColor) {
                styleDifferences["background-color"] = otherComposeViewDetails.backgroundColor
            }
        } else if (otherComposeViewDetails.backgroundColor != null) {
            styleDifferences["background-color"] = otherComposeViewDetails.backgroundColor
        }

        if (textColor != other.textColor) {
            styleDifferences["color"] = "#${other.textColor}"
        }

        if (fontFamily != other.fontFamily) {
            styleDifferences["font-family"] = other.fontFamily
        }

        if (textAlign != other.textAlign) {
            styleDifferences["text-align"] = other.textAlign
        }

        if (fontSize != other.fontSize) {
            styleDifferences["font-size"] = String.format("%.2fpx", other.fontSize)
        }

        val mutations = mutableListOf<MutationRecord>()
        val attributes = Attributes(viewDetails.cssSelector)
        attributes.metadata = styleDifferences
        mutations.add(RRWebMutationData.AttributeRecord(viewDetails.viewId, attributes))

        if (labelText != other.labelText) {
            mutations.add(RRWebMutationData.TextRecord(viewDetails.viewId, other.labelText))
        }

        return mutations
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

        // Compare using hashCode (which should reflect the content)
        return this.hashCode() != other.hashCode()
    }

    protected fun getMaskedTextIfNeeded(node: SemanticsNode, text: String, shouldMask: Boolean): String {
        if (text.isEmpty()) return text

        val viewTag = node.config.getOrNull(NewRelicPrivacyKey) ?: ""
        val isCustomMode = sessionReplayConfiguration.getMode() == "custom"
        val hasUnmaskTag = isCustomMode && viewTag == "nr-unmask"
        val hasMaskTag = viewTag == "nr-mask"

        return if ((shouldMask && !hasUnmaskTag) || (!shouldMask && hasMaskTag)) {
            "*".repeat(text.length)
        } else {
            text
        }
    }

    fun getSemanticsNode(): SemanticsNode = semanticsNode

    fun isEditableText(): Boolean {
        return semanticsNode.config.contains(SemanticsProperties.EditableText)
    }

    fun isPasswordField(): Boolean {
        return semanticsNode.config.contains(SemanticsProperties.Password)
    }

    fun getEditableText(): String {
        if (isEditableText()) {
            val editableText = semanticsNode.config[SemanticsProperties.EditableText]
            return when (editableText) {
                else -> editableText.text
            }
        }
        return ""
    }

    private data class Tuple5<A, B, C, D, E>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
        val fifth: E
    )
}