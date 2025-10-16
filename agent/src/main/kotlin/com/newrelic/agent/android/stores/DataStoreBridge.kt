package com.newrelic.agent.android.stores

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.newrelic.agent.android.logging.AgentLog
import com.newrelic.agent.android.logging.AgentLogManager
import com.newrelic.agent.android.stores.DataStoreKeys.defineKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.future.future
import java.io.IOException
import java.util.concurrent.CompletableFuture

/**
 * Kotlin bridge for DataStore, providing Java-friendly APIs.
 * Requires a CoroutineScope managed by the Java caller.
 */
class DataStoreBridge(
    context: Context,
    private val externalScope: CoroutineScope, // Scope provided by Java
    dataStore: DataStore<Preferences>
) {
    private val dataStore = dataStore
    private val log: AgentLog = AgentLogManager.getAgentLog()

    // Create keys
    private fun createStringKey(keyName: String?): Preferences.Key<String> {
        require(keyName?.isNotBlank() == true) { "Key name cannot be blank" }
        return defineKey(keyName ?: "default_string_key", PreferenceType.STRING)
    }

    private fun createStringSetKey(keyName: String?): Preferences.Key<Set<String>> {
        require(keyName?.isNotBlank() == true) { "Key name cannot be blank" }
        return defineKey(keyName ?: "default_string_set_key", PreferenceType.SET)
    }

    private fun createLongKey(keyName: String?): Preferences.Key<Long> {
        require(keyName?.isNotBlank() == true) { "Key name cannot be blank" }
        return defineKey(keyName ?: "default_long_key", PreferenceType.LONG)
    }

    private fun createBooleanKey(keyName: String?): Preferences.Key<Boolean> {
        require(keyName?.isNotBlank() == true) { "Key name cannot be blank" }
        return defineKey(keyName ?: "default_boolean_key", PreferenceType.BOOLEAN)
    }

    inline fun <reified T : Any> createTypedKeyByName(keyName: String): Preferences.Key<T> {
        return when (T::class) {
            String::class -> stringPreferencesKey(keyName) as Preferences.Key<T>
            Long::class -> longPreferencesKey(keyName) as Preferences.Key<T>
            Boolean::class -> booleanPreferencesKey(keyName) as Preferences.Key<T>
            // ... other types
            else -> throw IllegalArgumentException("Unsupported type ${T::class.simpleName}")
        }
    }

    // --- Read Data (as Flows - keep these public to be accessible from Java Manager) ---
    private fun readStringData(keyName: String?): Flow<String?> {
        val stringKey = createStringKey(keyName)
        return dataStore.data.map { preferences -> preferences[stringKey] }
    }

    private fun readStringSetData(keyName: String?): Flow<Set<String>?> {
        val stringKey = createStringSetKey(keyName)
        return dataStore.data.map { preferences -> preferences[stringKey] }
    }

    private fun readLongData(keyName: String?): Flow<Long?> {
        val longKey = createLongKey(keyName)
        return dataStore.data.map { preferences -> preferences[longKey] }
    }

    private fun readBooleanData(keyName: String?): Flow<Boolean?> {
        val booleanKey = createBooleanKey(keyName)
        return dataStore.data.map { preferences -> preferences[booleanKey] }
    }

    // --- Write Data (CompletableFuture for Java) ---
    fun saveStringValue(key: String?, value: String?): CompletableFuture<Boolean> {
        val stringKey: Preferences.Key<String> =
            defineKey(key ?: "default_string_key", PreferenceType.STRING);

        return externalScope.future(Dispatchers.IO) {
            try {
                dataStore.edit { settings ->
                    if (value == null) {
                        settings.remove(stringKey)
                    } else {
                        settings[stringKey] = value
                    }
                }
                true
            } catch (e: Exception) {
                log.error("DataStoreBridge: Error saving string value", e)
                false
            }
        }
    }

    fun saveStringSetValue(key: String?, value: Set<String>?): CompletableFuture<Boolean> {
        val stringSetKey: Preferences.Key<Set<String>> =
            defineKey(key ?: "default_string_set_key", PreferenceType.SET);

        return externalScope.future(Dispatchers.IO) {
            try {
                dataStore.edit { settings ->
                    if (value == null) {
                        settings.remove(stringSetKey)
                    } else {
                        settings[stringSetKey] = value
                    }
                }
                true
            } catch (e: Exception) {
                log.error("DataStoreBridge: Error saving stringset value", e)
                false
            }
        }
    }

    fun saveLongValue(key: String?, value: Long?): CompletableFuture<Boolean> {
        val longKey: Preferences.Key<Long> =
            defineKey(key ?: "default_long_key", PreferenceType.LONG)

        return externalScope.future(Dispatchers.IO) {
            try {
                dataStore.edit { settings ->
                    if (value == null) {
                        settings.remove(longKey)
                    } else {
                        settings[longKey] = value
                    }
                }
                true
            } catch (e: Exception) {
                log.error("DataStoreBridge: Error saving long value", e)
                false
            }
        }
    }

    fun saveBooleanValue(key: String?, value: Boolean?): CompletableFuture<Boolean> {
        val booleanKey: Preferences.Key<Boolean> =
            defineKey(key ?: "default_boolean_key", PreferenceType.BOOLEAN)

        return externalScope.future(Dispatchers.IO) {
            try {
                dataStore.edit { settings ->
                    if (value == null) {
                        settings.remove(booleanKey)
                    } else {
                        settings[booleanKey] = value
                    }
                }
                true
            } catch (e: Exception) {
                log.error("DataStoreBridge: Error saving boolean value", e)
                false
            }
        }
    }

    fun deleteStringValue(keyName: String): CompletableFuture<Boolean> {
        return externalScope.future(Dispatchers.IO) {
            try {
                dataStore.edit { settings ->
                    val stringKey = createStringKey(keyName)
                    settings.remove(stringKey)
                }
                true
            } catch (e: Exception) {
                log.error("DataStoreBridge: Error deleting string value", e)
                false
            }
        }
    }

    fun deleteLongValue(keyName: String): CompletableFuture<Boolean> {
        return externalScope.future(Dispatchers.IO) {
            try {
                dataStore.edit { settings ->
                    val longKey = createLongKey(keyName)
                    settings.remove(longKey)
                }
                true
            } catch (e: Exception) {
                log.error("DataStoreBridge: Error deleting long value", e)
                false
            }
        }
    }

    fun deleteBooleanValue(keyName: String): CompletableFuture<Boolean> {
        return externalScope.future(Dispatchers.IO) {
            try {
                dataStore.edit { settings ->
                    val booleanKey = createBooleanKey(keyName)
                    settings.remove(booleanKey)
                }
                true
            } catch (e: Exception) {
                log.error("DataStoreBridge: Error deleting boolean value", e)
                false
            }
        }
    }

    fun clearAllPreferences(): CompletableFuture<Boolean> {
        return externalScope.future(Dispatchers.IO) {
            try {
                dataStore.edit { it.clear() }
                true
            } catch (e: Exception) {
                log.error("DataStoreBridge: Error clearing all preferences values", e)
                false
            }
        }
    }

    fun countPreferences(): CompletableFuture<Int> {
        return externalScope.future(Dispatchers.IO) {
            dataStore.data.map { preferences -> preferences.asMap().size }.firstOrNull() ?: 0
        }
    }

    fun getAllPreferences(): CompletableFuture<Map<Preferences.Key<*>, Any>> {
        return externalScope.future(Dispatchers.IO) {
            dataStore.data.map { preferences -> preferences.asMap() }.firstOrNull() ?: emptyMap()
        }
    }

    // --- Private Error Handling for base Preferences Flow ---
    private fun Flow<Preferences>.handleErrors(flowName: String): Flow<Preferences> {
        return this.catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception // Rethrow other critical exceptions
            }
        }
    }

    // --- Kotlin-idiomatic way to get value once ---
    private suspend fun getStringOnceSuspend(key: String): String? {
        return readStringData(key).firstOrNull()
    }

    private suspend fun getStringSetOnceSuspend(key: String): Set<String>? {
        return readStringSetData(key).firstOrNull()
    }

    private suspend fun getLongOnceSuspend(key: String): Long {
        return readLongData(key).firstOrNull() ?: 0
    }

    private suspend fun getBooleanOnceSuspend(key: String): Boolean {
        return readBooleanData(key).firstOrNull() ?: true
    }

    // --- Bridging suspend functions to CompletableFuture for Java (Optional but good for your SettingsService) ---
    // These will use the `externalScope` passed to DataStoreBridge

    fun getStringOnceAsync(key: String): CompletableFuture<String?> {
        return externalScope.future(Dispatchers.IO) {
            getStringOnceSuspend(key)
        }
    }

    fun getStringSetOnceAsync(key: String): CompletableFuture<Set<String>?> {
        return externalScope.future(Dispatchers.IO) {
            getStringSetOnceSuspend(key)
        }
    }

    fun getLongOnceAsync(key: String): CompletableFuture<Long> {
        return externalScope.future(Dispatchers.IO) {
            getLongOnceSuspend(key)
        }
    }

    fun getBooleanOnceAsync(key: String): CompletableFuture<Boolean> {
        return externalScope.future(Dispatchers.IO) {
            getBooleanOnceSuspend(key)
        }
    }
}