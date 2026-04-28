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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class JSErrorDataController {
    private final AgentConfiguration agentConfiguration;
    private final JSErrorStore jsErrorStore;
    private final ReentrantReadWriteLock lock;

    private static final AgentLog log = AgentLogManager.getAgentLog();
    private static final Gson gson = new GsonBuilder().create();
    private static volatile JSErrorDataController instance;

    private JSErrorDataController() {
        this.agentConfiguration = AgentConfiguration.getInstance();
        this.jsErrorStore = agentConfiguration.getJsErrorStore();
        this.lock = new ReentrantReadWriteLock();
    }

    public static JSErrorDataController getInstance() {
        if (instance == null) {
            synchronized (JSErrorDataController.class) {
                if (instance == null) {
                    instance = new JSErrorDataController();
                }
            }
        }
        return instance;
    }

    /** Resets the singleton — call from {@link JSErrorDataReporter#shutdown()} only. */
    static void reset() {
        synchronized (JSErrorDataController.class) {
            instance = null;
        }
    }

    public boolean sendJSErrorData(String name, String message, String stackTrace, boolean isFatal, Map<String, Object> additionalAttributes) {
        try {
            if (name == null || name.trim().isEmpty()) {
                log.warn("JSError: error name cannot be null or empty");
                return false;
            }
            if (message == null) {
                message = "";
            }
            if (stackTrace == null) {
                stackTrace = "";
            }

            // additionalAttributes are merged first so that reserved keys below always win.
            final HashMap<String, Object> eventAttributes = new HashMap<>();
            if (additionalAttributes != null) {
                eventAttributes.putAll(additionalAttributes);
            }
            final String errorId = UUID.randomUUID().toString();
            eventAttributes.put(AnalyticsAttribute.JSERROR_ERRORID, errorId);
            eventAttributes.put(AnalyticsAttribute.JSERROR_THREADS, stackTrace);
            eventAttributes.put(AnalyticsAttribute.JSERROR_ISFATAL, isFatal);
            eventAttributes.put(AnalyticsAttribute.JSERROR_ERRORTYPE, name);
            eventAttributes.put(AnalyticsAttribute.JSERROR_DESCRIPTION, message);
            eventAttributes.put(AnalyticsAttribute.JSERROR_CAUSE, message);
            eventAttributes.put(AnalyticsAttribute.JSERROR_TIMESTAMP, System.currentTimeMillis());
            eventAttributes.put(AnalyticsAttribute.EVENT_TYPE_ATTRIBUTE, AnalyticsEvent.EVENT_TYPE_MOBILE_JSERROR);

            final JsonObject eventObject = new JsonObject();
            for (Map.Entry<String, Object> entry : eventAttributes.entrySet()) {
                eventObject.add(entry.getKey(), gson.toJsonTree(entry.getValue()));
            }

            PayloadController.submitCallable(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    if (jsErrorStore == null) {
                        log.error("JSErrorStore is not initialized");
                        return false;
                    }

                    lock.writeLock().lock();
                    try {
                        boolean stored = jsErrorStore.store(errorId, eventObject.toString());
                        if (stored) {
                            log.debug("JSError stored successfully with ID: " + errorId);
                            return true;
                        } else {
                            log.error("Failed to store JSError with ID: " + errorId);
                            return false;
                        }
                    } finally {
                        lock.writeLock().unlock();
                    }
                }
            });

            return true;
        } catch (Exception ex) {
            log.error("JSError: Failed to send error data - " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            return false;
        }
    }

    String buildErrorEnvelope(List<String> eventJsonList) {
        AnalyticsControllerImpl analyticsController = AnalyticsControllerImpl.getInstance();
        if (analyticsController == null) {
            log.error("JSError: AnalyticsController is not initialized");
            return null;
        }

        Error jsError = new Error(analyticsController.getSessionAttributes(), new HashMap<>());
        JsonArray eventsArray = new JsonArray();

        for (String eventJson : eventJsonList) {
            if (eventJson != null && !eventJson.trim().isEmpty()) {
                try {
                    eventsArray.add(JsonParser.parseString(eventJson));
                } catch (Exception e) {
                    log.warn("JSError: Failed to parse stored event JSON: " + e.getMessage());
                }
            }
        }

        JsonObject rootObject = jsError.asJsonObject();
        JsonArray existingEvents = rootObject.getAsJsonArray(Error.ANALYTICS_EVENTS_KEY);

        if (existingEvents != null) {
            existingEvents.addAll(eventsArray);
        } else {
            rootObject.add(Error.ANALYTICS_EVENTS_KEY, eventsArray);
        }

        return rootObject.toString();
    }

    /**
     * Snapshot of a single harvest batch: the JSON payload to send and the exact
     * store IDs included in it. Pass {@link #deleteErrors(List)} the
     * {@code snapshotIds} after a confirmed successful upload so that only the sent
     * errors are removed — errors that arrived after the snapshot are preserved.
     */
    public static final class HarvestSnapshot {
        public final String payloadJson;
        public final List<String> snapshotIds;

        HarvestSnapshot(String payloadJson, List<String> snapshotIds) {
            this.payloadJson = payloadJson;
            this.snapshotIds = Collections.unmodifiableList(snapshotIds);
        }
    }

    /**
     * Returns a {@link HarvestSnapshot} of all currently stored errors, or {@code null}
     * if there are none. Uses a single atomic store read to ensure the payload values
     * and their IDs are always consistent with each other.
     */
    public HarvestSnapshot getStoredJSErrorData() {
        if (jsErrorStore == null) {
            log.warn("JSErrorStore is not initialized");
            return null;
        }

        List<String> values;
        List<String> snapshotIds;
        lock.readLock().lock();
        try {
            Map<String, String> entries = jsErrorStore.fetchAllEntries();
            if (entries == null || entries.isEmpty()) {
                log.debug("No JS errors found in store");
                return null;
            }

            values = new ArrayList<>(entries.values());
            snapshotIds = new ArrayList<>(entries.keySet());
        } catch (Exception e) {
            log.error("Failed to retrieve stored JS error data: " + e.getMessage());
            return null;
        } finally {
            lock.readLock().unlock();
        }

        String payloadJson = buildErrorEnvelope(values);
        if (payloadJson == null) {
            return null;
        }

        return new HarvestSnapshot(payloadJson, snapshotIds);
    }

    /**
     * Deletes only the errors identified by {@code ids}. Call this with
     * {@link HarvestSnapshot#snapshotIds} after a confirmed HTTP 200/202 so that
     * errors arriving after the snapshot are preserved for the next harvest cycle.
     */
    public void deleteErrors(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        lock.writeLock().lock();
        try {
            if (jsErrorStore == null) {
                log.warn("JSErrorStore is not initialized");
                return;
            }
            for (String id : ids) {
                try {
                    jsErrorStore.delete(id);
                } catch (Exception e) {
                    log.warn("JSError: Failed to delete error ID " + id + ": " + e.getMessage());
                }
            }
            log.info("JSError: Deleted " + ids.size() + " sent errors");
        } catch (Exception e) {
            log.error("Failed to delete sent JS errors: " + e.getMessage());
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Clears all stored errors — used on agent shutdown or full reset only. */
    public void clearStoredErrors() {
        lock.writeLock().lock();
        try {
            if (jsErrorStore == null) {
                log.warn("JSErrorStore is not initialized");
                return;
            }
            jsErrorStore.clear();
            log.info("JSError: Cleared all stored errors");
        } catch (Exception e) {
            log.error("Failed to clear stored JS errors: " + e.getMessage());
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Returns the number of errors currently in the store. */
    public int getStoredErrorCount() {
        if (jsErrorStore == null) {
            return 0;
        }
        return jsErrorStore.count();
    }
}
