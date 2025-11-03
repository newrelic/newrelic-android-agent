package com.newrelic.agent.android.sessionReplay.compose

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsNode

@OptIn(ExperimentalComposeUiApi::class)
class SemanticsNodeUtil {

    companion object {
        // Lazy initialization of BackgroundNodeGetter for performance
        private val backgroundNodeGetter: BackgroundNodeGetter by lazy { BackgroundNodeGetter() }

        fun isNodePositionUnAvailable(node: SemanticsNode): Boolean {
            return node.positionInRoot == Offset.Zero && node.children.isEmpty()
        }

        /**
         * Legacy method for backward compatibility
         * Extracts background color from BackgroundElement (older Compose approach)
         *
         * @deprecated Use getBackgroundColorFromNode() with Modifier.Node instead
         */
        @Throws(ClassNotFoundException::class)
        fun getBackgroundColor(modifier: Modifier?): String? {
            val backGroundClass = Class.forName("androidx.compose.foundation.BackgroundElement")
            try {
                val colorField = backGroundClass.getDeclaredField("color")
                colorField.isAccessible = true
                val colorLong: Long = colorField.get(modifier) as Long
                val color = Color(colorLong.toULong())
                val colorString = Integer.toHexString(color.toArgb())
                if(colorString.length > 2) {
                    return  colorString.substring(2)
                } else {
                    return "FFFFFF"
                }
            } catch (e: NoSuchFieldException) {
                e.printStackTrace()
                return null
            } catch (e: IllegalAccessException) {
                e.printStackTrace()
                return null
            }
        }

        /**
         * Gets the background color from a BackgroundNode using the new BackgroundNodeGetter
         * This is the preferred method for extracting background colors from Compose nodes
         *
         * @param node The Modifier.Node instance (should be a BackgroundNode)
         * @return Hex color string (e.g., "FF0000" for red) or null if not available
         *
         * Example:
         * ```
         * val colorHex = getBackgroundColorFromNode(node)
         * // Returns "6200EE" for Color(0xFF6200EE)
         * ```
         */
        fun getBackgroundColorFromNode(node: Modifier.Node): String? {
            try {
                // Check if this is a BackgroundNode instance
                if (!backgroundNodeGetter.isInstance(node)) {
                    return null
                }

                // Get color from the node
                val color = backgroundNodeGetter.getColor(node)

                // Convert to hex string, excluding alpha if fully opaque
                if (color != Color.Unspecified) {
                    val argb = color.toArgb()
                    val colorString = Integer.toHexString(argb)

                    // Remove alpha channel if fully opaque (FF)
                    return if (colorString.length > 2) {
                        colorString.substring(2)
                    } else {
                        "FFFFFF"
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return null
        }



        /**
         * Gets the background alpha from a BackgroundNode
         *
         * @param node The Modifier.Node instance (should be a BackgroundNode)
         * @return Alpha value (0.0-1.0) or 1.0 if not available
         */
        fun getBackgroundAlpha(node: Modifier.Node): Float {
            return try {
                if (backgroundNodeGetter.isInstance(node)) {
                    backgroundNodeGetter.getAlpha(node)
                } else {
                    1.0f
                }
            } catch (e: Exception) {
                e.printStackTrace()
                1.0f
            }
        }

        /**
         * Checks if a node has a gradient/brush background
         *
         * @param node The Modifier.Node instance (should be a BackgroundNode)
         * @return true if the node has a brush background, false otherwise
         */
        fun hasGradientBackground(node: Modifier.Node): Boolean {
            return try {
                if (backgroundNodeGetter.isInstance(node)) {
                    backgroundNodeGetter.getBrush(node) != null
                } else {
                    false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

        /**
         * Checks if a node has a visible background (either color or brush)
         * A background is considered visible if:
         * - It has a non-transparent color, OR
         * - It has a brush (gradient/pattern)
         *
         * @param node The Modifier.Node instance (should be a BackgroundNode)
         * @return true if the node has a visible background
         */
        fun hasVisibleBackground(node: Modifier.Node): Boolean {
            return try {
                if (!backgroundNodeGetter.isInstance(node)) {
                    return false
                }

                val alpha = backgroundNodeGetter.getAlpha(node)
                if (alpha == 0.0f) {
                    return false // Fully transparent
                }

                val brush = backgroundNodeGetter.getBrush(node)
                if (brush != null) {
                    return true // Has gradient/pattern
                }

                val color = backgroundNodeGetter.getColor(node)
                return color != Color.Unspecified && color != Color.Transparent

            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

        /**
         * Gets the shape from a BackgroundNode
         *
         * @param node The Modifier.Node instance (should be a BackgroundNode)
         * @return The Shape object or null if not available
         */
        fun getBackgroundShape(node: Modifier.Node): Shape? {
            return try {
                if (backgroundNodeGetter.isInstance(node)) {
                    backgroundNodeGetter.getShape(node)
                } else {
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        /**
         * Gets the BackgroundNodeGetter instance for advanced use cases
         * @return The BackgroundNodeGetter instance
         */
        fun getBackgroundGetter(): BackgroundNodeGetter {
            return backgroundNodeGetter
        }

    }

    





}

