/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.crash;


import org.junit.Assert;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class TestCrashStore implements CrashStore {
    Set<Crash> crashes = new HashSet<>();

    @Override
    public boolean store(Crash crash) {
        crashes.add(crash);
        Assert.assertTrue(crashes.contains(crash));
        return crashes.contains(crash);
    }

    @Override
    public List<Crash> fetchAll() {
        return new ArrayList<>(crashes);
    }

    @Override
    public int count() {
        return crashes.size();
    }

    @Override
    public void clear() {
        crashes.clear();
    }

    @Override
    public void delete(Crash crash) {
        crashes.remove(crash);
    }
}

