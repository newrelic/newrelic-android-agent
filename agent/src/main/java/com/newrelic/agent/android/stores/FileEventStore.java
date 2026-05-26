/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.stores;

import android.content.Context;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.analytics.AnalyticsEvent;
import com.newrelic.agent.android.analytics.AnalyticsEventStore;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.payload.AbstractFileStore;
import com.newrelic.agent.android.stats.StatsEngine;

import java.util.List;

/**
 * File-backed analytics event store. Stores one JSON file per event under
 * {@code filesDir/nr_event_cache/}. Filename is the event UUID; body is
 * {@code event.asJsonObject().toString()}. The UUID is recovered from the filename
 * on deserialize.
 */
public class FileEventStore extends AbstractFileStore<AnalyticsEvent> implements AnalyticsEventStore {
    public static final String DIR_NAME = "nr_event_cache";

    public FileEventStore(Context context, AgentConfiguration config) {
        super(context, DIR_NAME, resolveCap(config),
                MetricNames.SUPPORTABILITY_EVENT_STORE_EVICTED,
                MetricNames.SUPPORTABILITY_EVENT_STORE_CORRUPTED,
                "FileEventStore");
    }

    private static int resolveCap(AgentConfiguration config) {
        final int configured = config.getMaxCachedEventCount();
        return configured > 0 ? configured : AgentConfiguration.DEFAULT_MAX_CACHED_EVENT_COUNT;
    }

    @Override
    public boolean store(AnalyticsEvent event) {
        if (event == null) {
            return false;
        }
        final String eventJson = serialize(event);
        StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_EVENT_SIZE_UNCOMPRESSED, eventJson.length());
        return storeInternal(event);
    }

    @Override
    public List<AnalyticsEvent> fetchAll() {
        return fetchAllInternal();
    }

    @Override
    public int count() {
        return countInternal();
    }

    @Override
    public void delete(AnalyticsEvent event) {
        if (event == null) {
            return;
        }
        deleteInternal(event.getEventUUID());
    }

    @Override
    public void clear() {
        clearInternal();
    }

    @Override
    protected String keyOf(AnalyticsEvent event) {
        return event.getEventUUID();
    }

    @Override
    protected String serialize(AnalyticsEvent event) {
        return event.asJsonObject().toString();
    }

    @Override
    protected AnalyticsEvent deserialize(String keyFromFile, String json) {
        return AnalyticsEvent.eventFromJsonString(keyFromFile, json);
    }
}