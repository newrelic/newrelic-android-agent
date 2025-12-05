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
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.core.graphics.createBitmap
import com.newrelic.agent.android.AgentConfiguration
import com.newrelic.agent.android.sessionReplay.ImageCompressionUtils
import com.newrelic.agent.android.sessionReplay.SessionReplayConfiguration
import com.newrelic.agent.android.sessionReplay.SessionReplayLocalConfiguration
import com.newrelic.agent.android.sessionReplay.SessionReplayViewThingyInterface
import com.newrelic.agent.android.sessionReplay.internal.ComposePainterReflectionUtils
import com.newrelic.agent.android.sessionReplay.models.Attributes
import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.MutationRecord
import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.RRWebMutationData
import com.newrelic.agent.android.sessionReplay.models.RRWebElementNode
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Session replay representation of Jetpack Compose Image composables.
 *
 * This class handles extraction, compression, and privacy masking of images rendered
 * in Compose UI for session replay functionality. It supports multiple painter types
 * including BitmapPainter, VectorPainter, and AsyncImagePainter (Coil).

 * @param viewDetails The Compose view's layout and styling information
 * @param semanticsNode The Compose SemanticsNode containing image data
 * @param agentConfiguration Agent configuration including session replay settings
 *
 * @see ComposeViewDetails
 * @see SessionReplayViewThingyInterface
 */
open class ComposeImageThingy(
    private val viewDetails: ComposeViewDetails,
    private val semanticsNode: SemanticsNode,
    agentConfiguration: AgentConfiguration
) : SessionReplayViewThingyInterface {

    companion object {
        private const val LOG_TAG = ComposeSessionReplayConstants.LogTags.COMPOSE_IMAGE

        // Static cache shared across all instances
        private const val MAX_CACHE_SIZE_BYTES = 50 * 1024 * 1024 // 50MB
        private val imageExtractionExecutor = Executors.newCachedThreadPool()
        private val imageCache = object : LruCache<String, String>(MAX_CACHE_SIZE_BYTES) {
            override fun sizeOf(key: String, value: String): Int {
                return value.length * 2
            }
        }

        /**
         * Clears all cached image data from the LRU cache.
         *
         * This can be used to free memory or force fresh image extraction.
         * Call this when memory pressure is detected or session replay is being reset.
         */
        fun clearImageCache() {
            imageCache.evictAll()
            Log.d(LOG_TAG, "Image cache cleared")
        }

        /**
         * Returns diagnostic information about the image cache performance.
         *
         * @return A formatted string containing cache size, hit count, and miss count
         */
        fun getCacheStats(): String {
            return "Cache stats - Size: ${imageCache.size()}, Hits: ${imageCache.hitCount()}, Misses: ${imageCache.missCount()}"
        }
    }

    @Volatile
    private var imageData: String? = null

    private var subviews: List<SessionReplayViewThingyInterface> = emptyList()
    var shouldRecordSubviews = false

    private val contentScale: ContentScale
    private val backgroundColor: String = viewDetails.backgroundColor

    protected val sessionReplayConfiguration: SessionReplayConfiguration =
        agentConfiguration.sessionReplayConfiguration
    protected val sessionReplayLocalConfiguration: SessionReplayLocalConfiguration =
        agentConfiguration.sessionReplayLocalConfiguration

    init {
        contentScale = extractContentScale()

        if (shouldUnMaskImage(semanticsNode)) {
            imageExtractionExecutor.execute {
                try {
                    imageData = extractImageFromModifierInfo()
                } catch (e: Exception) {
                    Log.e(LOG_TAG, "Error extracting image", e)
                }
            }
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
                    modifier.javaClass.name.contains("PainterModifier")
                ) {

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
            val cachedBitmap =
                ComposePainterReflectionUtils.extractCachedBitmapFromVectorPainter(vectorPainter)
            if (cachedBitmap != null) {
                Log.d(LOG_TAG, "Successfully extracted cached bitmap from VectorPainter")
                return cachedBitmap
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
            val bitmap =
                ComposePainterReflectionUtils.extractBitmapFromAsyncImagePath(asyncImagePainter)
            if (bitmap != null) {
                Log.d(LOG_TAG, "Successfully extracted bitmap from AsyncImagePainter path")
                return bitmap
            }

            // Fallback: Try to access the delegate painter (BitmapPainter, VectorPainter, etc.)
            val delegatePainter =
                ComposePainterReflectionUtils.getDelegatePainter(asyncImagePainter)
            if (delegatePainter != null) {
                Log.d(LOG_TAG, "Found delegate painter: ${delegatePainter.javaClass.simpleName}")
                return when (delegatePainter) {
                    is BitmapPainter -> extractBitmapFromBitmapPainter(
                        delegatePainter
                    )

                    is VectorPainter -> createBitmapFromVectorPainter(
                        delegatePainter
                    )

                    else -> null
                }
            }

            // Final fallback: Create a loading placeholder for AsyncImage
            Log.w(LOG_TAG, "Could not extract bitmap from AsyncImagePainter, creating placeholder")
            return null

        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error extracting bitmap from AsyncImagePainter", e)
            return null
        }
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

            val bitmap = createBitmap(width, height)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.MULTIPLY)

            // This is a simplified approach - in practice, drawing a Compose Painter
            // to an Android Canvas would require more complex integration
            // For now, we'll return null to indicate we couldn't convert this painter type
            Log.d(LOG_TAG, "Cannot directly draw Compose Painter to Android Canvas")
            return bitmap

        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error creating bitmap from painter", e)
            return null
        }
    }

    private fun generateCacheKey(painter: Painter): String {
        val intrinsicSize = painter.intrinsicSize

        // Use fixed-format string with string interpolation (more efficient)
        return buildString() {
            append(painter.javaClass.simpleName)
            append('_')
            append(painter.hashCode())

            if (!intrinsicSize.isUnspecified &&
                intrinsicSize.width.isFinite() &&
                intrinsicSize.height.isFinite()
            ) {
                append('_')
                append(intrinsicSize.width.toInt())
                append('x')
                append(intrinsicSize.height.toInt())
            }

            append('_')
            append(contentScale.javaClass.simpleName)
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String? {
        return ImageCompressionUtils.bitmapToBase64(bitmap)
    }

    private val imageDataUrl: String? by lazy {
        imageData?.let { ImageCompressionUtils.toImageDataUrl(it) }
    }


    /**
     * Determines if an image should be captured (unmasked) for session replay.
     *
     * ### Decision Logic:
     * 1. **CUSTOM Mode**: Only capture if NOT masked
     *    - Check privacy tag (inherited from ComposeTreeCapture propagation)
     *    - Check global isMaskAllImages flag
     *    - Return false if either indicates masking
     *
     * 2. **Non-CUSTOM Mode** (DEFAULT): Capture all images
     *    - Always return true (capture everything)
     *
     * ### Performance:
     * - O(1) lookup via ComposePrivacyUtils (tag already propagated)
     * - No parent chain traversal needed
     *
     * @param node The SemanticsNode to check for masking
     * @return true if image should be captured, false if should be masked (show placeholder)
     */
    private fun shouldUnMaskImage(node: SemanticsNode): Boolean {
        val testTag = try {
            node.config.getOrElseNullable(SemanticsProperties.TestTag) { null }
        } catch (e: IllegalStateException) {
            null
        }

        when (testTag) {
            "nr-unmask" -> return true
            "nr-mask" -> return false
        }

        if (testTag != null && sessionReplayConfiguration.unmaskedViewTags.contains(testTag)) {
            return true
        }

        if (testTag != null && sessionReplayConfiguration.maskedViewTags.contains(testTag)) {
            return false
        }

        if (testTag != null && sessionReplayLocalConfiguration.unmaskedViewTags.contains(testTag)) {
            return true
        }

        if (testTag != null && sessionReplayLocalConfiguration.maskedViewTags.contains(testTag)) {
            return false
        }

        val privacyTag = ComposePrivacyUtils.getEffectivePrivacyTag(node)
        val isCustomMode = ComposeSessionReplayConstants.Modes.CUSTOM.equals(
            sessionReplayConfiguration.mode
        )

        return if (isCustomMode) {
            val hasMaskTag = ComposeSessionReplayConstants.PrivacyTags.MASK.equals(privacyTag)
                    || sessionReplayConfiguration.isMaskAllImages
            !hasMaskTag
        } else {
            true
        }
    }

    private val backgroundSize: String by lazy {
        when (contentScale) {
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

    override fun getViewDetails(): Any? {
        return viewDetails
    }

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

    /**
     * Generates CSS for image rendering with privacy masking support.
     *
     * ### Rendering Modes:
     * 1. **Normal Image** (imageData exists):
     *    - Shows actual image with background-image
     *    - Uses backgroundColor as fallback/transparency base
     *    - Applies contentScale via background-size
     *
     * 2. **Masked Image** (imageData is null due to privacy tag):
     *    - Shows gray placeholder (#CCCCCC) instead of image
     *    - No background-image properties (optimization)
     *    - Indicates sensitive content to reviewers
     *
     * ### Performance:
     * - Lazy imageDataUrl evaluation (computed once on first access)
     * - Skips background-image properties for masked images (~30 bytes saved)
     * - Reuses backgroundSize lazy property
     */
    private fun generateImageCss(cssBuilder: StringBuilder) {
        if (imageData != null) {
            // Normal image rendering: use actual background color
            // This shows behind transparent images or as fallback during load
            if (backgroundColor.isNotEmpty() && backgroundColor != "transparent") {
                cssBuilder.append("background-color: ")
                cssBuilder.append(backgroundColor)
                cssBuilder.append("; ")
            }

            // Add image with scaling properties
            cssBuilder.append("background-image: url(")
            cssBuilder.append(imageDataUrl)
            cssBuilder.append("); ")
            cssBuilder.append("background-size: ")
            cssBuilder.append(backgroundSize)
            cssBuilder.append("; ")
            cssBuilder.append("background-repeat: no-repeat; ")
            cssBuilder.append("background-position: center; ")
        } else {
            // Masked image: show gray placeholder (privacy-protected)
            // This indicates to replay viewers that an image was present but masked
            cssBuilder.append("background-color: #CCCCCC; ")
            // Note: No background-image properties needed (performance optimization)
        }
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
            return emptyList()
        }

        val styleDifferences = HashMap<String, String>(8)
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
            other.imageDataUrl?.let { url ->
                styleDifferences["background-image"] = "url($url)"
            }
        }

        if (viewDetails.isHidden() != otherViewDetails.isHidden()) {
            styleDifferences.put(
                "visibility",
                if (otherViewDetails.isHidden()) "hidden" else "visible"
            )
        }
        if (styleDifferences.isEmpty()) {
            return emptyList()  // or emptyList()
        }

        val attributes = Attributes(viewDetails.cssSelector)
        attributes.metadata = styleDifferences
        return listOf(RRWebMutationData.AttributeRecord(viewDetails.viewId, attributes))
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
        if (other == null || other !is ComposeImageThingy) {
            return true
        }

        // Compare actual content
        return viewDetails != other.viewDetails ||
                imageData != other.imageData ||
                backgroundColor != other.backgroundColor
    }
}