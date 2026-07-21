/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.stores;

import android.content.Context;
import android.content.SharedPreferences;
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

public class SharedPrefsEventStore extends SharedPrefsStore implements AnalyticsEventStore {
    private static final AgentLog log = AgentLogManager.getAgentLog();
    private static final String STORE_FILE = "NREventStore";

    public SharedPrefsEventStore(Context context) {
        this(context, STORE_FILE);
    }

    public SharedPrefsEventStore(Context context, String storeFilename) {
        super(context, storeFilename);
    }

    @Override
    public boolean store(AnalyticsEvent event) {
        synchronized (this) {
            try {
                final JsonObject jsonObj = event.asJsonObject();
                String eventJson = jsonObj.toString();

                // events should be stored synchronously, since the app is terminating
                SharedPreferences.Editor editor = this.sharedPrefs.edit();
                editor.putString(event.getEventUUID(), eventJson);

                StatsEngine.SUPPORTABILITY.inc(MetricNames.SUPPORTABILITY_EVENT_SIZE_UNCOMPRESSED, eventJson.length());
                return applyOrCommitEditor(editor);
            } catch (Exception e) {
                log.error("SharedPrefsStore.store(String, String): ", e);
            }
        }
        return false;
    }

    @Override
    public List<AnalyticsEvent> fetchAll() {
        final List<AnalyticsEvent> events = new ArrayList<AnalyticsEvent>();
        Map<String, ?> objectStrings = sharedPrefs.getAll();
        for (Map.Entry<String, ?> entry : objectStrings.entrySet()) {
            if (entry.getValue() instanceof String) {
                try {
                    events.add(AnalyticsEvent.eventFromJsonString(entry.getKey(), (String) entry.getValue()));
                } catch (Exception e) {
                    log.error("Exception encountered while deserializing event", e);
                }
            }
        }
        return events;
    }

    @Override
    public void delete(AnalyticsEvent event) {
        try {
            synchronized (this) {
                final SharedPreferences.Editor editor = sharedPrefs.edit();
                editor.remove(event.getEventUUID()).apply();
            }
        } catch (Exception e) {
            log.error("SharedPrefsEventStore.delete(): ", e);
        }
    }
}