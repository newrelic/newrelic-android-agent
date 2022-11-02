/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.harvest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.newrelic.agent.android.harvest.type.HarvestableObject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AgentHealthExceptions extends HarvestableObject {
    private final static JsonArray keyArray = new JsonArray();

    private final Map<String, AgentHealthException> agentHealthExceptions = new ConcurrentHashMap<String, AgentHealthException>();

    public AgentHealthExceptions() {
        super();

        keyArray.add(new JsonPrimitive("ExceptionClass"));
        keyArray.add(new JsonPrimitive("Message"));
        keyArray.add(new JsonPrimitive("ThreadName"));
        keyArray.add(new JsonPrimitive("CallStack"));
        keyArray.add(new JsonPrimitive("Count"));
        keyArray.add(new JsonPrimitive("Extras"));
    }

    public void add(AgentHealthException exception) {
        final String aggregationKey = getKey(exception);
        synchronized (agentHealthExceptions) {
            final AgentHealthException healthException = agentHealthExceptions.get(aggregationKey);

            if (healthException == null) {
                agentHealthExceptions.put(aggregationKey, exception);
            } else {
                healthException.increment();
            }
        }
    }

    public void clear() {
        synchronized (agentHealthExceptions) {
            agentHealthExceptions.clear();
        }
    }

    public boolean isEmpty() {
        return agentHealthExceptions.isEmpty();
    }

    public Map<String, AgentHealthException> getAgentHealthExceptions() {
        return agentHealthExceptions;
    }

    /**
     * Aggregation key is ExceptionClass + first line of the callstack (a string) for now
     */
    public final String getKey(final AgentHealthException exception) {
        String key = this.getClass().getName();
        if (exception != null) {
            key = exception.getExceptionClass() + exception.getStackTrace()[0].toString();
        }
        return key;
    }

    @Override
    public JsonObject asJsonObject() {
        final JsonObject exceptions = new JsonObject();

        final JsonArray data = new JsonArray();

        for (AgentHealthException exception : agentHealthExceptions.values()) {
            data.add(exception.asJsonArray());
        }

        exceptions.add("Type", new JsonPrimitive("AgentErrors"));
        exceptions.add("Keys", keyArray);
        exceptions.add("Data", data);

        return exceptions;
    }
}
