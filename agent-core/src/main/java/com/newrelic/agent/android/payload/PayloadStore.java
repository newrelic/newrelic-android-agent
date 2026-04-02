/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.payload;

import java.util.List;

public interface PayloadStore<T> {
    public boolean store(T data);

    public List<T> fetchAll();

    public int count();

    public void clear();

    public void delete(T data);

}
