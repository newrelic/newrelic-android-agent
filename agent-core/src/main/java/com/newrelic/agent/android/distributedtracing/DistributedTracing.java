/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.distributedtracing;

import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.agentdata.AgentDataController;
import com.newrelic.agent.android.harvest.HarvestConfiguration;
import com.newrelic.agent.android.instrumentation.TransactionState;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The latest W3C New Relic agents send two required headers, and by default, the header of the
 * prior New Relic agent:
 *
 * W3C (traceparent): The primary header that identifies the entire trace (trace ID) and the calling
 * service (span id).
 * W3C (tracestate): A required header that carries vendor-specific information and tracks where a
 * trace has been.
 * New Relic (newrelic): The original, proprietary header that is still sent to maintain backward
 * compatibility with prior New Relic agents.
 *
 * This combination of three headers allows traces to be propagated across services instrumented with
 * these types of agents:
 *  * W3C New Relic agents
 *  * Non-W3C New Relic agents
 *  * W3C Trace Context-compatible agents
 */
public class DistributedTracing implements TraceFacade, TraceListener {
    static final DistributedTracing instance = new DistributedTracing();
    static final AgentLog log = AgentLogManager.getAgentLog();

    public static final String NR_GUID_ATTRIBUTE = "guid";
    public static final String NR_ID_ATTRIBUTE = "id";
    public static final String NR_TRACE_ID_ATTRIBUTE = "trace.id";
    public static final String NR_SPAN_ID_ATTRIBUTE = "span.id";
    public static final String ACTION_TYPE_ATTRIBUTE = "actionType";

    AtomicReference<TraceListener> traceListener = new AtomicReference<TraceListener>(this);

    public static final DistributedTracing getInstance() {
        return instance;
    }

    @Override
    public TraceContext startTrace(final TransactionState transactionState) {
        final Map<String, String> requestContext = new HashMap<String, String>() {{
            put("url", transactionState.getUrl());
            put("httpMethod", transactionState.getHttpMethod());
            put("thread.id", Long.toString(Thread.currentThread().getId()));
        }};

        TraceContext traceContext = TraceContext.createTraceContext(requestContext);
        invokeListeners(traceContext);

        return traceContext;
    }

    @Override
    public void setConfiguration(TraceConfiguration traceConfiguration) {
        TraceConfiguration.setInstance(traceConfiguration);
    }

    @Override
    public void setTraceListener(TraceListener listener) {
        if (FeatureFlag.featureEnabled(FeatureFlag.DistributedTracing)) {
            if (listener == null) {
                traceListener.set(this);
            } else {
                traceListener.set(listener);
            }
        }
    }

    public void setConfiguration(HarvestConfiguration harvestConfiguration) {
        TraceConfiguration.getInstance().setConfiguration(harvestConfiguration);
    }

    @Override
    public void onTraceCreated(Map<String, String> requestContext) {
    }

    @Override
    public void onSpanCreated(Map<String, String> requestContext) {
    }

    /**
     * Call installed listeners with request context of the new trace
     *
     * @param traceContext
     */
    private void invokeListeners(TraceContext traceContext) {
        try {
            traceContext.requestContext.put(DistributedTracing.NR_TRACE_ID_ATTRIBUTE, traceContext.traceId);
            instance.traceListener.get().onTraceCreated(traceContext.requestContext);
        } catch (Exception e) {
            instance.traceListener.set(instance);
            log.error("The provided listener has thrown an exception and has been removed: "
                    + e.getLocalizedMessage());
            AgentDataController.sendAgentData(e, null);
        }

        try {
            traceContext.requestContext.put(NR_SPAN_ID_ATTRIBUTE, traceContext.tracePayload.getSpanId());
            instance.traceListener.get().onSpanCreated(traceContext.requestContext);
        } catch (Exception e) {
            instance.traceListener.set(instance);
            log.error("The provided listener has thrown an exception and has been removed: "
                    + e.getLocalizedMessage());
            AgentDataController.sendAgentData(e, new HashMap<String, Object>());
        }
    }

    static String generateRandomBytes(int minLength) {
        String randomBytes = "";
        while (randomBytes.length() < minLength) {
            randomBytes += UUID.randomUUID().toString().replace("-", "");
        }
        return randomBytes.substring(0, minLength);
    }

    /**
     * Generate a long (16 byte/32 char) entity suitable for trace IDs
     *
     * @return GUID as String
     */
    public static String generateTraceId() {
        return generateRandomBytes(32);
    }

    /**
     * Generate a short (8 byte/16 char) entity suitable for parent/spans IDs
     *
     * @return GUID as String
     */
    public static String generateSpanId() {
        return generateRandomBytes(16);
    }

    public static long generateNormalizedTimestamp() {
        return System.currentTimeMillis();
    }

    public static void setDistributedTraceListener(TraceListener listener) {
        instance.setTraceListener(listener);
    }

}