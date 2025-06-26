package com.newrelic.agent.android.aei;

import static com.newrelic.agent.android.aei.AEISessionMapper.gson;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.AgentImpl;
import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.background.ApplicationStateMonitor;
import com.newrelic.agent.android.crash.CrashReporter;
import com.newrelic.agent.android.harvest.DataToken;
import com.newrelic.agent.android.harvest.Harvest;
import com.newrelic.agent.android.harvest.crash.ApplicationInfo;
import com.newrelic.agent.android.harvest.crash.DeviceInfo;
import com.newrelic.agent.android.harvest.type.HarvestableObject;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.util.SafeJsonPrimitive;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;


public class Error extends HarvestableObject {
    public static final int PROTOCOL_VERSION = 1;
    public static final int MAX_UPLOAD_COUNT = 3;

    final private String agentName;
    final private String agentVersion;

    protected String buildId;
    protected long timestamp;
    private DeviceInfo deviceInfo;
    private ApplicationInfo applicationInfo;
    private Set<AnalyticsAttribute> sessionAttributes;
    private HashMap<String, Object> event;
    private DataToken dataToken;
    private static final Gson gson = new GsonBuilder().create();


    public Error(String buildId, long timestamp) {
        final AgentImpl agentImpl = Agent.getImpl();

        this.agentVersion = Agent.getVersion();
        this.buildId = buildId;
        this.timestamp = timestamp;
        this.deviceInfo = new DeviceInfo(agentImpl.getDeviceInformation(), agentImpl.getEnvironmentInformation());
        this.applicationInfo = new ApplicationInfo(agentImpl.getApplicationInformation());
        this.sessionAttributes = new HashSet<>();
        this.event = new HashMap<>();
        this.agentName = agentImpl.getDeviceInformation().getAgentName();
        this.dataToken = Harvest.getHarvestConfiguration().getDataToken();
    }


    public Error(Set<AnalyticsAttribute> sessionAttributes, HashMap<String, Object> events) {
        this(sessionAttributes, events, null);
    }

    public Error(Set<AnalyticsAttribute> sessionAttributes, HashMap<String, Object> event, AEISessionMapper.AEISessionMeta sessionMeta) {
        final AgentImpl agentImpl = Agent.getImpl();

        this.agentVersion = Agent.getVersion();
        this.buildId = getSafeBuildId();
        this.timestamp = System.currentTimeMillis();
        this.deviceInfo = new DeviceInfo(agentImpl.getDeviceInformation(), agentImpl.getEnvironmentInformation());
        this.applicationInfo = new ApplicationInfo(agentImpl.getApplicationInformation());
        this.sessionAttributes = getErrorSessionAttributes(sessionAttributes);
        this.agentName = agentImpl.getDeviceInformation().getAgentName();
        this.event = event;
        this.dataToken = Harvest.getHarvestConfiguration().getDataToken();

        if (sessionMeta != null) {
            this.dataToken.setAgentId(sessionMeta.realAgentId);
        }
    }

    protected String getAppToken() {
        if (CrashReporter.getInstance() != null) {
            return CrashReporter.getInstance().getAgentConfiguration().getApplicationToken();
        }
        return "<missing app token>";
    }

    protected DataToken getDataToken() {
        return this.dataToken;
    }

    public void setDataToken(DataToken dataToken) {
        this.dataToken = dataToken;
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


    public void setSessionAttributes(Set<AnalyticsAttribute> sessionAttributes) {
        this.sessionAttributes = getErrorSessionAttributes(sessionAttributes);
    }

    public Set<AnalyticsAttribute> getSessionAttributes() {
        return sessionAttributes;
    }

    public void setAnalyticsEvents(HashMap<String, Object> event) {
        this.event = event;
    }

    public boolean getIsObfuscated() {
        return Agent.getIsObfuscated();
    }

    public Set<AnalyticsAttribute> getErrorSessionAttributes(Set<AnalyticsAttribute> sessionAttributes) {
        if (sessionAttributes == null) {
            return null;
        }
        Set<AnalyticsAttribute> attrs = new HashSet<>(sessionAttributes);
        //Offline Storage
        if (FeatureFlag.featureEnabled(FeatureFlag.OfflineStorage)) {
            if (!Agent.hasReachableNetworkConnection(null)) {
                attrs.add(new AnalyticsAttribute(AnalyticsAttribute.OFFLINE_NAME_ATTRIBUTE, true));
                StatsEngine.notice().inc(MetricNames.OFFLINE_STORAGE_CRASH_COUNT);
            }
        }

        //Background Reporting
        if (FeatureFlag.featureEnabled(FeatureFlag.BackgroundReporting)) {
            if (ApplicationStateMonitor.isAppInBackground()) {
                attrs.add(new AnalyticsAttribute(AnalyticsAttribute.BACKGROUND_ATTRIBUTE_NAME, true));
                StatsEngine.notice().inc(MetricNames.BACKGROUND_CRASH_COUNT);
            }
        }

        //add AccountId as Session Attribute for Debug Purpose
        attrs.add(new AnalyticsAttribute(AnalyticsAttribute.HARVEST_ACCOUNT_ID_ATTRIBUTE, Harvest.getHarvestConfiguration().getAccount_id()));

        return Collections.unmodifiableSet(attrs);
    }

    @Override
    public JsonObject asJsonObject() {
        final JsonObject data = new JsonObject();

        data.add("agentName", new JsonPrimitive(agentName));
        data.add("agentVersion", new JsonPrimitive(agentVersion));
        data.add("protocolVersion", new JsonPrimitive(PROTOCOL_VERSION));
        data.add("platform", new JsonPrimitive("Android"));
        data.add("buildId", SafeJsonPrimitive.factory(buildId));
        data.add("timestamp", SafeJsonPrimitive.factory(timestamp));
        data.add("deviceInfo", deviceInfo.asJsonObject());
        data.add("appInfo", applicationInfo.asJsonObject());

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
        if (event != null) {
            eventArray.add(gson.toJsonTree(event));
        }
        data.add("analyticsEvents", eventArray);
        data.add("dataToken", dataToken.asJsonArray());

        return data;
    }

    public static Error ErrorFromJsonString(String json) {
        final JsonObject errorObject = JsonParser.parseString(json).getAsJsonObject();

        final String buildIdentifier = errorObject.get("buildId").getAsString();
        final long timestamp = errorObject.get("timestamp").getAsLong();

        Error error = new Error(buildIdentifier, timestamp);

        error.deviceInfo = DeviceInfo.newFromJson(errorObject.get("deviceInfo").getAsJsonObject());
        error.applicationInfo = ApplicationInfo.newFromJson(errorObject.get("appInfo").getAsJsonObject());

        if (errorObject.has("sessionAttributes")) {
            final Set<AnalyticsAttribute> sessionAttributes = AnalyticsAttribute.newFromJson(errorObject.get("sessionAttributes").getAsJsonObject());
            error.setSessionAttributes(sessionAttributes);
        }

        if (errorObject.has("analyticsEvents")) {
            List<HashMap<String, Object>> events = new ArrayList<>();
            Iterator<JsonElement> entry = errorObject.get("analyticsEvents").getAsJsonArray().iterator();
            while (entry.hasNext()) {
                JsonElement e = entry.next();
                events.add(gson.fromJson(e, new com.google.gson.reflect.TypeToken<HashMap<String, Object>>(){}.getType()));
            }
            if (!events.isEmpty()) {
                error.setAnalyticsEvents(events.get(0));
            }
        }
        error.dataToken = DataToken.newFromJson(errorObject.get("dataToken").getAsJsonArray());
        return error;
    }

}
