/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.stores;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.datastore.core.DataStore;
import androidx.datastore.preferences.core.Preferences;

import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import kotlinx.coroutines.CompletableJob;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.CoroutineScopeKt;
import kotlinx.coroutines.Dispatchers;

@SuppressLint("NewApi")
public class DataStoreHelpler {
    protected static final AgentLog log = AgentLogManager.getAgentLog();
    protected static final Charset ENCODING = Charset.forName("ISO-8859-1");

    private final String storeFilename;
    private final DataStore<Preferences> dataStore;

    final DataStoreBridge dataStoreBridge;
    private final CoroutineScope serviceScope; // Managed by this singleton
    Preferences pref_error = new Preferences() {
        @Nullable
        @Override
        public <T> T get(@NonNull Key<T> key) {
            return null;
        }

        @Override
        public <T> boolean contains(@NonNull Key<T> key) {
            return false;
        }

        @NonNull
        @Override
        public Map<Key<?>, Object> asMap() {
            return Collections.emptyMap();
        }

        @Override
        public String toString() {
            return "Pref_error";
        }
    };

    public DataStoreHelpler(Context context, String storeFilename) {
        DataStoreSingleton dataStoreSingleton = DataStoreSingleton.getInstance();
        if (dataStoreSingleton.getDataStore() == null) {
            dataStore = DataStorePreference.getNamedDataStorePreference(context, storeFilename);
        } else {
            dataStore = dataStoreSingleton.getDataStore();
        }
        dataStoreSingleton.setDataStore(dataStore);

        this.storeFilename = storeFilename;

        // Create a long-lived scope for this service.
        CompletableJob supervisor = DataStorePreference.createSupervisorJob();
        this.serviceScope = CoroutineScopeKt.CoroutineScope(Dispatchers.getIO().plus(supervisor));
        this.dataStoreBridge = new DataStoreBridge(context.getApplicationContext(), serviceScope);
    }

    public String getStoreFilename() {
        return storeFilename;
    }

    public void shutdown() {
        CoroutineScopeKt.cancel(serviceScope, "DataStoreHelper is shutting down.", null);
    }

    public CompletableFuture<Void> putStringValue(String key, String value) {
        return dataStoreBridge.saveStringValue(key, value);
    }

    public CompletableFuture<Void> putLongValue(String key, Long value) {
        return dataStoreBridge.saveLongValue(key, value);
    }

    public CompletableFuture<Void> putBooleanValue(String key, Boolean value) {
        return dataStoreBridge.saveBooleanValue(key, value);
    }

    public boolean store(String uuid, byte[] bytes) {
        try {
            putStringValue(uuid, decodeBytesToString(bytes));
            return true;
        } catch (Exception e) {
            log.error("DataStoreHelper.store(String, byte[]): ", e);
        }

        return false;
    }

    public boolean store(String uuid, Set<String> stringSet) {
        try {
            //TODO: THIS FUNCTION
//            return putStringSetValue(uuid, stringSet);
        } catch (Exception e) {
            log.error("DataStoreHelper.store(String, Set<String>): ", e);
        }

        return false;
    }

    public boolean store(String uuid, String string) {
        try {
            dataStoreBridge.saveStringValue(uuid, string);
            return true;
        } catch (Exception e) {
            log.error("DataStoreHelper.store(String, String): ", e);
        }

        return false;
    }

    public List<?> fetchAll() {
        final ArrayList<Object> objectList = new ArrayList<Object>();

        try {
            synchronized (this) {
                Map<Preferences.Key<?>, Object> objectStrings = dataStoreBridge.getAllPreferences().get();
                objectList.addAll(objectStrings.values());
            }
        } catch (Exception e) {
            log.error("DataStoreHelper.fetchAll(): ", e);
        }

        return objectList;
    }

    public int count() {
        try {
            synchronized (dataStore) {
                return dataStoreBridge.countPreferences().get();
            }
        } catch (Exception e) {
            log.error("DataStoreHelper.count(): ", e);

        }

        return 0;
    }

    @SuppressLint("CheckResult")
    public void clear() {
        try {
            synchronized (this) {
                dataStoreBridge.clearAllPreferences();
            }
        } catch (Exception e) {
            log.error("DataStoreHelper.clear(): ", e);
        }
    }

    public void delete(String uuid) {
        try {
            synchronized (this) {
                //TODO: VALUE TYPE
                dataStoreBridge.deleteStringValue(uuid);
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
