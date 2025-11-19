package com.newrelic.agent.android.sessionReplay.internal

import android.util.Log
import java.lang.reflect.Field

/**
 * Base class for reflection-based property getters
 * Provides safe reflection utilities with graceful error handling
 *
 * This class is designed to access internal/private fields from Compose classes
 * that are not part of the public API, while maintaining cross-version compatibility.
 *
 * Usage:
 * ```
 * class MyGetter : ReflectionBaseGetter("androidx.compose.foundation.MyClass") {
 *     private val myField: Field? = getField("fieldName")
 *
 *     fun getProperty(node: Modifier.Node): String? {
 *         return myField?.getFieldValue(node) as? String
 *     }
 * }
 * ```
 */
open class ReflectionBaseGetter(
    private val targetClassName: String
) {
    companion object {
        private const val LOG_TAG = "ReflectionBaseGetter"
    }

    /**
     * The target class that this getter operates on
     * Will be null if the class doesn't exist in the current environment
     */
    protected val targetClass: Class<*>? = loadClass(targetClassName)

    /**
     * Loads a class by name, returning null if not found
     * @param className Fully qualified class name
     * @return The Class object or null if not found
     */
    private fun loadClass(className: String): Class<*>? {
        return try {
            Class.forName(className)
        } catch (e: ClassNotFoundException) {
            Log.d(LOG_TAG, "Class not found: $className (may not be available in this Compose version)")
            null
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Error loading class: $className", e)
            null
        }
    }

    /**
     * Gets a declared field from the target class with safe error handling
     * The field is made accessible for reading private/internal fields
     *
     * @param fieldName The name of the field to retrieve
     * @return The Field object or null if not found
     */
    protected fun getField(fieldName: String): Field? {
        val clazz = targetClass ?: return null

        return try {
            val field = clazz.getDeclaredField(fieldName)
            field.isAccessible = true
            field
        } catch (e: NoSuchFieldException) {
            Log.d(LOG_TAG, "Field '$fieldName' not found in ${clazz.simpleName} (may have been renamed/removed)")
            null
        } catch (e: SecurityException) {
            Log.w(LOG_TAG, "Security exception accessing field '$fieldName' in ${clazz.simpleName}", e)
            null
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Error getting field '$fieldName' from ${clazz.simpleName}", e)
            null
        }
    }

    /**
     * Checks if the given object is an instance of the target class
     * @param obj The object to check
     * @return true if the object is an instance of the target class, false otherwise
     */
    fun isInstance(obj: Any?): Boolean {
        if (obj == null || targetClass == null) {
            return false
        }
        return targetClass.isInstance(obj)
    }

    /**
     * Gets the simple name of the target class
     * @return The simple class name or "Unknown" if class not loaded
     */
    fun getTargetClassName(): String {
        return targetClass?.simpleName ?: "Unknown"
    }

    /**
     * Extension function to safely get a field value with proper error handling
     *
     * @param instance The object instance to get the field value from
     * @return The field value or null if retrieval fails
     */
    protected fun Field.getFieldValue(instance: Any?): Any? {
        if (instance == null) {
            return null
        }

        return try {
            this.get(instance)
        } catch (e: IllegalAccessException) {
            Log.w(LOG_TAG, "Cannot access field '${this.name}'", e)
            null
        } catch (e: IllegalArgumentException) {
            Log.w(LOG_TAG, "Invalid argument for field '${this.name}'", e)
            null
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Error getting field value '${this.name}'", e)
            null
        }
    }

    /**
     * Checks if the target class was successfully loaded
     * @return true if the class exists and was loaded
     */
    fun isAvailable(): Boolean {
        return targetClass != null
    }
}