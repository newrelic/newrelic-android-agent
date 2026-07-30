/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.hybrid;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.payload.AbstractFileStore;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * File-backed JS-error store. Stores one JSON file per JS error under
 * {@code filesDir/nr_jserror_cache/}.
 *
 * On-disk format: JSON object with {@code id} (original caller-supplied ID) and
 * {@code data} (the JSON payload string — stored verbatim). Filenames are the
 * sanitized/hashed form of the caller's ID; the original ID always lives inside the
 * file so {@code fetchAllEntries()} can recover it regardless of sanitization.
 */
public class FileJSErrorStore extends AbstractFileStore<Map.Entry<String, String>> implements JSErrorStore {
    public static final String DIR_NAME = "nr_jserror_cache";

    public FileJSErrorStore(Context context, AgentConfiguration config) {
        super(context, DIR_NAME, resolveCap(config),
                MetricNames.SUPPORTABILITY_JS_ERROR_EVICTED,
                MetricNames.SUPPORTABILITY_JS_ERROR_CORRUPTED,
                "FileJSErrorStore");
    }

    private static int resolveCap(AgentConfiguration config) {
        final int configured = config.getMaxCachedJsErrorCount();
        return configured > 0 ? configured : AgentConfiguration.DEFAULT_MAX_CACHED_JS_ERROR_COUNT;
    }

    @Override
    public boolean store(String id, String data) {
        if (id == null || id.isEmpty()) {
            log.warn("FileJSErrorStore.store: id is null or empty");
            return false;
        }
        if (data == null || data.trim().isEmpty()) {
            log.warn("FileJSErrorStore.store: data is null or empty");
            return false;
        }
        return storeInternal(new AbstractMap.SimpleImmutableEntry<>(id, data));
    }

    @Override
    public List<String> fetchAll() {
        final List<String> out = new ArrayList<>();
        for (Map.Entry<String, String> e : fetchAllInternal()) {
            out.add(e.getValue());
        }
        return out;
    }

    /**
     * Returns all stored errors as a single atomic {id → json} snapshot. The
     * in-file {@code id} is authoritative (filenames may have been hashed for
     * safety), so values and keys are guaranteed consistent with each other.
     */
    @Override
    public Map<String, String> fetchAllEntries() {
        final Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : fetchAllInternal()) {
            result.put(e.getKey(), e.getValue());
        }
        return result;
    }

    @Override
    public int count() {
        return countInternal();
    }

    @Override
    public void delete(String id) {
        deleteInternal(id);
    }

    @Override
    public void clear() {
        clearInternal();
    }

    @Override
    protected String keyOf(Map.Entry<String, String> entry) {
        return entry.getKey();
    }

    @Override
    protected String serialize(Map.Entry<String, String> entry) {
        final JsonObject obj = new JsonObject();
        obj.addProperty("id", entry.getKey());
        obj.addProperty("data", entry.getValue());
        return obj.toString();
    }

    @Override
    protected Map.Entry<String, String> deserialize(String keyFromFile, String json) throws IOException {
        final JsonObject outer = new Gson().fromJson(json, JsonObject.class);
        if (outer == null || !outer.has("id") || !outer.has("data")) {
            throw new IOException("missing id/data fields");
        }
        final String id = outer.get("id").getAsString();
        final String data = outer.get("data").getAsString();
        return new AbstractMap.SimpleImmutableEntry<>(id, data);
    }
}