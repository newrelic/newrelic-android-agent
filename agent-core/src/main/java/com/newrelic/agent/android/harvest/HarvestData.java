/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.harvest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.analytics.AnalyticsEvent;
import com.newrelic.agent.android.harvest.type.HarvestableArray;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.stats.StatsEngine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Information used in the {@code data} phase of harvesting.
 * <p>
 * The following entities are included in HarvestData:
 * <ul>
 *     <li>A {@link DataToken} which holds application identifying information.</li>
 *     <li>{@link DeviceInformation} describing device hardware.</li>
 *     <li>The time since last harvest.</li>
 *     <li>{@link HttpTransactions} which contains HTTP request metrics.</li>
 *     <li>{@link MachineMeasurements} for resource metrics such as CPU and Memory.</li>
 * </ul>
 */
public class HarvestData extends HarvestableArray {
    private static final AgentLog log = AgentLogManager.getAgentLog();

    private DataToken dataToken;
    private DeviceInformation deviceInformation;
    private double harvestTimeDelta;
    private HttpTransactions httpTransactions;
    private MachineMeasurements machineMeasurements;
    private ActivityTraces activityTraces;
    private AgentHealth agentHealth;
    private Set<AnalyticsAttribute> sessionAttributes;
    private Collection<AnalyticsEvent> analyticsEvents;
    private boolean analyticsEnabled;

    public HarvestData() {
        dataToken = new DataToken();
        httpTransactions = new HttpTransactions();
        activityTraces = new ActivityTraces();
        machineMeasurements = new MachineMeasurements();
        deviceInformation = Agent.getDeviceInformation();
        agentHealth = new AgentHealth();
        sessionAttributes = new HashSet<AnalyticsAttribute>();
        analyticsEvents = new ArrayList<AnalyticsEvent>();
        analyticsEnabled = false;
    }

    /**
     * Creates JSON suitable for a harvest {@code data} post.
     *
     * @return A {@code JsonArray} suitable vor a harvest {@code data} post.
     */
    @Override
    public JsonArray asJsonArray() {
        JsonArray array = new JsonArray();
        array.add(dataToken.asJson());
        array.add(deviceInformation.asJson());
        array.add(new JsonPrimitive(harvestTimeDelta));
        array.add(httpTransactions.asJson());
        array.add(machineMeasurements.asJson());

        JsonElement activityTracesElement = activityTraces.asJson();

        // Check the length of the Activity Trace and ensure it's under our limit.
        String activityTraceJson = activityTracesElement.toString();
        if (activityTraceJson.length() < Harvest.getHarvestConfiguration().getActivity_trace_max_size()) {
            array.add(activityTracesElement);
        } else {
            StatsEngine.get().sample(MetricNames.SUPPORTABILITY_TRACES_DROPPED, (float) activityTraceJson.length());
            array.add(new JsonArray());
        }

        array.add(agentHealth.asJson());

        if (analyticsEnabled) {
            JsonObject sessionAttrObj = new JsonObject();
            for (AnalyticsAttribute attribute : sessionAttributes) {
                switch (attribute.getAttributeDataType()) {
                    case STRING:
                        sessionAttrObj.addProperty(attribute.getName(), attribute.getStringValue());
                        break;
                    case DOUBLE:
                        sessionAttrObj.addProperty(attribute.getName(), attribute.getDoubleValue());
                        break;
                    case BOOLEAN:
                        sessionAttrObj.addProperty(attribute.getName(), attribute.getBooleanValue());
                        break;
                }
            }
            array.add(sessionAttrObj);

            JsonArray events = new JsonArray();
            for (AnalyticsEvent event : analyticsEvents) {
                events.add(event.asJsonObject());
            }
            array.add(events);
        }

        return array;
    }

    /**
     * Is this harvest data up-to-date?
     *
     * @return false if we need to make another collector connect
     */
    public boolean isValid() {
        return dataToken.isValid();
    }

    public void reset() {
        httpTransactions.clear();
        activityTraces.clear();
        machineMeasurements.clear();
        agentHealth.clear();
        sessionAttributes.clear();
        analyticsEvents.clear();
    }

    public void setDataToken(DataToken dataToken) {
        if (dataToken == null)
            return;
        this.dataToken = dataToken;
    }

    public void setDeviceInformation(DeviceInformation deviceInformation) {
        this.deviceInformation = deviceInformation;
    }

    public void setHarvestTimeDelta(double harvestTimeDelta) {
        this.harvestTimeDelta = harvestTimeDelta;
    }

    public void setHttpTransactions(HttpTransactions httpTransactions) {
        this.httpTransactions = httpTransactions;
    }

    public void setMachineMeasurements(MachineMeasurements machineMeasurements) {
        this.machineMeasurements = machineMeasurements;
    }

    public void setActivityTraces(ActivityTraces activityTraces) {
        this.activityTraces = activityTraces;
    }

    public Set<AnalyticsAttribute> getSessionAttributes() {
        return sessionAttributes;
    }

    public void setSessionAttributes(Set<AnalyticsAttribute> sessionAttributes) {
        log.debug("HarvestData.setSessionAttributes invoked with attribute set " + sessionAttributes);
        this.sessionAttributes = new HashSet<AnalyticsAttribute>(sessionAttributes);
    }

    public Collection<AnalyticsEvent> getAnalyticsEvents() {
        return analyticsEvents;
    }

    public void setAnalyticsEvents(Collection<AnalyticsEvent> analyticsEvents) {
        this.analyticsEvents = new ArrayList<>(analyticsEvents);
    }

    public DeviceInformation getDeviceInformation() {
        return deviceInformation;
    }

    public HttpTransactions getHttpTransactions() {
        return httpTransactions;
    }

    public MachineMeasurements getMetrics() {
        return machineMeasurements;
    }

    public ActivityTraces getActivityTraces() {
        return activityTraces;
    }

    public AgentHealth getAgentHealth() {
        return agentHealth;
    }

    public DataToken getDataToken() {
        return dataToken;
    }

    public boolean isAnalyticsEnabled() {
        return analyticsEnabled;
    }

    public void setAnalyticsEnabled(boolean analyticsEnabled) {
        this.analyticsEnabled = analyticsEnabled;
    }

    @Override
    public String toString() {
        return "HarvestData{" +
                "\n\tdataToken=" + dataToken +
                ", \n\tdeviceInformation=" + deviceInformation +
                ", \n\tharvestTimeDelta=" + harvestTimeDelta +
                ", \n\thttpTransactions=" + httpTransactions +
                ", \n\tmachineMeasurements=" + machineMeasurements +
                ", \n\tactivityTraces=" + activityTraces +
                ", \n\tsessionAttributes=" + sessionAttributes +
                ", \n\tanalyticsAttributes=" + analyticsEvents +
                '}';
    }
}