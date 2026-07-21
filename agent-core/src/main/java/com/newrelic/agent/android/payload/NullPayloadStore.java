/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.payload;

import java.util.ArrayList;
import java.util.List;

public class NullPayloadStore<T> implements PayloadStore<T> {
    @Override
    public boolean store(T payload) {
        return true;
    }

    @Override
    public List<T> fetchAll() {
        return new ArrayList<T>();
    }

    @Override
    public int count() {
        return 0;
    }

    @Override
    public void clear() {
    }

    @Override
    public void delete(T payload) {
    }
}
