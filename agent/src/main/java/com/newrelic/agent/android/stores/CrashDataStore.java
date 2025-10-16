/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.stores;

import android.content.Context;

import com.google.gson.JsonObject;
import com.newrelic.agent.android.crash.Crash;
import com.newrelic.agent.android.crash.CrashStore;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.util.SafeJsonPrimitive;

import java.util.ArrayList;
import java.util.List;

public class CrashDataStore extends DataStoreHelper implements CrashStore {
    private static final String STORE_FILE = "NRCrashStore";

    public CrashDataStore(Context context) {
        this(context, STORE_FILE);
    }

    public CrashDataStore(Context context, String storeFilename) {
        super(context, storeFilename);
    }

    @Override
    public boolean store(Crash crash) {
        try {
            final JsonObject jsonObj = crash.asJsonObject();
            jsonObj.add("uploadCount", SafeJsonPrimitive.factory(crash.getUploadCount()));

            String crashJson = jsonObj.toString();

            // crashes should be stored synchronously, since the app is terminating
            putStringValue(crash.getUuid().toString(), crashJson);

            StatsEngine.get().inc(MetricNames.SUPPORTABILITY_CRASH_SIZE_UNCOMPRESSED, crashJson.length());

            return true;

        } catch (Exception e) {
            log.error("CrashDataStore.store(String, String): ", e);
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
                super.delete(crash.getUuid().toString());
            }
        } catch (Exception e) {
            log.error("CrashDataStore.delete(): ", e);
        }
    }

}
