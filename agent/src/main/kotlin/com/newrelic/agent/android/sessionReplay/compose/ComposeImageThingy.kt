package com.newrelic.agent.android.sessionReplay.compose

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Build
import android.util.Base64
import android.util.Log
import android.util.LruCache
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import com.newrelic.agent.android.AgentConfiguration
import com.newrelic.agent.android.sessionReplay.SessionReplayConfiguration
import com.newrelic.agent.android.sessionReplay.SessionReplayLocalConfiguration
import com.newrelic.agent.android.sessionReplay.SessionReplayViewThingyInterface
import com.newrelic.agent.android.sessionReplay.ViewDetails
import com.newrelic.agent.android.sessionReplay.models.Attributes
import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.MutationRecord
import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.RRWebMutationData
import com.newrelic.agent.android.sessionReplay.models.RRWebElementNode
import java.io.ByteArrayOutputStream

open class ComposeImageThingy(
    private val viewDetails: ComposeViewDetails,
    private val semanticsNode: SemanticsNode,
    agentConfiguration: AgentConfiguration
) : SessionReplayViewThingyInterface {

    companion object {
        private const val LOG_TAG = "ComposeImageThingy"

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

        if (!shouldMaskImage(semanticsNode)) {
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
        try {
            // Common field names for painter in Compose Image modifiers
            val painterFields = arrayOf("painter", "intrinsicPainter", "imagePainter")

            for (fieldName in painterFields) {
                try {
                    val field = modifier.javaClass.getDeclaredField(fieldName)
                    field.isAccessible = true
                    val painter = field.get(modifier)

                    if (painter is Painter) {
                        return painter
                    }
                } catch (e: NoSuchFieldException) {
                    // Field doesn't exist, continue to next one
                } catch (e: IllegalAccessException) {
                    // Can't access field, continue to next one
                }
            }

            // Try to find painter through methods
            val methods = modifier.javaClass.declaredMethods
            for (method in methods) {
                if (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        method.name.contains("painter", ignoreCase = true) &&
                            method.parameterCount == 0 &&
                            Painter::class.java.isAssignableFrom(method.returnType)
                    } else {
                        TODO("VERSION.SDK_INT < O")
                    }
                ) {
                    try {
                        method.isAccessible = true
                        val painter = method.invoke(modifier)
                        if (painter is Painter) {
                            return painter
                        }
                    } catch (e: Exception) {
                        // Method invocation failed, continue
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error extracting painter using reflection", e)
        }

        return null
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
        try {
            // Use reflection to get the ImageBitmap from BitmapPainter
            val imageField = bitmapPainter.javaClass.getDeclaredField("image")
            imageField.isAccessible = true
            val imageBitmap = imageField.get(bitmapPainter) as? ImageBitmap

            return imageBitmap?.asAndroidBitmap()
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error extracting bitmap from BitmapPainter", e)
            return null
        }
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
            val cachedBitmap = extractCachedBitmapFromVectorPainter(vectorPainter)
            if (cachedBitmap != null) {
                Log.d(LOG_TAG, "Successfully extracted cached bitmap from VectorPainter")
                return cachedBitmap
            }

            // If we can't extract ImageVector, try to get the root group
            val rootGroup = extractRootGroupFromVectorPainter(vectorPainter)
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
            val bitmap = extractBitmapFromAsyncImagePath(asyncImagePainter)
            if (bitmap != null) {
                Log.d(LOG_TAG, "Successfully extracted bitmap from AsyncImagePainter path")
                return bitmap
            }

            // Fallback: Try to access the delegate painter (BitmapPainter, VectorPainter, etc.)
            val delegatePainter = getDelegatePainter(asyncImagePainter)
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

    private fun extractBitmapFromAsyncImagePath(asyncImagePainter: Painter): Bitmap? {
        try {
            // Follow the path: asyncImagePainter.painter._painter.image.bitmap

            // Step 2: Get _painter field from painter
            val _painterField = asyncImagePainter.javaClass.getDeclaredField("_painter")
            _painterField.isAccessible = true
            val _painter = _painterField.get(asyncImagePainter)

            if (_painter == null) {
                Log.w(LOG_TAG, "_painter field is null in painter")
                return null
            }

            // Step 3: Get image field from _painter
            val imageField = _painter.javaClass.getDeclaredField("image")
            imageField.isAccessible = true
            val image = imageField.get(_painter)

            if (image == null) {
                Log.w(LOG_TAG, "image field is null in _painter")
                return null
            }

            // Step 4: Get bitmap from image
            val bitmapField = image.javaClass.getDeclaredField("bitmap")
            bitmapField.isAccessible = true
            val bitmap = bitmapField.get(image)

            if (bitmap is Bitmap) {
                Log.d(LOG_TAG, "Successfully extracted bitmap from AsyncImagePainter path")
                return bitmap
            } else if (bitmap is ImageBitmap) {
                Log.d(LOG_TAG, "Successfully extracted ImageBitmap from AsyncImagePainter path")
                return bitmap.asAndroidBitmap()
            }

        } catch (e: NoSuchFieldException) {
            Log.w(LOG_TAG, "Field not found in AsyncImagePainter path: ${e.message}")
        } catch (e: IllegalAccessException) {
            Log.w(LOG_TAG, "Cannot access field in AsyncImagePainter path: ${e.message}")
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error extracting bitmap from AsyncImagePainter path", e)
        }

        return null
    }


    private fun getDelegatePainter(asyncImagePainter: Painter): Painter? {
        try {
            // AsyncImagePainter often delegates to other painters (BitmapPainter, etc.)
            val delegateFields = arrayOf("delegate", "painter", "currentPainter", "wrappedPainter")

            for (fieldName in delegateFields) {
                try {
                    val field = asyncImagePainter.javaClass.getDeclaredField(fieldName)
                    field.isAccessible = true
                    val delegate = field.get(asyncImagePainter)

                    if (delegate is Painter) {
                        return delegate
                    }
                } catch (e: NoSuchFieldException) {
                    // Field doesn't exist, continue to next one
                } catch (e: IllegalAccessException) {
                    // Can't access field, continue to next one
                }
            }
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error getting delegate painter from AsyncImagePainter", e)
        }
        return null
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

    private fun extractCachedBitmapFromVectorPainter(vectorPainter: VectorPainter): Bitmap? {
        try {
            // Access the nested path: vectorPainter.vector.mDrawScope.cachedImage.bitmap

            // Step 1: Get the vector field from VectorPainter
            val vectorField = vectorPainter.javaClass.getDeclaredField("vector")
            vectorField.isAccessible = true
            val vector = vectorField.get(vectorPainter)

            if (vector == null) {
                Log.w(LOG_TAG, "Vector field is null in VectorPainter")
                return null
            }


            // Step 2: Get mDrawScope from vector
            val drawScopeField = vector.javaClass.getDeclaredField("cacheDrawScope")
            drawScopeField.isAccessible = true
            val drawScope = drawScopeField.get(vector)

            if (drawScope == null) {
                Log.w(LOG_TAG, "mDrawScope field is null in vector")
                return null
            }

            // Step 3: Get cachedImage from mDrawScope
            val cachedImageField = drawScope.javaClass.getDeclaredField("mCachedImage")
            cachedImageField.isAccessible = true
            val cachedImage = cachedImageField.get(drawScope)

            if (cachedImage == null) {
                Log.w(LOG_TAG, "cachedImage field is null in mDrawScope")
                return null
            }

            // Step 4: Get bitmap from cachedImage
            val bitmapField = cachedImage.javaClass.getDeclaredField("bitmap")
            bitmapField.isAccessible = true
            val bitmap = bitmapField.get(cachedImage)

            if (bitmap is Bitmap) {
                Log.d(LOG_TAG, "Successfully extracted cached bitmap from VectorPainter")
                return bitmap
            } else if (bitmap is ImageBitmap) {
                Log.d(LOG_TAG, "Successfully extracted cached ImageBitmap from VectorPainter")
                return bitmap.asAndroidBitmap()
            }

        } catch (e: NoSuchFieldException) {
            Log.w(LOG_TAG, "Field not found in VectorPainter structure: ${e.message}")
        } catch (e: IllegalAccessException) {
            Log.w(LOG_TAG, "Cannot access field in VectorPainter structure: ${e.message}")
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error extracting cached bitmap from VectorPainter", e)
        }

        return null
    }

    private fun extractRootGroupFromVectorPainter(vectorPainter: VectorPainter): Any? {
        try {
            // Try to get the root group directly
            val rootField = vectorPainter.javaClass.getDeclaredField("rootGroup")
            rootField.isAccessible = true
            return rootField.get(vectorPainter)
        } catch (e: Exception) {
            // Try alternative field names
            val groupFields = arrayOf("group", "vectorGroup", "root")
            for (fieldName in groupFields) {
                try {
                    val field = vectorPainter.javaClass.getDeclaredField(fieldName)
                    field.isAccessible = true
                    val group = field.get(vectorPainter)
                    if (group != null) {
                        return group
                    }
                } catch (e: Exception) {
                    // Continue to next field
                }
            }
        }
        return null
    }

    private fun rasterizeImageVector(imageVector: Any, width: Int, height: Int): Bitmap? {
        try {
            // This is a simplified implementation
            // In practice, rasterizing an ImageVector requires complex drawing operations
            // that would involve recreating the vector drawing commands

            Log.d(LOG_TAG, "Attempting to rasterize ImageVector of size ${width}x${height}")

            // Create a bitmap to draw into
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

            // For now, we'll create a placeholder since full vector rasterization
            // would require implementing the entire Compose vector drawing pipeline
            // This could be enhanced in the future to properly render the vector

            Log.w(LOG_TAG, "ImageVector rasterization not fully implemented - returning placeholder")
            return createPlaceholderBitmap(width, height)

        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error rasterizing ImageVector", e)
            return null
        }
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
        return try {
            ByteArrayOutputStream().use { byteArrayOutputStream ->
                val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 10, byteArrayOutputStream)
                } else {
                    @Suppress("DEPRECATION")
                    bitmap.compress(Bitmap.CompressFormat.WEBP, 10, byteArrayOutputStream)
                }

                if (success) {
                    val byteArray = byteArrayOutputStream.toByteArray()
                    Base64.encodeToString(byteArray, Base64.NO_WRAP)
                } else {
                        return null;
                }
            }
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error converting bitmap to Base64", e)
            null
        }
    }


    fun getImageDataUrl(): String? {
        return imageData?.let { "data:image/webp;base64,$it" }
    }

    fun getImageData(): String? = imageData

    private fun shouldMaskImage(node: SemanticsNode): Boolean {
        // For now, always allow images - this can be enhanced with privacy settings later
        val viewTag = node.config.getOrNull(NewRelicPrivacyKey) ?: ""
        val isCustomMode = sessionReplayConfiguration.mode == "custom"
        val hasUnmaskTag = isCustomMode && viewTag == "nr-unmask"
        return !hasUnmaskTag
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
}