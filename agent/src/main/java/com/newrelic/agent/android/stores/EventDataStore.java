/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.stores;

import android.content.Context;

import androidx.datastore.preferences.core.Preferences;

import com.google.gson.JsonObject;
import com.newrelic.agent.android.analytics.AnalyticsEvent;
import com.newrelic.agent.android.analytics.AnalyticsEventStore;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.stats.StatsEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EventDataStore extends DataStoreHelper implements AnalyticsEventStore {
    private static final AgentLog log = AgentLogManager.getAgentLog();
    private static final String STORE_FILE = "NREventStore";

    public EventDataStore(Context context) {
        this(context, STORE_FILE);
    }

    public EventDataStore(Context context, String storeFilename) {
        super(context, storeFilename);
    }

    @Override
    public boolean store(AnalyticsEvent event) {
        try {
            final JsonObject jsonObj = event.asJsonObject();
            String eventJson = jsonObj.toString();

            // events should be stored synchronously, since the app is terminating
            putStringValue(event.getEventUUID(), eventJson);

            StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_EVENT_SIZE_UNCOMPRESSED, eventJson.length());
            return true;
        } catch (Exception e) {
            log.error("EventDataStore.store(String, String): ", e);
        }
        return false;
    }

    @Override
    public List<AnalyticsEvent> fetchAll() {
        final List<AnalyticsEvent> events = new ArrayList<AnalyticsEvent>();
        try {
            Map<Preferences.Key<?>, Object> objectStrings = dataStoreBridge.getAllPreferences().get();
            for (Map.Entry<Preferences.Key<?>, Object> entry : objectStrings.entrySet()) {
                if (entry.getValue() instanceof String) {
                    try {
                        events.add(AnalyticsEvent.eventFromJsonString(entry.getKey().toString(), (String) entry.getValue()));
                    } catch (Exception e) {
                        log.error("Exception encountered while deserializing event", e);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return events;
    }

    @Override
    public void delete(AnalyticsEvent event) {
        try {
            synchronized (this) {
                super.delete(event.getEventUUID());
            }
        } catch (Exception e) {
            log.error("EventDataStore.delete(): ", e);
        }
    }
}