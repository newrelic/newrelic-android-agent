/*
 * Copyright (c) 2021-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.crash;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.AgentImpl;
import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.analytics.AnalyticsEvent;
import com.newrelic.agent.android.harvest.ActivityHistory;
import com.newrelic.agent.android.harvest.ActivitySighting;
import com.newrelic.agent.android.harvest.DataToken;
import com.newrelic.agent.android.harvest.Harvest;
import com.newrelic.agent.android.harvest.crash.ApplicationInfo;
import com.newrelic.agent.android.harvest.crash.DeviceInfo;
import com.newrelic.agent.android.harvest.crash.ExceptionInfo;
import com.newrelic.agent.android.harvest.crash.ThreadInfo;
import com.newrelic.agent.android.harvest.type.HarvestableObject;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.tracing.TraceMachine;
import com.newrelic.agent.android.util.SafeJsonPrimitive;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class Crash extends HarvestableObject {
    public static final int PROTOCOL_VERSION = 1;
    public static final int MAX_UPLOAD_COUNT = 3;

    final private UUID uuid;
    final private String appToken;

    protected String buildId;
    protected long timestamp;
    private boolean analyticsEnabled;
    private DeviceInfo deviceInfo;
    private ApplicationInfo applicationInfo;
    private ExceptionInfo exceptionInfo;
    private List<ThreadInfo> threads;
    private ActivityHistory activityHistory;
    private Set<AnalyticsAttribute> sessionAttributes;
    private Collection<AnalyticsEvent> events;
    private int uploadCount;

    public Crash(UUID uuid, String buildId, long timestamp) {
        final AgentImpl agentImpl = Agent.getImpl();

        this.uuid = uuid;
        this.buildId = buildId;
        this.timestamp = timestamp;
        this.appToken = getAppToken();
        this.deviceInfo = new DeviceInfo(agentImpl.getDeviceInformation(), agentImpl.getEnvironmentInformation());
        this.applicationInfo = new ApplicationInfo(agentImpl.getApplicationInformation());
        this.exceptionInfo = new ExceptionInfo();
        this.threads = new ArrayList<ThreadInfo>();
        this.activityHistory = new ActivityHistory(new ArrayList<ActivitySighting>());
        this.sessionAttributes = new HashSet<AnalyticsAttribute>();
        this.events = new HashSet<AnalyticsEvent>();
        this.analyticsEnabled = true;
        this.uploadCount = 0;
    }

    public Crash(Throwable throwable) {
        this(throwable, new HashSet<AnalyticsAttribute>(), new HashSet<AnalyticsEvent>(), false);
    }

    public Crash(Throwable throwable, Set<AnalyticsAttribute> sessionAttributes, Collection<AnalyticsEvent> events, boolean analyticsEnabled) {
        final AgentImpl agentImpl = Agent.getImpl();
        final Throwable cause = getRootCause(throwable);

        this.uuid = UUID.randomUUID();
        this.buildId = getSafeBuildId();
        this.timestamp = System.currentTimeMillis();
        this.appToken = getAppToken();
        this.deviceInfo = new DeviceInfo(agentImpl.getDeviceInformation(), agentImpl.getEnvironmentInformation());
        this.applicationInfo = new ApplicationInfo(agentImpl.getApplicationInformation());
        this.exceptionInfo = new ExceptionInfo(cause);
        this.threads = extractThreads(cause);
        this.activityHistory = TraceMachine.getActivityHistory();
        this.sessionAttributes = sessionAttributes;
        this.events = events;
        this.analyticsEnabled = analyticsEnabled;
        this.uploadCount = 0;
    }

    protected String getAppToken() {
        if (CrashReporter.getInstance() != null) {
            return CrashReporter.getInstance().getAgentConfiguration().getApplicationToken();
        }
        return "<missing app token>";
    }

    public static String getSafeBuildId() {
        String buildId = getBuildId();
        if (buildId == null || buildId.isEmpty()) {
            buildId = Agent.getBuildId();
            // since we're probably crashing, this may never get harvested
            StatsEngine.get().inc(MetricNames.SUPPORTABILITY_CRASH_INVALID_BUILDID);
            if (buildId == null || buildId.isEmpty()) {
                // still invalid, so this crash is useless
                AgentLogManager.getAgentLog().error("Invalid (null or empty) build ID detected! Crash will be ignored by collector.");
            }
        }
        return buildId;
    }

    public static String getBuildId() {
        // The buildId is used to tie symbol maps to crashes
        // For Android this method is rewritten during instrumentation
        // to return a valid UUID build Id.
        // @see ./class-rewriter-gradle/src/main/java/com/newrelic/agent/compile/visitor/NewRelicClassVisitor.java:40:1
        // Delegate to Agent/getBuildId() to return an uninstrumented value
        return Agent.getBuildId();
    }

    public long getTimestamp() {
        return timestamp;
    }

    public UUID getUuid() {
        return uuid;
    }

    public ExceptionInfo getExceptionInfo() {
        return exceptionInfo;
    }

    public void setSessionAttributes(Set<AnalyticsAttribute> sessionAttributes) {
        this.sessionAttributes = sessionAttributes;
    }

    public Set<AnalyticsAttribute> getSessionAttributes() {
        return sessionAttributes;
    }

    public void setAnalyticsEvents(Collection<AnalyticsEvent> events) {
        this.events = events;
    }

    @Override
    public JsonObject asJsonObject() {
        final JsonObject data = new JsonObject();

        data.add("protocolVersion", new JsonPrimitive(PROTOCOL_VERSION));
        data.add("platform", new JsonPrimitive("Android"));
        data.add("uuid", SafeJsonPrimitive.factory(uuid.toString()));
        data.add("buildId", SafeJsonPrimitive.factory(buildId));
        data.add("timestamp", SafeJsonPrimitive.factory(timestamp));
        data.add("appToken", SafeJsonPrimitive.factory(appToken));
        data.add("deviceInfo", deviceInfo.asJsonObject());
        data.add("appInfo", applicationInfo.asJsonObject());
        data.add("exception", exceptionInfo.asJsonObject());
        data.add("threads", getThreadsAsJson());
        data.add("activityHistory", activityHistory.asJsonArrayWithoutDuration());

        // Always send session analytics
        JsonObject attributeObject = new JsonObject();
        if (sessionAttributes != null) {
            for (AnalyticsAttribute attribute : sessionAttributes) {
                attributeObject.add(attribute.getName(), attribute.asJsonElement());
            }
        }
        data.add("sessionAttributes", attributeObject);

        // Always send session events
        JsonArray eventArray = new JsonArray();
        if (events != null) {
            for (AnalyticsEvent event : events) {
                eventArray.add(event.asJsonObject());
            }
        }
        data.add("analyticsEvents", eventArray);

        final DataToken dataToken = Harvest.getHarvestConfiguration().getDataToken();
        if (dataToken != null) {
            data.add("dataToken", dataToken.asJsonArray());
        }

        return data;
    }

    public static Crash crashFromJsonString(String json) {
        final JsonElement element = new JsonParser().parse(json);
        final JsonObject crashObject = element.getAsJsonObject();

        final String uuid = crashObject.get("uuid").getAsString();
        final String buildIdentifier = crashObject.get("buildId").getAsString();
        final long timestamp = crashObject.get("timestamp").getAsLong();

        Crash crash = new Crash(UUID.fromString(uuid), buildIdentifier, timestamp);

        crash.deviceInfo = DeviceInfo.newFromJson(crashObject.get("deviceInfo").getAsJsonObject());
        crash.applicationInfo = ApplicationInfo.newFromJson(crashObject.get("appInfo").getAsJsonObject());
        crash.exceptionInfo = ExceptionInfo.newFromJson(crashObject.get("exception").getAsJsonObject());
        crash.threads = crash.newListFromJson(crashObject.get("threads").getAsJsonArray());
        crash.activityHistory = ActivityHistory.newFromJson(crashObject.get("activityHistory").getAsJsonArray());
        crash.analyticsEnabled = crashObject.has("sessionAttributes") || crashObject.has("analyticsEvents");

        if (crashObject.has("sessionAttributes")) {
            final Set<AnalyticsAttribute> sessionAttributes = AnalyticsAttribute.newFromJson(crashObject.get("sessionAttributes").getAsJsonObject());
            crash.setSessionAttributes(sessionAttributes);
        }

        if (crashObject.has("analyticsEvents")) {
            Collection<AnalyticsEvent> events = AnalyticsEvent.newFromJson(crashObject.get("analyticsEvents").getAsJsonArray());
            crash.setAnalyticsEvents(events);
        }

        if (crashObject.has("uploadCount")) {
            crash.uploadCount = crashObject.get("uploadCount").getAsInt();
        }

        return crash;
    }

    protected Throwable getRootCause(Throwable throwable) {
        try {
            if (throwable != null) {
                final Throwable cause = throwable.getCause();

                if (cause == null) {
                    return throwable;
                } else {
                    return getRootCause(cause);
                }
            }
        } catch (Exception e) {
            // RuntimeException thrown on: Duplicate found in causal chain so cropping to prevent loop
            if (throwable != null) {
                return throwable;       // return the last known throwable
            }
        }

        return new Throwable("Unknown cause");
    }

    protected JsonArray getThreadsAsJson() {
        final JsonArray data = new JsonArray();

        if (threads != null) {
            for (ThreadInfo thread : threads) {
                data.add(thread.asJsonObject());
            }
        }

        return data;
    }

    public void incrementUploadCount() {
        uploadCount++;
    }

    public int getUploadCount() {
        return uploadCount;
    }

    public boolean isStale() {
        return (uploadCount >= MAX_UPLOAD_COUNT);
    }

    protected List<ThreadInfo> extractThreads(Throwable throwable) {
        return new ThreadInfo(throwable).allThreads();
    }

    protected List<ThreadInfo> newListFromJson(JsonArray jsonArray) {
        return new ThreadInfo().newListFromJson(jsonArray);
    }
}
