/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.hybrid;

import java.util.List;
import java.util.Map;

public interface JSErrorStore {
    boolean store(String id, String data);

    List<String> fetchAll();

    Map<String, String> fetchAllEntries();

    void delete(String id);

    int count();

    void clear();
}
