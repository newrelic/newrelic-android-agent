/**
 * Copyright 2021-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.analytics.AnalyticsControllerImpl;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.stats.StatsEngine;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;

public class PersistentUUID {
    private static final String UUID_KEY = "nr_uuid";
    private static final String UUID_FILENAME = "nr_installation";
    private static File UUID_FILE = new File(Environment.getDataDirectory(), UUID_FILENAME);
    private static AgentLog log = AgentLogManager.getAgentLog();

    public PersistentUUID(final Context context) {
        UUID_FILE = new File(context.getFilesDir(), UUID_FILENAME);
    }

    /**
     * These methods are unused at the moment, but left in the event we need to pursue UUID generation as an option.
     */
    public String getDeviceId(Context context) {
        String id = generateUniqueID(context);
        if (TextUtils.isEmpty(id)) {
            /* Type 4 (pseudo randomly generated) UUID */
            id = UUID.randomUUID().toString();
        }

        return id;
    }

    @SuppressLint("MissingPermission")
    private String generateUniqueID(Context context) {
        String hardwareDeviceId = Build.SERIAL;
        String androidDeviceId = Build.ID;
        String uuid;

        // get internal android id
        try {

            androidDeviceId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);

            // build up the uuid
            // if call failed or exception thrown
            if (TextUtils.isEmpty(androidDeviceId)) {
                uuid = UUID.randomUUID().toString();
            } else {
                // get telephony id
                try {
                    // if app already uses READ_PHONE, use the telephony device id
                    final TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                    if (tm != null) {
                        //noinspection
                        hardwareDeviceId = tm.getDeviceId();
                    }
                } catch (Exception e) {
                    hardwareDeviceId = "badf00d";
                }

                // if call failed or exception thrown
                if (TextUtils.isEmpty(hardwareDeviceId)) {
                    hardwareDeviceId = Build.HARDWARE + Build.DEVICE + Build.BOARD + Build.BRAND;
                }

                // Type 4 (pseudo randomly generated) UUID?
                // format of existing UUID is xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
                uuid = intToHexString(androidDeviceId.hashCode(), 8)
                        + "-"
                        + intToHexString(hardwareDeviceId.hashCode(), 4)
                        + "-"
                        + intToHexString(Build.VERSION.SDK_INT, 4)
                        + "-"
                        + intToHexString(Build.VERSION.RELEASE.hashCode(), 12);

                throw new RuntimeException("Not supported (TODO)");
            }

        } catch (Exception e) {
            // worst case: default to previous UUID generator
            uuid = UUID.randomUUID().toString();
        }

        return uuid;
    }

    private String intToHexString(int value, int sublen) {
        String result = "";
        String string = Integer.toHexString(value);

        int remain = sublen - string.length();
        char[] chars = new char[remain];
        Arrays.fill(chars, '0');
        string = new String(chars) + string;

        int count = 0;
        for (int i = string.length() - 1; i >= 0; i--) {
            count++;
            result = string.substring(i, i + 1) + result;
            if (0 == (count % sublen)) {
                result = "-" + result;
            }
        }

        if (result.startsWith("-")) {
            result = result.substring(1, result.length());
        }

        return result;
    }

    /**
     * Increment supportability metrics for UUID creation and recovery.
     */
    protected void noticeUUIDMetric(final String tag) {
        final StatsEngine statsEngine = StatsEngine.get();
        if (statsEngine != null) {
            statsEngine.inc(MetricNames.METRIC_MOBILE + tag);
        } else {
            log.error("StatsEngine is null. " + tag + "  not recorded.");
        }
    }


    /**
     * Fetch uuid from local storage, using a fall-back approach:
     * . Look for UUID in local json file; if not found,
     * . Generate a new random UUID
     *
     * @return uuid
     */
    public String getPersistentUUID() {
        String uuid = getUUIDFromFileStore();

        if (!TextUtils.isEmpty(uuid)) {
            // we found a UUID in the persistent store, so increment the supportability metric
            StatsEngine.get().inc(MetricNames.METRIC_UUID_RECOVERED);
        } else {
            // At this point all options to fetch a saved UUID have failed,
            // so generate a new Type 4 (pseudo randomly generated) UUID
            // and save it
            uuid = UUID.randomUUID().toString();
            log.info("Created random UUID: " + uuid);

            // Creating a new uuid occurs at install, so record a some metrics
            // Would be clearer to move this out of this class, but where? how?
            StatsEngine.get().inc(MetricNames.MOBILE_APP_INSTALL);

            final AnalyticsAttribute attribute = new AnalyticsAttribute(AnalyticsAttribute.APP_INSTALL_ATTRIBUTE, true);
            AnalyticsControllerImpl.getInstance().addAttributeUnchecked(attribute, false);

            setPersistedUUID(uuid);
        }

        return uuid;
    }

    protected void setPersistedUUID(final String uuid) {
        putUUIDToFileStore(uuid);
    }

    /**
     * Fetch UUID from private file.
     *
     * @return uuid
     */
    protected String getUUIDFromFileStore() {
        String uuid = null;

        if (UUID_FILE.exists()) {
            BufferedReader in = null;
            try {
                in = new BufferedReader(new FileReader(UUID_FILE));
                String uuidJson = in.readLine();
                uuid = new JSONObject(uuidJson).getString(UUID_KEY);
            } catch (FileNotFoundException e) {
                log.error(e.getMessage());
            } catch (IOException e) {
                log.error(e.getMessage());
            } catch (JSONException e) {
                log.error(e.getMessage());
            } catch (NullPointerException e) {
                log.error(e.getMessage());
            }
        }

        return uuid;
    }

    /**
     * Save UUID to internal private storage.
     * This file is removed when the user uninstalls the app.
     *
     * @param uuid
     */
    protected void putUUIDToFileStore(final String uuid) {
        JSONObject jsonObject = new JSONObject();


        try {
            FileWriter fw = new FileWriter(UUID_FILE);
            BufferedWriter out = new BufferedWriter(fw);
            jsonObject.put(UUID_KEY, uuid);
            out.write(jsonObject.toString());
            out.flush();
            out.close();
            fw.close();
        } catch (Exception e) {
            log.error(e.getMessage());
        }
    }

}