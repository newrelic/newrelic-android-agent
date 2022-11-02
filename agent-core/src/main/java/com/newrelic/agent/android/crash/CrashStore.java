/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.crash;

import com.newrelic.agent.android.payload.PayloadStore;

import java.util.List;

public interface CrashStore extends PayloadStore<Crash> {

        public boolean store(Crash crash);

        public List<Crash> fetchAll();

        public int count();

        public void clear();

        public void delete(Crash crash);

}
