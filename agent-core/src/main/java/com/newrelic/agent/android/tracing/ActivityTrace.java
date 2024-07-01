/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.tracing;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.Measurements;
import com.newrelic.agent.android.TaskQueue;
import com.newrelic.agent.android.activity.NamedActivity;
import com.newrelic.agent.android.harvest.ActivitySighting;
import com.newrelic.agent.android.harvest.ConnectInformation;
import com.newrelic.agent.android.harvest.type.HarvestableArray;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.metric.Metric;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.util.SafeJsonPrimitive;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ActivityTrace extends HarvestableArray {
    public final static String TRACE_VERSION = "1.0";
    public final static int MAX_TRACES = 2000;

    public Trace rootTrace;
    final private ConcurrentHashMap<UUID, Trace> traces = new ConcurrentHashMap<UUID, Trace>();
    private int traceCount = 0;
    final private Set<UUID> missingChildren = Collections.synchronizedSet(new HashSet<UUID>());
    private NamedActivity measuredActivity;

    private long reportAttemptCount = 0;

    public long lastUpdatedAt;
    public long startedAt;
    public ActivitySighting previousActivity;
    private boolean complete = false;

    private final HashMap<String, String> params = new HashMap<String, String>();
    private Map<Sample.SampleType, Collection<Sample>> vitals;

    private final AgentLog log = AgentLogManager.getAgentLog();

    // will be renamed to include activity name during summary harvest.
    // The activity name could change during this instance's lifetime.
    public final Metric networkCountMetric = new Metric(MetricNames.ACTIVITY_NETWORK_METRIC_COUNT_FORMAT);
    public final Metric networkTimeMetric = new Metric(MetricNames.ACTIVITY_NETWORK_METRIC_TIME_FORMAT);

    private static final String SIZE_NORMAL = "NORMAL";

    private static final HashMap<String, String> ENVIRONMENT_TYPE = new HashMap<>() {{
        put("type", "ENVIRONMENT");
    }};
    private static final HashMap<String, String> VITALS_TYPE = new HashMap<>() {{
        put("type", "VITALS");
    }};

    private static final HashMap<String, String> ACTIVITY_HISTORY_TYPE = new HashMap<>() {{
        put("type", "ACTIVITY_HISTORY");
    }};

    public ActivityTrace() {
    }

    public ActivityTrace(Trace rootTrace) {
        this.rootTrace = rootTrace;

        lastUpdatedAt = rootTrace.entryTimestamp;
        startedAt = lastUpdatedAt;

        params.put("traceVersion", TRACE_VERSION);
        // Default for now
        params.put("type", "ACTIVITY");

        // Let the Measurement Engine know we're starting (lock contention may block here)
        measuredActivity = (NamedActivity) Measurements.startActivity(rootTrace.displayName);
        measuredActivity.setStartTime(rootTrace.entryTimestamp);
    }

    public String getId() {
        if (rootTrace == null)
            return null;

        return rootTrace.myUUID.toString();
    }

    public void addTrace(Trace trace) {
        missingChildren.add(trace.myUUID);

        lastUpdatedAt = System.currentTimeMillis();
    }

    public void addCompletedTrace(Trace trace) {
        // Check for network traces right away, before tearing down the trace machine,
        // or excluding the trace by limiting (MAX_TRACES)
        if (trace.getType() == TraceType.NETWORK) {
            networkCountMetric.sample(1.0f);
            networkTimeMetric.sample(trace.getDurationAsSeconds());

            if (rootTrace != null) {
                rootTrace.childExclusiveTime += trace.getDurationAsMilliseconds();
            }
        }
        // Remove the reference to the trace machine so it will be GC'd when ready.
        trace.traceMachine = null;

        missingChildren.remove(trace.myUUID);

        if (traceCount > MAX_TRACES) {
            log.verbose("Maximum trace limit reached, discarding trace " + trace.myUUID);
            return;
        }

        traces.put(trace.myUUID, trace);
        traceCount++;

        // Since there's no well defined end to a trace, we'll just use the timestamp of the last thing we record as the
        // duration of the activity trace.
        if (trace.exitTimestamp > rootTrace.exitTimestamp) {
            rootTrace.exitTimestamp = trace.exitTimestamp;
        }

        log.verbose("Added trace " + trace.myUUID.toString() + " missing children: " + missingChildren.size());

        lastUpdatedAt = System.currentTimeMillis();
    }

    public boolean hasMissingChildren() {
        return !missingChildren.isEmpty();
    }

    public boolean isComplete() {
        return complete;
    }

    public void discard() {
        log.debug("Discarding trace of " + rootTrace.displayName + ":" + rootTrace.myUUID.toString() + "(" + traces.size() + " traces)");

        rootTrace.traceMachine = null;
        complete = true;
        Measurements.endActivityWithoutMeasurement(measuredActivity);
    }

    public void complete() {
        log.debug("Completing trace of " + rootTrace.displayName + ":" + rootTrace.myUUID.toString() + "(" + traces.size() + " traces)");

        // This should be set, but just in case...
        if (rootTrace.exitTimestamp == 0)
            rootTrace.exitTimestamp = System.currentTimeMillis();

        // Don't record this AT if there are no children.
        if (traces.isEmpty()) {
            rootTrace.traceMachine = null;
            complete = true;
            Measurements.endActivityWithoutMeasurement(measuredActivity);

            return;
        }

        // Here we'll inform the Measurement Engine we're all done.
        measuredActivity.setEndTime(rootTrace.exitTimestamp);
        Measurements.endActivity(measuredActivity);

        // Remove the reference to the trace machine so it will be GC'd when ready.
        rootTrace.traceMachine = null;
        complete = true;

        TaskQueue.queue(this);
    }

    public Map<UUID, Trace> getTraces() {
        return traces;
    }

    @Override
    public JsonArray asJsonArray() {
        JsonArray tree = new JsonArray();

        if (!complete) {
            log.verbose("Attempted to serialize trace " + rootTrace.myUUID.toString() + " but it has yet to be finalized");
            return null;
        }

        tree.add(new Gson().toJsonTree(params, GSON_STRING_MAP_TYPE));
        tree.add(SafeJsonPrimitive.factory(rootTrace.entryTimestamp));
        tree.add(SafeJsonPrimitive.factory(rootTrace.exitTimestamp));
        tree.add(SafeJsonPrimitive.factory(rootTrace.displayName));

        JsonArray segments = new JsonArray();
        segments.add(getEnvironment());
        segments.add(traceToTree(rootTrace));
        segments.add(getVitalsAsJson());

        // Add the previous activity if we have one.
        if (previousActivity != null) {
            segments.add(getPreviousActivityAsJson());
        }

        tree.add(segments);

        return tree;
    }

    private JsonArray traceToTree(final Trace trace) {
        JsonArray segment = new JsonArray();

        trace.prepareForSerialization();

        segment.add(new Gson().toJsonTree(trace.getParams(), GSON_STRING_MAP_TYPE));
        segment.add(SafeJsonPrimitive.factory(trace.entryTimestamp));
        segment.add(SafeJsonPrimitive.factory(trace.exitTimestamp));
        segment.add(SafeJsonPrimitive.factory(trace.displayName));

        JsonArray threadData = new JsonArray();
        threadData.add(SafeJsonPrimitive.factory(trace.threadId));
        threadData.add(SafeJsonPrimitive.factory(trace.threadName));

        segment.add(threadData);

        // Useful for debugging
        //segment.add(new JsonPrimitive(trace.UUID.toString()));

        if (trace.getChildren().isEmpty()) {
            segment.add(new JsonArray());
        } else {
            JsonArray children = new JsonArray();

            for (UUID traceUUID : trace.getChildren()) {
                // Since we occasionally serialize a trace with missing children, it's important to check if they exist first.
                Trace childTrace = traces.get(traceUUID);
                if (childTrace != null) {
                    children.add(traceToTree(childTrace));
                }
            }

            segment.add(children);
        }

        return segment;
    }

    private JsonArray getEnvironment() {
        JsonArray environment = new JsonArray();

        environment.add(new Gson().toJsonTree(ENVIRONMENT_TYPE, GSON_STRING_MAP_TYPE));

        // Add the application and device information elements
        ConnectInformation connectInformation = new ConnectInformation(Agent.getApplicationInformation(), Agent.getDeviceInformation());
        environment.addAll(connectInformation.asJsonArray());

        // Add the environment params.  For the moment this includes just the size key.
        HashMap<String, String> environmentParams = new HashMap<String, String>();
        environmentParams.put("size", SIZE_NORMAL);
        environment.add(new Gson().toJsonTree(environmentParams, GSON_STRING_MAP_TYPE));

        return environment;
    }

    public void setVitals(Map<Sample.SampleType, Collection<Sample>> vitals) {
        this.vitals = vitals;
    }

    private JsonArray getVitalsAsJson() {
        JsonArray vitalsJson = new JsonArray();

        vitalsJson.add(new Gson().toJsonTree(VITALS_TYPE, GSON_STRING_MAP_TYPE));

        JsonObject vitalsMap = new JsonObject();

        /* We'll need to reformat the vitals structure a bit here.
           We're looking for something like:
            [{
			  "type": "VITALS"
			}, {
				"MEMORY": [
                    [<timestamp>, <sample value>],...
                ],
                "CPU": [
                    [<timestamp>, <sample value>],...
                ]
            }]
         */
        if (vitals != null) {
            for (Map.Entry<Sample.SampleType, Collection<Sample>> entry : vitals.entrySet()) {
                JsonArray samplesJsonArray = new JsonArray();

                for (Sample sample : entry.getValue()) {
                    // The sampler runs until the end of an ActivityTrace, which may be well beyond the last recorded Trace so we'll cull them here
                    if (sample.getTimestamp() <= lastUpdatedAt)
                        samplesJsonArray.add(sample.asJsonArray());
                }

                vitalsMap.add(entry.getKey().toString(), samplesJsonArray);
            }
        }

        vitalsJson.add(vitalsMap);

        return vitalsJson;
    }

    private JsonArray getPreviousActivityAsJson() {
        final JsonArray historyJson = new JsonArray();

        historyJson.add(new Gson().toJsonTree(ACTIVITY_HISTORY_TYPE, GSON_STRING_MAP_TYPE));
        historyJson.addAll(previousActivity.asJsonArray());

        return historyJson;
    }

    public void setLastUpdatedAt(long lastUpdatedAt) {
        this.lastUpdatedAt = lastUpdatedAt;
    }

    public long getLastUpdatedAt() {
        return lastUpdatedAt;
    }

    public long getReportAttemptCount() {
        return reportAttemptCount;
    }

    public void incrementReportAttemptCount() {
        reportAttemptCount++;
    }

    public String getActivityName() {
        String activityName = "<activity>";
        if (rootTrace != null) {
            activityName = rootTrace.displayName;
            if (activityName != null) {
                final int hashIndex = activityName.indexOf("#");
                if (hashIndex > 0) {
                    activityName = activityName.substring(0, hashIndex);
                }
            }
        }

        return activityName;
    }
}
