/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.hybrid;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.analytics.AnalyticsControllerImpl;
import com.newrelic.agent.android.analytics.AnalyticsEvent;
import com.newrelic.agent.android.error.Error;
import com.newrelic.agent.android.harvest.ApplicationInformation;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.payload.PayloadController;
import com.newrelic.agent.android.util.SafeJsonPrimitive;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
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

    /**
     * Returns the current runtime app version, or {@code null} if {@link Agent} or its
     * {@link ApplicationInformation} are not yet initialized.
     */
    private static String currentAppVersion() {
        try {
            ApplicationInformation appInfo = Agent.getApplicationInformation();
            return appInfo == null ? null : appInfo.getAppVersion();
        } catch (Exception e) {
            return null;
        }
    }

    public boolean sendJSErrorData(String name, String message, String stackTrace, boolean isFatal, Map<String, Object> additionalAttributes) {
        try {
            if (!FeatureFlag.featureEnabled(FeatureFlag.JSError)) {
                log.debug("JSError: feature disabled, dropping error");
                return false;
            }
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
            eventAttributes.put(AnalyticsAttribute.JSERROR_ERRORNAME, name);
            eventAttributes.put(AnalyticsAttribute.JSERROR_ERRORMESSAGE, message);
            eventAttributes.put(AnalyticsAttribute.JSERROR_TIMESTAMP, System.currentTimeMillis());
            eventAttributes.put(AnalyticsAttribute.EVENT_TYPE_ATTRIBUTE, AnalyticsEvent.EVENT_TYPE_MOBILE_JSERROR);

            final JsonObject eventObject = new JsonObject();
            for (Map.Entry<String, Object> entry : eventAttributes.entrySet()) {
                eventObject.add(entry.getKey(), gson.toJsonTree(entry.getValue()));
            }

            // Snapshot the current app version so the upload header can be set correctly
            // on flush even if the app has been upgraded since this error was recorded.
            // !has() preserves precedence: reserved JSERROR_* keys and user-supplied
            // additionalAttributes already in eventObject win on collision.
            final String currentAppVersion = currentAppVersion();
            if (currentAppVersion != null && !currentAppVersion.isEmpty()
                    && !eventObject.has(AnalyticsAttribute.APP_VERSION_AT_RECORD_ATTRIBUTE)) {
                eventObject.addProperty(AnalyticsAttribute.APP_VERSION_AT_RECORD_ATTRIBUTE, currentAppVersion);
            }

            // Snapshot session attributes synchronously on the calling thread so the event
            // freezes the session active at recordJavaScriptError time, not whichever session
            // is active when the cache is later flushed (which may be a different session
            // entirely after a crash, ANR, or force-stop).
            AnalyticsControllerImpl analyticsController = AnalyticsControllerImpl.getInstance();
            if (analyticsController != null) {
                for (AnalyticsAttribute attr : analyticsController.getSessionAttributes()) {
                    if (!eventObject.has(attr.getName())) {
                        eventObject.add(attr.getName(), attr.asJsonElement());
                    }
                }
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

    String buildErrorEnvelope(List<String> eventJsonList, String appVersionOverride) {
        AnalyticsControllerImpl analyticsController = AnalyticsControllerImpl.getInstance();
        if (analyticsController == null) {
            log.error("JSError: AnalyticsController is not initialized");
            return null;
        }

        Error jsError = new Error(null, new HashMap<>());
        JsonArray eventsArray = new JsonArray();

        for (String eventJson : eventJsonList) {
            if (eventJson != null && !eventJson.trim().isEmpty()) {
                try {
                    JsonElement parsed = JsonParser.parseString(eventJson);
                    if (parsed.isJsonObject()) {
                        // appVersionAtRecord is an internal-only marker used
                        // by the agent to group cached events and set the
                        // per-group upload header / appInfo.appVersion. It's
                        // not a customer-facing attribute and must not ship
                        // on the wire.
                        parsed.getAsJsonObject().remove(AnalyticsAttribute.APP_VERSION_AT_RECORD_ATTRIBUTE);
                    }
                    eventsArray.add(parsed);
                } catch (Exception e) {
                    log.warn("JSError: Failed to parse stored event JSON: " + e.getMessage());
                }
            }
        }

        JsonObject rootObject = jsError.asJsonObject();
        // Overwrite, don't append: Error.asJsonObject() seeded analyticsEvents
        // with the constructor's `event` field (designed for the one-event-per-crash
        // case). Setting the array explicitly here keeps JSError envelopes free
        // of any leading placeholder regardless of how Error evolves.
        rootObject.add(Error.ANALYTICS_EVENTS_KEY, eventsArray);

        // Override appInfo.appVersion to match the per-group recorded version so
        // the envelope is internally consistent with the X-NewRelic-App-Version
        // upload header (cached errors recorded under an older build must
        // self-identify as that older build for backend symbolication). Other
        // appInfo fields (appName, appBuild, bundleId) are intentionally left
        // at the current runtime value — snapshotting those is in a deferred
        // follow-up ticket.
        if (appVersionOverride != null && !appVersionOverride.isEmpty()) {
            JsonObject appInfo = rootObject.getAsJsonObject("appInfo");
            if (appInfo != null) {
                appInfo.add("appVersion", SafeJsonPrimitive.factory(appVersionOverride));
            }
        }

        return rootObject.toString();
    }

    /**
     * Snapshot of a single harvest batch: the JSON payload to send, the exact
     * store IDs included in it, and the {@code appVersion} to use for the
     * {@code X-NewRelic-App-Version} upload header. Pass
     * {@link #deleteErrors(List)} the {@code snapshotIds} after a confirmed
     * successful upload so that only the sent errors are removed — errors that
     * arrived after the snapshot are preserved.
     */
    public static final class HarvestSnapshot {
        public final String payloadJson;
        public final List<String> snapshotIds;
        public final String appVersion;

        HarvestSnapshot(String payloadJson, List<String> snapshotIds, String appVersion) {
            this.payloadJson = payloadJson;
            this.snapshotIds = Collections.unmodifiableList(snapshotIds);
            this.appVersion = appVersion;
        }
    }

    /**
     * Returns one {@link HarvestSnapshot} per distinct {@code appVersionAtRecord}
     * value found across stored errors, or an empty list if there are none.
     * Splitting by version lets each POST set the matching
     * {@code X-NewRelic-App-Version} header so the backend can pick the right
     * ProGuard mapping after an app upgrade.
     *
     * <p>Uses a single atomic store read so the snapshot IDs and payload values
     * across all groups are always derived from the same on-disk view.
     */
    public List<HarvestSnapshot> getStoredJSErrorData() {
        if (jsErrorStore == null) {
            log.warn("JSErrorStore is not initialized");
            return Collections.emptyList();
        }

        Map<String, String> entries;
        lock.readLock().lock();
        try {
            entries = jsErrorStore.fetchAllEntries();
            if (entries == null || entries.isEmpty()) {
                log.debug("No JS errors found in store");
                return Collections.emptyList();
            }
        } catch (Exception e) {
            log.error("Failed to retrieve stored JS error data: " + e.getMessage());
            return Collections.emptyList();
        } finally {
            lock.readLock().unlock();
        }

        // Group entries by their recorded app version. Use LinkedHashMap so the
        // group iteration order is deterministic (oldest-version-seen first).
        final String fallbackAppVersion = currentAppVersion();
        final Map<String, List<String>> valuesByVersion = new LinkedHashMap<>();
        final Map<String, List<String>> idsByVersion = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : entries.entrySet()) {
            String id = entry.getKey();
            String json = entry.getValue();
            String groupKey = extractAppVersion(json);
            if (groupKey == null || groupKey.isEmpty()) {
                groupKey = fallbackAppVersion == null ? "" : fallbackAppVersion;
            }
            List<String> groupValues = valuesByVersion.get(groupKey);
            if (groupValues == null) {
                groupValues = new ArrayList<>();
                valuesByVersion.put(groupKey, groupValues);
                idsByVersion.put(groupKey, new ArrayList<String>());
            }
            groupValues.add(json);
            idsByVersion.get(groupKey).add(id);
        }

        final List<HarvestSnapshot> snapshots = new ArrayList<>(valuesByVersion.size());
        for (Map.Entry<String, List<String>> e : valuesByVersion.entrySet()) {
            String groupVersion = e.getKey();
            // An empty groupVersion means we have no version at all (no fallback
            // was available either). Pass null so both the envelope's appInfo
            // and the sender's header fall back to the current runtime version.
            String snapshotVersion = groupVersion.isEmpty() ? null : groupVersion;
            String payloadJson = buildErrorEnvelope(e.getValue(), snapshotVersion);
            if (payloadJson == null) {
                continue;
            }
            snapshots.add(new HarvestSnapshot(payloadJson, idsByVersion.get(groupVersion), snapshotVersion));
        }

        return snapshots;
    }

    /** Returns the {@code appVersionAtRecord} field from a stored event's JSON, or {@code null} if absent or unparseable. */
    private static String extractAppVersion(String eventJson) {
        if (eventJson == null || eventJson.isEmpty()) {
            return null;
        }
        try {
            JsonElement parsed = JsonParser.parseString(eventJson);
            if (!parsed.isJsonObject()) {
                return null;
            }
            JsonElement v = parsed.getAsJsonObject().get(AnalyticsAttribute.APP_VERSION_AT_RECORD_ATTRIBUTE);
            if (v == null || v.isJsonNull()) {
                return null;
            }
            return v.getAsString();
        } catch (Exception e) {
            return null;
        }
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
