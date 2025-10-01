package com.newrelic.agent.android.sessionReplay.compose

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode.Companion.Color
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsNode
import kotlin.text.get

class SemanticsNodeUtil {
    
    companion object {
        fun isNodePositionUnAvailable(node: SemanticsNode): Boolean {
            return node.positionInRoot == Offset.Zero && node.children.isEmpty()
        }

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

    }





}

