package com.newrelic.agent.android.stores

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlin.jvm.javaClass

// Still good to have an enum or sealed class for known types if possible
enum class PreferenceType {
    STRING, BOOLEAN, LONG, SET// Add other types like LONG, FLOAT, STRING_SET as needed
}

object DataStoreKeys {
    val registeredKeys = mutableMapOf<String, Preferences.Key<*>>()

    // Method to define/register a key
    fun <T : Any> defineKey(name: String, type: PreferenceType): Preferences.Key<T> {
        val existingKey = registeredKeys[name];
        if (existingKey != null) {
            val canReuse = when (type) {
                PreferenceType.STRING -> existingKey is Preferences.Key<*> && stringPreferencesKey("dummy").javaClass == existingKey.javaClass
                PreferenceType.LONG -> existingKey is Preferences.Key<*> && intPreferencesKey("dummy").javaClass == existingKey.javaClass
                PreferenceType.BOOLEAN -> existingKey is Preferences.Key<*> && booleanPreferencesKey("dummy").javaClass == existingKey.javaClass
                PreferenceType.SET -> existingKey is Preferences.Key<*> && stringSetPreferencesKey("dummy").javaClass == existingKey.javaClass
                // Add other types as needed
            }

            if (canReuse) {
                try {
                    return existingKey as Preferences.Key<T>
                } catch (e: ClassCastException) {
                    println("Warning: Key '$name' existed but could not be cast to the expected generic type T. Recreating.")
                }
            } else {
                println("Warning: Key '$name' exists with a different type. Overwriting with new type: $type")
            }
        }

        val newKey: Preferences.Key<T> = when (type) {
            PreferenceType.STRING -> stringPreferencesKey(name) as Preferences.Key<T>
            PreferenceType.LONG -> intPreferencesKey(name) as Preferences.Key<T>
            PreferenceType.BOOLEAN -> booleanPreferencesKey(name) as Preferences.Key<T>
            PreferenceType.SET -> stringSetPreferencesKey(name) as Preferences.Key<T>
            // Add other types as needed
        }
        registeredKeys[name] = newKey
        return newKey
    }

    fun clearRegisteredKeys() {
        registeredKeys.clear()
    }

    // Method to get a previously defined key by name, with type checking
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getKey(name: String, expectedType: PreferenceType): Preferences.Key<T>? {
        val key = registeredKeys[name] ?: return null

        // Validate the type of the stored key against the expected type
        val actualKeyTypeMatches = when (expectedType) {
            PreferenceType.STRING -> key.name == name && (key as? Preferences.Key<String>) != null // Check if it's a String key
            PreferenceType.LONG -> key.name == name && (key as? Preferences.Key<Int>) != null
            PreferenceType.BOOLEAN -> key.name == name && (key as? Preferences.Key<Boolean>) != null
            PreferenceType.SET -> key.name == name && (key as? Preferences.Key<Set<String>>) != null
        }

        return if (actualKeyTypeMatches) {
            key as? Preferences.Key<T>
        } else {
            null // Or throw
        }
    }
}
