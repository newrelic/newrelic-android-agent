@file:JvmName("DataStorePreference")

package com.newrelic.agent.android.stores

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.SupervisorJob

// The name for your DataStore file
private const val USER_PREFERENCES_FILE_NAME = "default_preference_file";

// This creates the DataStore instance as a singleton, internal to this module
internal val Context.dataStorePreference: DataStore<Preferences> by preferencesDataStore(
    name = USER_PREFERENCES_FILE_NAME
)

fun Context.getNamedDataStorePreference(dataStoreName: String): DataStore<Preferences> {
    return preferencesDataStore(name = dataStoreName).getValue(this, ::dataStorePreference)
}

// wrapper function for SupervisorJob for the JAVA side, as java side wouldn't compile
fun createSupervisorJob(): CompletableJob {
    return SupervisorJob(null)
}
