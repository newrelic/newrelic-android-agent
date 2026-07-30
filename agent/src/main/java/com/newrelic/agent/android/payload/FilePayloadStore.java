/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.payload;

import android.content.Context;
import android.util.Base64;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.metric.MetricNames;

import java.io.IOException;
import java.util.List;

/**
 * File-backed payload store. Stores one JSON file per payload under
 * {@code filesDir/nr_payload_cache/} so per-operation heap cost is a single payload
 * rather than the whole cache.
 *
 * On-disk format: JSON object with {@code payload} (nested metadata object holding
 * {@code uuid} and {@code timestamp}) and {@code encodedPayload} (Base64 bytes).
 */
public class FilePayloadStore extends AbstractFileStore<Payload> implements PayloadStore<Payload> {
    static final String DIR_NAME = "nr_payload_cache";

    public FilePayloadStore(Context context, AgentConfiguration config) {
        super(context, DIR_NAME, resolveCap(config),
                MetricNames.SUPPORTABILITY_PAYLOAD_EVICTED,
                MetricNames.SUPPORTABILITY_PAYLOAD_CORRUPTED,
                "FilePayloadStore");
    }

    private static int resolveCap(AgentConfiguration config) {
        final int configured = config.getMaxCachedPayloadCount();
        return configured > 0 ? configured : AgentConfiguration.DEFAULT_MAX_CACHED_PAYLOAD_COUNT;
    }

    @Override
    public boolean store(Payload payload) {
        return storeInternal(payload);
    }

    @Override
    public List<Payload> fetchAll() {
        return fetchAllInternal();
    }

    @Override
    public int count() {
        return countInternal();
    }

    @Override
    public void delete(Payload payload) {
        if (payload == null) {
            return;
        }
        deleteInternal(payload.getUuid());
    }

    @Override
    public void clear() {
        clearInternal();
    }

    @Override
    protected String keyOf(Payload payload) {
        return payload.getUuid();
    }

    @Override
    protected String serialize(Payload payload) {
        final JsonObject obj = new JsonObject();
        obj.add("payload", payload.asJsonObject());
        obj.addProperty("encodedPayload", encodeBytes(payload.getBytes()));
        return obj.toString();
    }

    @Override
    protected Payload deserialize(String keyFromFile, String json) throws IOException {
        final JsonObject outer = new Gson().fromJson(json, JsonObject.class);
        if (outer == null || !outer.has("payload") || !outer.has("encodedPayload")) {
            throw new IOException("missing payload fields");
        }
        final JsonObject meta = outer.getAsJsonObject("payload");
        if (meta == null || !meta.has("uuid") || !meta.has("timestamp")) {
            throw new IOException("invalid payload metadata");
        }
        final Payload payload = new Payload();
        payload.uuid = meta.get("uuid").getAsString();
        payload.timestamp = meta.get("timestamp").getAsLong();
        payload.putBytes(decodeStringToBytes(outer.get("encodedPayload").getAsString()));
        return payload;
    }

    private static String encodeBytes(byte[] bytes) {
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }

    private static byte[] decodeStringToBytes(String encoded) {
        return Base64.decode(encoded, Base64.DEFAULT);
    }
}