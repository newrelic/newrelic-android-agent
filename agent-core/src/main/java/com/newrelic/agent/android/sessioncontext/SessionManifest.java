/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.newrelic.agent.android.sessioncontext;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.newrelic.agent.android.analytics.AnalyticsAttribute;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * A frozen snapshot of one session's context — its id, agent id, start time, and the
 * session attributes that were live at snapshot time. Persisted (one file per session,
 * keyed by {@link #getSessionId()}) so a cross-process consumer (e.g. an AEI emitted on
 * the next launch) can attach the attributes that were active when the session was alive
 * rather than the post-restart session's attributes.
 *
 * <p>Attributes are serialized as a JSON object of {@code name -> primitive} using
 * {@link AnalyticsAttribute#asJsonElement()} and reconstructed with
 * {@link AnalyticsAttribute#newFromJson(JsonObject)}. This round-trip is faithful because
 * {@code AnalyticsAttribute} has only STRING/DOUBLE/BOOLEAN types (no int — all numerics
 * are stored as double), so no custom typed record is needed.
 */
public class SessionManifest {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    static final String KEY_SCHEMA_VERSION = "schemaVersion";
    static final String KEY_SESSION_ID = "sessionId";
    static final String KEY_REAL_AGENT_ID = "realAgentId";
    static final String KEY_SESSION_START_MS = "sessionStartMs";
    static final String KEY_LAST_UPDATE_MS = "lastUpdateMs";
    static final String KEY_ATTRIBUTES = "attributes";
    static final String KEY_REACHED_FULL_MODE = "reachedFullMode";
    static final String KEY_IS_FIRST_CHUNK = "isFirstChunk";
    static final String KEY_EXIT_REASON = "exitReason";

    private final int schemaVersion;
    private final String sessionId;
    private final int realAgentId;
    private final long sessionStartMs;
    private final long lastUpdateMs;
    private final Set<AnalyticsAttribute> attributes;
    // Internal, never-sent fields. null = "unknown / not set by any writer".
    private final Boolean reachedFullMode;
    private final Boolean isFirstChunk;
    private final Integer exitReason;

    public SessionManifest(String sessionId, int realAgentId, long sessionStartMs,
                           long lastUpdateMs, Set<AnalyticsAttribute> attributes) {
        this(CURRENT_SCHEMA_VERSION, sessionId, realAgentId, sessionStartMs, lastUpdateMs,
                attributes, null, null, null);
    }

    SessionManifest(int schemaVersion, String sessionId, int realAgentId, long sessionStartMs,
                    long lastUpdateMs, Set<AnalyticsAttribute> attributes) {
        this(schemaVersion, sessionId, realAgentId, sessionStartMs, lastUpdateMs,
                attributes, null, null, null);
    }

    public SessionManifest(int schemaVersion, String sessionId, int realAgentId, long sessionStartMs,
                           long lastUpdateMs, Set<AnalyticsAttribute> attributes,
                           Boolean reachedFullMode, Boolean isFirstChunk, Integer exitReason) {
        this.schemaVersion = schemaVersion;
        this.sessionId = sessionId == null ? "" : sessionId;
        this.realAgentId = realAgentId;
        this.sessionStartMs = sessionStartMs;
        this.lastUpdateMs = lastUpdateMs;
        this.attributes = attributes == null ? new HashSet<>() : new HashSet<>(attributes);
        this.reachedFullMode = reachedFullMode;
        this.isFirstChunk = isFirstChunk;
        this.exitReason = exitReason;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public String getSessionId() {
        return sessionId;
    }

    public int getRealAgentId() {
        return realAgentId;
    }

    public long getSessionStartMs() {
        return sessionStartMs;
    }

    public long getLastUpdateMs() {
        return lastUpdateMs;
    }

    public Set<AnalyticsAttribute> getAttributes() {
        return Collections.unmodifiableSet(attributes);
    }

    public Boolean getReachedFullMode() {
        return reachedFullMode;
    }

    public Boolean getIsFirstChunk() {
        return isFirstChunk;
    }

    public Integer getExitReason() {
        return exitReason;
    }

    public boolean isValid() {
        return sessionId != null && !sessionId.isEmpty();
    }

    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty(KEY_SCHEMA_VERSION, schemaVersion);
        root.addProperty(KEY_SESSION_ID, sessionId);
        root.addProperty(KEY_REAL_AGENT_ID, realAgentId);
        root.addProperty(KEY_SESSION_START_MS, sessionStartMs);
        root.addProperty(KEY_LAST_UPDATE_MS, lastUpdateMs);

        JsonObject attrs = new JsonObject();
        for (AnalyticsAttribute attribute : attributes) {
            JsonElement element = attribute.asJsonElement();
            if (element != null) {
                attrs.add(attribute.getName(), element);
            }
        }
        root.add(KEY_ATTRIBUTES, attrs);

        // Internal, never-sent fields live at the manifest top level, never inside attributes.
        if (reachedFullMode != null) {
            root.addProperty(KEY_REACHED_FULL_MODE, reachedFullMode);
        }
        if (isFirstChunk != null) {
            root.addProperty(KEY_IS_FIRST_CHUNK, isFirstChunk);
        }
        if (exitReason != null) {
            root.addProperty(KEY_EXIT_REASON, exitReason);
        }
        return root;
    }

    public static SessionManifest fromJson(JsonObject root) {
        if (root == null) {
            return new SessionManifest(CURRENT_SCHEMA_VERSION, "", 0, 0L, 0L, new HashSet<>());
        }
        int schemaVersion = root.has(KEY_SCHEMA_VERSION) ? root.get(KEY_SCHEMA_VERSION).getAsInt() : CURRENT_SCHEMA_VERSION;
        String sessionId = root.has(KEY_SESSION_ID) ? root.get(KEY_SESSION_ID).getAsString() : "";
        int realAgentId = root.has(KEY_REAL_AGENT_ID) ? root.get(KEY_REAL_AGENT_ID).getAsInt() : 0;
        long sessionStartMs = root.has(KEY_SESSION_START_MS) ? root.get(KEY_SESSION_START_MS).getAsLong() : 0L;
        long lastUpdateMs = root.has(KEY_LAST_UPDATE_MS) ? root.get(KEY_LAST_UPDATE_MS).getAsLong() : 0L;

        Set<AnalyticsAttribute> attributes;
        if (root.has(KEY_ATTRIBUTES) && root.get(KEY_ATTRIBUTES).isJsonObject()) {
            attributes = AnalyticsAttribute.newFromJson(root.getAsJsonObject(KEY_ATTRIBUTES));
        } else {
            attributes = new HashSet<>();
        }

        Boolean reachedFullMode = root.has(KEY_REACHED_FULL_MODE) ? root.get(KEY_REACHED_FULL_MODE).getAsBoolean() : null;
        Boolean isFirstChunk = root.has(KEY_IS_FIRST_CHUNK) ? root.get(KEY_IS_FIRST_CHUNK).getAsBoolean() : null;
        Integer exitReason = root.has(KEY_EXIT_REASON) ? root.get(KEY_EXIT_REASON).getAsInt() : null;
        return new SessionManifest(schemaVersion, sessionId, realAgentId, sessionStartMs, lastUpdateMs,
                attributes, reachedFullMode, isFirstChunk, exitReason);
    }
}