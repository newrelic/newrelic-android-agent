/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.stores;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import androidx.annotation.WorkerThread;
import androidx.datastore.core.DataStore;
import androidx.datastore.preferences.core.Preferences;

import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import kotlinx.coroutines.BuildersKt;
import kotlinx.coroutines.CompletableJob;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.CoroutineScopeKt;
import kotlinx.coroutines.Dispatchers;

@SuppressLint("NewApi")
public class DataStoreHelper {
    protected static final AgentLog log = AgentLogManager.getAgentLog();
    protected static final Charset ENCODING = Charset.forName("ISO-8859-1");

    private final String storeFilename;
    private final DataStore<Preferences> dataStore;

    final DataStoreBridge dataStoreBridge;
    private final CoroutineScope serviceScope; // Managed by this singleton

    public static final int OPERATION_TIMEOUT_SECONDS = 10;

    public DataStoreHelper(Context context, String storeFilename) {
        dataStore = DataStorePreference.getNamedDataStorePreference(context, storeFilename);
        this.storeFilename = storeFilename;

        // Create a long-lived scope for this service.
        CompletableJob supervisor = DataStorePreference.createSupervisorJob();
        this.serviceScope = CoroutineScopeKt.CoroutineScope(Dispatchers.getIO().plus(supervisor));
        this.dataStoreBridge = new DataStoreBridge(context.getApplicationContext(), serviceScope, dataStore);

        // Migrate old SharedPreferences if they exist in a background thread
        BuildersKt.launch(
                serviceScope,
                serviceScope.getCoroutineContext(),
                kotlinx.coroutines.CoroutineStart.DEFAULT,
                (scope, continuation) -> {
                    try {
                        migrateFromSharedPrefs(context, storeFilename);
                        log.debug("DataStoreHelper: Background migration completed for " + storeFilename);
                    } catch (Exception e) {
                        log.error("DataStoreHelper: Background migration failed for "
                                + storeFilename, e);
                    }
                    return kotlin.Unit.INSTANCE;
                }
        );

    }

    /**
     * Migrates existing SharedPreferences data to DataStore asynchronously.
     *
     * <p>This method handles various data types with the following migration strategy:</p>
     * <ul>
     *   <li>String values: migrated directly</li>
     *   <li>Long values: migrated directly</li>
     *   <li>Integer values: converted to Long for DataStore compatibility</li>
     *   <li>Boolean values: migrated directly</li>
     *   <li>SetString values: migrated with type checking</li>
     *   <li>Float values: skipped with warning (deprecated in agent)</li>
     * </ul>
     *
     * <p>Migration is performed in batches for optimal performance and includes proper error handling.
     * Original SharedPreferences are preserved for safety and can be cleared manually later.</p>
     *
     * @param context Application context for accessing SharedPreferences
     * @param filename Name of the SharedPreferences file to migrate from
     */
    private void migrateFromSharedPrefs(Context context, String filename) {
        SharedPreferences oldPrefs = context.getSharedPreferences(filename, Context.MODE_PRIVATE);
        Map<String, ?> allPrefs = oldPrefs.getAll();

        if (allPrefs.isEmpty()) {
            return;
        }
        log.debug("DataStoreHelper: Migrating " + allPrefs.size() + " values from SharedPreferences [" + filename + "] to DataStore.");

        try {
            List<CompletableFuture<Boolean>> migrationTasks = new ArrayList<>();

            for (Map.Entry<String, ?> entry : allPrefs.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                if (value instanceof String) {
                    migrationTasks.add(dataStoreBridge.saveStringValue(key, (String) value));
                } else if (value instanceof Long) {
                    migrationTasks.add(dataStoreBridge.saveLongValue(key, (Long) value));
                } else if (value instanceof Integer) {
                    // SharedPreferences may store longs as integers
                    migrationTasks.add(dataStoreBridge.saveLongValue(key, ((Integer) value).longValue()));
                } else if (value instanceof Boolean) {
                    migrationTasks.add(dataStoreBridge.saveBooleanValue(key, (Boolean) value));
                } else if (value instanceof Float) {
                    log.warn("DataStoreHelper: Skipping float value during migration for key [" + key + "]");
                } else if (value instanceof Set) {
                    try {
                        @SuppressWarnings("unchecked")
                        Set<String> stringSet = (Set<String>) value;
                        migrationTasks.add(dataStoreBridge.saveStringSetValue(key, stringSet));
                    } catch (ClassCastException e) {
                        log.error("DataStoreHelper: Could not migrate Set for key [" + key + "]. It was not a Set<String>.", e);
                    }
                }
            }

            if (!migrationTasks.isEmpty()) {
                CompletableFuture.allOf(migrationTasks.toArray(new CompletableFuture[0])).get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                //Optional now: uncomment when the agent is stable
//                log.debug("DataStoreHelper: Migration complete. Clearing old SharedPreferences file.");
//                oldPrefs.edit().clear().apply();
            }

        } catch (Exception e) {
            log.error("DataStoreHelper: Failed to migrate SharedPreferences to DataStore.", e);
        }
    }

    public String getStoreFilename() {
        return storeFilename;
    }

    public void shutdown() {
        CoroutineScopeKt.cancel(serviceScope, "DataStoreHelper is shutting down.", null);
    }

    public CompletableFuture<Boolean> putStringValue(String key, String value) {
        return dataStoreBridge.saveStringValue(key, value);
    }

    public CompletableFuture<Boolean> putStringSetValue(String key, Set<String> value) {
        return dataStoreBridge.saveStringSetValue(key, value);
    }

    public CompletableFuture<Boolean> putLongValue(String key, Long value) {
        return dataStoreBridge.saveLongValue(key, value);
    }

    public CompletableFuture<Boolean> putBooleanValue(String key, Boolean value) {
        return dataStoreBridge.saveBooleanValue(key, value);
    }

    public CompletableFuture<String> getStringValue(String key) {
        return dataStoreBridge.getStringOnceAsync(key);
    }

    public CompletableFuture<Set<String>> getStringSetValue(String key) {
        return dataStoreBridge.getStringSetOnceAsync(key);
    }

    public CompletableFuture<Long> getLongValue(String key) {
        return dataStoreBridge.getLongOnceAsync(key);
    }

    public CompletableFuture<Boolean> getBooleanValue(String key) {
        return dataStoreBridge.getBooleanOnceAsync(key);
    }

    public boolean store(String uuid, byte[] bytes) {
        try {
            boolean result = putStringValue(uuid, decodeBytesToString(bytes)).get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            return result;
        } catch (Exception e) {
            log.error("DataStoreHelper.store(String, byte[]): ", e);
        }

        return false;
    }

    @WorkerThread
    public boolean store(String uuid, Set<String> stringSet) {
        try {
            boolean result = putStringSetValue(uuid, stringSet).get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return result;
        } catch (Exception e) {
            log.error("DataStoreHelper.store(String, Set<String>): ", e);
        }

        return false;
    }

    @WorkerThread
    public boolean store(String uuid, String string) {
        try {
            boolean result = putStringValue(uuid, string).get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return result;
        } catch (Exception e) {
            log.error("DataStoreHelper.store(String, String): ", e);
        }

        return false;
    }

    @WorkerThread
    public List<?> fetchAll() {
        final ArrayList<Object> objectList = new ArrayList<Object>();

        try {
            Map<Preferences.Key<?>, Object> objectStrings = dataStoreBridge.getAllPreferences().get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            objectList.addAll(objectStrings.values());
        } catch (Exception e) {
            log.error("DataStoreHelper.fetchAll(): ", e);
        }

        return objectList;
    }

    @WorkerThread
    public int count() {
        try {
            return dataStoreBridge.countPreferences().get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("DataStoreHelper.count(): ", e);

        }

        return 0;
    }

    @SuppressLint("CheckResult")
    public void clear() {
        try {
            synchronized (this) {
                dataStoreBridge.clearAllPreferences().get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            log.error("DataStoreHelper.clear(): ", e);
        }
    }

    @WorkerThread
    public void delete(String uuid) {
        try {
            synchronized (this) {
                //No way to identify the value type, so try all
                dataStoreBridge.deleteStringValue(uuid).get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                dataStoreBridge.deleteBooleanValue(uuid).get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                dataStoreBridge.deleteLongValue(uuid).get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            log.error("DataStoreHelper.delete(): ", e);
        }
    }

    protected String encodeBytes(byte[] bytes) {
        try {
            return Base64.encodeToString(bytes, Base64.NO_WRAP);
        } catch (Exception e) {
            log.error("DataStoreHelper.encodeBytes(byte[]): ", e);
        }

        return null;
    }

    protected byte[] decodeStringToBytes(String encodedString) {
        try {
            return Base64.decode(encodedString, Base64.DEFAULT);
        } catch (Exception e) {
            log.error("DataStoreHelper.decodeStringToBytes(String): ", e);
        }

        return null;
    }

    protected String decodeBytesToString(byte[] decodedString) {
        try {
            return new String(decodedString, ENCODING);
        } catch (Exception e) {
            log.error("DataStoreHelper.decodeBytesToString(byte[]): ", e);
        }

        return null;
    }
}
