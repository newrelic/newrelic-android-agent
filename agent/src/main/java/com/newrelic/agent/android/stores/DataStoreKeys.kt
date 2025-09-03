package com.newrelic.agent.android.stores

import androidx.compose.ui.input.key.type
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

// Still good to have an enum or sealed class for known types if possible
enum class PreferenceType {
    STRING, BOOLEAN, LONG // Add other types like LONG, FLOAT, STRING_SET as needed
}

object DataStoreKeys {
    private val registeredKeys = mutableMapOf<String, Preferences.Key<*>>()

    // Method to define/register a key
    // This is more about ensuring a key is known and created correctly
    // than truly defining it "on-the-fly" for every access,
    // as you still need to know its type at definition time.
    fun <T : Any> defineKey(name: String, type: PreferenceType): Preferences.Key<T> {
        if (registeredKeys.containsKey(name)) {
            val existingKey = registeredKeys[name]
            val expectedClass = when (type) {
                PreferenceType.STRING -> String::class.java
                PreferenceType.LONG -> Int::class.javaObjectType // Use object type for Int
                PreferenceType.BOOLEAN -> Boolean::class.javaObjectType // Use object type for Boolean
            }

            if (existingKey != null && existingKey.javaClass.getDeclaredField("type").type == expectedClass) {

            }
        }

        val newKey: Preferences.Key<T> = when (type) {
            PreferenceType.STRING -> stringPreferencesKey(name) as Preferences.Key<T>
            PreferenceType.LONG -> intPreferencesKey(name) as Preferences.Key<T>
            PreferenceType.BOOLEAN -> booleanPreferencesKey(name) as Preferences.Key<T>
            // Add other types as needed
        }
        registeredKeys[name] = newKey
        return newKey
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
        }

        return if (actualKeyTypeMatches) {
            key as? Preferences.Key<T>
        } else {
            null // Or throw
        }
    }
}
