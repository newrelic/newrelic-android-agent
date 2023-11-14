/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.stores;


import com.newrelic.agent.android.analytics.AnalyticsEvent;
import com.newrelic.agent.android.analytics.AnalyticsEventStore;

import org.junit.Assert;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class TestEventStore implements AnalyticsEventStore {
    Set<AnalyticsEvent> events = new HashSet<>();

    @Override
    public boolean store(AnalyticsEvent event) {
        events.add(event);
        Assert.assertTrue(events.contains(event));
        return events.contains(event);
    }

    @Override
    public List<AnalyticsEvent> fetchAll() {
        return new ArrayList<>(events);
    }

    @Override
    public int count() {
        return events.size();
    }

    @Override
    public void clear() {
        events.clear();
    }

    @Override
    public void delete(AnalyticsEvent event) {
        events.remove(event);
    }
}

