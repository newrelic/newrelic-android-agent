/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessionReplay;

import com.newrelic.agent.android.payload.PayloadStore;

import java.util.List;

public interface SessionReplayStore<T> extends PayloadStore<T> {
    public boolean store(T data);

    public List<T> fetchAll();

    public int count();

    public void clear();

    public void delete(T data);

}
