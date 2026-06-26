/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.newrelic.agent.android.sessioncontext;

import android.content.Context;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.payload.AbstractFileStore;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.util.Streams;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * File-backed {@link SessionContextStore}. Stores one JSON file per session under
 * {@code filesDir/nr_session_context_cache/}, named by the (sanitized) {@code sessionId}.
 */
public class FileSessionContextStore extends AbstractFileStore<SessionManifest> implements SessionContextStore {

    public static final String DIR_NAME = "nr_session_context_cache";

    public FileSessionContextStore(Context context, AgentConfiguration config) {
        super(context, DIR_NAME, resolveCap(config),
                MetricNames.SUPPORTABILITY_SESSION_CONTEXT_EVICTED,
                MetricNames.SUPPORTABILITY_SESSION_CONTEXT_CORRUPTED,
                "FileSessionContextStore");
    }

    private static int resolveCap(AgentConfiguration config) {
        final int configured = config.getMaxCachedSessionContextCount();
        return configured > 0 ? configured : AgentConfiguration.DEFAULT_MAX_CACHED_SESSION_CONTEXT_COUNT;
    }

    @Override
    public synchronized boolean upsert(SessionManifest manifest) {
        if (manifest == null || !manifest.isValid()) {
            log.warn("FileSessionContextStore.upsert: null or invalid manifest");
            return false;
        }
        SessionManifest existing = get(manifest.getSessionId());
        SessionManifest merged = (existing == null) ? manifest : new SessionManifest(
                SessionManifest.CURRENT_SCHEMA_VERSION,
                manifest.getSessionId(), manifest.getRealAgentId(),
                manifest.getSessionStartMs(), manifest.getLastUpdateMs(),
                manifest.getAttributes(),
                manifest.getReachedFullMode() != null ? manifest.getReachedFullMode() : existing.getReachedFullMode(),
                manifest.getIsFirstChunk() != null ? manifest.getIsFirstChunk() : existing.getIsFirstChunk(),
                manifest.getExitReason() != null ? manifest.getExitReason() : existing.getExitReason());
        return storeInternal(merged);
    }

    @Override
    public synchronized void updateSessionReplayState(String sessionId, boolean reachedFullMode, boolean isFirstChunk) {
        if (sessionId == null || sessionId.isEmpty()) {
            return;
        }
        SessionManifest existing = get(sessionId);
        long now = existing != null ? existing.getLastUpdateMs() : 0L;
        SessionManifest merged = new SessionManifest(
                SessionManifest.CURRENT_SCHEMA_VERSION, sessionId,
                existing != null ? existing.getRealAgentId() : 0,
                existing != null ? existing.getSessionStartMs() : 0L, now,
                existing != null ? existing.getAttributes() : null,
                reachedFullMode, isFirstChunk,
                existing != null ? existing.getExitReason() : null);
        storeInternal(merged);
    }

    @Override
    public synchronized void updateExitReason(String sessionId, int exitReason) {
        if (sessionId == null || sessionId.isEmpty()) {
            return;
        }
        SessionManifest existing = get(sessionId);
        if (existing == null) {
            return; // No record to attach an exit reason to.
        }
        storeInternal(new SessionManifest(
                SessionManifest.CURRENT_SCHEMA_VERSION, sessionId, existing.getRealAgentId(),
                existing.getSessionStartMs(), existing.getLastUpdateMs(), existing.getAttributes(),
                existing.getReachedFullMode(), existing.getIsFirstChunk(), Integer.valueOf(exitReason)));
    }

    @Override
    public SessionManifest get(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return null;
        }
        final File target = new File(dir, safeFilename(sessionId) + FILE_SUFFIX);
        if (!target.exists()) {
            return null;
        }
        try {
            final String json = Streams.slurpString(target, StandardCharsets.UTF_8.name());
            return deserialize(sessionId, json);
        } catch (Exception e) {
            // Corrupt/unreadable entry: delete it and count, mirroring fetchAllInternal() so a
            // bad dead-session file can't silently yield empty attributes on every AEI lookup.
            log.debug("FileSessionContextStore.get: corrupt entry [" + sessionId + "], deleting: " + e);
            if (!target.delete()) {
                log.debug("FileSessionContextStore.get: failed to delete corrupt entry [" + sessionId + "]");
            }
            StatsEngine.get().inc(MetricNames.SUPPORTABILITY_SESSION_CONTEXT_CORRUPTED);
            return null;
        }
    }

    @Override
    public List<SessionManifest> fetchAll() {
        return fetchAllInternal();
    }

    @Override
    public void delete(String sessionId) {
        deleteInternal(sessionId);
    }

    @Override
    public int count() {
        return countInternal();
    }

    @Override
    public void clear() {
        clearInternal();
    }

    @Override
    protected String keyOf(SessionManifest entry) {
        return entry.getSessionId();
    }

    @Override
    protected String serialize(SessionManifest entry) {
        return entry.toJson().toString();
    }

    @Override
    protected SessionManifest deserialize(String keyFromFile, String json) throws IOException {
        final JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (root == null || !root.has(SessionManifest.KEY_SESSION_ID)) {
            throw new IOException("missing sessionId field");
        }
        return SessionManifest.fromJson(root);
    }
}