/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay;

import com.newrelic.agent.android.payload.PayloadStore;

import java.util.List;

/**
 * Multi-entry store for {@link OfflineSessionReplayPayload}. Distinct from the
 * single-entry {@link SessionReplayStore} so the offline flush path can drain
 * many cached payloads without mutating the existing single-frame cache.
 */
public interface OfflineSessionReplayStore extends PayloadStore<OfflineSessionReplayPayload> {

    @Override
    boolean store(OfflineSessionReplayPayload data);

    @Override
    List<OfflineSessionReplayPayload> fetchAll();

    @Override
    int count();

    @Override
    void clear();

    @Override
    void delete(OfflineSessionReplayPayload data);
}
