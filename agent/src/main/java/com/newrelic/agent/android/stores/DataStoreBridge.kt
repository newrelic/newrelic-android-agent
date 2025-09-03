package com.newrelic.agent.android.stores

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.newrelic.agent.android.stores.DataStoreKeys.defineKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.future.future
import kotlinx.coroutines.flow.firstOrNull
import java.io.IOException
import java.util.concurrent.CompletableFuture

/**
 * Kotlin bridge for DataStore, providing Java-friendly APIs.
 * Requires a CoroutineScope managed by the Java caller.
 */
class DataStoreBridge( // Public class
    context: Context,
    private val externalScope: CoroutineScope // Scope provided by Java
) {
    private val dataStore = context.applicationContext.dataStorePreference
    private val TAG = "DataStoreBridge"

    // Create keys
    private fun createStringKey(keyName: String?): Preferences.Key<String> {
        return defineKey(keyName?: "default_string_key", PreferenceType.STRING)
    }

    private fun createLongKey(keyName: String?): Preferences.Key<Long> {
        return defineKey(keyName?: "default_long_key", PreferenceType.LONG)
    }

    private fun createBooleanKey(keyName: String?): Preferences.Key<Boolean> {
        return defineKey(keyName?: "default_boolean_key", PreferenceType.BOOLEAN)
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

    private fun readLongData(keyName: String?): Flow<Long?> {
        val longKey = createLongKey(keyName)
        return dataStore.data.map { preferences -> preferences[longKey] }
    }

    private fun readBooleanData(keyName: String?): Flow<Boolean?> {
        val booleanKey = createBooleanKey(keyName)
        return dataStore.data.map { preferences -> preferences[booleanKey] }
    }

    // --- Write Data (CompletableFuture for Java) ---
    fun saveStringValue(key: String?, value: String?): CompletableFuture<Void> {
        val stringKey: Preferences.Key<String> = defineKey(key?: "default_string_key", PreferenceType.STRING);

        return externalScope.future(Dispatchers.IO) {
            dataStore.edit { settings ->
                if (value == null) {
                    settings.remove(stringKey)
                } else {
                    settings[stringKey] = value
                }
            }
            null as Void// For CompletableFuture<Void>
        }
    }

    fun saveLongValue(key: String?, value: Long?): CompletableFuture<Void> {
        val longKey: Preferences.Key<Long> = defineKey(key?: "default_long_key", PreferenceType.LONG)

        return externalScope.future(Dispatchers.IO) {
            dataStore.edit { settings ->
                if (value == null) {
                    settings.remove(longKey)
                } else {
                    settings[longKey] = value
                }
            }
            null as Void // Necessary for CompletableFuture<Void>
        }
    }

    fun saveBooleanValue(key: String?, value: Boolean?): CompletableFuture<Void> {
        val booleanKey: Preferences.Key<Boolean> = defineKey(key?: "default_boolean_key", PreferenceType.BOOLEAN)

        return externalScope.future(Dispatchers.IO) {
            dataStore.edit { settings ->
                if (value == null) {
                    settings.remove(booleanKey)
                } else {
                    settings[booleanKey] = value
                }
            }
            null as Void // Necessary for CompletableFuture<Void>
        }
    }

    fun deleteStringValue(keyName: String): CompletableFuture<Void> { // Example generic clear
        return externalScope.future(Dispatchers.IO) {
            dataStore.edit { settings ->
                val stringKey = createStringKey(keyName)
                settings.remove(stringKey)
            }
            null as Void
        }
    }

    fun deleteLongValue(keyName: String): CompletableFuture<Void> { // Example generic clear
        return externalScope.future(Dispatchers.IO) {
            dataStore.edit { settings ->
                val longKey = createLongKey(keyName)
                settings.remove(longKey)
            }
            null as Void
        }
    }

    fun deleteBooleanValue(keyName: String): CompletableFuture<Void> { // Example generic clear
        return externalScope.future(Dispatchers.IO) {
            dataStore.edit { settings ->
                val booleanKey = createBooleanKey(keyName)
                settings.remove(booleanKey)
            }
            null as Void
        }
    }

    fun clearAllPreferences(): CompletableFuture<Void> {
        return externalScope.future(Dispatchers.IO) {
            dataStore.edit { it.clear() }
            null as Void
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
                Log.e(TAG, "IOException in $flowName. Emitting empty preferences.", exception)
                emit(emptyPreferences())
            } else {
                Log.e(TAG, "Unexpected error in $flowName.", exception)
                throw exception // Rethrow other critical exceptions
            }
        }
    }

    /**
     * Suspends until the first user token value is available from the Flow.
     * To be called from a coroutine or another suspend function.
     */
    private suspend fun getStringOnceSuspend(key: String): String? {
        return readStringData(key).firstOrNull()
    }

    /**
     * Kotlin-idiomatic way to get items count once.
     */
    private suspend fun getLongOnceSuspend(key: String): Long {
        // itemsCountFlow provides a default, so it should not be null
        return readLongData(key).firstOrNull() ?: 0
    }

    /**
     * Kotlin-idiomatic way to check if it's the first launch.
     */
    private suspend fun getBooleanOnceSuspend(key: String): Boolean {
        // isFirstLaunchFlow provides a default, so it should not be null
        return readBooleanData(key).firstOrNull() ?: true
    }

    // --- Bridging suspend functions to CompletableFuture for Java (Optional but good for your SettingsService) ---
    // These will use the `externalScope` passed to DataStoreBridge

    fun getStringOnceAsync(key: String): CompletableFuture<String?> { // Note: Return type is CompletableFuture<String?>
        return externalScope.future(Dispatchers.IO) { // You can choose the dispatcher; IO is good for DataStore
            getStringOnceSuspend(key) // Call the suspend function
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