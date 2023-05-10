/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.harvest;

import com.newrelic.agent.android.harvest.type.Harvestable;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CopyOnWriteArrayList;

public class HarvestableCache {
    private final static int DEFAULT_CACHE_LIMIT = 1024;
    private int limit = DEFAULT_CACHE_LIMIT;
    private final Collection<Harvestable> cache = getNewCache();

    protected Collection<Harvestable> getNewCache() {
        return new CopyOnWriteArrayList<Harvestable>();
    }

    public void add(Harvestable harvestable) {
        if (harvestable == null || cache.size() >= limit) {
            return;
        }
        cache.add(harvestable);
    }

    public boolean get(Object h) {
        return cache.contains(h);
    }

    public Collection<Harvestable> flush() {
        if (cache.size() == 0) {
            return Collections.emptyList();
        }

        Collection<Harvestable> oldCache = getNewCache();
        oldCache.addAll(cache);
        cache.clear();

        return oldCache;
    }

    public int getSize() {
        return cache.size();
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }
}
