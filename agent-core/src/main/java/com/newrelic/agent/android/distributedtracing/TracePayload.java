/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.distributedtracing;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.newrelic.agent.android.Agent;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;

/**
 * The original, proprietary header that is still sent to maintain backward compatibility
 * with prior New Relic agents.
 */
public class TracePayload implements TraceHeader {
    private static final AgentLog log = AgentLogManager.getAgentLog();

    /**
     * As defined in https://source.datanerd.us/agents/agent-specs/blob/master/distributed_tracing/New-Relic-Payload.md#payload-fields
     */
    public static final String TRACE_PAYLOAD_HEADER = "newrelic";

    static final String VERSION_KEY = "v";
    static final String DATA_KEY = "d";
    static final String PAYLOAD_TYPE_KEY = "ty";
    static final String ACCOUNT_ID_KEY = "ac";
    static final String APP_ID_KEY = "ap";
    static final String GUID_KEY = "id";
    static final String TRACE_ID_KEY = "tr";
    static final String TRUST_ACCOUNT_KEY = "tk";
    static final String TIMESTAMP_KEY = "ti";

    static final String CALLER_TYPE = "Mobile";

    static final int MAJOR_VERSION = 0;
    static final int MINOR_VERSION = 2;

    final long timestampMs;
    final TraceContext traceContext;
    final String spanId;

    public TracePayload(TraceContext traceContext) {
        this.timestampMs = DistributedTracing.generateNormalizedTimestamp();
        this.traceContext = traceContext;
        this.spanId = (traceContext.tracePayload == null)
                ? traceContext.getParentId()
                : traceContext.tracePayload.spanId;
    }

    /**
     * https://source.datanerd.us/agents/agent-specs/blob/master/distributed_tracing/New-Relic-Payload.md#mobile-agent
     *
     * @return The payload as JSON object string
     */
    public JsonObject asJson() {
        JsonObject payload = new JsonObject();
        JsonArray version = new JsonArray();
        JsonObject data = new JsonObject();

        try {
            version.add(new JsonPrimitive(MAJOR_VERSION));
            version.add(new JsonPrimitive(MINOR_VERSION));

            data.add(PAYLOAD_TYPE_KEY, new JsonPrimitive(CALLER_TYPE));
            data.add(ACCOUNT_ID_KEY, new JsonPrimitive(traceContext.traceConfiguration.accountId));
            data.add(APP_ID_KEY, new JsonPrimitive(traceContext.traceConfiguration.applicationId));
            data.add(TRACE_ID_KEY, new JsonPrimitive(traceContext.traceId));
            data.add(GUID_KEY, new JsonPrimitive(spanId));
            data.add(TIMESTAMP_KEY, new JsonPrimitive(timestampMs));
            data.add(TRUST_ACCOUNT_KEY, new JsonPrimitive(traceContext.traceConfiguration.trustedAccountId));

            payload.add(VERSION_KEY, version);
            payload.add(DATA_KEY, data);

        } catch (Exception e) {
            log.error("Unable to create payload asJSON", e);
        }

        return payload;
    }

    String asBase64Json() {
        try {
            String payloadAsJson = asJson().toString();
            return Agent.getEncoder().encodeNoWrap(payloadAsJson.getBytes());
        } catch (Exception e) {
            log.error("asBase64Json: " + e.getLocalizedMessage());
        }

        return "";
    }

    public String getTraceId() {
        return traceContext.getTraceId();
    }

    public String getSpanId() {
        return spanId;
    }

    @Override
    public String getHeaderName() {
        return TRACE_PAYLOAD_HEADER;
    }

    @Override
    public String getHeaderValue() {
        return asBase64Json();
    }
}
