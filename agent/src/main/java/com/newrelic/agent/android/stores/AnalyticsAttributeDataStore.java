/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.stores;

import android.content.Context;

import androidx.datastore.preferences.core.Preferences;

import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.analytics.AnalyticsAttributeStore;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AnalyticsAttributeDataStore extends DataStoreHelper implements AnalyticsAttributeStore {
    private static final AgentLog log = AgentLogManager.getAgentLog();
    private static final String STORE_FILE = "NRAnalyticsAttributeStore";

    public AnalyticsAttributeDataStore(Context context) {
        super(context, STORE_FILE);
    }

    public AnalyticsAttributeDataStore(Context context, String storeFilename) {
        super(context, storeFilename);
    }

    @Override
    public boolean store(AnalyticsAttribute attribute) {
        synchronized (this) {
            if (attribute.isPersistent()) {
                switch (attribute.getAttributeDataType()) {
                    case STRING:
                        log.audit("SharedPrefsAnalyticsAttributeStore.store(" + attribute + ")");
                        putStringValue(attribute.getName(), attribute.getStringValue());
                        break;
                    case DOUBLE:
                        log.audit("SharedPrefsAnalyticsAttributeStore.store(" + attribute + ")");
                        putLongValue(attribute.getName(), Double.doubleToLongBits(attribute.getDoubleValue()));
                        break;
                    case BOOLEAN:
                        log.audit("SharedPrefsAnalyticsAttributeStore.store(" + attribute + ")");
                        putBooleanValue(attribute.getName(), attribute.getBooleanValue());
                        break;
                    default:
                        log.error("SharedPrefsAnalyticsAttributeStore.store - unsupported analytic attribute data type" + attribute.getName());
                        return false;
                }

                return true;
            }
        }

        return false;
    }

    @Override
    public List<AnalyticsAttribute> fetchAll() {
        ArrayList<AnalyticsAttribute> analyticsAttributeArrayList = new ArrayList<AnalyticsAttribute>();
        try {
            Map<Preferences.Key<?>, Object> storedAttributes = dataStoreBridge.getAllPreferences().get();

            for (Map.Entry entry : storedAttributes.entrySet()) {
                log.audit("SharedPrefsAnalyticsAttributeStore contains attribute [" + entry.getKey() + "=" + entry.getValue() + "]");
                if (entry.getValue() instanceof String) {
                    analyticsAttributeArrayList.add(new AnalyticsAttribute(entry.getKey().toString(), entry.getValue().toString(), true));
                } else if (entry.getValue() instanceof Float) { // keep  the float unwrap around to handled deprecated agent storage method
                    analyticsAttributeArrayList.add(new AnalyticsAttribute(entry.getKey().toString(), Double.valueOf(entry.getValue().toString()), true));
                } else if (entry.getValue() instanceof Long) {
                    analyticsAttributeArrayList.add(new AnalyticsAttribute(entry.getKey().toString(), Double.longBitsToDouble(Long.valueOf(entry.getValue().toString())), true));
                } else if (entry.getValue() instanceof Boolean) {
                    analyticsAttributeArrayList.add(new AnalyticsAttribute(entry.getKey().toString(), Boolean.valueOf(entry.getValue().toString()), true));
                } else {
                    log.error("SharedPrefsAnalyticsAttributeStore.fetchAll(): unsupported attribute [" + entry.getKey() + "=" + entry.getValue() + "]");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return analyticsAttributeArrayList;
    }

    @Override
    public void delete(AnalyticsAttribute attribute) {
        synchronized (this) {
            log.audit("SharedPrefsAnalyticsAttributeStore.delete(" + attribute.getName() + ")");
            super.delete(attribute.getName());
        }
    }
}
