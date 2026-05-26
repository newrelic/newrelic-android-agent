/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.stores;

import android.content.Context;
import com.newrelic.agent.android.hybrid.JSErrorStore;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SharedPrefsJSErrorStore extends SharedPrefsStore implements JSErrorStore {
    public static final String STORE_FILE = "NRJSErrorStore";
    private static final AgentLog log = AgentLogManager.getAgentLog();

    public SharedPrefsJSErrorStore(Context context) {
        this(context, STORE_FILE);
    }

    public SharedPrefsJSErrorStore(Context context, String storeFilename) {
        super(context, storeFilename);
    }

    @Override
    public boolean store(String id, String data) {
        if (data == null || data.trim().isEmpty()) {
            log.warn("SharedPrefsJSErrorStore: Cannot store null or empty data");
            return false;
        }
        return super.store(id, data);
    }

    @Override
    public List<String> fetchAll() {
        synchronized (this) {
            try {
                Map<String, ?> all = sharedPrefs.getAll();
                List<String> jsErrors = new ArrayList<>(all.size());
                for (Object obj : all.values()) {
                    if (obj instanceof String) {
                        jsErrors.add((String) obj);
                    }
                }
                log.debug("SharedPrefsJSErrorStore: Retrieved " + jsErrors.size() + " JS errors");
                return jsErrors;
            } catch (Exception e) {
                log.error("SharedPrefsJSErrorStore.fetchAll(): ", e);
                return new ArrayList<>();
            }
        }
    }

    /**
     * Returns all stored errors as a single atomic {id → json} snapshot.
     * Both the values and their keys are read in one {@code sharedPrefs.getAll()} call,
     * ensuring the two collections are consistent with each other.
     */
    @Override
    public Map<String, String> fetchAllEntries() {
        synchronized (this) {
            try {
                Map<String, ?> all = sharedPrefs.getAll();
                Map<String, String> entries = new HashMap<>(all.size());
                for (Map.Entry<String, ?> entry : all.entrySet()) {
                    if (entry.getValue() instanceof String) {
                        entries.put(entry.getKey(), (String) entry.getValue());
                    }
                }
                log.debug("SharedPrefsJSErrorStore: Retrieved " + entries.size() + " JS error entries");
                return entries;
            } catch (Exception e) {
                log.error("SharedPrefsJSErrorStore.fetchAllEntries(): ", e);
                return new HashMap<>();
            }
        }
    }

    @Override
    public void delete(String id) {
        synchronized (this) {
            try {
                super.delete(id);
                log.debug("SharedPrefsJSErrorStore: Deleted error with ID: " + id);
            } catch (Exception e) {
                log.error("SharedPrefsJSErrorStore.delete(): ", e);
            }
        }
    }

    @Override
    public int count() {
        synchronized (this) {
            try {
                return super.count();
            } catch (Exception e) {
                log.error("SharedPrefsJSErrorStore.count(): ", e);
                return 0;
            }
        }
    }

    @Override
    public void clear() {
        synchronized (this) {
            try {
                super.clear();
                log.info("SharedPrefsJSErrorStore: All JS errors cleared");
            } catch (Exception e) {
                log.error("SharedPrefsJSErrorStore.clear(): ", e);
            }
        }
    }
}
