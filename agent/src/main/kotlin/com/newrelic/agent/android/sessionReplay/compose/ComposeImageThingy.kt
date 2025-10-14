package com.newrelic.agent.android.sessionReplay.compose

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.SemanticsNode
import com.newrelic.agent.android.AgentConfiguration
import com.newrelic.agent.android.sessionReplay.ImageCompressionUtils
import com.newrelic.agent.android.sessionReplay.SessionReplayConfiguration
import com.newrelic.agent.android.sessionReplay.internal.ComposePainterReflectionUtils
import com.newrelic.agent.android.sessionReplay.SessionReplayViewThingyInterface
import com.newrelic.agent.android.sessionReplay.ViewDetails
import com.newrelic.agent.android.sessionReplay.models.Attributes
import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.MutationRecord
import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.RRWebMutationData
import com.newrelic.agent.android.sessionReplay.models.RRWebElementNode

open class ComposeImageThingy(
    private val viewDetails: ComposeViewDetails,
    private val semanticsNode: SemanticsNode,
    agentConfiguration: AgentConfiguration
) : SessionReplayViewThingyInterface {

    companion object {
        private const val LOG_TAG = ComposeSessionReplayConstants.LogTags.COMPOSE_IMAGE

        // Static cache shared across all instances
        private val imageCache = LruCache<String, String>(1024)

        fun clearImageCache() {
            imageCache.evictAll()
            Log.d(LOG_TAG, "Image cache cleared")
        }

        fun getCacheStats(): String {
            return "Cache stats - Size: ${imageCache.size()}, Hits: ${imageCache.hitCount()}, Misses: ${imageCache.missCount()}"
        }
    }

    private var subviews: List<SessionReplayViewThingyInterface> = ArrayList()
    var shouldRecordSubviews = false

    private val contentScale: ContentScale
    private val backgroundColor: String = viewDetails.backgroundColor
    private var imageData: String? = null // Base64 encoded image data

    protected val sessionReplayConfiguration: SessionReplayConfiguration = agentConfiguration.sessionReplayConfiguration

    init {
        contentScale = extractContentScale()

        if (shouldUnMaskImage(semanticsNode)) {
            imageData = extractImageFromModifierInfo()
        }
    }

    private fun extractContentScale(): ContentScale {
        // Try to extract ContentScale from semantics or default to Fit
        return ContentScale.Fit
    }


    private fun extractImageFromModifierInfo(): String? {
        try {
            val modifierInfoList = semanticsNode.layoutInfo.getModifierInfo()

            for (modifierInfo in modifierInfoList) {
                val modifier = modifierInfo.modifier
                val modifierClassName = modifier.javaClass.simpleName

                // Check if this is a painter modifier (Image composable uses PainterModifier)
                if (modifierClassName.contains("Painter") ||
                    modifier.javaClass.name.contains("foundation.Image") ||
                    modifier.javaClass.name.contains("PainterModifier")) {

                    val painter = extractPainterFromModifier(modifier)
                    if (painter != null) {
                        return convertPainterToBase64(painter)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error extracting image from modifier info", e)
        }

        return null
    }


    private fun extractPainterFromModifier(modifier: Any): Painter? {
        return ComposePainterReflectionUtils.extractPainterFromModifier(modifier)
    }

    private fun convertPainterToBase64(painter: Painter): String? {
        try {
            val cacheKey = generateCacheKey(painter)

            val cachedData = imageCache.get(cacheKey)
            if (cachedData != null) {
                Log.d(LOG_TAG, "Cache hit for image: $cacheKey")
                return cachedData
            }

            val bitmap = when {
                painter is BitmapPainter -> {
                    // Extract bitmap directly from BitmapPainter
                    extractBitmapFromBitmapPainter(painter)
                }
                painter is VectorPainter -> {
                    // Create bitmap from VectorPainter
                    createBitmapFromVectorPainter(painter)
                }
                painter.javaClass.simpleName.contains("AsyncImagePainter") -> {
                    // Handle Coil's AsyncImagePainter
                    extractBitmapFromAsyncImagePainter(painter)
                }
                else -> {
                    // For other painter types, try to draw them to a bitmap
                    createBitmapFromPainter(painter)
                }
            }

            if (bitmap != null && !bitmap.isRecycled) {
                val base64Data = bitmapToBase64(bitmap)
                if (base64Data != null) {
                    imageCache.put(cacheKey, base64Data)
                    Log.d(LOG_TAG, "Cached image data for key: $cacheKey")
                }
                return base64Data
            }
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error converting painter to Base64", e)
        }

        return null
    }

    private fun extractBitmapFromBitmapPainter(bitmapPainter: BitmapPainter): Bitmap? {
        return ComposePainterReflectionUtils.extractBitmapFromBitmapPainter(bitmapPainter)
    }

    private fun createBitmapFromVectorPainter(vectorPainter: VectorPainter): Bitmap? {
        try {
            // Get the intrinsic size of the vector
            val intrinsicSize = vectorPainter.intrinsicSize
            val width = if (intrinsicSize.width.isFinite()) {
                intrinsicSize.width.toInt()
            } else {
                viewDetails.frame.width().takeIf { it > 0 } ?: 24 // Default icon size
            }
            val height = if (intrinsicSize.height.isFinite()) {
                intrinsicSize.height.toInt()
            } else {
                viewDetails.frame.height().takeIf { it > 0 } ?: 24 // Default icon size
            }

            if (width <= 0 || height <= 0) {
                Log.w(LOG_TAG, "Invalid dimensions for VectorPainter: ${width}x${height}")
                return null
            }

            // Try to extract cached bitmap first (most efficient)
            val cachedBitmap = ComposePainterReflectionUtils.extractCachedBitmapFromVectorPainter(vectorPainter)
            if (cachedBitmap != null) {
                Log.d(LOG_TAG, "Successfully extracted cached bitmap from VectorPainter")
                return cachedBitmap
            }

            // If we can't extract ImageVector, try to get the root group
            val rootGroup = ComposePainterReflectionUtils.extractRootGroupFromVectorPainter(vectorPainter)
            if (rootGroup != null) {
                return rasterizeVectorGroup(rootGroup, width, height)
            }

            Log.w(LOG_TAG, "Could not extract vector data from VectorPainter using reflection")
            return null

        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error creating bitmap from VectorPainter", e)
            return null
        }
    }

    private fun extractBitmapFromAsyncImagePainter(asyncImagePainter: Painter): Bitmap? {
        try {
            Log.d(LOG_TAG, "Attempting to extract bitmap from AsyncImagePainter")

            // Direct path: asyncImagePainter.painter._painter.image.bitmap
            val bitmap = ComposePainterReflectionUtils.extractBitmapFromAsyncImagePath(asyncImagePainter)
            if (bitmap != null) {
                Log.d(LOG_TAG, "Successfully extracted bitmap from AsyncImagePainter path")
                return bitmap
            }

            // Fallback: Try to access the delegate painter (BitmapPainter, VectorPainter, etc.)
            val delegatePainter = ComposePainterReflectionUtils.getDelegatePainter(asyncImagePainter)
            if (delegatePainter != null) {
                Log.d(LOG_TAG, "Found delegate painter: ${delegatePainter.javaClass.simpleName}")
                return when {
                    delegatePainter is BitmapPainter -> extractBitmapFromBitmapPainter(delegatePainter)
                    delegatePainter is VectorPainter -> createBitmapFromVectorPainter(delegatePainter)
                    else -> null
                }
            }

            // Final fallback: Create a loading placeholder for AsyncImage
            Log.w(LOG_TAG, "Could not extract bitmap from AsyncImagePainter, creating placeholder")
            return createAsyncImagePlaceholder()

        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error extracting bitmap from AsyncImagePainter", e)
            return createAsyncImagePlaceholder()
        }
    }


    private fun createAsyncImagePlaceholder(): Bitmap {
        // Create a specific placeholder for AsyncImage that indicates it's loading/async
        val width = viewDetails.frame.width().takeIf { it > 0 } ?: 100
        val height = viewDetails.frame.height().takeIf { it > 0 } ?: 100

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Light blue background to indicate async image
        canvas.drawColor(0xFFE3F2FD.toInt()) // Light blue background

        // Draw a loading indicator pattern
        val paint = android.graphics.Paint().apply {
            color = 0xFF2196F3.toInt() // Blue border
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 3f
        }

        // Draw border
        canvas.drawRect(2f, 2f, width - 2f, height - 2f, paint)

        // Draw a simple loading pattern (cross pattern)
        paint.strokeWidth = 2f
        canvas.drawLine(width * 0.3f, height * 0.3f, width * 0.7f, height * 0.7f, paint)
        canvas.drawLine(width * 0.7f, height * 0.3f, width * 0.3f, height * 0.7f, paint)

        return bitmap
    }


    private fun rasterizeVectorGroup(rootGroup: Any, width: Int, height: Int): Bitmap? {
        try {
            Log.d(LOG_TAG, "Attempting to rasterize vector group of size ${width}x${height}")

            // Similar to ImageVector, this would require implementing the full
            // vector rendering pipeline to properly draw the vector graphics
            Log.w(LOG_TAG, "Vector group rasterization not fully implemented - returning placeholder")
            return createPlaceholderBitmap(width, height)

        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error rasterizing vector group", e)
            return null
        }
    }

    private fun createPlaceholderBitmap(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Create a simple placeholder - a light gray rectangle with a border
        canvas.drawColor(0xFFE0E0E0.toInt()) // Light gray background

        // Draw a simple border
        val paint = android.graphics.Paint().apply {
            color = 0xFF999999.toInt() // Dark gray border
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawRect(1f, 1f, width - 1f, height - 1f, paint)

        return bitmap
    }

    private fun createBitmapFromPainter(painter: Painter): Bitmap? {
        try {
            val intrinsicSize = painter.intrinsicSize
            val width = if (intrinsicSize.width.isFinite()) {
                intrinsicSize.width.toInt()
            } else {
                viewDetails.frame.width()
            }
            val height = if (intrinsicSize.height.isFinite()) {
                intrinsicSize.height.toInt()
            } else {
                viewDetails.frame.height()
            }

            if (width <= 0 || height <= 0) {
                return null
            }

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.MULTIPLY)

            // This is a simplified approach - in practice, drawing a Compose Painter
            // to an Android Canvas would require more complex integration
            // For now, we'll return null to indicate we couldn't convert this painter type
            Log.d(LOG_TAG, "Cannot directly draw Compose Painter to Android Canvas")
            return null

        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error creating bitmap from painter", e)
            return null
        }
    }

    private fun generateCacheKey(painter: Painter): String {
        val keyBuilder = StringBuilder()

        keyBuilder.append(painter.javaClass.simpleName)
        keyBuilder.append("_")
        keyBuilder.append(painter.hashCode())

        val intrinsicSize = painter.intrinsicSize
        if (!intrinsicSize.isUnspecified && intrinsicSize.width.isFinite() && intrinsicSize.height.isFinite()) {
            keyBuilder.append("_").append(intrinsicSize.width.toInt())
                .append("x").append(intrinsicSize.height.toInt())
        }

        keyBuilder.append("_").append(contentScale.toString())

        return keyBuilder.toString()
    }

    private fun bitmapToBase64(bitmap: Bitmap): String? {
        return ImageCompressionUtils.bitmapToBase64(bitmap)
    }


    fun getImageDataUrl(): String? {
        return ImageCompressionUtils.toImageDataUrl(imageData)
    }

    fun getImageData(): String? = imageData

    private fun shouldUnMaskImage(node: SemanticsNode): Boolean {
        // Check current node and all parent nodes for privacy tags
        val privacyTag = ComposePrivacyUtils.getEffectivePrivacyTag(node)
        val isCustomMode = sessionReplayConfiguration.mode == ComposeSessionReplayConstants.Modes.CUSTOM
        if(isCustomMode) {
            val hasMaskTag = privacyTag == ComposeSessionReplayConstants.PrivacyTags.MASK
            return !hasMaskTag
        } else {
            return true
        }
    }

    private fun getBackgroundSizeFromContentScale(): String {
        return when (contentScale) {
            ContentScale.FillBounds -> "100% 100%"
            ContentScale.Crop -> "cover"
            ContentScale.Fit, ContentScale.Inside -> "contain"
            ContentScale.FillWidth -> "100% auto"
            ContentScale.FillHeight -> "auto 100%"
            else -> "auto"
        }
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
        generateImageCss(cssBuilder)
        return cssBuilder.toString()
    }

    override fun generateInlineCss(): String {
        val cssBuilder = StringBuilder(viewDetails.generateInlineCSS())
        cssBuilder.append(" ")
        generateImageCss(cssBuilder)
        return cssBuilder.toString()
    }

    private fun generateImageCss(cssBuilder: StringBuilder) {
        cssBuilder.append("background-color: ")
        cssBuilder.append(backgroundColor)
        cssBuilder.append("; ")

        imageData?.let {
            cssBuilder.append("background-image: url(")
            cssBuilder.append(getImageDataUrl())
            cssBuilder.append("); ")
        }

        cssBuilder.append("background-size: ")
        cssBuilder.append(getBackgroundSizeFromContentScale())
        cssBuilder.append("; ")
        cssBuilder.append("background-repeat: no-repeat; ")
        cssBuilder.append("background-position: center; ")
    }

    override fun generateRRWebNode(): RRWebElementNode {
        val attributes = Attributes(viewDetails.cssSelector)
        return RRWebElementNode(
            attributes,
            RRWebElementNode.TAG_TYPE_DIV,
            viewDetails.viewId,
            ArrayList()
        )
    }

    override fun generateDifferences(other: SessionReplayViewThingyInterface): List<MutationRecord>? {
        if (other !is ComposeImageThingy) {
            return null
        }

        val styleDifferences = mutableMapOf<String, String>()
        val otherViewDetails = other.viewDetails

        if (viewDetails.frame != otherViewDetails.frame) {
            styleDifferences["left"] = "${otherViewDetails.frame.left}px"
            styleDifferences["top"] = "${otherViewDetails.frame.top}px"
            styleDifferences["width"] = "${otherViewDetails.frame.width()}px"
            styleDifferences["height"] = "${otherViewDetails.frame.height()}px"
        }

        if (viewDetails.backgroundColor != otherViewDetails.backgroundColor) {
            styleDifferences["background-color"] = otherViewDetails.backgroundColor
        }

        if (imageData != other.imageData) {
            other.getImageDataUrl()?.let { url ->
                styleDifferences["background-image"] = "url($url)"
            }
        }

        val attributes = Attributes(viewDetails.cssSelector)
        attributes.metadata = styleDifferences
        val mutations = mutableListOf<MutationRecord>()
        mutations.add(RRWebMutationData.AttributeRecord(viewDetails.viewId, attributes))
        return mutations
    }

    override fun generateAdditionNodes(parentId: Int): List<RRWebMutationData.AddRecord> {
        val node = generateRRWebNode()
        node.attributes.metadata["style"] = generateInlineCss()
        val addRecord = RRWebMutationData.AddRecord(parentId, null, node)

        val adds = mutableListOf<RRWebMutationData.AddRecord>()
        adds.add(addRecord)
        return adds
    }

    override fun getViewId(): Int = viewDetails.viewId

    override fun getParentViewId(): Int = viewDetails.parentId

    override fun hasChanged(other: SessionReplayViewThingyInterface?): Boolean {
        // Quick check: if it's not the same type, it has changed
        if (other == null || other !is ComposeImageThingy) {
            return true
        }

        // Compare using hashCode (which should reflect the content)
        return this.hashCode() != other.hashCode()
    }
}