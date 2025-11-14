package com.newrelic.agent.android.sessionReplay.internal

import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.VectorPainter

/**
 * Utility object for Compose Painter reflection operations
 * Used to extract bitmap data from various Compose Painter types
 */
object ComposePainterReflectionUtils {
    private const val LOG_TAG = "ComposePainterReflectionUtils"

    /**
     * Extracts a Painter from a Compose Image modifier using reflection
     * @param modifier The modifier to extract the painter from
     * @return The Painter object or null if extraction fails
     */
    fun extractPainterFromModifier(modifier: Any): Painter? {
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
                        method.name.contains("painter", ignoreCase = true) &&
                            Painter::class.java.isAssignableFrom(method.returnType)
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

    /**
     * Extracts a Bitmap from a BitmapPainter using reflection
     * @param bitmapPainter The BitmapPainter to extract the bitmap from
     * @return The Bitmap or null if extraction fails
     */
    fun extractBitmapFromBitmapPainter(bitmapPainter: BitmapPainter): Bitmap? {
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

    /**
     * Extracts a cached Bitmap from a VectorPainter using reflection
     * Follows the path: vectorPainter.vector.cacheDrawScope.mCachedImage.bitmap
     * @param vectorPainter The VectorPainter to extract the cached bitmap from
     * @return The cached Bitmap or null if extraction fails
     */
    fun extractCachedBitmapFromVectorPainter(vectorPainter: VectorPainter): Bitmap? {
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
                Log.w(LOG_TAG, "cacheDrawScope field is null in vector")
                return null
            }

            // Step 3: Get cachedImage from mDrawScope
            val cachedImageField = drawScope.javaClass.getDeclaredField("mCachedImage")
            cachedImageField.isAccessible = true
            val cachedImage = cachedImageField.get(drawScope)

            if (cachedImage == null) {
                Log.w(LOG_TAG, "mCachedImage field is null in cacheDrawScope")
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

    /**
     * Extracts the root group from a VectorPainter using reflection
     * @param vectorPainter The VectorPainter to extract the root group from
     * @return The root group object or null if extraction fails
     */
    fun extractRootGroupFromVectorPainter(vectorPainter: VectorPainter): Any? {
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

    /**
     * Extracts a Bitmap from an AsyncImagePainter using reflection
     * Follows the path: asyncImagePainter._painter.image.bitmap
     * @param asyncImagePainter The AsyncImagePainter to extract the bitmap from
     * @return The Bitmap or null if extraction fails
     */
    fun extractBitmapFromAsyncImagePath(asyncImagePainter: Painter): Bitmap? {
        try {
            // Follow the path: asyncImagePainter._painter.image.bitmap

            // Step 1: Get _painter field from painter
            val _painterField = asyncImagePainter.javaClass.getDeclaredField("_painter")
            _painterField.isAccessible = true
            val _painter = _painterField.get(asyncImagePainter)

            if (_painter == null) {
                Log.w(LOG_TAG, "_painter field is null in painter")
                return null
            }

            // Step 2: Get image field from _painter
            val imageField = _painter.javaClass.getDeclaredField("image")
            imageField.isAccessible = true
            val image = imageField.get(_painter)

            if (image == null) {
                Log.w(LOG_TAG, "image field is null in _painter")
                return null
            }

            // Step 3: Get bitmap from image
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

    /**
     * Gets the delegate painter from an AsyncImagePainter using reflection
     * @param asyncImagePainter The AsyncImagePainter to extract the delegate from
     * @return The delegate Painter or null if extraction fails
     */
    fun getDelegatePainter(asyncImagePainter: Painter): Painter? {
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
}