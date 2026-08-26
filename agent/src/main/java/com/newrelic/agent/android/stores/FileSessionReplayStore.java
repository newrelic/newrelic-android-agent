/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.stores;

import android.content.Context;

import com.newrelic.agent.android.payload.AbstractFileStore;
import com.newrelic.agent.android.sessionReplay.SessionReplayStore;

import java.util.List;

/**
 * File-backed session replay frame cache. Single-entry: one fixed file
 * {@code session_replay_frame.json} that is overwritten on every {@code store(...)}.
 * The consumer in {@code SessionReplayReporter#reportCachedSessionReplayData} reads
 * {@code fetchAll().get(0)} to retrieve the last stored frame. {@code delete()} is
 * intentionally a no-op — use {@link #clear()} to wipe.
 */
public class FileSessionReplayStore extends AbstractFileStore<Object> implements SessionReplayStore<Object> {
    public static final String DIR_NAME = "nr_session_replay_cache";
    static final String FIXED_KEY = "session_replay_frame";

    public FileSessionReplayStore(Context context) {
        super(context, DIR_NAME, 1, null, null, "FileSessionReplayStore");
    }

    @Override
    public boolean store(Object data) {
        if (data == null) {
            return false;
        }
        return storeInternal(data);
    }

    @Override
    public List<Object> fetchAll() {
        return fetchAllInternal();
    }

    @Override
    public int count() {
        return countInternal();
    }

    @Override
    public void delete(Object data) {
        // no-op: single-entry store; callers use clear() to wipe the cached frame
    }

    @Override
    public void clear() {
        clearInternal();
    }

    @Override
    protected String keyOf(Object data) {
        return FIXED_KEY;
    }

    @Override
    protected String serialize(Object data) {
        return data.toString();
    }

    @Override
    protected Object deserialize(String keyFromFile, String json) {
        return json;
    }
}