/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.hybrid;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.analytics.AnalyticsControllerImpl;
import com.newrelic.agent.android.analytics.AnalyticsEvent;
import com.newrelic.agent.android.error.Error;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.payload.PayloadController;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class JSErrorDataController {
    protected static final AgentConfiguration agentConfiguration = new AgentConfiguration();
    private static final AgentLog log = AgentLogManager.getAgentLog();
    private static final Gson gson = new GsonBuilder().create();
    private static final String JSERROR_FILE_PATH = "js-errors-cache.json";

    public static boolean sendJSErrorData(String name, String message, String stackTrace, boolean isFatal, Map<String, Object> additionalAttributes) {
        final HashMap<String, Object> eventAttributes = new HashMap<>();
        try {
            //map attributes first, then all internal attributes will overwrite if any duplicate
            if (additionalAttributes != null) {
                eventAttributes.putAll(additionalAttributes);
            }
            eventAttributes.put(AnalyticsAttribute.JSERROR_ERRORID, UUID.randomUUID().toString());
            eventAttributes.put(AnalyticsAttribute.JSERROR_THREADS, stackTrace);
            eventAttributes.put(AnalyticsAttribute.JSERROR_ISFATAL, isFatal);
            eventAttributes.put(AnalyticsAttribute.JSERROR_ERRORTYPE, name);
            eventAttributes.put(AnalyticsAttribute.JSERROR_DESCRIPTION, message);
            eventAttributes.put(AnalyticsAttribute.JSERROR_CAUSE, message);
            eventAttributes.put(AnalyticsAttribute.JSERROR_TIMESTAMP, System.currentTimeMillis());
            eventAttributes.put(AnalyticsAttribute.EVENT_TYPE_ATTRIBUTE, AnalyticsEvent.EVENT_TYPE_MOBILE_JSERROR);

            JSErrorDataReporter jsErrorReporter = JSErrorDataReporter.getInstance();
            final AnalyticsControllerImpl analyticsController = AnalyticsControllerImpl.getInstance();

            if (jsErrorReporter.payloadStore != null) {
                String rootPath = jsErrorReporter.payloadStore.getRootPath();
                File file = new File(rootPath, JSERROR_FILE_PATH);

                Error jsError = new Error(analyticsController.getSessionAttributes(), eventAttributes);
                String currentErrorJson = jsError.asJsonObject().toString();

                JsonObject rootContainer = new JsonObject();
                JsonArray events = new JsonArray();
                if (file.exists() && file.length() > 0) {
                    try (FileInputStream fis = new FileInputStream(file);
                         InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8)) {

                        rootContainer = JsonParser.parseReader(isr).getAsJsonObject();
                        if (rootContainer.has(Error.ANALYTICS_EVENTS_KEY)) {
                            events = rootContainer.getAsJsonArray(Error.ANALYTICS_EVENTS_KEY);
                        }
                    } catch (Exception e) {
                        log.warn("JSErrorDataController: Failed to read existing cache, resetting: " + e.getMessage());
                    }
                }

                events.add(gson.toJsonTree(eventAttributes));
                rootContainer.add(Error.ANALYTICS_EVENTS_KEY, events);
                currentErrorJson = rootContainer.toString();

                saveJsonToDisk(rootPath, currentErrorJson);
            }

            return true;
        } catch (Exception ex) {
            log.error("HandledJSError: exception " + ex.getClass().getName() + " failed to send data.");
        }

        return false;
    }

    private static void saveJsonToDisk(final String rootPath, final String rootJsonContent) {
        if (rootPath != null) {
            PayloadController.submitCallable(() -> {
                try {
                    File file = new File(rootPath, JSERROR_FILE_PATH);
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        fos.write(rootJsonContent.getBytes(StandardCharsets.UTF_8));
                    }
                    log.info("JSErrorDataController: Updated local JSON cache at " + file.getAbsolutePath());
                } catch (Exception e) {
                    log.error("JSErrorDataController: Failed to save local JSON: " + e.getMessage());
                }
                return null;
            });
        }
    }

    public static byte[] getStoredJSErrorData() {
        JSErrorDataReporter jsErrorReporter = JSErrorDataReporter.getInstance();
        if (jsErrorReporter.payloadStore != null) {
            String rootPath = jsErrorReporter.payloadStore.getRootPath();
            File file = new File(rootPath, JSERROR_FILE_PATH);

            if (file.exists() && file.length() > 0) {
                try (FileInputStream fis = new FileInputStream(file);
                     InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8)) {

                    JsonObject rootContainer = JsonParser.parseReader(isr).getAsJsonObject();
                    return rootContainer.toString().getBytes(StandardCharsets.UTF_8);

                } catch (Exception e) {
                    log.error("JSErrorDataController: Failed to read cached JS errors: " + e.getMessage());
                }
            }
        }
        return null;
    }

    public static void deleteCacheFile() {
        JSErrorDataReporter jsErrorReporter = JSErrorDataReporter.getInstance();
        if (jsErrorReporter.payloadStore != null) {
            String rootPath = jsErrorReporter.payloadStore.getRootPath();
            File file = new File(rootPath, JSERROR_FILE_PATH);
            if (file.exists()) {
                if (file.delete()) {
                    log.info("JSErrorDataController: Local JSON cache deleted successfully.");
                } else {
                    log.warn("JSErrorDataController: Failed to delete local JSON cache.");
                }
            }
        }
    }
}
