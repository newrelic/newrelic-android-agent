/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.stores;

import android.content.Context;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.payload.AbstractFileStore;
import com.newrelic.agent.android.sessionReplay.OfflineSessionReplayPayload;
import com.newrelic.agent.android.sessionReplay.OfflineSessionReplayStore;

import java.util.List;

/**
 * File-backed offline cache for session replay payloads. Stores up to
 * {@link #MAX_COUNT} entries under {@code filesDir/nr_offline_session_replay_cache/}
 * with FIFO eviction (oldest by mtime) when the cap is reached.
 */
public class FileOfflineSessionReplayStore extends AbstractFileStore<OfflineSessionReplayPayload>
        implements OfflineSessionReplayStore {

    public static final String DIR_NAME = "nr_offline_session_replay_cache";
    public static final int MAX_COUNT = 50;

    public FileOfflineSessionReplayStore(Context context) {
        super(context, DIR_NAME, MAX_COUNT,
                MetricNames.SUPPORTABILITY_SESSION_REPLAY_OFFLINE_EVICTED,
                MetricNames.SUPPORTABILITY_SESSION_REPLAY_OFFLINE_CORRUPTED,
                "FileOfflineSessionReplayStore");
    }

    @Override
    public boolean store(OfflineSessionReplayPayload data) {
        if (data == null) {
            return false;
        }
        return storeInternal(data);
    }

    @Override
    public List<OfflineSessionReplayPayload> fetchAll() {
        return fetchAllInternal();
    }

    @Override
    public int count() {
        return countInternal();
    }

    @Override
    public void delete(OfflineSessionReplayPayload data) {
        if (data == null) {
            return;
        }
        deleteInternal(keyOf(data));
    }

    @Override
    public void clear() {
        clearInternal();
    }

    @Override
    protected String keyOf(OfflineSessionReplayPayload entry) {
        return entry.getUuid();
    }

    @Override
    protected String serialize(OfflineSessionReplayPayload entry) {
        return entry.asJsonObject().toString();
    }

    @Override
    protected OfflineSessionReplayPayload deserialize(String keyFromFile, String json) {
        final JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        return OfflineSessionReplayPayload.fromJsonObject(obj);
    }
}