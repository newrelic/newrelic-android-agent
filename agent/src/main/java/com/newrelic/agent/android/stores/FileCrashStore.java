/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.stores;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.JsonObject;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.crash.Crash;
import com.newrelic.agent.android.crash.CrashStore;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.payload.AbstractFileStore;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.util.SafeJsonPrimitive;

import java.util.List;
import java.util.Map;

/**
 * File-backed crash store. Stores one JSON file per crash under
 * {@code filesDir/nr_crash_cache/}. Body is {@code Crash.asJsonObject()} with an
 * added {@code uploadCount} primitive so {@link Crash#crashFromJsonString(String)}
 * can round-trip.
 */
public class FileCrashStore extends AbstractFileStore<Crash> implements CrashStore {
    public static final String DIR_NAME = "nr_crash_cache";
    public static final String LEGACY_PREFS_NAME = "NRCrashStore";

    public FileCrashStore(Context context, AgentConfiguration config) {
        super(context, DIR_NAME, resolveCap(config),
                MetricNames.SUPPORTABILITY_CRASH_REMOVED_EVICTED,
                MetricNames.SUPPORTABILITY_CRASH_CORRUPTED,
                "FileCrashStore");
    }

    private static int resolveCap(AgentConfiguration config) {
        final int configured = config.getMaxCachedCrashCount();
        return configured > 0 ? configured : AgentConfiguration.DEFAULT_MAX_CACHED_CRASH_COUNT;
    }

    @Override
    public boolean store(Crash crash) {
        if (crash == null) {
            return false;
        }
        final String crashJson = serialize(crash);
        StatsEngine.get().inc(MetricNames.SUPPORTABILITY_CRASH_SIZE_UNCOMPRESSED, crashJson.length());
        return storeInternal(crash);
    }

    @Override
    public List<Crash> fetchAll() {
        return fetchAllInternal();
    }

    @Override
    public int count() {
        return countInternal();
    }

    @Override
    public void delete(Crash crash) {
        if (crash == null || crash.getUuid() == null) {
            return;
        }
        deleteInternal(crash.getUuid().toString());
    }

    @Override
    public void clear() {
        clearInternal();
    }

    @Override
    protected String keyOf(Crash crash) {
        return crash.getUuid().toString();
    }

    @Override
    protected String serialize(Crash crash) {
        final JsonObject obj = crash.asJsonObject();
        obj.add("uploadCount", SafeJsonPrimitive.factory(crash.getUploadCount()));
        return obj.toString();
    }

    @Override
    protected Crash deserialize(String keyFromFile, String json) {
        return Crash.crashFromJsonString(json);
    }

    /**
     * Best-effort one-time migration from the legacy SharedPreferences-backed store.
     * Each value in {@code legacyPrefsName} is parsed via {@link Crash#crashFromJsonString(String)}
     * and re-stored in the file store. Failed entries are skipped (logged + corrupted metric).
     * Safe to call multiple times: file store's {@link #store(Crash)} will overwrite on UUID match.
     */
    public void migrateFromSharedPrefs(Context context, String legacyPrefsName) {
        try {
            final SharedPreferences prefs = context.getSharedPreferences(legacyPrefsName, Context.MODE_PRIVATE);
            final Map<String, ?> all = prefs.getAll();
            if (all == null || all.isEmpty()) {
                return;
            }
            for (Map.Entry<String, ?> entry : all.entrySet()) {
                final Object value = entry.getValue();
                if (!(value instanceof String)) {
                    continue;
                }
                try {
                    final Crash crash = Crash.crashFromJsonString((String) value);
                    storeInternal(crash);
                } catch (Exception e) {
                    log.debug("FileCrashStore.migrateFromSharedPrefs: skipping corrupt entry [" + entry.getKey() + "]: " + e);
                    StatsEngine.get().inc(MetricNames.SUPPORTABILITY_CRASH_CORRUPTED);
                }
            }
        } catch (Exception e) {
            log.error("FileCrashStore.migrateFromSharedPrefs: ", e);
        }
    }
}