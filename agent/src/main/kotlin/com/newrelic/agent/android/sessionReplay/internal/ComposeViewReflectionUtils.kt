package com.newrelic.agent.android.sessionReplay.internal

import android.util.Log
import android.view.View
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner

/**
 * Utility object for Compose View reflection operations
 * Used to access internal Compose classes like AndroidComposeView
 */
object ComposeViewReflectionUtils {
    private const val LOG_TAG = "ComposeViewReflectionUtils"

    /**
     * Gets the SemanticsOwner from an AndroidComposeView using reflection
     * AndroidComposeView is an internal class, so we need reflection to access it
     *
     * @param view The view to extract the SemanticsOwner from
     * @return The SemanticsOwner or null if extraction fails
     */
    fun getSemanticsOwnerFromView(view: View): SemanticsOwner? {
        try {
            // AndroidComposeView is internal, use reflection
            val androidComposeViewClass = Class.forName("androidx.compose.ui.platform.AndroidComposeView")

            if (androidComposeViewClass.isInstance(view)) {
                // Get SemanticsOwner using reflection
                val getSemanticsOwnerMethod = androidComposeViewClass.getMethod("getSemanticsOwner")
                val semanticsOwner = getSemanticsOwnerMethod.invoke(view)

                if (semanticsOwner is SemanticsOwner) {
                    return semanticsOwner
                }
            }
        } catch (e: ClassNotFoundException) {
            Log.w(LOG_TAG, "AndroidComposeView class not found. Compose may not be available.", e)
        } catch (e: NoSuchMethodException) {
            Log.w(LOG_TAG, "getSemanticsOwner method not found on AndroidComposeView", e)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error getting SemanticsOwner from AndroidComposeView", e)
        }
        return null
    }

    /**
     * Gets the unmerged root SemanticsNode from a SemanticsOwner
     *
     * @param semanticsOwner The SemanticsOwner to get the root node from
     * @return The root SemanticsNode or null if extraction fails
     */
    fun getUnmergedRootSemanticsNode(semanticsOwner: SemanticsOwner): SemanticsNode? {
        try {
            // Get unmerged root semantics node
            val getUnmergedRootMethod = semanticsOwner.javaClass.getMethod("getUnmergedRootSemanticsNode")
            val semanticsNode = getUnmergedRootMethod.invoke(semanticsOwner)

            if (semanticsNode is SemanticsNode) {
                return semanticsNode
            }
        } catch (e: NoSuchMethodException) {
            Log.w(LOG_TAG, "getUnmergedRootSemanticsNode method not found on SemanticsOwner", e)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error getting unmerged root SemanticsNode", e)
        }
        return null
    }

    /**
     * Checks if the given view is an instance of AndroidComposeView
     *
     * @param view The view to check
     * @return true if the view is an AndroidComposeView, false otherwise
     */
    fun isAndroidComposeView(view: View): Boolean {
        return try {
            val androidComposeViewClass = Class.forName("androidx.compose.ui.platform.AndroidComposeView")
            androidComposeViewClass.isInstance(view)
        } catch (e: ClassNotFoundException) {
            false
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error checking if view is AndroidComposeView", e)
            false
        }
    }
}