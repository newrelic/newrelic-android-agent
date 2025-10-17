package com.newrelic.agent.android.stores

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.newrelic.agent.android.logging.AgentLog
import com.newrelic.agent.android.logging.AgentLogManager
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

// Still good to have an enum or sealed class for known types if possible
enum class PreferenceType {
    STRING, BOOLEAN, LONG, SET// Add other types like LONG, FLOAT, STRING_SET as needed
}

object DataStoreKeys {
    private val registeredKeys = ConcurrentHashMap<String, WeakReference<Preferences.Key<*>>>()
    private val log: AgentLog = AgentLogManager.getAgentLog()

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> defineKey(name: String, type: PreferenceType): Preferences.Key<T> {
        require(name.isNotBlank()) { "Key name cannot be blank." }

        val existingRef = registeredKeys[name]
        val existingKey = existingRef?.get()

        if (existingKey != null) {
            // Optional: You can still perform a type check if you are paranoid about key collisions.
            // For simplicity, this example assumes no name/type collisions.
            try {
                return existingKey as Preferences.Key<T>
            } catch (e: ClassCastException) {
                // This would happen if another part of the code created a key with the same name
                // but a different type. We'll fall through and overwrite it.
                log.warn("Warning: Key '$name' existed but could not be cast. Recreating.")
            }
        }

        val newKey: Preferences.Key<T> = when (type) {
            PreferenceType.STRING -> stringPreferencesKey(name) as Preferences.Key<T>
            PreferenceType.LONG -> longPreferencesKey(name) as Preferences.Key<T>
            PreferenceType.BOOLEAN -> booleanPreferencesKey(name) as Preferences.Key<T>
            PreferenceType.SET -> stringSetPreferencesKey(name) as Preferences.Key<T>
        }

        registeredKeys[name] = WeakReference(newKey)
        return newKey
    }

    fun expungeStaleEntries() {
        val i = registeredKeys.entries.iterator()
        while (i.hasNext()) {
            val entry = i.next()
            if (entry.value.get() == null) {
                i.remove()
            }
        }
    }

    fun clearRegisteredKeys() {
        registeredKeys.clear()
    }
}

