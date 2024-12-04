/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.analytics;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.background.ApplicationStateMonitor;
import com.newrelic.agent.android.harvest.type.HarvestableObject;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.stats.StatsEngine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class AnalyticsEvent extends HarvestableObject {
    protected final static AgentLog log = AgentLogManager.getAgentLog();

    public static final String EVENT_TYPE_MOBILE = "Mobile";
    public static final String EVENT_TYPE_MOBILE_REQUEST = "MobileRequest";
    public static final String EVENT_TYPE_MOBILE_REQUEST_ERROR = "MobileRequestError";
    public static final String EVENT_TYPE_MOBILE_BREADCRUMB = "MobileBreadcrumb";
    public static final String EVENT_TYPE_MOBILE_CRASH = "MobileCrash";
    public static final String EVENT_TYPE_MOBILE_USER_ACTION = "MobileUserAction";
    public static final String EVENT_TYPE_MOBILE_APPLICATION_EXIT = "MobileApplicationExit";

    // Same as AnalyticsAttribute.ATTRIBUTE_NAME_MAX_LENGTH
    public static final int EVENT_NAME_MAX_LENGTH = 255;

    // The current limit for total number of eventType values is 250 per sub-account in a given 24-hour time period.
    public static final int EVENT_TYPE_LIMIT = 250;

    // Use event type as name
    public static final String EVENT_NAME_IS_TYPE = null;

    protected String uuid;
    protected String name;
    protected long timestamp;
    protected AnalyticsEventCategory category;
    protected String eventType;
    protected Set<AnalyticsAttribute> attributeSet = Collections.synchronizedSet(new HashSet<AnalyticsAttribute>());

    protected final static AnalyticsValidator validator = new AnalyticsValidator();

    public AnalyticsEvent(String name) {
        this(name, AnalyticsEventCategory.Custom, null, null);
    }

    protected AnalyticsEvent(String name, AnalyticsEventCategory category) {
        this(name, category, null, null);
    }

    protected AnalyticsEvent(String name, AnalyticsEventCategory category, String eventType, Set<AnalyticsAttribute> initialAttributeSet) {
        this(name, category, eventType, System.currentTimeMillis(), initialAttributeSet);
    }

    AnalyticsEvent(final AnalyticsEvent analyticsEvent) {
        this(analyticsEvent.name, analyticsEvent.category, analyticsEvent.eventType,
                analyticsEvent.timestamp, analyticsEvent.attributeSet);
    }

    AnalyticsEvent(String name, AnalyticsEventCategory category, String eventType, long timeStamp, Set<AnalyticsAttribute> initialAttributeSet) {
        this.uuid = UUID.randomUUID().toString();
        this.name = name;
        this.timestamp = timeStamp;
        this.category = validator.toValidCategory(category);
        this.eventType = validator.toValidEventType(eventType);

        if (initialAttributeSet != null) {
            for (AnalyticsAttribute attribute : initialAttributeSet) {
                if (validator.isValidKeyName(attribute.getName())) {
                    this.attributeSet.add(new AnalyticsAttribute(attribute));
                }
            }
        }

        // Ensure that the attribute set contains the "name" attribute, and that it is identical the name of the event
        if (validator.isValidEventName(name)) {
            this.attributeSet.add(new AnalyticsAttribute(AnalyticsAttribute.EVENT_NAME_ATTRIBUTE, this.name));
        }

        this.attributeSet.add(new AnalyticsAttribute(AnalyticsAttribute.EVENT_TIMESTAMP_ATTRIBUTE, String.valueOf(this.timestamp)));
        this.attributeSet.add(new AnalyticsAttribute(AnalyticsAttribute.EVENT_CATEGORY_ATTRIBUTE, this.category.name()));
        this.attributeSet.add(new AnalyticsAttribute(AnalyticsAttribute.EVENT_TYPE_ATTRIBUTE, this.eventType));

        //Offline Storage
        if (FeatureFlag.featureEnabled(FeatureFlag.OfflineStorage)) {
            if (!Agent.hasReachableNetworkConnection(null)) {
                this.attributeSet.add(new AnalyticsAttribute(AnalyticsAttribute.OFFLINE_NAME_ATTRIBUTE, true));
                StatsEngine.notice().inc(MetricNames.OFFLINE_STORAGE_EVENT_COUNT);
            }
        }

        //Background Reporting
        if (FeatureFlag.featureEnabled(FeatureFlag.BackgroundReporting)) {
            if (ApplicationStateMonitor.isAppInBackground()) {
                this.attributeSet.add(new AnalyticsAttribute(AnalyticsAttribute.BACKGROUND_ATTRIBUTE_NAME, true));
                StatsEngine.notice().inc(MetricNames.BACKGROUND_EVENT_COUNT);
            }
        }
    }

    /**
     * Add or replace the event's attributes with the passed set
     *
     * @param newAttributes
     */
    public void addAttributes(Set<AnalyticsAttribute> newAttributes) {
        if (newAttributes != null) {
            for (AnalyticsAttribute attribute : newAttributes) {
                if (!(validator.isValidAttribute(attribute) && attributeSet.add(attribute))) {
                    log.error("Failed to add attribute " + attribute.getName() + " to event " + getName() +
                            ": the attribute is invalid or the event already contains that attribute.");
                }
            }
        }
    }

    public String getName() {
        return name;
    }

    public AnalyticsEventCategory getCategory() {
        return category;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getEventType() {
        return eventType;
    }

    public String getEventUUID() {
        return uuid;
    }

    public void setEventUUID(String uuid) {
        this.uuid = uuid;
    }

    @Override
    public JsonObject asJsonObject() {
        final JsonObject data = new JsonObject();
        synchronized (this) {
            for (AnalyticsAttribute attribute : attributeSet) {
                data.add(attribute.getName(), attribute.asJsonElement());
            }
        }
        return data;
    }

    /**
     * Returns an immutable set of the attributes bound to this event.
     *
     * @return an immutable set of the attributes bound to this event.
     */
    public Collection<AnalyticsAttribute> getAttributeSet() {
        return Collections.unmodifiableCollection(attributeSet);
    }

    /**
     * Returns a mutable set of the attributes bound to this event.
     *
     * @return a mutable set of the attributes bound to this event.
     */
    public Collection<AnalyticsAttribute> getMutableAttributeSet() {
        Set<AnalyticsAttribute> collection = Collections.checkedSet(attributeSet, AnalyticsAttribute.class);
        return collection;
    }

    /**
     * Restore an AnalyticsEvent from Json representation.
     *
     * @param analyticsEventJson
     * @return
     */
    public static AnalyticsEvent newFromJson(JsonObject analyticsEventJson) {
        final Iterator<Map.Entry<String, JsonElement>> entry = analyticsEventJson.entrySet().iterator();

        String name = null;
        String eventType = null;
        AnalyticsEventCategory category = null;
        long timestamp = 0;
        Set<AnalyticsAttribute> attributeSet = new HashSet<AnalyticsAttribute>();

        while (entry.hasNext()) {
            Map.Entry<String, JsonElement> elem = entry.next();
            String key = elem.getKey();

            if (key.equalsIgnoreCase(AnalyticsAttribute.EVENT_NAME_ATTRIBUTE)) {
                name = elem.getValue().getAsString();
            } else if (key.equalsIgnoreCase(AnalyticsAttribute.EVENT_CATEGORY_ATTRIBUTE)) {
                category = AnalyticsEventCategory.fromString(elem.getValue().getAsString());
            } else if (key.equalsIgnoreCase(AnalyticsAttribute.EVENT_TYPE_ATTRIBUTE)) {
                eventType = elem.getValue().getAsString();
            } else if (key.equalsIgnoreCase(AnalyticsAttribute.EVENT_TIMESTAMP_ATTRIBUTE)) {
                timestamp = elem.getValue().getAsLong();
            } else {
                JsonPrimitive value = elem.getValue().getAsJsonPrimitive();
                if (value.isString()) {
                    attributeSet.add(new AnalyticsAttribute(key, value.getAsString(), false));
                } else if (value.isBoolean()) {
                    attributeSet.add(new AnalyticsAttribute(key, value.getAsBoolean(), false));
                } else if (value.isNumber()) {
                    attributeSet.add(new AnalyticsAttribute(key, value.getAsFloat(), false));
                }
            }
        }

        return new AnalyticsEvent(name, category, eventType, timestamp, attributeSet);
    }

    public static AnalyticsEvent eventFromJsonString(String uuid, String eventString) {
        JsonObject eventObj = new Gson().fromJson(eventString, JsonObject.class);
        AnalyticsEvent event = newFromJson(eventObj);
        event.setEventUUID(uuid);
        return event;
    }

    /**
     * Restore an AnalyticsEvent collection from Json representation.
     *
     * @param analyticsEventsJson
     * @return Collection<AnalyticsEvent>
     */
    public static Collection<AnalyticsEvent> newFromJson(JsonArray analyticsEventsJson) {
        ArrayList<AnalyticsEvent> events = new ArrayList<AnalyticsEvent>();

        Iterator<JsonElement> entry = analyticsEventsJson.iterator();
        while (entry.hasNext()) {
            JsonElement e = entry.next();
            events.add(newFromJson(e.getAsJsonObject()));
        }

        return events;
    }

    public boolean isValid() {
        return isValid(name, eventType);
    }

    static boolean isValid(final String eventName, final String eventType) {
        return validator.isValidEventName(eventName) && (validator.isValidEventType(eventType) && !validator.isReservedEventType(eventType));
    }
}
