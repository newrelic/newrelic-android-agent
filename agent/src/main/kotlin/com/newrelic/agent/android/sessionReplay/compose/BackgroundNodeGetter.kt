package com.newrelic.agent.android.sessionReplay.compose

import android.util.Log
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.newrelic.agent.android.sessionReplay.internal.ReflectionBaseGetter
import java.lang.reflect.Field

/**
 * BackgroundNodeGetter - Reflection-based utility for extracting background styling information
 * from Compose's internal BackgroundNode instances.
 *
 * This class uses reflection to access private fields in androidx.compose.foundation.BackgroundNode
 * which is an internal Compose class that manages background modifiers applied via
 * Modifier.background().
 *
 * Why Reflection?
 * - BackgroundNode is internal to Compose and not part of the public API
 * - Cross-version compatibility: Different Compose versions may have different implementations
 * - Non-invasive: Works without modifying application code
 * - Graceful degradation: Returns safe defaults if reflection fails
 *
 * Background Properties Extracted:
 * - color: Solid color background (stored as Long/ULong in ARGB format)
 * - brush: Gradient or pattern background (LinearGradient, RadialGradient, etc.)
 * - alpha: Opacity value (0.0 = transparent, 1.0 = opaque)
 * - shape: Background shape (RectangleShape, CircleShape, RoundedCornerShape, etc.)
 *
 * Usage Example:
 * ```
 * val getter = BackgroundNodeGetter()
 * for (node in modifierNodes) {
 *     if (getter.isInstance(node)) {
 *         val color = getter.getColor(node)
 *         val brush = getter.getBrush(node)
 *         val alpha = getter.getAlpha(node)
 *         val shape = getter.getShape(node)
 *         // Use extracted properties for session replay...
 *     }
 * }
 * ```
 *
 * Real-World Example:
 * ```
 * // In user's Compose code:
 * Box(
 *     modifier = Modifier.background(
 *         color = Color(0xFF6200EE),
 *         shape = RoundedCornerShape(16.dp)
 *     )
 * )
 *
 * // BackgroundNodeGetter extracts:
 * getColor() → Color(0xFF6200EE)
 * getBrush() → null
 * getAlpha() → 1.0f
 * getShape() → RoundedCornerShape(16.dp)
 * ```
 *
 * @see ReflectionBaseGetter for the base reflection utilities
 */
class BackgroundNodeGetter : ReflectionBaseGetter(TARGET_CLASS_NAME) {

    companion object {
        private const val LOG_TAG = "BackgroundNodeGetter"
        private const val TARGET_CLASS_NAME = "androidx.compose.foundation.BackgroundNode"

        // Field names in BackgroundNode
        private const val FIELD_COLOR = "color"
        private const val FIELD_BRUSH = "brush"
        private const val FIELD_ALPHA = "alpha"
        private const val FIELD_SHAPE = "shape"
    }

    /**
     * Reflected field for color property
     * In Compose, color is stored as a Long representing ARGB values packed together
     */
    private val colorField: Field? = getField(FIELD_COLOR)

    /**
     * Reflected field for brush property
     * Brush can be LinearGradient, RadialGradient, or other gradient/pattern types
     */
    private val brushField: Field? = getField(FIELD_BRUSH)

    /**
     * Reflected field for alpha property
     * Alpha is a Float value between 0.0 (transparent) and 1.0 (opaque)
     */
    private val alphaField: Field? = getField(FIELD_ALPHA)

    /**
     * Reflected field for shape property
     * Shape defines the outline (RectangleShape, CircleShape, RoundedCornerShape, etc.)
     */
    private val shapeField: Field? = getField(FIELD_SHAPE)

    /**
     * Gets the color value from a BackgroundNode
     *
     * This method extracts the solid color background. In Compose, colors are represented
     * as ULong values with ARGB components packed together.
     *
     * Implementation Details:
     * - Retrieves the color field value via reflection
     * - Converts Long to Color using Compose's Color constructor
     * - Returns Color.Unspecified if field doesn't exist or reflection fails
     *
     * @param node The Modifier.Node instance (should be a BackgroundNode)
     * @return The Color value or Color.Unspecified if not available
     *
     * Example:
     * ```
     * // User code: Modifier.background(Color.Red)
     * val color = getter.getColor(node)
     * // Returns: Color(0xFFFF0000)
     * ```
     */
    @OptIn(ExperimentalComposeUiApi::class)
    fun getColor(node: Modifier.Node): Color {
        return try {
            val colorValue = colorField?.getFieldValue(node)

            when (colorValue) {
                is Long -> {
                    // Color is stored as Long, convert to ULong and then to Color
                    Color(colorValue.toULong())
                }
                is ULong -> {
                    // Direct ULong conversion
                    Color(colorValue)
                }
                else -> {
                    Log.d(LOG_TAG, "Color field value is not Long/ULong: ${colorValue?.javaClass?.simpleName}")
                    Color.Unspecified
                }
            }
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Error getting color from BackgroundNode", e)
            Color.Unspecified
        }
    }

    /**
     * Gets the brush value from a BackgroundNode
     *
     * A Brush represents gradient or pattern backgrounds like LinearGradient, RadialGradient,
     * or SweepGradient. If a solid color is used instead of a brush, this returns null.
     *
     * Implementation Details:
     * - Retrieves the brush field value via reflection
     * - Validates that the value is a Brush instance
     * - Returns null if not set or if reflection fails
     *
     * @param node The Modifier.Node instance (should be a BackgroundNode)
     * @return The Brush object or null if not available
     *
     * Example:
     * ```
     * // User code: Modifier.background(
     * //   brush = Brush.linearGradient(listOf(Color.Red, Color.Blue))
     * // )
     * val brush = getter.getBrush(node)
     * // Returns: LinearGradient instance
     * ```
     */
    @OptIn(ExperimentalComposeUiApi::class)
    fun getBrush(node: Modifier.Node): Brush? {
        return try {
            val brushValue = brushField?.getFieldValue(node)

            if (brushValue is Brush) {
                brushValue
            } else {
                if (brushValue != null) {
                    Log.d(LOG_TAG, "Brush field value is not Brush: ${brushValue.javaClass.simpleName}")
                }
                null
            }
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Error getting brush from BackgroundNode", e)
            null
        }
    }

    /**
     * Gets the alpha (opacity) value from a BackgroundNode
     *
     * Alpha controls the transparency of the background, where:
     * - 0.0f = completely transparent
     * - 1.0f = completely opaque
     * - Values in between = partial transparency
     *
     * Implementation Details:
     * - Retrieves the alpha field value via reflection
     * - Validates that the value is a Float
     * - Returns 1.0f (fully opaque) as default if not found
     *
     * @param node The Modifier.Node instance (should be a BackgroundNode)
     * @return The alpha value (0.0-1.0) or 1.0 if not available
     *
     * Example:
     * ```
     * // User code: Modifier.background(Color.Red, alpha = 0.5f)
     * val alpha = getter.getAlpha(node)
     * // Returns: 0.5f
     * ```
     */
    @OptIn(ExperimentalComposeUiApi::class)
    fun getAlpha(node: Modifier.Node): Float {
        return try {
            val alphaValue = alphaField?.getFieldValue(node)

            when (alphaValue) {
                is Float -> alphaValue
                is Number -> alphaValue.toFloat()
                else -> {
                    if (alphaValue != null) {
                        Log.d(LOG_TAG, "Alpha field value is not Float: ${alphaValue.javaClass.simpleName}")
                    }
                    1.0f // Default: fully opaque
                }
            }
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Error getting alpha from BackgroundNode", e)
            1.0f // Default: fully opaque
        }
    }

    /**
     * Gets the shape from a BackgroundNode
     *
     * Shape defines the outline of the background. Common shapes include:
     * - RectangleShape (default rectangle)
     * - CircleShape (perfect circle)
     * - RoundedCornerShape (rectangle with rounded corners)
     * - CutCornerShape (rectangle with cut corners)
     * - Custom shapes implementing Shape interface
     *
     * Implementation Details:
     * - Retrieves the shape field value via reflection
     * - Validates that the value is a Shape instance
     * - Returns null if not set or if reflection fails
     *
     * @param node The Modifier.Node instance (should be a BackgroundNode)
     * @return The Shape object or null if not available
     *
     * Example:
     * ```
     * // User code: Modifier.background(
     * //   color = Color.Blue,
     * //   shape = RoundedCornerShape(8.dp)
     * // )
     * val shape = getter.getShape(node)
     * // Returns: RoundedCornerShape instance
     * ```
     */
    @OptIn(ExperimentalComposeUiApi::class)
    fun getShape(node: Modifier.Node): Shape? {
        return try {
            val shapeValue = shapeField?.getFieldValue(node)

            if (shapeValue is Shape) {
                shapeValue
            } else {
                if (shapeValue != null) {
                    Log.d(LOG_TAG, "Shape field value is not Shape: ${shapeValue.javaClass.simpleName}")
                }
                null
            }
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Error getting shape from BackgroundNode", e)
            null
        }
    }

    /**
     * Checks if all reflected fields are available
     * Useful for determining if BackgroundNode structure matches expectations
     *
     * @return true if all fields were successfully reflected, false otherwise
     */
    fun areAllFieldsAvailable(): Boolean {
        return colorField != null &&
               brushField != null &&
               alphaField != null &&
               shapeField != null
    }

    /**
     * Gets diagnostic information about which fields are available
     * Useful for debugging cross-version compatibility issues
     *
     * @return Map of field name to availability status
     */
    fun getFieldAvailability(): Map<String, Boolean> {
        return mapOf(
            FIELD_COLOR to (colorField != null),
            FIELD_BRUSH to (brushField != null),
            FIELD_ALPHA to (alphaField != null),
            FIELD_SHAPE to (shapeField != null)
        )
    }
}