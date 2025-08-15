/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.stores;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.datastore.preferences.core.MutablePreferences;
import androidx.datastore.preferences.core.Preferences;
import androidx.datastore.preferences.core.PreferencesKeys;
import androidx.datastore.preferences.rxjava2.RxPreferenceDataStoreBuilder;
import androidx.datastore.rxjava2.RxDataStore;

import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.reactivex.Single;

@SuppressLint("NewApi")
public class DataStoreHelpler {
    protected static final AgentLog log = AgentLogManager.getAgentLog();
    protected static final Charset ENCODING = Charset.forName("ISO-8859-1");

    protected final String storeFilename;
    protected final RxDataStore<Preferences> dataStoreRX;
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
            dataStoreRX = new RxPreferenceDataStoreBuilder(context, storeFilename).build();
        } else {
            dataStoreRX = dataStoreSingleton.getDataStore();
        }
        dataStoreSingleton.setDataStore(dataStoreRX);

        this.storeFilename = storeFilename;
    }

    public String getStoreFilename() {
        return storeFilename;
    }

    public boolean putStringValue(String Key, String value) {
        boolean returnValue;
        Preferences.Key<String> PREF_KEY = PreferencesKeys.stringKey(Key);
        Single<Preferences> updateResult = dataStoreRX.updateDataAsync(prefsIn -> {
            MutablePreferences mutablePreferences = prefsIn.toMutablePreferences();
            mutablePreferences.set(PREF_KEY, value);
            return Single.just(mutablePreferences);
        }).onErrorReturnItem(pref_error);
        returnValue = updateResult.blockingGet() != pref_error;
        return returnValue;
    }

    public boolean putLongValue(String Key, Long value) {
        boolean returnValue;
        Preferences.Key<Long> PREF_KEY = PreferencesKeys.longKey(Key);
        Single<Preferences> updateResult = dataStoreRX.updateDataAsync(prefsIn -> {
            MutablePreferences mutablePreferences = prefsIn.toMutablePreferences();
            mutablePreferences.set(PREF_KEY, value);
            return Single.just(mutablePreferences);
        }).onErrorReturnItem(pref_error);
        returnValue = updateResult.blockingGet() != pref_error;
        return returnValue;
    }

    public boolean putBooleanValue(String Key, Boolean value) {
        boolean returnValue;
        Preferences.Key<Boolean> PREF_KEY = PreferencesKeys.booleanKey(Key);
        Single<Preferences> updateResult = dataStoreRX.updateDataAsync(prefsIn -> {
            MutablePreferences mutablePreferences = prefsIn.toMutablePreferences();
            mutablePreferences.set(PREF_KEY, value);
            return Single.just(mutablePreferences);
        }).onErrorReturnItem(pref_error);
        returnValue = updateResult.blockingGet() != pref_error;
        return returnValue;
    }

    public boolean putStringSetValue(String Key, Set<String> value) {
        boolean returnValue;
        Preferences.Key<Set<String>> PREF_KEY = PreferencesKeys.stringSetKey(Key);
        Single<Preferences> updateResult = dataStoreRX.updateDataAsync(prefsIn -> {
            MutablePreferences mutablePreferences = prefsIn.toMutablePreferences();
            mutablePreferences.set(PREF_KEY, value);
            return Single.just(mutablePreferences);
        }).onErrorReturnItem(pref_error);
        returnValue = updateResult.blockingGet() != pref_error;
        return returnValue;
    }

    public String getStringValue(String Key) {
        Preferences.Key<String> PREF_KEY = PreferencesKeys.stringKey(Key);
        Single<String> value = dataStoreRX.data().firstOrError().map(prefs -> prefs.get(PREF_KEY)).onErrorReturnItem("null");
        return value.blockingGet();
    }

    public Set<String> getStringSetValue(String Key) {
        Preferences.Key<Set<String>> PREF_KEY = PreferencesKeys.stringSetKey(Key);
        Single<Set<String>> value = dataStoreRX.data().firstOrError().map(prefs -> prefs.get(PREF_KEY)).onErrorReturnItem(Collections.emptySet());
        return value.blockingGet();
    }

    public long getLongSetValue(String Key) {
        Preferences.Key<Long> PREF_KEY = PreferencesKeys.longKey(Key);
        Single<Long> value = dataStoreRX.data().firstOrError().map(prefs -> prefs.get(PREF_KEY)).onErrorReturnItem(Long.valueOf(-1));
        return value.blockingGet();
    }

    public boolean getBooleanSetValue(String Key) {
        Preferences.Key<Boolean> PREF_KEY = PreferencesKeys.booleanKey(Key);
        Single<Boolean> value = dataStoreRX.data().firstOrError().map(prefs -> prefs.get(PREF_KEY)).onErrorReturnItem(false);
        return value.blockingGet();
    }

    public boolean store(String uuid, byte[] bytes) {
        try {
            return putStringValue(uuid, decodeBytesToString(bytes));
        } catch (Exception e) {
            log.error("DataStoreHelper.store(String, byte[]): ", e);
        }

        return false;
    }

    public boolean store(String uuid, Set<String> stringSet) {
        try {
            return putStringSetValue(uuid, stringSet);
        } catch (Exception e) {
            log.error("DataStoreHelper.store(String, Set<String>): ", e);
        }

        return false;
    }

    public boolean store(String uuid, String string) {
        try {
            return putStringValue(uuid, string);
        } catch (Exception e) {
            log.error("DataStoreHelper.store(String, String): ", e);
        }

        return false;
    }

    public List<?> fetchAll() {
        final ArrayList<Object> objectList = new ArrayList<Object>();

        try {
            synchronized (this) {
                Map<Preferences.Key<?>, Object> objectStrings = dataStoreRX.data().firstOrError().blockingGet().asMap();
                objectList.addAll(objectStrings.values());
            }
        } catch (Exception e) {
            log.error("DataStoreHelper.fetchAll(): ", e);
        }

        return objectList;
    }

    public int count() {
        try {
            synchronized (dataStoreRX) {
                Map<Preferences.Key<?>, Object> objectStrings = dataStoreRX.data().firstOrError().blockingGet().asMap();
                return objectStrings.size();
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
                dataStoreRX.updateDataAsync(preferences -> {
                    MutablePreferences mutablePreferences = preferences.toMutablePreferences();
                    mutablePreferences.clear();
                    return Single.just(mutablePreferences);
                }).blockingGet();
            }
        } catch (Exception e) {
            log.error("DataStoreHelper.clear(): ", e);
        }
    }

    public void delete(String uuid) {
        try {
            synchronized (this) {
                dataStoreRX.updateDataAsync(preferences -> {
                    Map<Preferences.Key<?>, Object> test1 = dataStoreRX.data().firstOrError().blockingGet().asMap();
                    MutablePreferences mutablePreferences = preferences.toMutablePreferences();
                    Preferences.Key<String> key = PreferencesKeys.stringKey(uuid); // Or other type, e.g., intKey, booleanKey
                    mutablePreferences.remove(key);
                    Map<Preferences.Key<?>, Object> test2 = dataStoreRX.data().firstOrError().blockingGet().asMap();
                    return Single.just(mutablePreferences);
                }).blockingGet();
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
