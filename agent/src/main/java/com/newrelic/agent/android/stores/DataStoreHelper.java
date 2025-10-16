/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.stores;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Base64;

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

    public DataStoreHelper(Context context, String storeFilename) {
        dataStore = DataStorePreference.getNamedDataStorePreference(context, storeFilename);
        this.storeFilename = storeFilename;

        // Create a long-lived scope for this service.
        CompletableJob supervisor = DataStorePreference.createSupervisorJob();
        this.serviceScope = CoroutineScopeKt.CoroutineScope(Dispatchers.getIO().plus(supervisor));
        this.dataStoreBridge = new DataStoreBridge(context.getApplicationContext(), serviceScope, dataStore);
    }

    public String getStoreFilename() {
        return storeFilename;
    }

    public void shutdown() {
        CoroutineScopeKt.cancel(serviceScope, "DataStoreHelper is shutting down.", null);
    }

    public CompletableFuture<Boolean> putStringValueAsync(String uuid, String bytes) {
        return putStringValue(uuid, bytes);
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
            boolean result = putStringValueAsync(uuid, decodeBytesToString(bytes)).get(5, TimeUnit.SECONDS);

            return result;
        } catch (Exception e) {
            log.error("DataStoreHelper.store(String, byte[]): ", e);
        }

        return false;
    }

    public boolean store(String uuid, Set<String> stringSet) {
        try {
            boolean result = putStringSetValue(uuid, stringSet).get();
            return result;
        } catch (Exception e) {
            log.error("DataStoreHelper.store(String, Set<String>): ", e);
        }

        return false;
    }

    public boolean store(String uuid, String string) {
        try {
            boolean result = putStringValue(uuid, string).get();
            return result;
        } catch (Exception e) {
            log.error("DataStoreHelper.store(String, String): ", e);
        }

        return false;
    }

    public List<?> fetchAll() {
        final ArrayList<Object> objectList = new ArrayList<Object>();

        try {
            Map<Preferences.Key<?>, Object> objectStrings = dataStoreBridge.getAllPreferences().get();
            objectList.addAll(objectStrings.values());
        } catch (Exception e) {
            log.error("DataStoreHelper.fetchAll(): ", e);
        }

        return objectList;
    }

    public int count() {
        try {
            return dataStoreBridge.countPreferences().get();
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
                //No way to identify the value type, so try all
                dataStoreBridge.deleteStringValue(uuid).get();
                dataStoreBridge.deleteBooleanValue(uuid).get();
                dataStoreBridge.deleteLongValue(uuid).get();
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
