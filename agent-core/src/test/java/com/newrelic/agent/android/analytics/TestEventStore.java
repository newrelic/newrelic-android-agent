/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.analytics;


import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TestEventStore implements AnalyticsEventStore {
    // Keyed by event UUID to mirror FileEventStore (which stores one file per UUID and
    // deletes by UUID), so a persisted clone is correctly de-duped and deletable.
    Map<String, AnalyticsEvent> events = new LinkedHashMap<>();

    @Override
    public boolean store(AnalyticsEvent event) {
        events.put(event.getEventUUID(), event);
        return true;
    }

    @Override
    public List<AnalyticsEvent> fetchAll() {
        return new ArrayList<>(events.values());
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
        events.remove(event.getEventUUID());
    }

    @Override
    public String getRootPath() {
        return "";
    }
}

