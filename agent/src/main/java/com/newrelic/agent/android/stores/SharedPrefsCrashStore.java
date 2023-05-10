/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.stores;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.JsonObject;
import com.newrelic.agent.android.crash.Crash;
import com.newrelic.agent.android.crash.CrashStore;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.util.SafeJsonPrimitive;

import java.util.ArrayList;
import java.util.List;

public class SharedPrefsCrashStore extends SharedPrefsStore implements CrashStore {
    private static final String STORE_FILE = "NRCrashStore";

    public SharedPrefsCrashStore(Context context) {
        this(context, STORE_FILE);
    }

    public SharedPrefsCrashStore(Context context, String storeFilename) {
        super(context, storeFilename);
    }

    @Override
    public boolean store(Crash crash) {
        synchronized (this) {
            try {
                final JsonObject jsonObj = crash.asJsonObject();
                jsonObj.add("uploadCount", SafeJsonPrimitive.factory(crash.getUploadCount()));

                String crashJson = jsonObj.toString();

                // crashes should be stored synchronously, since the app is terminating
                SharedPreferences.Editor editor = this.sharedPrefs.edit();
                editor.putString(crash.getUuid().toString(), crashJson);

                StatsEngine.get().inc(MetricNames.SUPPORTABILITY_CRASH_SIZE_UNCOMPRESSED, crashJson.length());

                return editor.commit();

            } catch (Exception e) {
                log.error("SharedPrefsStore.store(String, String): ", e);
            }
        }
        return false;
    }

    @Override
    public List<Crash> fetchAll() {
        final List<Crash> crashes = new ArrayList<Crash>();
        for (Object object : super.fetchAll()) {
            if (object instanceof String) {
                try {
                    crashes.add(Crash.crashFromJsonString((String) object));
                } catch (Exception e) {
                    log.error("Exception encountered while deserializing crash", e);
                }
            }
        }

        return crashes;
    }


    @Override
    public void delete(Crash crash) {
        try {
            synchronized (this) {
                final SharedPreferences.Editor editor = sharedPrefs.edit();
                editor.remove(crash.getUuid().toString()).commit();
            }
        } catch (Exception e) {
            log.error("SharedPrefsCrashStore.delete(): ", e);
        }
    }

}
