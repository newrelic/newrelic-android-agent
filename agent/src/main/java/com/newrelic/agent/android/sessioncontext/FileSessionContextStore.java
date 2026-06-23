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
    public boolean upsert(SessionManifest manifest) {
        if (manifest == null || !manifest.isValid()) {
            log.warn("FileSessionContextStore.upsert: null or invalid manifest");
            return false;
        }
        return storeInternal(manifest);
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
            log.debug("FileSessionContextStore.get: cannot read [" + sessionId + "]: " + e);
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