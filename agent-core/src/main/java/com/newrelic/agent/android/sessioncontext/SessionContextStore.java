/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.newrelic.agent.android.sessioncontext;

import java.util.List;

/**
 * Persistent, {@code sessionId}-keyed store of {@link SessionManifest} snapshots. One
 * manifest per session; {@link #upsert(SessionManifest)} replaces an existing entry for
 * the same {@code sessionId}.
 */
public interface SessionContextStore {

    /** Store or replace the manifest for its {@code sessionId}. Returns false on IO failure. */
    boolean upsert(SessionManifest manifest);

    /** Returns the manifest for {@code sessionId}, or {@code null} if absent/unreadable. */
    SessionManifest get(String sessionId);

    /** Returns all stored manifests (order unspecified). */
    List<SessionManifest> fetchAll();

    /** Removes the manifest for {@code sessionId} if present. */
    void delete(String sessionId);

    /** Number of stored manifests. */
    int count();

    /** Removes all stored manifests. */
    void clear();
}