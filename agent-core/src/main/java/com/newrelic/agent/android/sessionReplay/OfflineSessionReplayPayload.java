/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay;

import com.google.gson.JsonObject;
import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.util.Constants;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Frozen snapshot of a session replay payload persisted under
 * {@code filesDir/nr_offline_session_replay_cache/} when the network is
 * unavailable or the upload fails with a retriable response. The body is the
 * already-gzipped payload; attributes and the URL {@code timestamp=} value are
 * captured at the time the replay was produced so a later retry sends exactly
 * what would have been sent originally.
 */
public class OfflineSessionReplayPayload {

    private final String uuid;
    private final long capturedAt;
    private final long urlTimestamp;
    private final Map<String, String> attributes;
    private final byte[] body;

    public OfflineSessionReplayPayload(String uuid,
                                       long capturedAt,
                                       long urlTimestamp,
                                       Map<String, String> attributes,
                                       byte[] body) {
        this.uuid = uuid;
        this.capturedAt = capturedAt;
        this.urlTimestamp = urlTimestamp;
        this.attributes = attributes != null ? new LinkedHashMap<>(attributes) : new LinkedHashMap<>();
        this.body = body != null ? body : new byte[0];
    }

    public String getUuid() {
        return uuid;
    }

    public long getCapturedAt() {
        return capturedAt;
    }

    public long getUrlTimestamp() {
        return urlTimestamp;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public byte[] getBody() {
        return body;
    }

    public boolean isStale(long ttlMs) {
        return (System.currentTimeMillis() - capturedAt) > ttlMs;
    }

    public JsonObject asJsonObject() {
        final JsonObject obj = new JsonObject();
        obj.addProperty(Constants.SessionReplay.OFFLINE_KEY_UUID, uuid);
        obj.addProperty(Constants.SessionReplay.OFFLINE_KEY_CAPTURED_AT, capturedAt);
        obj.addProperty(Constants.SessionReplay.OFFLINE_KEY_URL_TIMESTAMP, urlTimestamp);

        final JsonObject attrs = new JsonObject();
        for (Map.Entry<String, String> e : attributes.entrySet()) {
            if (e.getValue() == null) {
                continue;
            }
            attrs.addProperty(e.getKey(), e.getValue());
        }
        obj.add(Constants.SessionReplay.OFFLINE_KEY_ATTRIBUTES, attrs);

            obj.addProperty(Constants.SessionReplay.OFFLINE_KEY_BODY,
                    Agent.getEncoder().encode(body));

        return obj;
    }

    public static OfflineSessionReplayPayload fromJsonObject(JsonObject obj) {
        if (obj == null) {
            throw new IllegalArgumentException("null JsonObject");
        }
        final String uuid = obj.has(Constants.SessionReplay.OFFLINE_KEY_UUID)
                ? obj.get(Constants.SessionReplay.OFFLINE_KEY_UUID).getAsString() : null;
        final long capturedAt = obj.has(Constants.SessionReplay.OFFLINE_KEY_CAPTURED_AT)
                ? obj.get(Constants.SessionReplay.OFFLINE_KEY_CAPTURED_AT).getAsLong() : 0L;
        final long urlTimestamp = obj.has(Constants.SessionReplay.OFFLINE_KEY_URL_TIMESTAMP)
                ? obj.get(Constants.SessionReplay.OFFLINE_KEY_URL_TIMESTAMP).getAsLong() : 0L;

        final Map<String, String> attributes = new LinkedHashMap<>();
        if (obj.has(Constants.SessionReplay.OFFLINE_KEY_ATTRIBUTES)) {
            final JsonObject attrs = obj.getAsJsonObject(Constants.SessionReplay.OFFLINE_KEY_ATTRIBUTES);
            for (Map.Entry<String, com.google.gson.JsonElement> e : attrs.entrySet()) {
                if (e.getValue() == null || e.getValue().isJsonNull()) {
                    continue;
                }
                attributes.put(e.getKey(), e.getValue().getAsString());
            }
        }

        byte[] body = new byte[0];
        if (obj.has(Constants.SessionReplay.OFFLINE_KEY_BODY)) {
            final String b64 = obj.get(Constants.SessionReplay.OFFLINE_KEY_BODY).getAsString();
                body = Agent.getDecoder().decode(b64);

        }

        return new OfflineSessionReplayPayload(uuid, capturedAt, urlTimestamp, attributes, body);
    }
}
