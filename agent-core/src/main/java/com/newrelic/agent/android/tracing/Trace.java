/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.tracing;

import com.newrelic.agent.android.instrumentation.MetricCategory;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.util.Util;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Trace {
    private static final String CATEGORY_PARAMETER = "category";

    private static final AgentLog log = AgentLogManager.getAgentLog();
    // UUIDs are used for internal tracking
    final public UUID parentUUID;
    final public UUID myUUID = new UUID(Util.getRandom().nextLong(), Util.getRandom().nextLong());

    public long entryTimestamp = 0;
    public long exitTimestamp = 0;
    public long exclusiveTime = 0;
    public long childExclusiveTime = 0;

    public String metricName;
    public String metricBackgroundName;
    public String displayName;
    public String scope;

    public long threadId = 0;
    // We default to "main" here just in case the android interface isn't up yet and we're unable to get the real name
    // of the thread.
    public String threadName = "main";

    // We allocated these as needed for performance reasons.
    private volatile Map<String, Object> params;
    private List<String> rawAnnotationParams;
    private volatile Set<UUID> children;

    private TraceType type = TraceType.TRACE;
    private boolean isComplete = false;

    public TraceMachine traceMachine;

    public Trace() {
        parentUUID = null;
    }

    public Trace(String displayName, UUID parentUUID, TraceMachine traceMachine) {
        this.displayName = displayName;
        this.parentUUID = parentUUID;
        this.traceMachine = traceMachine;
    }

    public void addChild(Trace trace) {
        // Employ double-checked locking
        if (children == null) {
            synchronized (this) {
                if (children == null)
                    children = Collections.synchronizedSet(new HashSet<UUID>());
            }
        }

        children.add(trace.myUUID);
    }

    public Set<UUID> getChildren() {
        // Employ double-checked locking
        if (children == null) {
            synchronized (this) {
                if (children == null)
                    children = Collections.synchronizedSet(new HashSet<UUID>());
            }
        }
        return children;
    }

    public Map<String, Object> getParams() {
        // Employ double-checked locking
        if (params == null) {
            synchronized (this) {
                if (params == null)
                    params = new ConcurrentHashMap<String, Object>();
            }
        }

        return params;
    }

    public void setAnnotationParams(List<String> rawAnnotationParams) {
        this.rawAnnotationParams = rawAnnotationParams;
    }

    public Map<String, Object> getAnnotationParams() {
        HashMap<String, Object> annotationParams = new HashMap<String, Object>();

        if (rawAnnotationParams != null && rawAnnotationParams.size() > 0) {
            Iterator<String> i = rawAnnotationParams.iterator();

            while (i.hasNext()) {
                // Elements will always come in triples.
                String paramName = i.next();
                String paramClass = i.next();
                String paramValue = i.next();

                Object param = createParameter(paramName, paramClass, paramValue);
                if (param != null) {
                    annotationParams.put(paramName, param);
                }
            }
        }

        return annotationParams;
    }

    public boolean isComplete() {
        return isComplete;
    }

    public void complete() throws TracingInactiveException {
        if (isComplete) {
            log.warning("Attempted to double complete trace " + myUUID.toString());
            return;
        }

        // This should be set, but just in case...
        if (exitTimestamp == 0)
            exitTimestamp = System.currentTimeMillis();

        exclusiveTime = getDurationAsMilliseconds() - childExclusiveTime;

        isComplete = true;

        try {
            traceMachine.storeCompletedTrace(this);
        } catch (NullPointerException e) {
            throw new TracingInactiveException();
        }
    }

    public void prepareForSerialization() {
        getParams().put("type", type.toString());
    }

    public TraceType getType() {
        return type;
    }

    public void setType(TraceType type) {
        this.type = type;
    }

    public long getDurationAsMilliseconds() {
        return exitTimestamp - entryTimestamp;
    }

    public float getDurationAsSeconds() {
        return (float) (exitTimestamp - entryTimestamp) / 1000.0f;
    }

    public MetricCategory getCategory() {
        if (!getAnnotationParams().containsKey(CATEGORY_PARAMETER))
            return null;
        Object category = getAnnotationParams().get(CATEGORY_PARAMETER);
        if (!(category instanceof MetricCategory)) {
            log.error("Category annotation parameter is not of type MetricCategory");
            return null;
        }
        return (MetricCategory)category;
    }

    private static Object createParameter(String parameterName, String parameterClass, String parameterValue) {
        // Resolve the parameter class.
        Class clazz;
        try {
            clazz = Class.forName(parameterClass);
        } catch (ClassNotFoundException e) {
            log.error("Unable to resolve parameter class in enterMethod: " + e.getMessage(), e);
            return null;
        }

        // Handle the classes we know about.
        if (MetricCategory.class == clazz) {
            return MetricCategory.valueOf(parameterValue);
        }

        if (String.class == clazz) {
            return parameterValue;
        }

        return null;
    }
}
