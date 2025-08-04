/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.stores;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.newrelic.agent.android.analytics.AnalyticsEvent;
import com.newrelic.agent.android.analytics.AnalyticsEventStore;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.payload.Payload;
import com.newrelic.agent.android.sessionReplay.SessionReplayStore;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.util.SafeJsonPrimitive;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SharedPrefsSessionReplayStore extends SharedPrefsStore implements SessionReplayStore {
    private static final AgentLog log = AgentLogManager.getAgentLog();
    private static final String STORE_FILE = "NRSessionReplayStore";

    public SharedPrefsSessionReplayStore(Context context) {
        this(context, STORE_FILE);
    }

    public SharedPrefsSessionReplayStore(Context context, String storeFilename) {
        super(context, storeFilename);
    }

    @Override
    public boolean store(Object json) {
        synchronized (this) {
            try {
                SharedPreferences.Editor editor = this.sharedPrefs.edit();
                editor.putString("SessionReplayFrame", json.toString());
                return applyOrCommitEditor(editor);
            } catch (Exception e) {
                log.error("SharedPrefsStore.store(String, String): ", e);
            }
        }
        return false;
    }

    @Override
    public void delete(Object data) {

    }

    @Override
    public List fetchAll() {
        final List<String> sessionReplayData = new ArrayList<String>();
        try {
            JsonArray array = new JsonArray();
            String data = sharedPrefs.getString("SessionReplayFrame", array.toString());
            sessionReplayData.add(data);

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return sessionReplayData;
    }
}