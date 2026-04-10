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
            // The `painter` property in AsyncImagePainter is delegated via mutableStateOf,
            // so the compiled field name is `painter$delegate` and holds a MutableState<Painter?>.
            // We read the inner painter from that delegate, then extract the bitmap from it.

            // Step 1: Get the painter$delegate field (MutableState<Painter?>)
            val delegateField = asyncImagePainter.javaClass.getDeclaredField("painter\$delegate")
            delegateField.isAccessible = true
            val mutableState = delegateField.get(asyncImagePainter)

            if (mutableState == null) {
                Log.w(LOG_TAG, "painter\$delegate field is null in AsyncImagePainter")
                return null
            }

            // Step 2: Get the value from MutableState via getValue()
            val getValueMethod = mutableState.javaClass.getMethod("getValue")
            val innerPainter = getValueMethod.invoke(mutableState)

            if (innerPainter == null) {
                Log.w(LOG_TAG, "Inner painter is null in AsyncImagePainter (image may still be loading)")
                return null
            }

            // Step 3: The inner painter is typically a BitmapPainter — extract bitmap from it
            if (innerPainter is BitmapPainter) {
                return extractBitmapFromBitmapPainter(innerPainter)
            }

            // Step 4: It could also be a CrossfadePainter wrapping a BitmapPainter
            if (innerPainter is Painter) {
                // Try to get image field directly (works for BitmapPainter subtypes)
                try {
                    val imageField = innerPainter.javaClass.getDeclaredField("image")
                    imageField.isAccessible = true
                    val image = imageField.get(innerPainter)
                    if (image is ImageBitmap) {
                        Log.d(LOG_TAG, "Successfully extracted ImageBitmap from AsyncImagePainter inner painter")
                        return image.asAndroidBitmap()
                    }
                } catch (e: NoSuchFieldException) {
                    // Not a BitmapPainter-like type, try delegate chain
                }

                // Try extracting from a wrapped/delegate painter (e.g. CrossfadePainter)
                val wrappedBitmap = extractBitmapFromWrappedPainter(innerPainter)
                if (wrappedBitmap != null) return wrappedBitmap
            }

            Log.w(LOG_TAG, "Inner painter type not supported: ${innerPainter.javaClass.name}")

        } catch (e: NoSuchFieldException) {
            Log.w(LOG_TAG, "Field not found in AsyncImagePainter path: ${e.message}")
        } catch (e: NoSuchMethodException) {
            Log.w(LOG_TAG, "Method not found in AsyncImagePainter path: ${e.message}")
        } catch (e: IllegalAccessException) {
            Log.w(LOG_TAG, "Cannot access field in AsyncImagePainter path: ${e.message}")
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error extracting bitmap from AsyncImagePainter path", e)
        }

        return null
    }

    /**
     * Extracts a Bitmap from a wrapped painter (e.g. CrossfadePainter) by searching
     * for BitmapPainter fields recursively
     */
    private fun extractBitmapFromWrappedPainter(painter: Painter): Bitmap? {
        try {
            for (field in painter.javaClass.declaredFields) {
                field.isAccessible = true
                val value = field.get(painter)
                if (value is BitmapPainter) {
                    return extractBitmapFromBitmapPainter(value)
                }
                if (value is Painter && value !== painter) {
                    val bitmap = extractBitmapFromWrappedPainter(value)
                    if (bitmap != null) return bitmap
                }
            }
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Error extracting bitmap from wrapped painter: ${e.message}")
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